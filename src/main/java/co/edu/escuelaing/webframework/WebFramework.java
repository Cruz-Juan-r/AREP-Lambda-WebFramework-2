package co.edu.escuelaing.webframework;

import java.io.IOException;

/**
 * Public API of the framework. Application code only needs:
 *
 * <pre>{@code
 * import static co.edu.escuelaing.webframework.WebFramework.*;
 *
 * staticfiles("/webroot");
 * get("/hello", (req, resp) -> "Hello " + req.getValue("name"));
 * start();
 * }</pre>
 *
 * Sockets, parsing and routing stay hidden behind these methods.
 */
public final class WebFramework {

    public static final int DEFAULT_PORT = 8080;

    private static final Router ROUTER = new Router();
    private static StaticFileService staticFileService;
    private static volatile HttpServer server;

    private WebFramework() {
    }

    /** Sets the folder (classpath or disk) where static files live. */
    public static void staticfiles(String location) {
        staticFileService = StaticFileService.of(location);
    }

    /** Registers a GET REST service implemented by a lambda. */
    public static void get(String path, RouteHandler handler) {
        ROUTER.register("GET", path, handler);
    }

    /** Starts on the port given by the PORT environment variable (8080 by default). */
    public static void start() throws IOException {
        start(resolvePort(System.getenv("PORT")));
    }

    /** Starts the sequential server. Blocks until {@link #stop()} is called. */
    public static void start(int port) throws IOException {
        if (server != null && server.isRunning()) {
            throw new IllegalStateException("Server is already running");
        }
        server = new HttpServer(ROUTER, staticFileService);
        ROUTER.routes().forEach(r -> System.out.println("[router] " + r.method() + " " + r.path()));
        server.start(port);
    }

    /** Graceful stop: the current request finishes before the server closes. */
    public static void stop() {
        HttpServer current = server;
        if (current != null) {
            current.stop();
        }
    }

    /** Parses the PORT variable; falls back to 8080 when it is missing or blank. */
    public static int resolvePort(String portValue) {
        if (portValue == null || portValue.isBlank()) {
            return DEFAULT_PORT;
        }
        try {
            int port = Integer.parseInt(portValue.trim());
            if (port < 0 || port > 65_535) {
                throw new IllegalArgumentException("PORT out of range: " + portValue);
            }
            return port;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("PORT must be a number, got: " + portValue, e);
        }
    }
}
