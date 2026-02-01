package router;

import config.RouteConfig;
import java.nio.file.Path;

public final class RouteMatch {

    private final RouteConfig route;
    private final Path resolvedPath;     // filesystem path
    private final String requestPath;    // URI path (/images/a.png)

    public RouteMatch(RouteConfig route, Path resolvedPath, String requestPath) {
        this.route = route;
        this.resolvedPath = resolvedPath;
        this.requestPath = requestPath;
    }

    public RouteConfig getRoute() {
        return route;
    }

    public Path getResolvedPath() {
        return resolvedPath;
    }

    public String getRequestPath() {
        return requestPath;
    }
}
