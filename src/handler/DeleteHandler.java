package handler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import http.HttpRequest;
import http.HttpResponse;
import http.HttpResponseFactory;
import router.RouteMatch;

public class DeleteHandler implements RequestHandler {

    @Override
    public HttpResponse handle(HttpRequest request, RouteMatch match) {
        Path rootPath = Path.of(match.getRoute().root).normalize();
        Path filePath = match.getResolvedPath().normalize();

        if (!PathUtils.isSafe(filePath, rootPath)) return HttpResponseFactory.forbidden();
        if (!Files.exists(filePath))               return HttpResponseFactory.notFound();
        if (Files.isDirectory(filePath))           return HttpResponseFactory.forbidden();

        try {
            Files.delete(filePath);
            return HttpResponseFactory.noContent();
        } catch (IOException e) {
            return HttpResponseFactory.internalServerError();
        }
    }
}
