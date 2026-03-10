# java-localserver — Project Lifecycle

Pure Java HTTP/1.1 server. No frameworks. Single process, single thread, fully non-blocking using `java.nio`.

---

## Build & Run

```
make all     # compile  → target/
make run     # compile + start (needs config.json in working dir)
make clean   # remove target/
```

---

## 1 · Startup

```
main()
  └─ ConfigLoader.loadConfig("config.json")   parse JSON → List<Config>
  └─ new Server(configs)
  └─ server.start()
       ├─ initServer()    open one ServerSocketChannel per unique host:port
       └─ eventLoop()     block forever on Selector
```

`ConfigLoader` is a hand-written strict JSON parser. It produces a `List<Config>`, one per `servers[]` entry. If two server blocks share a host:port, a single `ServerSocketChannel` serves both and the correct virtual host is chosen per-request by the `Host` header.

---

## 2 · Event Loop (single thread, forever)

```
selector.select(1_000ms)   ← blocks here waiting for I/O or timeout
checkTimeouts()            ← close idle connections (30 s)
Session.cleanup()          ← evict expired server-side sessions

for each ready SelectionKey:
    ACCEPTABLE  →  handleAccept()
    READABLE    →  handleRead()
    WRITABLE    →  handleWrite()
```

Every client socket is registered with the same `Selector`. There are no worker threads; every read, parse, route, and write happens on the selector thread.

---

## 3 · Accept — new client connects

```
handleAccept(key)
  ├─ server.accept()                        non-blocking accept
  ├─ client.register(selector, OP_READ)     watch for data
  └─ new Connection(channel, key, candidates)
       ├─ candidates  = List<Config> for this binding
       ├─ state       = READING
       └─ timeout     = TimeoutTracker(30 s)
```

`candidates` is the pre-built list of `Config` objects that share the binding. Virtual-host selection happens later, once the `Host` header is known.

---

## 4 · Read — receive the HTTP request

```
handleRead(conn)
  └─ conn.read()
       ├─ channel.read(readBuffer)          fill ByteBuffer from socket
       ├─ timeout.touch()                   reset idle timer
       └─ parser.tryParse(readBuffer)       incremental parser

HttpParser stages (resumable across multiple reads):
  REQUEST_LINE  →  method, path, query, version
  HEADERS       →  name:value pairs, detects Content-Length / Transfer-Encoding / Connection
  BODY          →  fixed-length  or  chunked
  COMPLETE      →  returns HttpRequest; parser.reset() for next request
  ERROR         →  sets errorCode (400 or 413); short-circuits to error response
```

The parser is incremental. If the socket delivers the request in fragments, `tryParse` returns `null` and the buffer is compacted. The next read picks up exactly where it left off.

---

## 5 · Route — pick config + route

```
new Router(conn.configCandidates).match(request)

Router.selectConfig(request)
  1. exact serverName / alias match on Host header
  2. is_default flag
  3. first declared config (catch-all)

Router.match(request)
  ├─ normalize request path
  ├─ longest-prefix match over config.routes (List<RouteConfig>)
  └─ returns RouteMatch { config, route, resolvedPath, requestPath }
       or   RouteMatch { config, null, … }   if no route matched
```

---

## 6 · Handle — produce the response

```
HandlerResolver.handle(request, match, config)
  │
  ├─ 405  method not in route.methods
  ├─ 3xx  route.redirect != null
  │
  ├─ CGI  file extension == route.cgiExtension
  │         CGIHandler.execute(interpreter, scriptPath, request)
  │           ├─ ProcessBuilder  with CGI env vars
  │           ├─ write request body → stdin
  │           ├─ waitFor(30 s)
  │           └─ parseCgiOutput()  →  status, headers, Set-Cookie list, body
  │
  ├─ DELETE   DeleteHandler   path-traversal check → Files.delete()
  ├─ POST     UploadHandler   multipart or raw body → Files.write()
  │
  ├─ Directory  DirectoryHandler
  │               ├─ auto-redirect  /dir → /dir/
  │               ├─ index file     serve indexFile if exists
  │               └─ directory listing  HTML <ul> of directory entries
  │
  └─ Static   StaticFileHandler
                path-traversal check → FileBodyProducer (streamed 8 KB/chunk)
```

Every handler returns an `HttpResponse`. The response holds:
- `status` + `reason`
- `headers` (`Map<String, String>` — one value per key)
- `setCookies` (`List<String>` — multiple Set-Cookie lines allowed)
- `bodyProducer` (`BodyProducer` — null, `FixedBodyProducer`, or `FileBodyProducer`)
- `keepAlive` flag

---

## 7 · Write — send the response

```
conn.setResponse(response)     creates HttpSerializer
conn.state = WRITING
key.interestOps(OP_WRITE)

handleWrite(conn) → conn.write()
  └─ HttpSerializer.nextBytes()
       HEADERS  →  "HTTP/1.1 200 OK\r\n" + headers + Set-Cookie lines + "\r\n"
       BODY     →  bodyProducer.nextChunk()  (loop until null)
                   if chunked: wrap each chunk in hex-size\r\n…\r\n
       DONE     →  empty byte[]

ByteBuffer writeBuffer (8 KB)  handles socket backpressure:
  if channel.write() can't flush everything → return, retry next OP_WRITE
```

---

## 8 · After the response

```
finishResponse()
  keepAlive == true  →  parser.reset(), clear request/response, state = READING
                         timeout.touch()  (fresh idle window for next request)
  keepAlive == false →  conn.close()  (cancel key, close channel)
```

HTTP/1.1 defaults to keep-alive. HTTP/1.0 or `Connection: close` closes after each response.

---

## 9 · Timeout sweep (every 1 s)

```
checkTimeouts()
  for each SelectionKey:
    if conn.timeout.isTimedOut()  →  conn.close()
  Session.cleanup()               →  evict expired server-side sessions
```

`TimeoutTracker` records `lastActivityAt`. It is `touch()`ed on read, after routing, and after keep-alive reset. Any connection idle for 30 s is closed.

---

## Data Flow Diagram

```
TCP client
    │
    │  bytes
    ▼
  Selector (single thread)
    │
    ├── OP_ACCEPT ──────────────────► Connection
    │                                  state: READING
    │                                  TimeoutTracker
    │                                  configCandidates
    │
    ├── OP_READ ────► readBuffer
    │                   └─► HttpParser
    │                         REQUEST_LINE
    │                         HEADERS
    │                         BODY (fixed / chunked)
    │                         └─► HttpRequest
    │
    │                ┌──────────────────────────────┐
    │                │          Router               │
    │                │  selectConfig (Host header)   │
    │                │  longest-prefix route match   │
    │                │  resolvedPath                 │
    │                └─────────────┬────────────────┘
    │                              │ RouteMatch
    │                              ▼
    │                ┌──────────────────────────────┐
    │                │       HandlerResolver         │
    │                │  CGI / DELETE / POST          │
    │                │  Directory / StaticFile       │
    │                └─────────────┬────────────────┘
    │                              │ HttpResponse
    │
    ├── OP_WRITE ───► writeBuffer
    │                   └─► HttpSerializer
    │                         HEADERS block
    │                         BODY chunks (8 KB each)
    │                         done → keepAlive or close
    │
    └── 1 s tick ──► checkTimeouts + Session.cleanup
```

---

## Class Map

```
src/
├─ Main.java                  entry point
├─ Server.java                Selector event loop, virtual-host binding
├─ Connection.java            per-client state (buffers, parser, serializer, timeout)
├─ ConnectionState.java       READING / WRITING / CLOSED
│
├─ config/
│   ├─ ConfigLoader.java      hand-written JSON parser → List<Config>
│   ├─ Config.java            host, ports, serverName, root, errorPages, routes
│   └─ RouteConfig.java       path, methods, root, cgiExtension, redirect, …
│
├─ router/
│   ├─ Router.java            virtual-host selection + longest-prefix route match
│   └─ RouteMatch.java        (config, route, resolvedPath, requestPath)
│
├─ http/
│   ├─ HttpParser.java        incremental NIO request parser
│   ├─ HttpRequest.java       method, path, query, headers, body, cookies
│   ├─ HttpResponse.java      status, headers, setCookies, bodyProducer
│   ├─ HttpResponseFactory.java  static builders (ok, redirect, errorResponse, …)
│   ├─ HttpSerializer.java    streams response bytes (headers → body chunks)
│   ├─ BodyProducer.java      interface: nextChunk() → byte[] or null
│   ├─ FixedBodyProducer.java wraps byte[] as a one-shot producer
│   └─ FileBodyProducer.java  streams a file in 8 KB chunks via InputStream
│
├─ handler/
│   ├─ RequestHandler.java    interface: handle(request, match) → HttpResponse
│   ├─ HandlerResolver.java   dispatches to the right handler
│   ├─ StaticFileHandler.java path-traversal guard + FileBodyProducer
│   ├─ DirectoryHandler.java  redirect / index file / directory listing
│   ├─ DeleteHandler.java     DELETE: path-traversal guard + Files.delete()
│   ├─ UploadHandler.java     POST: raw body or multipart/form-data
│   └─ PathUtils.java         isSafe(file, root) — shared traversal guard
│
├─ cgi/
│   └─ CGIHandler.java        ProcessBuilder CGI, env vars, 30 s timeout
│
└─ utils/
    ├─ Cookie.java            parse Cookie header / build Set-Cookie header
    ├─ Session.java           in-memory session store (UUID → attributes, 30 min TTL)
    └─ TimeoutTracker.java    touch() / isTimedOut()  for connection idle timeout
```

---

## Config Reference (`config.json`)

```jsonc
{
  "servers": [
    {
      "host": "127.0.0.1",
      "ports": [8081],                       // one channel per unique host:port
      "server_name": "localhost",            // matched against Host header
      "root": "./www",
      "is_default": true,                    // catch-all when Host doesn't match
      "client_max_body_size": 10485760,      // 0 = unlimited
      "error_pages": { "404": "./error_pages/404.html" },
      "routes": [
        {
          "path": "/",
          "methods": ["GET"],
          "root": "./www",
          "directory_listing": true
        },
        {
          "path": "/uploads",
          "methods": ["GET", "POST", "DELETE"],
          "root": "./www/uploads"
        },
        {
          "path": "/cgi-bin",
          "methods": ["GET", "POST"],
          "root": "./www/cgi-bin",
          "cgi_extension": ".py",
          "cgi_path": "/usr/bin/python3"
        }
      ]
    }
  ]
}
```

| Field | Effect |
|---|---|
| `ports` | multiple ports → one channel each |
| `server_name` | matched to `Host` header for virtual hosting |
| `is_default` | fallback when no name matches |
| `client_max_body_size` | 413 if `Content-Length` exceeds this |
| `error_pages` | custom HTML files for 4xx/5xx codes |
| `directory_listing` | generate `<ul>` file list |
| `cgi_extension` + `cgi_path` | run matching files via `ProcessBuilder` |
| `redirect` + `redirect_code` | 301/302/307 redirect |
| `index_file` | serve this file when a directory is requested |
