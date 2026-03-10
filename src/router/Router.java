package router;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import config.Config;
import config.RouteConfig;
import http.HttpRequest;

public class Router {

    private final List<Config> candidates;

    /** Primary constructor: selects virtual host then matches route. */
    public Router(List<Config> candidates) {
        this.candidates = candidates;
    }

    /** Convenience constructor for single-config use (e.g. unit tests). */
    public Router(Config config) {
        this(List.of(config));
    }

    /**
     * Returns a RouteMatch where:
     *   - null               → no config could be selected (empty candidates)
     *   - match.getRoute() == null → config selected, but no route matched
     *   - otherwise         → config + route + resolved path all set
     */
    public RouteMatch match(HttpRequest request) {
        Config config = selectConfig(request);
        if (config == null) return null;

        String requestPath = normalizePath(request.getPath(), true);

        RouteConfig bestRoute = null;
        String bestPrefix = null;

        for (RouteConfig route : config.routes) {
            String prefix = route.path;
            if (prefix == null) continue;

            if (requestPath.equals(prefix) || prefix.equals("/") || requestPath.startsWith(prefix + "/")) {
                if (bestPrefix == null || prefix.length() > bestPrefix.length()) {
                    bestPrefix = prefix;
                    bestRoute = route;
                }
            }
        }

        if (bestRoute == null) {
            // Config was found but no route matched — caller uses config for error pages
            return new RouteMatch(config, null, null, requestPath);
        }

        Path resolvedPath = resolvePath(bestRoute, bestPrefix, requestPath);
        return new RouteMatch(config, bestRoute, resolvedPath, requestPath);
    }

    // ── Virtual-host selection ────────────────────────────────────────────────

    private Config selectConfig(HttpRequest request) {
        if (candidates == null || candidates.isEmpty()) return null;

        String hostHeader = request.headers.get("Host");

        // 1. Exact serverName / alias match
        for (Config c : candidates) {
            if (c.matchesHostname(hostHeader)) return c;
        }

        // 2. isDefault flag
        for (Config c : candidates) {
            if (c.isDefault) return c;
        }

        // 3. Catch-all: first declared
        return candidates.get(0);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Path resolvePath(RouteConfig route, String prefix, String requestPath) {
        String relative = requestPath.substring(prefix.length());
        if (relative.isEmpty() || relative.equals("/")) {
            return Paths.get(route.root).normalize();
        }
        if (relative.startsWith("/")) relative = relative.substring(1);
        return Paths.get(route.root).resolve(relative).normalize();
    }

    private String normalizePath(String path, boolean keepTrailingSlash) {
        if (path == null || path.isEmpty()) return "/";
        if (!path.startsWith("/")) path = "/" + path;
        if (!keepTrailingSlash && path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }
}