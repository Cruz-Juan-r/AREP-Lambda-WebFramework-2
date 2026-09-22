package co.edu.escuelaing.webframework;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Maps an HTTP method and path to the lambda handler registered for it.
 * Adding a route here never requires touching the server loop.
 */
public final class Router {

    private final Map<String, Route> routes = new LinkedHashMap<>();

    public void register(String method, String path, RouteHandler handler) {
        if (handler == null) {
            throw new IllegalArgumentException("Handler must not be null");
        }
        Route route = new Route(method.toUpperCase(Locale.ROOT), normalize(path), handler);
        Route previous = routes.put(key(route.method(), route.path()), route);
        if (previous != null) {
            System.out.println("[router] Route replaced: " + route.method() + " " + route.path());
        }
    }

    public Optional<RouteHandler> find(String method, String path) {
        if (method == null || path == null || !path.startsWith("/")) {
            return Optional.empty();
        }
        Route route = routes.get(key(method.toUpperCase(Locale.ROOT), normalize(path)));
        return Optional.ofNullable(route).map(Route::handler);
    }

    public List<Route> routes() {
        return Collections.unmodifiableList(new ArrayList<>(routes.values()));
    }

    /** "/hello/" and "/hello" are the same route; "/" stays "/". */
    static String normalize(String path) {
        if (path == null || !path.startsWith("/")) {
            throw new IllegalArgumentException("Route paths must start with '/': " + path);
        }
        String normalized = path;
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String key(String method, String path) {
        return method + " " + path;
    }
}
