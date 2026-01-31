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

    public static HttpResponse noContent() {
        return new HttpResponse.Builder()
                .status(204, "No Content")
                .build();
    }
}
