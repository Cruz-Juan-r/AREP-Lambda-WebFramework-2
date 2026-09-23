package co.edu.escuelaing.webframework;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Concurrent HTTP/1.1 server. One acceptor thread takes connections from the
 * {@link ServerSocket} and hands each one to a fixed pool of worker threads,
 * so a slow request no longer blocks the rest.
 *
 * <p>The server knows nothing about specific endpoints. It asks the
 * {@link Router} for a lambda and, if none matches, the
 * {@link StaticFileService} for a file. Otherwise it answers 404.</p>
 *
 * <p>Graceful shutdown: {@link #stop()} closes the listening socket, so no new
 * connection is accepted, and then waits up to the configured timeout for the
 * requests that are already being processed to finish.</p>
 */
public final class HttpServer {

    /** Bind on every interface so cloud load balancers and containers can reach us. */
    public static final String BIND_ADDRESS = "0.0.0.0";

    public static final int DEFAULT_WORKER_THREADS = Math.max(4, Runtime.getRuntime().availableProcessors() * 2);
    public static final Duration DEFAULT_SHUTDOWN_TIMEOUT = Duration.ofSeconds(5);

    /** A silent client must not hold a worker thread forever. */
    private static final int CLIENT_READ_TIMEOUT_MS = 5_000;

    private final Router router;
    private final StaticFileService staticFiles;
    private final int workerThreads;
    private final Duration shutdownTimeout;

    private final AtomicInteger activeRequests = new AtomicInteger();
    private final CountDownLatch stopped = new CountDownLatch(1);

    private volatile boolean running = false;
    private volatile int localPort = -1;
    private volatile ServerSocket serverSocket;

    public HttpServer(Router router, StaticFileService staticFiles) {
        this(router, staticFiles, DEFAULT_WORKER_THREADS, DEFAULT_SHUTDOWN_TIMEOUT);
    }

    public HttpServer(Router router, StaticFileService staticFiles, int workerThreads, Duration shutdownTimeout) {
        if (workerThreads < 1) {
            throw new IllegalArgumentException("workerThreads must be at least 1, got: " + workerThreads);
        }
        this.router = router;
        this.staticFiles = staticFiles;
        this.workerThreads = workerThreads;
        this.shutdownTimeout = shutdownTimeout;
    }

    /** Accepts connections until {@link #stop()} is called, then drains the workers. Blocks the caller. */
    public void start(int port) throws IOException {
        if (stopped.getCount() == 0) {
            throw new IllegalStateException("A stopped HttpServer cannot be restarted; create a new one");
        }
        ExecutorService workers = Executors.newFixedThreadPool(workerThreads, new WorkerThreadFactory());
        try (ServerSocket socket = new ServerSocket()) {
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(BIND_ADDRESS, port));
            serverSocket = socket;
            localPort = socket.getLocalPort();
            running = true;
            log("Listening on http://" + BIND_ADDRESS + ":" + localPort
                    + " with " + workerThreads + " worker threads"
                    + " (static files: " + (staticFiles == null ? "disabled" : staticFiles.describe()) + ")");

            while (running) {
                Socket clientSocket;
                try {
                    clientSocket = socket.accept();
                } catch (IOException e) {
                    if (running) {
                        log("Accept error: " + e.getMessage());
                        continue;
                    }
                    break; // stop() closed the socket to unblock accept().
                }
                try {
                    workers.execute(() -> serve(clientSocket));
                } catch (RejectedExecutionException e) {
                    closeQuietly(clientSocket);
                }
            }
        } finally {
            running = false;
            localPort = -1;
            drain(workers);
            stopped.countDown();
        }
    }

    /**
     * Requests a graceful stop and returns immediately. The listening socket is
     * closed right away; requests already in progress are allowed to finish.
     * Safe to call from a request handler or from a JVM shutdown hook.
     */
    public void stop() {
        if (!running) {
            return;
        }
        running = false;
        log("Shutdown requested: no new connections, waiting for " + activeRequests.get() + " active request(s).");
        closeQuietly(serverSocket);
    }

    /** Blocks until the server has fully stopped or the timeout expires. */
    public boolean awaitStopped(Duration timeout) throws InterruptedException {
        return stopped.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    public boolean isRunning() {
        return running;
    }

    /** Actual bound port (useful when started with port 0), or -1 if not running. */
    public int getLocalPort() {
        return localPort;
    }

    public int getActiveRequests() {
        return activeRequests.get();
    }

    private void drain(ExecutorService workers) {
        workers.shutdown();
        try {
            if (workers.awaitTermination(shutdownTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                log("Server stopped gracefully.");
            } else {
                log("Shutdown timeout (" + shutdownTimeout.toSeconds() + " s) reached; interrupting "
                        + activeRequests.get() + " request(s).");
                workers.shutdownNow();
            }
        } catch (InterruptedException e) {
            workers.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /** Runs on a worker thread: one connection, one request, one response. */
    private void serve(Socket clientSocket) {
        activeRequests.incrementAndGet();
        try (clientSocket) {
            clientSocket.setSoTimeout(CLIENT_READ_TIMEOUT_MS);
            handleConnection(clientSocket);
        } catch (IOException e) {
            // One misbehaving client must never bring the server down.
            log("Connection error: " + e.getMessage());
        } finally {
            activeRequests.decrementAndGet();
        }
    }

    private void handleConnection(Socket clientSocket) throws IOException {
        InputStream in = clientSocket.getInputStream();
        OutputStream out = clientSocket.getOutputStream();

        Request request;
        try {
            request = HttpRequestParser.parse(in);
        } catch (BadRequestException e) {
            log("400 " + e.getMessage());
            HttpResponse.error(400).writeTo(out);
            return;
        } catch (SocketTimeoutException e) {
            log("Client sent an incomplete request (timeout)");
            HttpResponse.error(400).writeTo(out);
            return;
        }
        if (request == null) {
            return; // Client connected and left without sending a request.
        }

        HttpResponse response = dispatch(request);
        log(response.getStatus() + " " + request);
        response.writeTo(out);
    }

    /** Route resolution: lambda first, then static file, then 404. */
    HttpResponse dispatch(Request request) {
        if (!"GET".equals(request.getMethod())) {
            return new HttpResponse(405, Response.TEXT_PLAIN, Map.of("Allow", "GET"),
                    "405 Method Not Allowed".getBytes(StandardCharsets.UTF_8));
        }

        Optional<RouteHandler> handler = router.find(request.getMethod(), request.getPath());
        if (handler.isPresent()) {
            return invoke(handler.get(), request);
        }

        if (staticFiles != null) {
            Optional<StaticResource> resource = staticFiles.find(request.getPath());
            if (resource.isPresent()) {
                return new HttpResponse(200, resource.get().contentType(), Map.of(), resource.get().content());
            }
        }
        return HttpResponse.error(404);
    }

    private HttpResponse invoke(RouteHandler handler, Request request) {
        Response response = new Response();
        try {
            String body = handler.handle(request, response);
            return new HttpResponse(response.getStatus(), response.getContentType(), response.getHeaders(),
                    body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log("Handler for " + request.getPath() + " failed: " + e);
            return HttpResponse.error(500);
        }
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
            // Nothing useful to do while closing.
        }
    }

    private static void log(String message) {
        System.out.println("[http] [" + Thread.currentThread().getName() + "] " + message);
    }

    /** Named threads make the concurrency visible in the logs. */
    private static final class WorkerThreadFactory implements ThreadFactory {
        private final AtomicInteger count = new AtomicInteger();

        @Override
        public Thread newThread(Runnable task) {
            return new Thread(task, "http-worker-" + count.incrementAndGet());
        }
    }
}
