package handler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import http.BodyProducer;
import http.FileBodyProducer;
import http.HttpRequest;
import http.HttpResponse;
import http.HttpResponseFactory;
import router.RouteMatch;

public class StaticFileHandler implements RequestHandler {

    @Override
    public HttpResponse handle(HttpRequest request, RouteMatch match) {
        return serveFile(match.getResolvedPath(), match.getRoute().root);
    }

    public HttpResponse serveFile(Path filePath, String root) {

        Path rootPath = Path.of(root).normalize();
        filePath = filePath.normalize();

        if (!filePath.startsWith(rootPath)) {
            return HttpResponseFactory.forbidden();
        }

        if (!Files.exists(filePath)) {
            return HttpResponseFactory.notFound();
        }

        if (!Files.isRegularFile(filePath)) {
            return HttpResponseFactory.forbidden();
        }

        if (!Files.isReadable(filePath)) {
            return HttpResponseFactory.forbidden();
        }

        try {
            BodyProducer body = new FileBodyProducer(filePath);
            long size = Files.size(filePath);

            String contentType = Files.probeContentType(filePath);
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            return HttpResponseFactory.ok(body, contentType, size);

        } catch (IOException e) {
            return HttpResponseFactory.internalServerError();
        }
    }
}
