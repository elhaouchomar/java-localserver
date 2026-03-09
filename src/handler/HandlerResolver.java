package handler;

import java.io.IOException;
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
            return buildError(config, 405);
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
            return handleDelete(path, route.root != null ? route.root : config.root);
        }

        // POST — file upload
        if ("POST".equals(request.getMethod())) {
            return handleUpload(request, path, route.root != null ? route.root : config.root);
        }

        // Directory or file
        if (Files.isDirectory(path)) {
            return new DirectoryHandler().handle(request, match);
        }

        return new StaticFileHandler().handle(request, match);
    }

    private HttpResponse handleDelete(Path filePath, String root) {
        Path rootPath = Path.of(root).normalize();
        filePath = filePath.normalize();

        if (!filePath.startsWith(rootPath)) {
            return HttpResponseFactory.forbidden();
        }

        if (!Files.exists(filePath)) {
            return HttpResponseFactory.notFound();
        }

        if (Files.isDirectory(filePath)) {
            return HttpResponseFactory.forbidden();
        }

        try {
            Files.delete(filePath);
            return HttpResponseFactory.noContent();
        } catch (IOException e) {
            return HttpResponseFactory.internalServerError();
        }
    }

    private HttpResponse handleUpload(HttpRequest request, Path targetPath, String root) {
        Path rootPath = Path.of(root).normalize();
        targetPath = targetPath.normalize();

        if (!targetPath.startsWith(rootPath)) {
            return HttpResponseFactory.forbidden();
        }

        if (request.body == null || request.body.length == 0) {
            return HttpResponseFactory.badRequest("No body provided");
        }

        try {
            if (targetPath.getParent() != null) {
                Files.createDirectories(targetPath.getParent());
            }
            Files.write(targetPath, request.body);
            return HttpResponseFactory.noContent();
        } catch (IOException e) {
            return HttpResponseFactory.internalServerError();
        }
    }

    private HttpResponse buildError(Config config, int code) {
        if (config != null && config.errorPages.containsKey(code)) {
            String pagePath = config.errorPages.get(code);
            Path filePath = Path.of(pagePath).normalize();
            if (Files.exists(filePath) && Files.isRegularFile(filePath)) {
                try {
                    byte[] data = Files.readAllBytes(filePath);
                    String reason = switch (code) {
                        case 400 -> "Bad Request";
                        case 403 -> "Forbidden";
                        case 404 -> "Not Found";
                        case 405 -> "Method Not Allowed";
                        case 413 -> "Payload Too Large";
                        default -> "Internal Server Error";
                    };
                    return new http.HttpResponse.Builder()
                            .status(code, reason)
                            .body(data, "text/html; charset=utf-8")
                            .build();
                } catch (IOException ignored) {
                }
            }
        }
        return switch (code) {
            case 400 -> HttpResponseFactory.badRequest("400 Bad Request");
            case 403 -> HttpResponseFactory.forbidden();
            case 405 -> HttpResponseFactory.methodNotAllowed();
            case 413 -> HttpResponseFactory.payloadTooLarge();
            default -> HttpResponseFactory.internalServerError();
        };
    }
}
