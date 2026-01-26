## 👤 MEMBER 1 — Core Server / NIO Engine  
**Role:** Low-level backend (Sockets, Selector, stability)

### Files to work on
```
src/
├── Main.java
├── Server.java
├── Connection.java
├── ConnectionState.java
└── utils/TimeoutTracker.java
```


### Responsibilities
- Create `Selector`
- Accept connections
- Register channels (READ / WRITE)
- Maintain **one Connection object per client**
- Handle:
  - Partial reads
  - Partial writes
  - Client timeouts
- State machine:
```
READING → PROCESSING → WRITING → CLOSING
```

- Ensure server **never crashes** (catch all exceptions)

### Exposed Contract
```java
class Connection {
  SocketChannel channel;
  ByteBuffer readBuffer;
  ByteBuffer writeBuffer;
  HttpRequest request;
  HttpResponse response;
  ConnectionState state;
}
```
## 👤 MEMBER 2 — HTTP Protocol & Routing
**Role:** Brain of the HTTP server

Files to work on:
```
src/
├── Router.java
├── http/
│   ├── HttpRequest.java
│   ├── HttpResponse.java
│   ├── HttpParser.java
│   └── HttpSerializer.java
└── error/
    └── ErrorResponseFactory.java
```
### Responsibilities
- Parse raw bytes → **HttpRequest**

    - Method

    - Path

    - Headers

    - Body

    - Chunked / unchunked

- Validate HTTP rules

- Build HttpResponse

- Routing:

    - GET / POST / DELETE

    - Static files

    - Method not allowed (405)

    - Not found (404)

- Generate error responses (400, 403, 404, 405, 413, 500)

### Exposed Contract
```Java
HttpRequest request = HttpParser.parse(ByteBuffer buffer);
HttpResponse response = Router.handle(request, config);
ByteBuffer output = HttpSerializer.serialize(response);
```
## 👤 MEMBER 3 — Config, CGI, Sessions
**Role**: Features & extensibility

Files to work on
```
src/
├── config/
│   ├── Config.java
│   └── ConfigLoader.java
├── cgi/
│   └── CGIHandler.java
└── utils/
    ├── Session.java
    └── Cookie.java
```
### Responsibilities
- Manual parsing of config.json

- Validate config at startup

- Route configuration:

    - Root paths

    - Allowed methods

    - Redirections

    - Upload size limits

    - Directory listing

    - Default files

    - CGI extensions

- Execute CGI via ProcessBuilder

- Manage cookies & sessions


### Exposed Contract
```Java
Config config = ConfigLoader.load("config.json");
CGIHandler.execute(request, routeConfig);
Session session = Session.fromRequest(request);
```
## Shared Base Models
These files define the contract between all members:

### HttpRequest
```Java
public class HttpRequest {
    public String method;
    public String path;
    public String version;
    public Map<String, String> headers;
    public byte[] body;
}
```

### HttpResponse
```Java
public class HttpResponse {
    public int status;
    public Map<String, String> headers;
    public byte[] body;
}
```

###Config
```Java
public class Config {
    public int clientMaxBodySize;
    public Map<String, RouteConfig> routes;
}
```