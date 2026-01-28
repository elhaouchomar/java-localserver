package http;

import java.util.HashMap;
import java.util.Map;

public class HttpRequest {
    public String method;
    public String path;
    public String version;

    public Map<String, String> headers = new HashMap<>();

    public int contentLength = -1; // from headers
    public byte[] body;

    public boolean keepAlive = true;
    public boolean isChunked = false;

    public void addHeader(String name, String value) {
        this.headers.put(name, value);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append(method).append(" ")
                .append(path).append(" ")
                .append(version).append("\n");

        for (var e : headers.entrySet()) {
            sb.append(e.getKey()).append(": ").append(e.getValue()).append("\n");
        }

        sb.append("\n");

        if (body != null) {
            sb.append("Body (").append(body.length).append(" bytes):\n");
            sb.append(new String(body));
        }

        return sb.toString();
    }

}
