package http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class HttpParser {

    public enum ParseStage { REQUEST_LINE, HEADERS, BODY, COMPLETE, ERROR }
    enum ChunkState        { READ_SIZE, READ_DATA, READ_CRLF, DONE }

    /** Maximum bytes allowed for the header block (request line excluded). */
    private static final int MAX_HEADER_SIZE = 8192;

    /** Maximum allowed body size in bytes. 0 means unlimited. */
    private long maxBodySize = 0;

    /** Non-zero after a parse failure: 400 = bad request, 413 = payload too large. */
    private int errorCode = 0;

    private ParseStage stage = ParseStage.REQUEST_LINE;
    private HttpRequest currentRequest = new HttpRequest();

    // Chunked parsing state
    private ChunkState chunkState;
    private int currentChunkSize;
    private int currentChunkRead;
    private ByteArrayOutputStream chunkedBody;

    // Fixed-length parsing state
    private ByteBuffer bodyBuffer;
    private int bodyRead;

    public void setMaxBodySize(long max) { this.maxBodySize = max; }
    public int  getErrorCode()           { return errorCode; }

    public HttpRequest tryParse(ByteBuffer buffer) throws IOException {
        while (true) {
            switch (stage) {
                case REQUEST_LINE -> { if (!parseRequestLine(buffer)) return null; stage = ParseStage.HEADERS; }
                case HEADERS      -> {
                    if (!parseHeaders(buffer)) return null;
                    stage = (currentRequest.isChunked || currentRequest.contentLength > 0)
                            ? ParseStage.BODY : ParseStage.COMPLETE;
                }
                case BODY     -> { if (!parseBody(buffer)) return null; stage = ParseStage.COMPLETE; }
                case COMPLETE -> {
                    if (currentRequest.isChunked) {
                        currentRequest.body = chunkedBody.toByteArray();
                    } else if (currentRequest.contentLength > 0) {
                        bodyBuffer.flip();
                        currentRequest.body = bodyBuffer.array();
                    }
                    HttpRequest ready = currentRequest;
                    reset();
                    return ready;
                }
                case ERROR -> { return null; }
            }
        }
    }

    // ─── Stage parsers ────────────────────────────────────────────────────────

    private boolean parseRequestLine(ByteBuffer buffer) {
        int start = buffer.position();
        int lineEnd = -1;
        for (int i = start; i < buffer.limit() - 1; i++) {
            if (buffer.get(i) == '\r' && buffer.get(i + 1) == '\n') { lineEnd = i - start; break; }
        }
        if (lineEnd == -1) return false;

        byte[] lineBytes = new byte[lineEnd];
        buffer.get(lineBytes);
        buffer.get(); // \r
        buffer.get(); // \n

        String[] parts = new String(lineBytes, StandardCharsets.US_ASCII).split(" ");
        if (parts.length != 3) return fail(400);

        currentRequest.method  = parts[0];
        currentRequest.version = parts[2];

        int q = parts[1].indexOf('?');
        currentRequest.setPath((q == -1) ? parts[1] : parts[1].substring(0, q));
        currentRequest.setRawQuery((q == -1) ? null : parts[1].substring(q + 1));

        return true;
    }

    private boolean parseHeaders(ByteBuffer buffer) {
        int start = buffer.position();
        int end   = -1;
        for (int i = start; i < buffer.limit() - 3; i++) {
            if (buffer.get(i) == '\r' && buffer.get(i+1) == '\n' &&
                buffer.get(i+2) == '\r' && buffer.get(i+3) == '\n') { end = i; break; }
        }

        if (end == -1) {
            if (buffer.limit() - start >= MAX_HEADER_SIZE) return fail(400);
            return false;
        }

        int headersLength = end - start;
        if (headersLength > MAX_HEADER_SIZE) return fail(400);

        byte[] headerBytes = new byte[headersLength];
        buffer.get(headerBytes);
        buffer.get(); buffer.get(); buffer.get(); buffer.get(); // consume \r\n\r\n

        for (String line : new String(headerBytes, StandardCharsets.US_ASCII).split("\r\n")) {
            int colon = line.indexOf(':');
            if (colon <= 0) return fail(400);

            String name  = line.substring(0, colon).trim();
            String value = line.substring(colon + 1).trim();
            currentRequest.addHeader(name, value);

            if (name.equalsIgnoreCase("Content-Length")) {
                try {
                    currentRequest.contentLength = Integer.parseInt(value);
                    if (maxBodySize > 0 && currentRequest.contentLength > maxBodySize) return fail(413);
                } catch (NumberFormatException e) { return fail(400); }
            }
            if (name.equalsIgnoreCase("Transfer-Encoding") && value.equalsIgnoreCase("chunked"))
                currentRequest.isChunked = true;
            if (name.equalsIgnoreCase("Connection") && value.equalsIgnoreCase("close"))
                currentRequest.keepAlive = false;
        }

        if (currentRequest.isChunked && currentRequest.contentLength > 0) return fail(400);

        // HTTP/1.0 defaults to close unless the client explicitly opts in.
        if ("HTTP/1.0".equalsIgnoreCase(currentRequest.version) && currentRequest.keepAlive) {
            String conn = currentRequest.headers.get("Connection");
            currentRequest.keepAlive = conn != null && conn.equalsIgnoreCase("keep-alive");
        }

        if (currentRequest.isChunked) {
            chunkState   = ChunkState.READ_SIZE;
            chunkedBody  = new ByteArrayOutputStream();
        } else if (currentRequest.contentLength > 0) {
            bodyBuffer = ByteBuffer.allocate(currentRequest.contentLength);
            bodyRead   = 0;
        }

        return true;
    }

    private boolean parseBody(ByteBuffer buffer) throws IOException {
        if (currentRequest.isChunked)          return parseChunkedBody(buffer);
        if (currentRequest.contentLength > 0)  return parseFixedBody(buffer);
        return true;
    }

    private boolean parseChunkedBody(ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            switch (chunkState) {
                case READ_SIZE -> {
                    String line = readLine(buffer);
                    if (line == null) return false;
                    try {
                        currentChunkSize = Integer.parseInt(line.trim(), 16);
                    } catch (NumberFormatException e) { return fail(400); }
                    currentChunkRead = 0;
                    chunkState = (currentChunkSize == 0) ? ChunkState.READ_CRLF : ChunkState.READ_DATA;
                }
                case READ_DATA -> {
                    int toRead = Math.min(currentChunkSize - currentChunkRead, buffer.remaining());
                    byte[] data = new byte[toRead];
                    buffer.get(data);
                    chunkedBody.write(data);
                    currentChunkRead += toRead;
                    if (maxBodySize > 0 && chunkedBody.size() > maxBodySize) return fail(413);
                    if (currentChunkRead == currentChunkSize) chunkState = ChunkState.READ_CRLF;
                }
                case READ_CRLF -> {
                    if (currentChunkSize > 0) {
                        if (buffer.remaining() < 2) return false;
                        buffer.get(); buffer.get(); // consume \r\n after chunk data
                        chunkState = ChunkState.READ_SIZE;
                        break;
                    }
                    // Final chunk: consume the trailing empty line
                    String line = readLine(buffer);
                    if (line == null) return false;
                    chunkState = ChunkState.DONE;
                    return true;
                }
                case DONE -> { return true; }
            }
        }
        return false;
    }

    private boolean parseFixedBody(ByteBuffer buffer) {
        if (bodyBuffer == null)
            bodyBuffer = ByteBuffer.allocate(currentRequest.contentLength);

        int toRead  = Math.min(currentRequest.contentLength - bodyRead, buffer.remaining());
        int oldLimit = buffer.limit();
        buffer.limit(buffer.position() + toRead);
        bodyBuffer.put(buffer);
        buffer.limit(oldLimit);
        bodyRead += toRead;

        return bodyRead == currentRequest.contentLength;
    }

    // ─── Utilities ────────────────────────────────────────────────────────────

    /** Sets the error state and returns false — use as {@code return fail(400)}. */
    private boolean fail(int code) {
        errorCode = code;
        stage     = ParseStage.ERROR;
        return false;
    }

    private String readLine(ByteBuffer buffer) {
        int start = buffer.position();
        for (int i = start; i < buffer.limit() - 1; i++) {
            if (buffer.get(i) == '\r' && buffer.get(i + 1) == '\n') {
                byte[] lineBytes = new byte[i - start];
                buffer.get(lineBytes);
                buffer.get(); buffer.get(); // consume \r\n
                return new String(lineBytes, StandardCharsets.US_ASCII);
            }
        }
        return null;
    }

    public void reset() {
        currentRequest   = new HttpRequest();
        stage            = ParseStage.REQUEST_LINE;
        errorCode        = 0;
        chunkState       = null;
        currentChunkSize = 0;
        currentChunkRead = 0;
        chunkedBody      = null;
        bodyBuffer       = null;
        bodyRead         = 0;
    }
}
