package handler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

import config.RouteConfig;
import http.HttpRequest;
import http.HttpResponse;
import http.HttpResponseFactory;
import router.RouteMatch;

public class DirectoryHandler implements RequestHandler {
    @Override
    public HttpResponse handle(HttpRequest request, RouteMatch match) {
        RouteConfig route = match.getRoute();
        Path dir = match.getResolvedPath();
        String requestPath = match.getRequestPath();

        if (route.redirect != null) {
            int code = route.redirectCode > 0 ? route.redirectCode : 301;
            return HttpResponseFactory.redirect(code, route.redirect);
        }

        if (route.autoRedirect && !requestPath.endsWith("/")) {
            return HttpResponseFactory.redirect(requestPath + "/");
        }

        if (route.indexFile != null) {
            Path index = dir.resolve(route.indexFile).normalize();

            if (Files.exists(index) && Files.isRegularFile(index)) {
                return new StaticFileHandler().serveFile(index, route.root);
            }
        }

        if (route.directoryListing) {
            return listDirectory(requestPath, dir);
        }

        return HttpResponseFactory.forbidden();
    }

    private HttpResponse listDirectory(String requestPath, Path dir) {
        try {
            String body = Files.list(dir)
                    .map(p -> {
                        String name = p.getFileName().toString();
                        if (Files.isDirectory(p))
                            name += "/";
                        return "<li><a href=\"" + name + "\">" + name + "</a></li>";
                    })
                    .collect(Collectors.joining(
                            "\n",
                            "<html><body><h1>Index of " + requestPath + "</h1><ul>",
                            "</ul></body></html>"));

            return HttpResponseFactory.ok(body.getBytes(), "text/html; charset=utf-8");

        } catch (IOException e) {
            return HttpResponseFactory.internalServerError();
        }
    }

}
