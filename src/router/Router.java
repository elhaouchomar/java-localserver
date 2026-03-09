package router;

import java.nio.file.Path;
import java.nio.file.Paths;

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

        // longest-prefix match over the list of routes
        for (RouteConfig route : config.routes) {
            String prefix = route.path;
            if (prefix == null) continue;

            if (requestPath.equals(prefix) || requestPath.startsWith(prefix + "/")) {
                if (bestPrefix == null || prefix.length() > bestPrefix.length()) {
                    bestPrefix = prefix;
                    bestRoute = route;
                }
            }
        }

        if (bestRoute == null) {
            return null; // 404
        }

        // resolve filesystem path
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