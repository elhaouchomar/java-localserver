package http;

import java.util.HashMap;
import java.util.Map;

import http.HttpParser.ParsingStatus; // Import the nested enum

public class HttpRequest {
    public String method;
    public String path;
    public String version;

    public Map<String, String> headers = new HashMap<>();

    public int contentLength = -1; // -1 indicates Content-Length not yet determined
    public byte[] body;

    public boolean keepAlive = true;

    // Parser-related state
    public ParsingStatus parsingStatus = ParsingStatus.INCOMPLETE_REQUEST_LINE;
    public StringBuilder unparsedData = new StringBuilder(); // To accumulate raw data

    public void addHeader(String name, String value) {
        this.headers.put(name, value);
    }
}
