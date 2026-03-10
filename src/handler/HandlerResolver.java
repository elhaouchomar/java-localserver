package handler;

import java.nio.file.Files;
import java.nio.file.Path;

import cgi.CGIHandler;
import config.Config;
import config.RouteConfig;
import http.HttpRequest;
import http.HttpResponse;
import http.HttpResponseFactory;
import router.RouteMatch;

public class HandlerResolver {

    public HttpResponse handle(HttpRequest request, RouteMatch match, Config config) {

        RouteConfig route = match.getRoute();
        Path path = match.getResolvedPath();

        // Method check
        if (!route.methods.contains(request.getMethod())) {
            return HttpResponseFactory.errorResponse(config, 405);
        }

        // Redirect
        if (route.redirect != null) {
            int code = route.redirectCode > 0 ? route.redirectCode : 301;
            return HttpResponseFactory.redirect(code, route.redirect);
        }

        // CGI: check if the resolved file extension matches
        if (route.cgiExtension != null && route.cgiPath != null) {
            String fileName = path.getFileName() != null ? path.getFileName().toString() : "";
            if (fileName.endsWith(route.cgiExtension)) {
                return CGIHandler.execute(route.cgiPath, path.toAbsolutePath().toString(), request);
            }
        }

        // DELETE
        if ("DELETE".equals(request.getMethod())) {
            return new DeleteHandler().handle(request, match);
        }

        // POST — file upload
        if ("POST".equals(request.getMethod())) {
            return new UploadHandler().handle(request, match);
        }

        // Directory or file
        if (Files.isDirectory(path)) {
            return new DirectoryHandler().handle(request, match);
        }

        return new StaticFileHandler().handle(request, match);
    }

}

