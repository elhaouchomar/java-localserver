package handler;

import http.HttpRequest;
import http.HttpResponse;
import router.RouteMatch;

public interface RequestHandler {
    HttpResponse handle(HttpRequest request, RouteMatch match);
}
