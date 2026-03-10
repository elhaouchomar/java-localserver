package handler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import config.Config;
import http.HttpRequest;
import http.HttpResponse;
import http.HttpResponseFactory;
import router.RouteMatch;

public class UploadHandler implements RequestHandler {

    @Override
    public HttpResponse handle(HttpRequest request, RouteMatch match) {
        Config config  = match.getConfig();
        Path rootPath  = Path.of(match.getRoute().root).normalize();
        String ct      = request.headers.getOrDefault("Content-Type", "");

        if (ct.startsWith("multipart/form-data")) {
            return handleMultipart(request, ct, rootPath, config);
        }
        return handleRaw(request, match.getResolvedPath(), rootPath, config);
    }

    // ─── Multipart ────────────────────────────────────────────────────────────

    private HttpResponse handleMultipart(HttpRequest request, String contentType,
                                          Path uploadDir, Config config) {
        String boundary = parseBoundary(contentType);
        if (boundary == null)
            return HttpResponseFactory.errorResponse(config, 400);
        if (request.body == null || request.body.length == 0)
            return HttpResponseFactory.errorResponse(config, 400);

        byte[] body       = request.body;
        byte[] firstDelim = ("--" + boundary).getBytes(StandardCharsets.ISO_8859_1);
        byte[] delim      = ("\r\n--" + boundary).getBytes(StandardCharsets.ISO_8859_1);

        int pos = indexOf(body, firstDelim, 0);
        if (pos == -1)
            return HttpResponseFactory.errorResponse(config, 400);
        pos += firstDelim.length;

        if (pos + 1 >= body.length || body[pos] != '\r' || body[pos + 1] != '\n')
            return HttpResponseFactory.errorResponse(config, 400);
        pos += 2;

        List<String> saved  = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        while (pos < body.length) {
            int nextDelim = indexOf(body, delim, pos);
            byte[] partBytes = (nextDelim == -1)
                    ? Arrays.copyOfRange(body, pos, body.length)
                    : Arrays.copyOfRange(body, pos, nextDelim);

            processFilePart(partBytes, uploadDir, saved, errors);

            if (nextDelim == -1) break;
            pos = nextDelim + delim.length;

            if (pos + 1 < body.length && body[pos] == '-' && body[pos + 1] == '-') break;
            if (pos + 1 < body.length && body[pos] == '\r' && body[pos + 1] == '\n') {
                pos += 2;
            } else {
                break;
            }
        }

        return jsonResponse(saved, errors);
    }

    private void processFilePart(byte[] partBytes, Path uploadDir,
                                  List<String> saved, List<String> errors) {
        byte[] headerSep = {'\r', '\n', '\r', '\n'};
        int sepIdx = indexOf(partBytes, headerSep, 0);
        if (sepIdx == -1) return;

        String headers = new String(partBytes, 0, sepIdx, StandardCharsets.US_ASCII);
        byte[] content = Arrays.copyOfRange(partBytes, sepIdx + 4, partBytes.length);

        String filename = null;
        for (String line : headers.split("\r\n")) {
            if (!line.toLowerCase().startsWith("content-disposition:")) continue;
            for (String token : line.split(";")) {
                token = token.trim();
                if (!token.toLowerCase().startsWith("filename=")) continue;
                filename = token.substring("filename=".length()).trim();
                if (filename.startsWith("\""))
                    filename = filename.substring(1, filename.length() - 1);
            }
        }

        if (filename == null || filename.isEmpty()) return;

        filename = sanitizeFilename(Path.of(filename).getFileName().toString());
        if (filename.isEmpty()) {
            errors.add("(unnamed): filename is empty after sanitization");
            return;
        }

        Path target = uploadDir.resolve(filename).normalize();
        if (!PathUtils.isSafe(target, uploadDir)) {
            errors.add(filename + ": path traversal denied");
            return;
        }

        try {
            Files.createDirectories(uploadDir);
            Files.write(target, content);
            saved.add(filename);
        } catch (IOException e) {
            errors.add(filename + ": " + e.getMessage());
        }
    }

    // ─── Raw upload ───────────────────────────────────────────────────────────

    private HttpResponse handleRaw(HttpRequest request, Path targetPath,
                                    Path rootPath, Config config) {
        targetPath = targetPath.normalize();
        if (!PathUtils.isSafe(targetPath, rootPath))
            return HttpResponseFactory.errorResponse(config, 403);
        if (request.body == null || request.body.length == 0)
            return HttpResponseFactory.errorResponse(config, 400);

        try {
            if (targetPath.getParent() != null)
                Files.createDirectories(targetPath.getParent());
            Files.write(targetPath, request.body);
            return HttpResponseFactory.noContent();
        } catch (IOException e) {
            return HttpResponseFactory.errorResponse(config, 500);
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static String parseBoundary(String contentType) {
        for (String token : contentType.split(";")) {
            token = token.trim();
            if (token.startsWith("boundary=")) {
                String b = token.substring("boundary=".length()).trim();
                return b.startsWith("\"") ? b.substring(1, b.length() - 1) : b;
            }
        }
        return null;
    }

    /**
     * Keeps alphanumerics, dots, hyphens, underscores; replaces spaces with
     * underscores; collapses consecutive dots; strips leading dots/dashes.
     */
    private static String sanitizeFilename(String name) {
        name = name.replace(' ', '_');
        name = name.replaceAll("[^a-zA-Z0-9._\\-]", "_");
        name = name.replaceAll("\\.{2,}", ".");
        name = name.replaceAll("^[.\\-]+", "");
        if (name.length() > 200) name = name.substring(0, 200);
        return name;
    }

    private static int indexOf(byte[] haystack, byte[] needle, int from) {
        outer:
        for (int i = from; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return i;
        }
        return -1;
    }

    private static String jsonArray(List<String> items) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(jsonEscape(items.get(i))).append("\"");
        }
        return sb.append("]").toString();
    }

    private static HttpResponse jsonResponse(List<String> saved, List<String> errors) {
        String json = "{\"saved\":" + jsonArray(saved) + ",\"errors\":" + jsonArray(errors) + "}";
        return HttpResponseFactory.ok(json.getBytes(StandardCharsets.UTF_8), "application/json");
    }

    private static String jsonEscape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
