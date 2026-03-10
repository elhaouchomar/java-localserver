package handler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import config.Config;
import http.BodyProducer;
import http.FileBodyProducer;
import http.HttpRequest;
import http.HttpResponse;
import http.HttpResponseFactory;
import router.RouteMatch;

public class StaticFileHandler implements RequestHandler {

    @Override
    public HttpResponse handle(HttpRequest request, RouteMatch match) {
        return serveFile(match.getResolvedPath(), match.getRoute().root, match.getConfig());
    }

    public HttpResponse serveFile(Path filePath, String root, Config config) {
        Path rootPath = Path.of(root).normalize();
        filePath = filePath.normalize();

        if (!PathUtils.isSafe(filePath, rootPath)) return HttpResponseFactory.errorResponse(config, 403);
        if (!Files.exists(filePath))               return HttpResponseFactory.errorResponse(config, 404);
        if (!Files.isRegularFile(filePath))        return HttpResponseFactory.errorResponse(config, 403);
        if (!Files.isReadable(filePath))           return HttpResponseFactory.errorResponse(config, 403);

        try {
            BodyProducer body = new FileBodyProducer(filePath);
            long size = Files.size(filePath);
            String contentType = Files.probeContentType(filePath);
            if (contentType == null) contentType = "application/octet-stream";
            return HttpResponseFactory.ok(body, contentType, size);
        } catch (IOException e) {
            return HttpResponseFactory.errorResponse(config, 500);
        }
    }
}
