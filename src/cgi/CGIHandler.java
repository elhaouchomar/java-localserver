package cgi;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import http.HttpRequest;
import http.HttpResponse;
import http.HttpResponseFactory;

public class CGIHandler {

    private static final int TIMEOUT_SECONDS = 30;

    public static HttpResponse execute(String interpreter, String scriptPath, HttpRequest request) {
        try {
            ProcessBuilder pb = new ProcessBuilder(interpreter, scriptPath);
            pb.redirectErrorStream(false);

            Map<String, String> env = pb.environment();
            env.put("REQUEST_METHOD", request.getMethod());
            env.put("PATH_INFO", scriptPath);
            env.put("SCRIPT_FILENAME", scriptPath);
            env.put("QUERY_STRING", request.getRawQuery() != null ? request.getRawQuery() : "");
            env.put("CONTENT_LENGTH", String.valueOf(request.contentLength));

            String ct = request.headers.get("Content-Type");
            if (ct != null) env.put("CONTENT_TYPE", ct);

            String host = request.headers.get("Host");
            if (host != null) env.put("HTTP_HOST", host);

            String ua = request.headers.get("User-Agent");
            if (ua != null) env.put("HTTP_USER_AGENT", ua);

            Process process = pb.start();

            // Feed request body into CGI stdin (for POST)
            if (request.body != null && request.body.length > 0) {
                process.getOutputStream().write(request.body);
            }
            process.getOutputStream().close();

            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                System.err.println("[CGI] Script timed out: " + scriptPath);
                return HttpResponseFactory.internalServerError();
            }

            byte[] output = process.getInputStream().readAllBytes();

            if (process.exitValue() != 0) {
                System.err.println("[CGI] Script exited with code " + process.exitValue());
                return HttpResponseFactory.internalServerError();
            }

            return parseCgiOutput(output);

        } catch (Exception e) {
            System.err.println("[CGI] Error executing " + scriptPath + ": " + e.getMessage());
            return HttpResponseFactory.internalServerError();
        }
    }

    /**
     * Parses CGI output format: optional headers followed by a blank line, then body.
     * Headers example:
     *   Content-Type: text/html\r\n
     *   Status: 200 OK\r\n
     *   \r\n
     *   <html>...</html>
     */
    private static HttpResponse parseCgiOutput(byte[] output) {
        // Find header/body separator: \r\n\r\n or \n\n
        int sep = -1;
        int bodyOffset = 0;

        for (int i = 0; i < output.length - 1; i++) {
            if (output[i] == '\r' && i + 3 < output.length
                    && output[i + 1] == '\n' && output[i + 2] == '\r' && output[i + 3] == '\n') {
                sep = i;
                bodyOffset = i + 4;
                break;
            }
            if (output[i] == '\n' && output[i + 1] == '\n') {
                sep = i;
                bodyOffset = i + 2;
                break;
            }
        }

        if (sep == -1) {
            // No headers — treat entire output as HTML body
            return HttpResponseFactory.ok(output, "text/html; charset=utf-8");
        }

        String headerBlock = new String(output, 0, sep, StandardCharsets.US_ASCII);
        byte[] body = Arrays.copyOfRange(output, bodyOffset, output.length);

        int status = 200;
        String statusReason = "OK";
        String contentType = "text/html; charset=utf-8";

        for (String line : headerBlock.split("\r?\n")) {
            int colon = line.indexOf(':');
            if (colon <= 0) continue;
            String name = line.substring(0, colon).trim();
            String value = line.substring(colon + 1).trim();

            if (name.equalsIgnoreCase("Status")) {
                String[] parts = value.split(" ", 2);
                try {
                    status = Integer.parseInt(parts[0]);
                } catch (NumberFormatException ignored) {
                }
                if (parts.length > 1) statusReason = parts[1];
            } else if (name.equalsIgnoreCase("Content-Type")) {
                contentType = value;
            }
        }

        return new HttpResponse.Builder()
                .status(status, statusReason)
                .body(body, contentType)
                .build();
    }
}
