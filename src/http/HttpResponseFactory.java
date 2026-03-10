package http;

import config.Config;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class HttpResponseFactory {
    private HttpResponseFactory() {}

    // ─── Plain-text error responses ───────────────────────────────────────────

    public static HttpResponse notFound()            { return plainText(404, "Not Found",             "404 Not Found"); }
    public static HttpResponse forbidden()           { return plainText(403, "Forbidden",              "403 Forbidden"); }
    public static HttpResponse methodNotAllowed()    { return plainText(405, "Method Not Allowed",     "405 Method Not Allowed"); }
    public static HttpResponse payloadTooLarge()     { return plainText(413, "Payload Too Large",      "413 Payload Too Large"); }
    public static HttpResponse internalServerError() { return plainText(500, "Internal Server Error",  "500 Internal Server Error"); }
    public static HttpResponse badRequest(String msg){ return plainText(400, "Bad Request",             msg); }

    private static HttpResponse plainText(int code, String reason, String body) {
        return new HttpResponse.Builder()
                .status(code, reason)
                .header("Content-Type", "text/plain; charset=utf-8")
                .body(body)
                .build();
    }

    // ─── Success responses ────────────────────────────────────────────────────

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

    public static HttpResponse noContent() {
        return new HttpResponse.Builder()
                .status(204, "No Content")
                .build();
    }

    // ─── Redirects ────────────────────────────────────────────────────────────

    public static HttpResponse redirect(String location) {
        return redirect(301, location);
    }

    public static HttpResponse redirect(int code, String location) {
        String reason = switch (code) {
            case 302 -> "Found";
            case 307 -> "Temporary Redirect";
            default  -> "Moved Permanently";
        };
        return new HttpResponse.Builder()
                .status(code, reason)
                .header("Location", location)
                .body("")
                .build();
    }

    // ─── Config-aware error response ──────────────────────────────────────────

    /**
     * Returns a custom error page from {@code config} if configured,
     * otherwise falls back to a plain-text response.
     */
    public static HttpResponse errorResponse(Config config, int code) {
        if (config != null && config.errorPages.containsKey(code)) {
            String pagePath = config.errorPages.get(code);
            Path filePath = Path.of(pagePath).normalize();
            if (Files.exists(filePath) && Files.isRegularFile(filePath)) {
                try {
                    byte[] data = Files.readAllBytes(filePath);
                    return new HttpResponse.Builder()
                            .status(code, reasonPhrase(code))
                            .body(data, "text/html; charset=utf-8")
                            .build();
                } catch (IOException ignored) {}
            }
        }
        return switch (code) {
            case 400 -> badRequest("400 Bad Request");
            case 403 -> forbidden();
            case 404 -> notFound();
            case 405 -> methodNotAllowed();
            case 413 -> payloadTooLarge();
            default  -> internalServerError();
        };
    }

    private static String reasonPhrase(int code) {
        return switch (code) {
            case 400 -> "Bad Request";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 413 -> "Payload Too Large";
            default  -> "Internal Server Error";
        };
    }
}
