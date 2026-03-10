package handler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import config.Config;
import http.HttpRequest;
import http.HttpResponse;
import http.HttpResponseFactory;
import router.RouteMatch;

public class DeleteHandler implements RequestHandler {

    @Override
    public HttpResponse handle(HttpRequest request, RouteMatch match) {
        Config config  = match.getConfig();
        Path rootPath  = Path.of(match.getRoute().root).normalize();
        Path filePath  = match.getResolvedPath().normalize();

        if (!PathUtils.isSafe(filePath, rootPath)) return HttpResponseFactory.errorResponse(config, 403);
        if (!Files.exists(filePath))               return HttpResponseFactory.errorResponse(config, 404);
        if (Files.isDirectory(filePath))           return HttpResponseFactory.errorResponse(config, 403);

        try {
            Files.delete(filePath);
            return HttpResponseFactory.noContent();
        } catch (IOException e) {
            return HttpResponseFactory.errorResponse(config, 500);
        }
    }
}
