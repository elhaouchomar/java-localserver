package http;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HttpParser {

    // Regex to parse the request line: Method, Path (with optional query), Version
    private static final Pattern REQUEST_LINE_PATTERN = Pattern.compile("^(GET|POST|DELETE|PUT|HEAD|OPTIONS|PATCH)\s([^\s]+)\s(HTTP/1\.[01])$ ");

    public enum ParsingStatus {
        INCOMPLETE_REQUEST_LINE,
        INCOMPLETE_HEADERS,
        INCOMPLETE_BODY,
        COMPLETE,
        ERROR
    }

    public static ParsingStatus parse(ByteBuffer newDataBuffer, HttpRequest request) {
        // Append new data to the request's unparsedData
        newDataBuffer.flip(); // Prepare buffer for reading
        byte[] data = new byte[newDataBuffer.remaining()];
        newDataBuffer.get(data);
        newDataBuffer.compact(); // Prepare buffer for writing again

        request.unparsedData.append(new String(data, StandardCharsets.UTF_8));

        // Loop to process data until request is complete, error, or more data is needed
        while (request.parsingStatus != ParsingStatus.COMPLETE && request.parsingStatus != ParsingStatus.ERROR) {
            switch (request.parsingStatus) {
                case INCOMPLETE_REQUEST_LINE:
                    if (!parseRequestLine(request)) {
                        return request.parsingStatus; // Still incomplete or error
                    }
                    // If successful, status is updated inside parseRequestLine
                    break;
                case INCOMPLETE_HEADERS:
                    if (!parseHeaders(request)) {
                        return request.parsingStatus; // Still incomplete or error
                    }
                    // If successful, status is updated inside parseHeaders
                    break;
                case INCOMPLETE_BODY:
                    if (!readBody(request)) {
                        return request.parsingStatus; // Still incomplete or error
                    }
                    // If successful, status is updated inside readBody
                    break;
                case COMPLETE:
                case ERROR:
                    // Should be caught by the while loop condition
                    break;
            }
        }
        return request.parsingStatus;
    }

    private static boolean parseRequestLine(HttpRequest request) {
        int firstNewLine = request.unparsedData.indexOf("\r\n");
        if (firstNewLine == -1) {
            return false; // Request line not fully received yet
        }

        String requestLine = request.unparsedData.substring(0, firstNewLine);
        Matcher matcher = REQUEST_LINE_PATTERN.matcher(requestLine);

        if (matcher.matches()) {
            request.method = matcher.group(1);
            String fullPath = matcher.group(2);
            request.version = matcher.group(3);

            // Parse path and query parameters
            int queryStartIndex = fullPath.indexOf('?');
            if (queryStartIndex != -1) {
                request.path = fullPath.substring(0, queryStartIndex);
                String queryString = fullPath.substring(queryStartIndex + 1);
                parseQueryParams(queryString, request.queryParams);
            } else {
                request.path = fullPath;
            }

            request.unparsedData.delete(0, firstNewLine + 2); // Remove request line + CRLF
            request.parsingStatus = ParsingStatus.INCOMPLETE_HEADERS;
            return true;
        } else {
            System.err.println("Malformed request line: " + requestLine);
            request.parsingStatus = ParsingStatus.ERROR;
            return false;
        }
    }

    private static boolean parseHeaders(HttpRequest request) {
        int endOfHeaders = request.unparsedData.indexOf("\r\n\r\n");
        if (endOfHeaders == -1) {
            return false; // Headers not fully received yet
        }

        String headersBlock = request.unparsedData.substring(0, endOfHeaders);
        String[] headerLines = headersBlock.split("\r\n");

        for (String line : headerLines) {
            if (line.isEmpty()) continue; // Skip empty lines, especially the one after the last header
            int colonIndex = line.indexOf(':');
            if (colonIndex != -1) {
                String name = line.substring(0, colonIndex).trim();
                String value = line.substring(colonIndex + 1).trim();
                request.addHeader(name, value);
            } else {
                System.err.println("Malformed header line: " + line);
                request.parsingStatus = ParsingStatus.ERROR;
                return false; // Malformed header
            }
        }

        request.unparsedData.delete(0, endOfHeaders + 4); // Remove headers block + CRLFCRLF

        // Check for Content-Length or Transfer-Encoding
        String contentLengthHeader = request.headers.get("Content-Length");
        String transferEncoding = request.headers.get("Transfer-Encoding");

        if (contentLengthHeader != null) {
            try {
                request.contentLength = Integer.parseInt(contentLengthHeader.trim());
                if (request.contentLength > 0) {
                    request.parsingStatus = ParsingStatus.INCOMPLETE_BODY;
                }
                else {
                    request.parsingStatus = ParsingStatus.COMPLETE; // No body expected
                }
            } catch (NumberFormatException e) {
                System.err.println("Malformed Content-Length header: " + contentLengthHeader);
                request.parsingStatus = ParsingStatus.ERROR; // Malformed content length
                return false;
            }
        } else if (transferEncoding != null && transferEncoding.equalsIgnoreCase("chunked")) {
            // isChunked = true; // This state should be part of HttpRequest if needed for body parsing
            request.parsingStatus = ParsingStatus.ERROR; // Temporarily, chunked not supported
            System.err.println("Chunked encoding not yet supported, treating as error.");
            return false;
        } else {
            request.parsingStatus = ParsingStatus.COMPLETE; // No Content-Length, no chunked -> no body
        }
        return true;
    }

    private static boolean readBody(HttpRequest request) {
        if (request.contentLength == -1) {
            // Should not happen if headers were parsed correctly and no chunked encoding
            request.parsingStatus = ParsingStatus.ERROR;
            return false;
        }

        if (request.unparsedData.length() >= request.contentLength) {
            byte[] bodyBytes = request.unparsedData.substring(0, request.contentLength).getBytes(StandardCharsets.UTF_8);
            request.body = bodyBytes;
            request.unparsedData.delete(0, request.contentLength); // Remove body bytes
            request.parsingStatus = ParsingStatus.COMPLETE;
            return true;
        }
        return false; // Not enough body data yet
    }

    private static void parseQueryParams(String queryString, Map<String, String> queryParams) {
        String[] pairs = queryString.split("&");
        for (String pair : pairs) {
            int equalsIndex = pair.indexOf('=');
            if (equalsIndex != -1) {
                String name = pair.substring(0, equalsIndex);
                String value = pair.substring(equalsIndex + 1);
                queryParams.put(name, value);
            } else {
                queryParams.put(pair, ""); // Query parameter without a value
            }
        }
    }
}