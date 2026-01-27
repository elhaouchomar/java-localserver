import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

import http.HttpParser;
import http.HttpParser.ParsingStatus;
import http.HttpRequest;
import http.HttpResponse;

public class Connection {
    public SocketChannel channel;

    // Buffers
    public ByteBuffer readBuffer;
    public ByteBuffer writeBuffer;

    // HTTP layer objects (filled later)
    public HttpRequest request;
    public HttpResponse response;

    public ConnectionState state;

    // Timestamp for timeout handling
    public long lastActivityTime;

    public Connection(SocketChannel channel) {
        this.channel = channel;
        this.readBuffer = ByteBuffer.allocate(8192);
        this.writeBuffer = ByteBuffer.allocate(8192);
        this.state = ConnectionState.READING;
        this.lastActivityTime = System.currentTimeMillis();
        // HttpParser is now a static utility, no instance needed
    }

    public void read() throws IOException {
        int bytesRead = channel.read(this.readBuffer);
        if (bytesRead == -1) { // Client closed connection
            this.state = ConnectionState.CLOSING;
            return;
        }

        if (this.request == null) {
            this.request = new HttpRequest(); // Start a new request if none is active
        }

        // Use static parse method
        ParsingStatus status = HttpParser.parse(this.readBuffer, this.request);

        if (status == ParsingStatus.COMPLETE) {
            this.state = ConnectionState.PROCESSING;
            // Handle pipelined data: If unparsedData still contains data, it's for the next request
            if (this.request.unparsedData.length() > 0) {
                // Move remaining data to the beginning of readBuffer for the next request
                // First, make sure readBuffer is empty or has been compacted
                this.readBuffer.clear();
                byte[] remainingBytes = this.request.unparsedData.toString().getBytes(StandardCharsets.UTF_8);
                this.readBuffer.put(remainingBytes);
                this.readBuffer.flip(); // Prepare for reading by the next HttpParser.parse call

                System.out.println("Pipelined data detected and moved to readBuffer.");

                // Reset HttpRequest for the next request, but keep the unparsedData as current readBuffer
                HttpRequest nextRequest = new HttpRequest();
                nextRequest.unparsedData.append(this.request.unparsedData.toString());
                nextRequest.parsingStatus = ParsingStatus.INCOMPLETE_REQUEST_LINE;
                this.request = nextRequest; // Assign the next request to this connection
            } else {
                // No pipelined data, reset request for next use
                this.request = null;
            }

        } else if (status == ParsingStatus.ERROR) {
            this.state = ConnectionState.CLOSING; // Close connection on parsing error
            System.err.println("HTTP parsing error, closing connection.");
            this.request = null; // Discard malformed request
        }
        // If INCOMPLETE, stay in READING state and wait for more data
    }

    public void write() throws IOException {
        channel.write(this.writeBuffer);
    }
}
