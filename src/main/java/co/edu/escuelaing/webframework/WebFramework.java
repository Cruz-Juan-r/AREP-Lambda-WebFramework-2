package co.edu.escuelaing.webframework;

import java.io.IOException;
import java.time.Duration;

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
 * Sockets, threads, parsing and routing stay hidden behind these methods.
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

    /**
     * Starts on the port given by the PORT environment variable (8080 by default).
     * WORKER_THREADS and SHUTDOWN_TIMEOUT_SECONDS tune the thread pool and the
     * graceful-shutdown wait.
     */
    public static void start() throws IOException {
        start(resolvePort(System.getenv("PORT")));
    }

    /** Starts the concurrent server. Blocks until {@link #stop()} is called or the JVM receives SIGTERM. */
    public static void start(int port) throws IOException {
        if (server != null && server.isRunning()) {
            throw new IllegalStateException("Server is already running");
        }
        int workers = resolveWorkerThreads(System.getenv("WORKER_THREADS"));
        Duration shutdownTimeout = resolveShutdownTimeout(System.getenv("SHUTDOWN_TIMEOUT_SECONDS"));
        HttpServer current = new HttpServer(ROUTER, staticFileService, workers, shutdownTimeout);
        server = current;
        ROUTER.routes().forEach(r -> System.out.println("[router] " + r.method() + " " + r.path()));

        // docker stop, Ctrl+C and EC2 shutdowns send SIGTERM/SIGINT: drain before the JVM exits.
        Thread hook = new Thread(() -> {
            current.stop();
            try {
                current.awaitStopped(shutdownTimeout.plusSeconds(1));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "shutdown-hook");
        Runtime.getRuntime().addShutdownHook(hook);

        try {
            current.start(port);
        } finally {
            try {
                Runtime.getRuntime().removeShutdownHook(hook);
            } catch (IllegalStateException alreadyShuttingDown) {
                // The hook itself triggered this stop; the JVM is exiting.
            }
        }
    }

    /** Graceful stop: no new connections; requests in progress finish before the server closes. */
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

    /** Parses WORKER_THREADS; falls back to max(4, 2 x CPUs). */
    public static int resolveWorkerThreads(String value) {
        if (value == null || value.isBlank()) {
            return HttpServer.DEFAULT_WORKER_THREADS;
        }
        try {
            int threads = Integer.parseInt(value.trim());
            if (threads < 1 || threads > 1_000) {
                throw new IllegalArgumentException("WORKER_THREADS must be between 1 and 1000, got: " + value);
            }
            return threads;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("WORKER_THREADS must be a number, got: " + value, e);
        }
    }

    /** Parses SHUTDOWN_TIMEOUT_SECONDS; falls back to 5 seconds. */
    public static Duration resolveShutdownTimeout(String value) {
        if (value == null || value.isBlank()) {
            return HttpServer.DEFAULT_SHUTDOWN_TIMEOUT;
        }
        try {
            int seconds = Integer.parseInt(value.trim());
            if (seconds < 0) {
                throw new IllegalArgumentException("SHUTDOWN_TIMEOUT_SECONDS must not be negative, got: " + value);
            }
            return Duration.ofSeconds(seconds);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("SHUTDOWN_TIMEOUT_SECONDS must be a number, got: " + value, e);
        }
    }
}
