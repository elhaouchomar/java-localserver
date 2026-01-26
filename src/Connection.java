import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

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
    }
}
