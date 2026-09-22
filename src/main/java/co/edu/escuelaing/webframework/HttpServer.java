package co.edu.escuelaing.webframework;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

/**
 * Sequential HTTP/1.1 server: it accepts one connection, answers it completely,
 * closes it and only then accepts the next one. No worker threads are used.
 *
 * <p>The server knows nothing about specific endpoints. It asks the
 * {@link Router} for a lambda and, if none matches, the
 * {@link StaticFileService} for a file. Otherwise it answers 404.</p>
 */
public final class HttpServer {

    /** Bind on every interface so cloud load balancers and containers can reach us. */
    public static final String BIND_ADDRESS = "0.0.0.0";

    /** A silent client must not block the sequential server forever. */
    private static final int CLIENT_READ_TIMEOUT_MS = 5_000;

    private final Router router;
    private final StaticFileService staticFiles;

    private volatile boolean running = false;
    private volatile int localPort = -1;

    public HttpServer(Router router, StaticFileService staticFiles) {
        this.router = router;
        this.staticFiles = staticFiles;
    }

    public void start(int port) throws IOException {
        running = true;
        try (ServerSocket serverSocket = new ServerSocket()) {
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(BIND_ADDRESS, port));
            localPort = serverSocket.getLocalPort();
            log("Listening on http://" + BIND_ADDRESS + ":" + localPort
                    + " (static files: " + (staticFiles == null ? "disabled" : staticFiles.describe()) + ")");

            while (running) {
                try (Socket clientSocket = serverSocket.accept()) {
                    clientSocket.setSoTimeout(CLIENT_READ_TIMEOUT_MS);
                    handleConnection(clientSocket);
                } catch (IOException e) {
                    // One misbehaving client must never bring the server down.
                    log("Connection error: " + e.getMessage());
                }
            }
        } finally {
            running = false;
            localPort = -1;
        }
        log("Server stopped gracefully.");
    }

    /**
     * Requests a graceful stop. The request currently being processed is
     * answered and its connection closed; then the loop exits and the
     * ServerSocket is closed.
     */
    public void stop() {
        if (running) {
            log("Shutdown requested: finishing the current request before closing.");
        }
        running = false;
    }

    public boolean isRunning() {
        return running;
    }

    /** Actual bound port (useful when started with port 0), or -1 if not running. */
    public int getLocalPort() {
        return localPort;
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

    private static void log(String message) {
        System.out.println("[http] " + message);
    }
}
