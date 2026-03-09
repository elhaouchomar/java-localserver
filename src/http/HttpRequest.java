package http;

import java.util.HashMap;
import java.util.Map;
import utils.Cookie;
import utils.Session;

public class HttpRequest {
    public String method;
    private String path;
    public String version;
    private String rawQuery;

    public Map<String, String> headers = new HashMap<>();

    public int contentLength = 0; // from headers
    public byte[] body;

    public boolean keepAlive = true;
    public boolean isChunked = false;

    public void addHeader(String name, String value) {
        this.headers.put(name, value);
    }

    public String getMethod() {
        return this.method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getPath() {
        return this.path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getRawQuery() {
        return this.rawQuery;
    }

    public void setRawQuery(String rawQuery) {
        this.rawQuery = rawQuery;
    }

    /** Parsed cookies from the Cookie request header (lazy, cached). */
    private Map<String, String> parsedCookies;

    public Map<String, String> getCookies() {
        if (parsedCookies == null) {
            parsedCookies = Cookie.parse(headers.get("Cookie"));
        }
        return parsedCookies;
    }

    /**
     * Look up an existing session from the SESSION_ID cookie.
     * Returns null if there is no session cookie or the session has expired.
     */
    public Session getSession() {
        return Session.get(getCookies().get("SESSION_ID"));
    }

    /**
     * Look up an existing session or create a new one.
     * Callers must send the new session ID back as a Set-Cookie header.
     */
    public Session getOrCreateSession() {
        return Session.getOrCreate(getCookies().get("SESSION_ID"));
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("========== HTTP REQUEST ==========\n");

        // ───── Request Line ─────
        sb.append("----------Request Line----------\n\n");
        sb.append("  Method  : ").append(method).append("\n");
        sb.append("  Path    : ").append(path).append("\n");
        sb.append("  Version : ").append(version).append("\n");

        // ───── Headers ─────
        sb.append("\n----------Headers----------\n\n");
        if (headers.isEmpty()) {
            sb.append("  (none)\n");
        } else {
            for (var e : headers.entrySet()) {
                sb.append("  ")
                        .append(e.getKey())
                        .append(" : ")
                        .append(e.getValue())
                        .append("\n");
            }
        }

        // ───── Meta Data ─────
        sb.append("\n----------Meta Data----------\n\n");
        sb.append("  Content-Length : ").append(contentLength).append("\n");
        sb.append("  Chunked        : ").append(isChunked).append("\n");
        sb.append("  Keep-Alive     : ").append(keepAlive).append("\n");

        // ───── Body Info ─────
        sb.append("\n----------Body----------\n\n");
        if (body == null || body.length == 0) {
            sb.append("  (empty)\n");
        } else {
            int previewLen = Math.min(body.length, 512);
            sb.append("  Size    : ").append(body.length).append(" bytes\n");
            sb.append("  Preview : ");
            sb.append(new String(body, 0, previewLen));
            // sb.append(" ----------------\n");
            if (previewLen < body.length) {
                sb.append("  ... (truncated)");
            }
            sb.append("\n");
        }

        sb.append("==================================\n");

        return sb.toString();
    }

}
