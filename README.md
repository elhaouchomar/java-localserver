# java-localserver

A pure Java HTTP/1.1 server built from scratch — no frameworks, no external dependencies. Uses Java NIO (non-blocking I/O) in a single-threaded, event-driven architecture.

## Features

- HTTP/1.1 with persistent connections (keep-alive)
- Virtual hosting via `Host` header
- Multiple ports per server
- Static file serving with path-traversal protection
- Directory listing and index file serving
- CGI support (Python, Bash, etc.)
- File uploads (raw body and multipart/form-data)
- DELETE handler
- Custom error pages (400, 403, 404, 405, 413, 500)
- Request body size limits
- Cookies and in-memory sessions
- 30-second connection idle timeout

## Requirements

- Java 11+
- `make`

## Build & Run

```bash
make all    # Compile source to target/
make run    # Compile + start server
make test   # Run all unit and integration tests
make clean  # Remove compiled classes
```

The server reads `config.json` from the working directory. Run from the project root.

## Configuration

`config.json` defines one or more virtual server blocks:

```json
{
  "servers": [
    {
      "host": "127.0.0.1",
      "ports": [8081],
      "server_name": "localhost",
      "root": "./www",
      "is_default": true,
      "client_max_body_size": 10485760,
      "error_pages": {
        "404": "./error_pages/404.html",
        "500": "./error_pages/500.html"
      },
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
          "root": "./www/uploads",
          "directory_listing": true
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

### Server fields

| Field | Type | Description |
|---|---|---|
| `host` | string | Bind address (e.g. `"127.0.0.1"`) |
| `ports` | array | Ports to listen on |
| `server_name` | string | Hostname for virtual-host matching |
| `root` | string | Document root directory |
| `is_default` | boolean | Catch-all when no hostname matches |
| `client_max_body_size` | number | Max request body in bytes (0 = unlimited) |
| `error_pages` | object | Map of status code to custom HTML file path |
| `routes` | array | List of route configurations |

### Route fields

| Field | Type | Description |
|---|---|---|
| `path` | string | URL prefix (longest match wins) |
| `methods` | array | Allowed HTTP methods |
| `root` | string | Route-specific document root |
| `directory_listing` | boolean | Generate HTML listing for directories |
| `default_file` | string | File to serve for directory requests (e.g. `"index.html"`) |
| `redirect` | string | Redirect target URL |
| `redirect_code` | number | Redirect status code: 301, 302, 307 (default 301) |
| `cgi_extension` | string | File extension that triggers CGI (e.g. `".py"`) |
| `cgi_path` | string | CGI interpreter path (e.g. `"/usr/bin/python3"`) |

## Virtual Hosting

Multiple server blocks can share the same `host:port`. The server selects the correct config using the `Host` request header:

1. Exact `server_name` match
2. Config marked `is_default`
3. First declared config

```json
{
  "servers": [
    { "ports": [8081], "server_name": "localhost",       "is_default": true  },
    { "ports": [8081], "server_name": "site2.localhost", "is_default": false }
  ]
}
```

One `ServerSocketChannel` is opened per unique `host:port` binding; all matching configs share it.

## Project Structure

```
src/
  Main.java                  # Entry point
  Server.java                # NIO selector loop, accept/read/write dispatch
  Connection.java            # Per-client state: buffers, parser, serializer, timeout
  ConnectionState.java       # Enum: READING, WRITING, CLOSED

  config/
    Config.java              # Server config model
    RouteConfig.java         # Route config model
    ConfigLoader.java        # Hand-written JSON parser

  http/
    HttpRequest.java         # Parsed request (method, path, headers, body)
    HttpResponse.java        # Response builder (status, headers, body producer)
    HttpResponseFactory.java # Static helpers: ok(), notFound(), redirect(), etc.
    HttpParser.java          # Incremental NIO parser (request line → headers → body)
    HttpSerializer.java      # Streams response bytes with backpressure support
    BodyProducer.java        # Interface: nextChunk() → byte[]
    FixedBodyProducer.java   # In-memory byte[] producer
    FileBodyProducer.java    # File streaming in 8 KB chunks

  router/
    Router.java              # Virtual-host selection + longest-prefix route match
    RouteMatch.java          # Result: config, route, resolvedPath

  handler/
    RequestHandler.java      # Interface: handle(request, match) → HttpResponse
    HandlerResolver.java     # Dispatch: redirect / CGI / DELETE / POST / dir / static
    StaticFileHandler.java   # Static file serving
    DirectoryHandler.java    # Index file, directory listing, auto-redirect
    DeleteHandler.java       # DELETE with path-traversal guard
    UploadHandler.java       # POST: multipart or raw body
    PathUtils.java           # isSafe(file, root) traversal check

  cgi/
    CGIHandler.java          # ProcessBuilder CGI with 30s timeout

  utils/
    Cookie.java              # Parse/build Cookie and Set-Cookie headers
    Session.java             # In-memory session store (UUID, 30-min TTL)
    TimeoutTracker.java      # Connection idle timeout (30s)

  test/                      # Unit and integration tests

error_pages/                 # Custom error HTML (400, 403, 404, 405, 413, 500)
www/                         # Default site root
config.json                  # Server configuration
makefile                     # Build script
```

## Architecture

The server is a single-threaded NIO event loop:

```
startup
  └─ load config.json → List<Config>
  └─ bind host:port per config → ServerSocketChannel per unique binding
  └─ register with Selector

event loop (forever)
  selector.select(1s timeout)
  checkTimeouts()          ← close connections idle > 30s
  Session.cleanup()        ← evict expired sessions

  OP_ACCEPT  → accept client, create Connection, register OP_READ
  OP_READ    → HttpParser.tryParse() → on complete: route + handle → WRITING
  OP_WRITE   → HttpSerializer.nextBytes() → on done: keep-alive or close
```

All I/O is non-blocking. A single `Selector` handles hundreds of concurrent connections without threads.

## HTTP Support

| Feature | Supported |
|---|---|
| HTTP/1.1 keep-alive | Yes (default) |
| HTTP/1.0 | Yes (close by default) |
| Transfer-Encoding: chunked | Yes |
| Content-Length | Yes |
| GET / POST / DELETE | Yes |
| CGI | Yes |
| Multipart uploads | Yes |
| HEAD / PUT | No |
| Range requests | No |
| TLS / HTTPS | No |

## Security

- Path-traversal protection on all file access (`PathUtils.isSafe()`)
- Configurable body size limit; returns 413 if exceeded
- 30-second idle timeout prevents resource exhaustion
- Session TTL of 30 minutes with automatic eviction

## Testing

```bash
make test
```

Tests are in `src/test/` and cover: HTTP parsing, serialization, config loading, routing, cookies, sessions, timeouts, multi-port binding, CGI, and end-to-end integration.
