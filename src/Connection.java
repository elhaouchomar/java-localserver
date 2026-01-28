
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import http.HttpParser;
import http.HttpRequest;

public class Connection {
    public SelectionKey key;
    public SocketChannel channel;

    private final ByteBuffer readBuffer = ByteBuffer.allocate(8192);
    private final ByteBuffer writeBuffer = ByteBuffer.allocate(8192);

    private final HttpParser parser = new HttpParser();
    private HttpRequest request;

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
            // IMPORTANT: prepare buffer for next read
            readBuffer.compact();
            return;
        }

        this.request = req;

        System.out.println("===== HTTP REQUEST =====");
        System.out.println(req);
        System.out.println("========================");

        // For now, close after one request
        close();
    }

    public void close() {
        state = ConnectionState.CLOSED;
        try {
            key.cancel();
            channel.close();
        } catch (IOException ignored) {
        }
    }
}
