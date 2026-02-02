package handler;

import java.nio.file.Files;
import java.nio.file.Path;

import config.RouteConfig;
import http.HttpRequest;
import http.HttpResponse;
import http.HttpResponseFactory;
import router.RouteMatch;

public class HandlerResolver {
    public HttpResponse handle(HttpRequest request, RouteMatch match) {

        RouteConfig route = match.getRoute();
        Path path = match.getResolvedPath();

        // method check
        if (!route.allowedMethods.contains(request.getMethod())) {
            return HttpResponseFactory.methodNotAllowed();
        }

        // redirect
        if (route.redirectTo != null) {
            // return redirect(route.redirectTo);
        }

        // filesystem
        if (Files.isDirectory(path)) {
            return new DirectoryHandler().handle(request, match);
        }

        return new StaticFileHandler().handle(request, match);
    }

}
