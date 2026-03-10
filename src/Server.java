import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import config.Config;
import handler.HandlerResolver;
import utils.Session;
import http.HttpRequest;
import http.HttpResponse;
import http.HttpResponseFactory;
import router.RouteMatch;
import router.Router;

public class Server {

    /**
     * Unique key for a host:port binding.
     * Two configs with the same host and port share one ServerSocketChannel.
     */
    private record BindingKey(String host, int port) {
        @Override public String toString() { return host + ":" + port; }
    }

    private Selector selector;
    private final List<Config> configs;
    private final HandlerResolver handlerResolver = new HandlerResolver();

    // One ServerSocketChannel per unique host:port binding
    private final Map<ServerSocketChannel, BindingKey> channelBindingMap = new IdentityHashMap<>();

    // Candidate configs for each binding (used for virtual-host selection)
    private final Map<BindingKey, List<Config>> bindingConfigMap = new HashMap<>();

    public Server(List<Config> configs) {
        this.configs = configs;
    }

    public void start() throws IOException {
        initServer();
        eventLoop();
    }

    // ─── Initialization ──────────────────────────────────────────────────────

    private void initServer() throws IOException {
        selector = Selector.open();

        // 1. Group configs by their host:port bindings
        for (Config config : configs) {
            for (int port : config.ports) {
                BindingKey bk = new BindingKey(config.host, port);
                bindingConfigMap.computeIfAbsent(bk, k -> new ArrayList<>()).add(config);
            }
        }

        // 2. Open exactly one ServerSocketChannel per unique binding
        for (Map.Entry<BindingKey, List<Config>> entry : bindingConfigMap.entrySet()) {
            BindingKey bk = entry.getKey();
            List<Config> candidates = entry.getValue();
            try {
                ServerSocketChannel ch = ServerSocketChannel.open();
                ch.configureBlocking(false);
                ch.bind(new InetSocketAddress(bk.host(), bk.port()));
                ch.register(selector, SelectionKey.OP_ACCEPT);
                channelBindingMap.put(ch, bk);
                System.out.printf("[SERVER] Listening on %s  (%d virtual host(s))%n",
                        bk, candidates.size());
            } catch (IOException e) {
                System.err.printf("[SERVER] Failed to bind %s — %s%n", bk, e.getMessage());
            }
        }

        if (channelBindingMap.isEmpty()) {
            throw new IOException("No ports could be bound. Server cannot start.");
        }
    }

    // ─── Event Loop ──────────────────────────────────────────────────────────

    private void eventLoop() throws IOException {
        System.out.println("[SERVER] Event loop started.");

        while (true) {
            selector.select(1000); // 1-second timeout so we can run timeout checks
            checkTimeouts();

            Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
            while (keys.hasNext()) {
                SelectionKey key = keys.next();
                keys.remove();

                if (!key.isValid()) continue;

                try {
                    if (key.isAcceptable()) {
                        handleAccept(key);
                        continue;
                    }

                    Connection conn = (Connection) key.attachment();
                    if (conn == null || conn.state == ConnectionState.CLOSED) continue;

                    if (conn.state == ConnectionState.READING && key.isReadable()) {
                        handleRead(conn);
                    }

                    if (conn.state == ConnectionState.WRITING && key.isWritable()
                            && conn.getResponse() != null) {
                        conn.write();
                    }

                } catch (Exception e) {
                    System.err.println("[SERVER] Error on key: " + e.getMessage());
                    Object att = key.attachment();
                    if (att instanceof Connection c) c.close();
                }
            }
        }
    }

    // ─── Accept ──────────────────────────────────────────────────────────────

    private void handleAccept(SelectionKey key) throws IOException {
        ServerSocketChannel server = (ServerSocketChannel) key.channel();
        SocketChannel client = server.accept();
        if (client == null) return;

        client.configureBlocking(false);
        SelectionKey selectionKey = client.register(selector, SelectionKey.OP_READ);

        // Look up which binding this accept came from, then pass its candidates to
        // the connection so the virtual-host selection later needs no map lookup.
        BindingKey bk = channelBindingMap.get(server);
        List<Config> candidates = bindingConfigMap.get(bk);

        Connection conn = new Connection(client, selectionKey, candidates);
        selectionKey.attach(conn);

        System.out.printf("[SERVER] Client connected from %s  (binding %s)%n",
                client.getRemoteAddress(), bk);
    }

    // ─── Read / Route ─────────────────────────────────────────────────────────

    private void handleRead(Connection conn) throws IOException {
        conn.read();

        // Parser detected a protocol error or oversized payload before the
        // request was complete — respond immediately without routing.
        int parseError = conn.getParseError();
        if (parseError != 0) {
            Config cfg = conn.configCandidates.isEmpty() ? null : conn.configCandidates.get(0);
            HttpResponse errResp = HttpResponseFactory.errorResponse(cfg, parseError);
            errResp.keepAlive = false;
            conn.setResponse(errResp);
            conn.state = ConnectionState.WRITING;
            conn.key.interestOps(SelectionKey.OP_WRITE);
            return;
        }

        if (conn.getRequest() == null) return; // request not yet complete

        conn.timeout.touch();

        HttpRequest request = conn.getRequest();
        RouteMatch match = new Router(conn.configCandidates).match(request);

        HttpResponse response;
        if (match == null) {
            // No config could be selected at all
            response = HttpResponseFactory.notFound();
        } else {
            Config config = match.getConfig();
            if (config.maxBodySize > 0 && request.contentLength > config.maxBodySize) {
                response = HttpResponseFactory.errorResponse(config, 413);
            } else if (match.getRoute() == null) {
                response = HttpResponseFactory.errorResponse(config, 404);
            } else {
                try {
                    response = handlerResolver.handle(request, match, config);
                } catch (Exception e) {
                    System.err.println("[SERVER] Handler error: " + e.getMessage());
                    response = HttpResponseFactory.errorResponse(config, 500);
                }
            }
        }

        // Reset the inactivity timer now that the handler has finished.
        // This ensures the write phase and the next keep-alive idle window
        // always start with a full budget, regardless of how long the handler
        // (e.g. CGI) took.
        conn.timeout.touch();

        response.keepAlive = request.keepAlive;
        conn.setResponse(response);
        conn.state = ConnectionState.WRITING;
        conn.key.interestOps(SelectionKey.OP_WRITE);
    }

    // ─── Timeout sweep ───────────────────────────────────────────────────────

    private void checkTimeouts() {
        Set<SelectionKey> keys = selector.keys();
        for (SelectionKey key : keys) {
            Object att = key.attachment();
            if (att instanceof Connection conn
                    && conn.state != ConnectionState.CLOSED
                    && conn.timeout.isTimedOut()) {
                System.out.println("[SERVER] Connection timed out, closing.");
                conn.close();
            }
        }
        Session.cleanup();
    }
}
