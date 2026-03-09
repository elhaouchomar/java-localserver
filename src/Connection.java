
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.List;

import config.Config;
import http.HttpParser;
import http.HttpRequest;
import http.HttpResponse;
import http.HttpSerializer;
import utils.TimeoutTracker;

public class Connection {
    public SelectionKey key;
    public SocketChannel channel;

    /**
     * The list of Config objects that share the host:port binding this client
     * connected to. Server uses this to pick the right virtual host.
     */
    public final List<Config> configCandidates;

    /** Tracks inactivity; connection is closed after 30 seconds of silence. */
    public final TimeoutTracker timeout = new TimeoutTracker(30_000);

    private final ByteBuffer readBuffer = ByteBuffer.allocate(8192);
    private final ByteBuffer writeBuffer = ByteBuffer.allocate(8192);

    private final HttpParser parser = new HttpParser();
    private HttpSerializer serializer;

    private HttpRequest request;
    private HttpResponse response;

    public ConnectionState state = ConnectionState.READING;

    private byte[] pendingBytes;
    private int pendingOffset;

    public Connection(SocketChannel channel, SelectionKey key, List<Config> configCandidates) {
        this.channel = channel;
        this.key = key;
        this.configCandidates = configCandidates;
    }

    public HttpRequest getRequest() {
        return request;
    }

    public HttpResponse getResponse() {
        return response;
    }

    public void setResponse(HttpResponse response) {
        this.response = response;
        this.serializer = new HttpSerializer(response);
    }

    public void read() throws IOException {
        if (state != ConnectionState.READING)
            return;
        // System.out.println("Reading from channel...");
        int bytes = channel.read(readBuffer);

        if (bytes == -1) {
            close();
            return;
        }

        if (bytes > 0) timeout.touch();

        readBuffer.flip();

        HttpRequest req = parser.tryParse(readBuffer);

        if (req == null) {
            readBuffer.compact();
            return;
        }

        this.request = req;
        // this.readBuffer.clear();
        // System.out.println("===== HTTP REQUEST =====");
        System.out.println(req);
        readBuffer.compact();
        // System.out.println("========================");

        // prepare a temp response to test
        // this.response = http.HttpResponseFactory.ok("Hello from NIO server!\nHello from NIO server!\n" + //
        //         "Hello from NIO server!\nHello from NIO server!\n" + //
        //         "Hello from NIO server!\nHello from NIO server!\n" + //
        //         "Hello from NIO server!\nHello from NIO server!\n" + //
        //         "Hello from NIO server!\nHello from NIO server!\n" + //
        //         "Hello from NIO server!\nHello from NIO server!\n");
        // this.serializer = new HttpSerializer(response);

        
    }

    public void close() {
        state = ConnectionState.CLOSED;
        try {
            key.cancel();
            channel.close();
        } catch (IOException ignored) {
        }
    }

    public void write() throws IOException {
        if (state != ConnectionState.WRITING)
            return;

        // If buffer has data, try flushing first
        if (writeBuffer.hasRemaining() && writeBuffer.limit() != writeBuffer.capacity()) {
            channel.write(writeBuffer);
            if (writeBuffer.hasRemaining()) {
                return; // socket backpressure
            }
            writeBuffer.clear(); // back to WRITE MODE
        }

        // Ask serializer for next bytes
        if (pendingBytes == null) {
            pendingBytes = serializer.nextBytes();
            pendingOffset = 0;

            if (pendingBytes == null) {
                return;
            }

            if (pendingBytes.length == 0) {
                finishResponse();
                return;
            }
        }


        while (pendingOffset < pendingBytes.length) {
            int toCopy = Math.min(writeBuffer.remaining(), pendingBytes.length - pendingOffset);
            writeBuffer.put(pendingBytes, pendingOffset, toCopy);
            pendingOffset += toCopy;

            writeBuffer.flip();
            channel.write(writeBuffer);

            if (writeBuffer.hasRemaining()) {
                // System.out.println("--- write buffer full, wait for next OP_WRITE ---");
                return;
            }

            writeBuffer.clear();
        }

        pendingBytes = null;

    }

    private void finishResponse() throws IOException {
        // System.out.println("Response fully sent.");
        if (response.keepAlive) {
            // reset for next request
            parser.reset();
            serializer = null;
            response = null;
            request = null;

            state = ConnectionState.READING;
            key.interestOps(SelectionKey.OP_READ);
        } else {
            close();
        }
    }
}
