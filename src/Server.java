import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.List;

import config.Config;
import handler.HandlerResolver;
import http.HttpResponse;
import http.HttpResponseFactory;
import router.RouteMatch;
import router.Router;

public class Server {

    private Selector selector;
    private ServerSocketChannel serverChannel;
    // private final int port;
    private  Router router;
    private HandlerResolver handlerResolver;
    private List<Config> configs;

    public Server(List<Config> configs) {
        this.configs = configs;
    }

    public void start() throws IOException {
        initServer();
        eventLoop();
    }

    private void initServer() throws IOException {
        selector = Selector.open();

        serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        // serverChannel.bind(new InetSocketAddress(port));

        serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        // System.out.println("Server listening on port " + port);
    }

    private void eventLoop() throws IOException {
        while (true) {
            this.selector.select();

            Iterator<SelectionKey> keys = selector.selectedKeys().iterator();

            while (keys.hasNext()) {
                SelectionKey key = keys.next();
                keys.remove();

                if (!key.isValid())
                    continue;

                if (key.isAcceptable()) {
                    handleAccept(key);
                }

                Connection conn = (Connection) key.attachment();

                if (conn == null || conn.state == ConnectionState.CLOSED)
                    continue;

                if (conn.state == ConnectionState.READING && key.isReadable()) {
                    conn.read();
                    if (conn.getRequest() != null) {
                        RouteMatch match = router.match(conn.getRequest());
                        if(match == null) {
                            HttpResponse response = HttpResponseFactory.notFound();
                            conn.setResponse(response);
                            conn.state = ConnectionState.WRITING;
                            conn.key.interestOps(SelectionKey.OP_WRITE);
                            continue;
                        }
                        
                        HttpResponse response = handlerResolver.handle(conn.getRequest(), match);
                        conn.setResponse(response);
                        conn.state = ConnectionState.WRITING;
                        conn.key.interestOps(SelectionKey.OP_WRITE);
                    }
                }

                if (conn.state == ConnectionState.WRITING && key.isWritable() && conn.getResponse() != null) {
                    conn.write();
                }

            }
        }

    }

    private void handleAccept(SelectionKey key) throws IOException {
        ServerSocketChannel server = (ServerSocketChannel) key.channel();
        SocketChannel client = server.accept();

        if (client == null)
            return;

        client.configureBlocking(false);

        SelectionKey selectionKey = client.register(selector, SelectionKey.OP_READ);

        Connection conn = new Connection();
        conn.channel = client;
        conn.key = selectionKey;

        selectionKey.attach(conn);

        System.out.println("New client connected: " + client);
    }

}
