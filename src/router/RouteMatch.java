package router;

import config.Config;
import config.RouteConfig;
import java.nio.file.Path;

public final class RouteMatch {

    private final Config config;         // selected virtual-host config
    private final RouteConfig route;     // null if no route matched
    private final Path resolvedPath;     // null if no route matched
    private final String requestPath;    // URI path (/images/a.png)

    public RouteMatch(Config config, RouteConfig route, Path resolvedPath, String requestPath) {
        this.config = config;
        this.route = route;
        this.resolvedPath = resolvedPath;
        this.requestPath = requestPath;
    }

    public Config getConfig() { return config; }
    public RouteConfig getRoute() { return route; }
    public Path getResolvedPath() { return resolvedPath; }
    public String getRequestPath() { return requestPath; }
}
