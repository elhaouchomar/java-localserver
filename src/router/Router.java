package router;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import config.Config;
import config.RouteConfig;
import http.HttpRequest;

public class Router {

    private final Config config;

    public Router(Config config) {
        this.config = config;
    }

    public RouteMatch match(HttpRequest request) {

        String requestPath = normalizePath(request.getPath(), true);

        RouteConfig bestRoute = null;
        String bestPrefix = null;

        // 1️⃣ longest-prefix match
        for (Map.Entry<String, RouteConfig> entry : config.routes.entrySet()) {
            String prefix = entry.getKey();

            if (requestPath.equals(prefix) || requestPath.startsWith(prefix + "/")) {
                if (bestPrefix == null || prefix.length() > bestPrefix.length()) {
                    bestPrefix = prefix;
                    bestRoute = entry.getValue();
                }
            }
        }

        if (bestRoute == null) {
            return null; // 404
        }

        // 2️⃣ resolve filesystem path
        Path resolvedPath = resolvePath(bestRoute, bestPrefix, requestPath);

        return new RouteMatch(bestRoute, resolvedPath, requestPath);
    }

    private Path resolvePath(RouteConfig route, String prefix, String requestPath) {
        String relative = requestPath.substring(prefix.length());
        if (relative.isEmpty()) {
            relative = "/";
        }

        return Paths.get(route.root).resolve(relative.substring(1)).normalize();
    }

    private String normalizePath(String path, boolean keepTrailingSlash) {
        if (path == null || path.isEmpty())
            return "/";
        if (!path.startsWith("/"))
            path = "/" + path;

        if (!keepTrailingSlash && path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        return path;
    }
}