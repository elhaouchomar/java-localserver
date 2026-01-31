package http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class HttpParser {
    public enum ParseStage {
        REQUEST_LINE,
        HEADERS,
        BODY,
        COMPLETE,
        ERROR
    }

    enum ChunkState {
        READ_SIZE,
        READ_DATA,
        READ_CRLF,
        DONE
    }

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

    public HttpRequest tryParse(ByteBuffer buffer) throws IOException {
        while (true) {
            switch (stage) {
                case REQUEST_LINE:
                    if (!parseRequestLine(buffer))
                        return null;
                    stage = ParseStage.HEADERS;
                    break;
                case HEADERS:
                    if (!parseHeaders(buffer))
                        return null;
                    // decide if body is needed
                    if (currentRequest.isChunked || currentRequest.contentLength > 0) {
                        stage = ParseStage.BODY;
                    } else {
                        stage = ParseStage.COMPLETE;
                    }
                    break;
                case BODY:
                    if (!parseBody(buffer))
                        return null;
                    // buffer.compact();
                    stage = ParseStage.COMPLETE;
                    break;
                case COMPLETE:
                    if (currentRequest.isChunked) {
                        currentRequest.body = chunkedBody.toByteArray();
                    } else if (currentRequest.contentLength > 0) {
                        bodyBuffer.flip();
                        currentRequest.body = bodyBuffer.array();
                    }

                    HttpRequest ready = currentRequest;
                    reset();
                    return ready;
                case ERROR:
                    return null;
            }
        }
    }

    private boolean parseRequestLine(ByteBuffer buffer) {
        int start = buffer.position();

        int lineEnd = -1;
        for (int i = start; i < buffer.limit() - 1; i++) {
            if (buffer.get(i) == '\r' && buffer.get(i + 1) == '\n') {
                lineEnd = i - start;
                break;
            }
        }

        if (lineEnd == -1) {
            return false;
        }

        // Extract bytes for the request line
        int length = lineEnd - start;
        byte[] lineBytes = new byte[length];
        buffer.get(lineBytes); // consumes the bytes
        buffer.get(); // consume \r
        buffer.get(); // consume \n

        // Extract the request line
        String requestLine = new String(lineBytes, StandardCharsets.US_ASCII);
        String[] parts = requestLine.split(" ");
        if (parts.length != 3) {
            stage = ParseStage.ERROR;
            return false;
        }

        currentRequest.method = parts[0];
        currentRequest.path = parts[1];
        currentRequest.version = parts[2];

        // buffer.compact();

        return true;
    }

    private boolean parseHeaders(ByteBuffer buffer) {
        int start = buffer.position();
        int end = -1;

        // Find \r\n\r\n
        for (int i = start; i < buffer.limit() - 3; i++) {
            if (buffer.get(i) == '\r' &&
                    buffer.get(i + 1) == '\n' &&
                    buffer.get(i + 2) == '\r' &&
                    buffer.get(i + 3) == '\n') {
                end = i;
                break;
            }
        }

        if (end == -1) {
            // headers not fully received yet
            return false;
        }

        int headersLength = end - start;
        byte[] headerBytes = new byte[headersLength];
        buffer.get(headerBytes); // consume headers
        buffer.get(); // \r
        buffer.get(); // \n
        buffer.get(); // \r
        buffer.get(); // \n

        String headersBlock = new String(headerBytes, StandardCharsets.US_ASCII);
        String[] lines = headersBlock.split("\r\n");


        for (String line : lines) {
            int colon = line.indexOf(':');
            if (colon <= 0) {
                stage = ParseStage.ERROR;
                return false;
            }

            String name = line.substring(0, colon).trim();
            String value = line.substring(colon + 1).trim();
            // System.out.println("Header: " + name + " = " + value);
            currentRequest.addHeader(name, value);

            // Detect important headers
            if (name.equalsIgnoreCase("Content-Length")) {
                try {
                    currentRequest.contentLength = Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    stage = ParseStage.ERROR;
                    return false;
                }
            }

            if (name.equalsIgnoreCase("Transfer-Encoding") &&
                    value.equalsIgnoreCase("chunked")) {
                currentRequest.isChunked = true;
            }

            if (name.equalsIgnoreCase("Connection") &&
                    value.equalsIgnoreCase("close")) {
                currentRequest.keepAlive = false;
            }
        }

        if (currentRequest.isChunked && currentRequest.contentLength > 0) {
            stage = ParseStage.ERROR;
            return false;
        }

        if (currentRequest.isChunked) {
            chunkState = ChunkState.READ_SIZE;
            chunkedBody = new ByteArrayOutputStream();
        } else if (currentRequest.contentLength > 0) {
            bodyBuffer = ByteBuffer.allocate(currentRequest.contentLength);
            bodyRead = 0;
        }

        // buffer.compact();

        return true;
    }

    private boolean parseBody(ByteBuffer buffer) throws IOException {
        // System.out.println("------------------ Parsing body------------------\n");
        // System.out.println(currentRequest.contentLength + " bytes expected.\n");
        // System.out.println(currentRequest.isChunked ? "Chunked encoding.\n" : "chuncked flag does not detected\n------------------------------------\n");
        if (currentRequest.isChunked) {
            // System.out.println("Parsing chunked body........");
            return parseChunkedBody(buffer);
        }

        if (currentRequest.contentLength > 0) {
            return parseFixedBody(buffer);
        }

        return true; // no body
    }

    private boolean parseChunkedBody(ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            switch (chunkState) {

                case READ_SIZE -> {
                    String line = readLine(buffer);
                    if (line == null)
                        return false;

                    try {
                        currentChunkSize = Integer.parseInt(line.trim(), 16);
                    } catch (NumberFormatException e) {
                        stage = ParseStage.ERROR;
                        return false;
                    }

                    currentChunkRead = 0;

                    if (currentChunkSize == 0) {
                        chunkState = ChunkState.READ_CRLF;
                    } else {
                        chunkState = ChunkState.READ_DATA;
                    }
                }

                case READ_DATA -> {
                    int remaining = currentChunkSize - currentChunkRead;
                    int toRead = Math.min(remaining, buffer.remaining());

                    byte[] data = new byte[toRead];
                    buffer.get(data);
                    chunkedBody.write(data);

                    currentChunkRead += toRead;

                    if (currentChunkRead == currentChunkSize) {
                        chunkState = ChunkState.READ_CRLF;
                    }
                }

                case READ_CRLF -> {
                    if (currentChunkSize > 0) {
                        // Just consume the CRLF after chunk data
                        if (buffer.remaining() < 2)
                            return false;

                        buffer.get(); // \r
                        buffer.get(); // \n

                        chunkState = ChunkState.READ_SIZE;
                        break;
                    }

                    // currentChunkSize == 0
                    // RFC: final chunk must be followed by an empty line
                    String line = readLine(buffer);
                    if (line == null)
                        return false;

                    // Optional: trailers would be here (ignored for now)
                    chunkState = ChunkState.DONE;
                    return true;
                }

                case DONE -> {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean parseFixedBody(ByteBuffer buffer) {
        // No body expected
        if (currentRequest.contentLength == 0) {
            return true;
        }

        // Allocate body buffer once
        if (bodyBuffer == null) {
            bodyBuffer = ByteBuffer.allocate(currentRequest.contentLength);
        }

        // How many bytes we still need
        int remaining = currentRequest.contentLength - bodyRead;

        // How many bytes are available in socket buffer
        int available = buffer.remaining();

        // Read the minimum
        int toRead = Math.min(remaining, available);

        // Copy bytes (NO parsing)
        int oldLimit = buffer.limit();
        buffer.limit(buffer.position() + toRead);
        bodyBuffer.put(buffer);
        buffer.limit(oldLimit);

        bodyRead += toRead;

        // Body complete?
        if (bodyRead == currentRequest.contentLength) {
            // bodyBuffer.flip(); // ready for app layer to read from
            return true;
        }

        return false;
    }

    private String readLine(ByteBuffer buffer) {
        int start = buffer.position();

        // Search for CRLF
        for (int i = start; i < buffer.limit() - 1; i++) {
            if (buffer.get(i) == '\r' && buffer.get(i + 1) == '\n') {

                int length = i - start;
                byte[] lineBytes = new byte[length];

                // Consume the line bytes
                buffer.get(lineBytes);

                // Consume CRLF
                buffer.get(); // \r
                buffer.get(); // \n

                return new String(lineBytes, StandardCharsets.US_ASCII);
            }
        }

        // No full line yet
        return null;
    }

    public void reset() {
        currentRequest = new HttpRequest();
        stage = ParseStage.REQUEST_LINE;

        // Reset body state
        chunkState = null;
        currentChunkSize = 0;
        currentChunkRead = 0;
        chunkedBody = null;

        bodyBuffer = null;
        bodyRead = 0;
    }

}