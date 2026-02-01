package handler;

import http.HttpRequest;
import http.HttpResponse;

public interface RequestHandler {
    HttpResponse handle(HttpRequest request);
}
