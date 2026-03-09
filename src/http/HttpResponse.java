package http;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import utils.Cookie;

public class HttpResponse {

    public int status;
    public String reason;

    public Map<String, String> headers = new HashMap<>();
    /** Each entry becomes its own Set-Cookie response header line. */
    public List<String> setCookies = new ArrayList<>();
    public BodyProducer bodyProducer;

    public boolean keepAlive = true;

    public HttpResponse() {
    }

    private HttpResponse(Builder builder) {
        this.status = builder.statusCode;
        this.reason = builder.reason;
        this.headers = builder.headers;
        this.setCookies = builder.setCookies;
        this.bodyProducer = builder.bodyProducer;
    }

    public static class Builder {
        private int statusCode = 200;
        private String reason = "OK";
        private Map<String, String> headers = new LinkedHashMap<>();
        private List<String> setCookies = new ArrayList<>();
        private BodyProducer bodyProducer;

        public Builder status(int code, String reason) {
            this.statusCode = code;
            this.reason = reason;
            return this;
        }

        public Builder header(String key, String value) {
            headers.put(key, value);
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            if (headers == null)
                return this;

            for (Map.Entry<String, String> entry : headers.entrySet()) {
                this.headers.put(entry.getKey(), entry.getValue());
            }
            return this;
        }

        // Set body from String -> FixedBodyProducer
        public Builder body(String body) {
            if (body == null)
                body = "";
            byte[] data = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            this.bodyProducer = new FixedBodyProducer(data);
            headers.put("Content-Length", String.valueOf(data.length));
            return this;
        }


        

        // Set body from bytes -> FixedBodyProducer
        public Builder body(byte[] data) {
            if (data == null)
                data = new byte[0];
            this.bodyProducer = new FixedBodyProducer(data);
            headers.put("Content-Length", String.valueOf(data.length));
            return this;
        }

        public Builder body(byte[] data, String contentType) {
            body(data);
            if (contentType != null && !contentType.isEmpty()) {
                headers.put("Content-Type", contentType);
            }
            return this;
        }

        // Set body directly from a producer (for FileBodyProducer, CGIBodyProducer later)
        public Builder cookie(Cookie cookie) {
            setCookies.add(cookie.toSetCookieHeader());
            return this;
        }

        public Builder body(BodyProducer producer, String contentType) {
            this.bodyProducer = producer;
            if (contentType != null && !contentType.isEmpty()) {
                headers.put("Content-Type", contentType);
            }
            return this;
        }

        public HttpResponse build() {
            // If we have a producer but no Content-Length, we might use chunked
            if (bodyProducer != null && !headers.containsKey("Content-Length")) {
                headers.put("Transfer-Encoding", "chunked");
            }
            return new HttpResponse(this);
        }
    }
}
