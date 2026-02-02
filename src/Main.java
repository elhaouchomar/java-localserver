import java.util.Map;
import java.util.Set;

import config.Config;
import config.RouteConfig;
import router.Router;

public class Main {
    public static void main(String[] args) throws Exception {
        RouteConfig rootRoute = new RouteConfig();
        rootRoute.root = "www";
        rootRoute.allowedMethods = Set.of("GET", "HEAD");
        rootRoute.indexFile = "index.html";
        rootRoute.directoryListing = false;
        rootRoute.autoRedirect = true;

        RouteConfig imagesRoute = new RouteConfig();
        imagesRoute.root = "www/images";
        imagesRoute.allowedMethods = Set.of("GET", "HEAD");
        imagesRoute.directoryListing = true;
        imagesRoute.indexFile = "index.html";
        imagesRoute.autoRedirect = true;

        Config config = new Config();
        config.clientMaxBodySize = 1024 * 1024;
        config.routes = Map.of(
                "/", rootRoute,
                "/images", imagesRoute
        );

        Router router = new Router(config);

        Server server = new Server(8080, router);
        server.start();
    }
}
