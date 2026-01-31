package http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class HttpSerializer {
    public enum Stage {
        HEADERS,
        BODY,
        DONE
    }

    private final HttpResponse response;
    private Stage stage = Stage.HEADERS;

    private boolean chunked;
    private BodyProducer bodyProducer;

    public HttpSerializer(HttpResponse response) {
        this.response = response;
        this.bodyProducer = response.bodyProducer;
        this.chunked = "chunked".equalsIgnoreCase(response.headers.get("Transfer-Encoding"));
    }

    public Stage getStage() {
        return stage;
    }

    /**
     * Produces the next bytes to write to the socket.
     *
     * @return
     *         - byte[n > 0] : bytes to write
     *         - null : nothing to write now, try later
     *         - byte[0] : response fully sent
     */
    public byte[] nextBytes() throws IOException {
        switch (stage) {
            case HEADERS:
                stage = Stage.BODY;
                return serializeHeaders();

            case BODY:
                if (bodyProducer == null) {
                    stage = Stage.DONE;
                    return new byte[0];
                }

                byte[] chunk = bodyProducer.nextChunk();

                if (chunk == null) {
                    // body fully produced
                    if (chunked) {
                        stage = Stage.DONE;
                        return "0\r\n\r\n".getBytes(StandardCharsets.US_ASCII); // final chunk
                    } else {
                        stage = Stage.DONE;
                        return new byte[0];
                    }
                }

                if (chunk.length == 0) {
                    // No data available yet, but more is coming
                    return null;
                }

                if (chunked) {
                    return serializeChunk(chunk);
                } else {
                    return chunk;
                }

            case DONE:
                return new byte[0];
        }

        return new byte[0];
    }

    private byte[] serializeHeaders() {
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP/1.1 ").append(response.status).append(" ").append(response.reason).append("\r\n");
        response.headers.forEach((k, v) -> sb.append(k).append(": ").append(v).append("\r\n"));
        sb.append("\r\n");
        return sb.toString().getBytes(StandardCharsets.US_ASCII);
    }

    private byte[] serializeChunk(byte[] data) {
        String prefix = Integer.toHexString(data.length) + "\r\n";
        String suffix = "\r\n";
        byte[] prefixBytes = prefix.getBytes(StandardCharsets.US_ASCII);
        byte[] suffixBytes = suffix.getBytes(StandardCharsets.US_ASCII);

        byte[] result = new byte[prefixBytes.length + data.length + suffixBytes.length];
        System.arraycopy(prefixBytes, 0, result, 0, prefixBytes.length);
        System.arraycopy(data, 0, result, prefixBytes.length, data.length);
        System.arraycopy(suffixBytes, 0, result, prefixBytes.length + data.length, suffixBytes.length);

        return result;
    }
}