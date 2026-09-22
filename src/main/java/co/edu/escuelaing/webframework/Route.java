package co.edu.escuelaing.webframework;

/** A registered endpoint: HTTP method + path + the lambda that serves it. */
public record Route(String method, String path, RouteHandler handler) {
}
