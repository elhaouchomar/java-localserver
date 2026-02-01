package http;

import java.util.HashMap;
import java.util.Map;

public class HttpRequest {
    public String method;
    private String path;
    public String version;

    public Map<String, String> headers = new HashMap<>();

    public int contentLength = 0; // from headers
    public byte[] body;

    public boolean keepAlive = true;
    public boolean isChunked = false;

    public void addHeader(String name, String value) {
        this.headers.put(name, value);
    }

    public String path() {
        return this.path;
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
