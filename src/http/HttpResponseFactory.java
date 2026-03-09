package http;

public class HttpResponseFactory {
    private HttpResponseFactory() {
    }

    public static HttpResponse ok(String body) {
        return new HttpResponse.Builder()
                .status(200, "OK")
                .header("Content-Type", "text/plain; charset=utf-8")
                .body(body)
                .build();
    }

    public static HttpResponse ok(byte[] data, String contentType) {
        return new HttpResponse.Builder()
                .status(200, "OK")
                .body(data, contentType)
                .build();
    }

    public static HttpResponse ok(BodyProducer data, String contentType) {
        return new HttpResponse.Builder()
                .status(200, "OK")
                .body(data, contentType)
                .build();
    }

    public static HttpResponse ok(BodyProducer data, String contentType, long size) {
        return new HttpResponse.Builder()
                .status(200, "OK")
                .body(data, contentType)
                .header("Content-Length", String.valueOf(size))
                .build();
    }

    public static HttpResponse notFound() {
        return new HttpResponse.Builder()
                .status(404, "Not Found")
                .header("Content-Type", "text/plain; charset=utf-8")
                .body("404 Not Found")
                .build();
    }

    public static HttpResponse badRequest(String message) {
        return new HttpResponse.Builder()
                .status(400, "Bad Request")
                .header("Content-Type", "text/plain; charset=utf-8")
                .body(message)
                .build();
    }

    public static HttpResponse internalServerError() {
        return new HttpResponse.Builder()
                .status(500, "Internal Server Error")
                .header("Content-Type", "text/plain; charset=utf-8")
                .body("500 Internal Server Error")
                .build();
    }

    public static HttpResponse methodNotAllowed() {
        return new HttpResponse.Builder()
                .status(405, "Method Not Allowed")
                .header("Content-Type", "text/plain; charset=utf-8")
                .body("405 Method Not Allowed")
                .build();
    }

    public static HttpResponse forbidden() {
        return new HttpResponse.Builder()
                .status(403, "Forbidden")
                .header("Content-Type", "text/plain; charset=utf-8")
                .body("403 Forbidden")
                .build();
    }

    public static HttpResponse redirect(String location) {
        return redirect(301, location);
    }

    public static HttpResponse redirect(int code, String location) {
        String reason = code == 302 ? "Found" : code == 307 ? "Temporary Redirect" : "Moved Permanently";
        return new HttpResponse.Builder()
                .status(code, reason)
                .header("Location", location)
                .body("")
                .build();
    }

    public static HttpResponse payloadTooLarge() {
        return new HttpResponse.Builder()
                .status(413, "Payload Too Large")
                .header("Content-Type", "text/plain; charset=utf-8")
                .body("413 Payload Too Large")
                .build();
    }

    public static HttpResponse noContent() {
        return new HttpResponse.Builder()
                .status(204, "No Content")
                .build();
    }
}
