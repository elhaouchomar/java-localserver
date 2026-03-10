import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
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
        if (conn.getRequest() == null) return; // request not yet complete

        conn.timeout.touch();

        HttpRequest request = conn.getRequest();
        Config config = selectConfig(conn.configCandidates, request);

        HttpResponse response;
        if (config == null) {
            response = HttpResponseFactory.notFound();
        } else if (config.maxBodySize > 0 && request.contentLength > config.maxBodySize) {
            response = buildErrorResponse(config, 413);
        } else {
            Router router = new Router(config);
            RouteMatch match = router.match(request);
            if (match == null) {
                response = buildErrorResponse(config, 404);
            } else {
                try {
                    response = handlerResolver.handle(request, match, config);
                } catch (Exception e) {
                    System.err.println("[SERVER] Handler error: " + e.getMessage());
                    response = buildErrorResponse(config, 500);
                }
            }
        }

        response.keepAlive = request.keepAlive;
        conn.setResponse(response);
        conn.state = ConnectionState.WRITING;
        conn.key.interestOps(SelectionKey.OP_WRITE);
    }

    // ─── Virtual-host selection ───────────────────────────────────────────────

    /**
     * Pick the right Config from the candidates that share a host:port binding.
     *
     * Priority:
     *   1. Exact match on serverName / serverAliases via the Host header
     *   2. The config marked is_default among the candidates
     *   3. First config in the list (catch-all)
     *
     * If candidates is null or empty, returns null (→ 404).
     */
    private Config selectConfig(List<Config> candidates, HttpRequest request) {
        if (candidates == null || candidates.isEmpty()) return null;

        String hostHeader = request.headers.get("Host");

        // 1. Hostname match
        for (Config c : candidates) {
            if (c.matchesHostname(hostHeader)) return c;
        }

        // 2. Default server for this binding
        for (Config c : candidates) {
            if (c.isDefault) return c;
        }

        // 3. Catch-all: first declared
        return candidates.get(0);
    }

    // ─── Error responses ─────────────────────────────────────────────────────

    /**
     * Return an error response using the config's custom error page if defined,
     * falling back to the built-in plain-text responses.
     */
    private HttpResponse buildErrorResponse(Config config, int code) {
        if (config != null && config.errorPages.containsKey(code)) {
            String pagePath = config.errorPages.get(code);
            Path filePath = Path.of(pagePath).normalize();
            if (Files.exists(filePath) && Files.isRegularFile(filePath)) {
                try {
                    byte[] data = Files.readAllBytes(filePath);
                    return new http.HttpResponse.Builder()
                            .status(code, reasonPhrase(code))
                            .body(data, "text/html; charset=utf-8")
                            .build();
                } catch (IOException ignored) {
                }
            }
        }
        return switch (code) {
            case 400 -> HttpResponseFactory.badRequest("400 Bad Request");
            case 403 -> HttpResponseFactory.forbidden();
            case 404 -> HttpResponseFactory.notFound();
            case 405 -> HttpResponseFactory.methodNotAllowed();
            case 413 -> HttpResponseFactory.payloadTooLarge();
            default  -> HttpResponseFactory.internalServerError();
        };
    }

    private static String reasonPhrase(int code) {
        return switch (code) {
            case 400 -> "Bad Request";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 413 -> "Payload Too Large";
            default  -> "Internal Server Error";
        };
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
