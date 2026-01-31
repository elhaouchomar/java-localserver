
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import http.HttpParser;
import http.HttpRequest;
import http.HttpResponse;
import http.HttpSerializer;

public class Connection {
    public SelectionKey key;
    public SocketChannel channel;

    private final ByteBuffer readBuffer = ByteBuffer.allocate(8192);
    private final ByteBuffer writeBuffer = ByteBuffer.allocate(8192);

    private final HttpParser parser = new HttpParser();
    private HttpSerializer serializer;

    private HttpRequest request;
    private HttpResponse response;

    public ConnectionState state = ConnectionState.READING;

    public Connection() {
    }

    public Connection(SocketChannel channel, SelectionKey key) {
        this.channel = channel;
        this.key = key;
    }

    public void read() throws IOException {
        int bytes = channel.read(readBuffer);

        if (bytes == -1) {
            close();
            return;
        }

        readBuffer.flip();

        HttpRequest req = parser.tryParse(readBuffer);

        if (req == null) {
            readBuffer.compact();
            return;
        }

        this.request = req;

        // System.out.println("===== HTTP REQUEST =====");
        System.out.println(req);
        // System.out.println("========================");

        // prepare a temp response to test
        this.response = http.HttpResponseFactory.ok("Hello from NIO server!");
        this.serializer = new HttpSerializer(response);

        state = ConnectionState.WRITING;
        key.interestOps(SelectionKey.OP_WRITE);
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
        if (writeBuffer.hasRemaining()) {
            System.out.println("Flushing remaining data...\n");
            channel.write(writeBuffer);
            if (writeBuffer.hasRemaining())
                return;
            writeBuffer.clear();
        }

        // Ask serializer for next bytes
        byte[] bytes = serializer.nextBytes();

        if (bytes == null) {
            // nothing ready yet
            return;
        }

        if (bytes.length == 0) {
            // response fully sent
            finishResponse();
            return;
        }

        System.out.println("data to write ==> " + new String(bytes));

        System.out.println(
                "bytes.length=" + bytes.length +
                        " remaining=" + writeBuffer.remaining() +
                        " position=" + writeBuffer.position() +
                        " limit=" + writeBuffer.limit() + "\n");


        // writeBuffer.clear();
        writeBuffer.put(bytes);
        writeBuffer.flip();
        channel.write(writeBuffer);
    }

    private void finishResponse() throws IOException {
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
