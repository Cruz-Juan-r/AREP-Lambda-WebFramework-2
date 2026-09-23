package co.edu.escuelaing.webframework;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Starts a real server on a random port and talks to it through sockets,
 * exactly like a browser would.
 */
class HttpServerIntegrationTest {

    private HttpServer server;
    private Thread serverThread;
    private int port;
    private final AtomicReference<Throwable> serverError = new AtomicReference<>();
    private final CyclicBarrier rendezvous = new CyclicBarrier(2);
    private final CountDownLatch heldStarted = new CountDownLatch(1);
    private final CountDownLatch heldRelease = new CountDownLatch(1);

    @BeforeEach
    void startServer() throws Exception {
        Router router = new Router();
        router.register("GET", "/hello", (req, resp) -> "Hello " + req.getValueOrDefault("name", "world"));
        router.register("GET", "/pi", (req, resp) -> String.valueOf(Math.PI));
        router.register("GET", "/json", (req, resp) -> {
            resp.type(Response.APPLICATION_JSON).status(201).header("X-Custom", "yes");
            return "{\"ok\":true}";
        });
        router.register("GET", "/boom", (req, resp) -> {
            throw new IllegalStateException("handler bug");
        });
        router.register("GET", "/shutdown", (req, resp) -> {
            server.stop();
            return "Server will stop after this response.";
        });
        // Completes only when two requests are inside it at the same time.
        router.register("GET", "/rendezvous", (req, resp) -> {
            rendezvous.await(3, TimeUnit.SECONDS);
            return "met on " + Thread.currentThread().getName();
        });
        // Signals that it started, then waits until the test releases it.
        router.register("GET", "/held", (req, resp) -> {
            heldStarted.countDown();
            heldRelease.await(5, TimeUnit.SECONDS);
            return "finished during shutdown";
        });

        server = new HttpServer(router, StaticFileService.of("/webroot"), 4, Duration.ofSeconds(5));
        serverThread = new Thread(() -> {
            try {
                server.start(0);
            } catch (Throwable t) {
                serverError.set(t);
            }
        });
        serverThread.start();

        long deadline = System.currentTimeMillis() + 5_000;
        while (server.getLocalPort() <= 0) {
            if (System.currentTimeMillis() > deadline) {
                throw new IllegalStateException("Server did not start", serverError.get());
            }
            Thread.sleep(10);
        }
        port = server.getLocalPort();
    }

    @AfterEach
    void stopServer() throws Exception {
        if (server.isRunning()) {
            send("GET /shutdown HTTP/1.1\r\n\r\n");
        }
        serverThread.join(5_000);
        assertNull(serverError.get());
    }

    @Test
    void lambdaRouteReadsQueryParameter() throws Exception {
        RawResponse r = get("/hello?name=Pedro");
        assertEquals(200, r.status);
        assertEquals("Hello Pedro", r.bodyText());
        assertTrue(r.head.contains("Content-Type: text/plain; charset=UTF-8"));
    }

    @Test
    void lambdaRouteWithoutParameterUsesDefault() throws Exception {
        assertEquals("Hello world", get("/hello").bodyText());
    }

    @Test
    void secondLambdaRoute() throws Exception {
        assertEquals(String.valueOf(Math.PI), get("/pi").bodyText());
    }

    @Test
    void handlerCanChangeStatusTypeAndHeaders() throws Exception {
        RawResponse r = get("/json");
        assertEquals(201, r.status);
        assertTrue(r.head.contains("Content-Type: application/json; charset=UTF-8"));
        assertTrue(r.head.contains("X-Custom: yes"));
    }

    @Test
    void servesStaticHtmlCssJsAndImage() throws Exception {
        RawResponse html = get("/index.html");
        assertEquals(200, html.status);
        assertTrue(html.head.contains("Content-Type: text/html"));

        assertTrue(get("/styles.css").head.contains("Content-Type: text/css"));
        assertTrue(get("/app.js").head.contains("Content-Type: text/javascript"));

        RawResponse png = get("/images/logo.png");
        assertEquals(200, png.status);
        assertTrue(png.head.contains("Content-Type: image/png"));
        byte[] expected = StaticFileService.of("/webroot").find("/images/logo.png").orElseThrow().content();
        assertArrayEquals(expected, png.body);
        assertTrue(png.head.contains("Content-Length: " + expected.length));
    }

    @Test
    void rootServesIndex() throws Exception {
        assertTrue(get("/").bodyText().contains("Lambda Web Framework"));
    }

    @Test
    void unknownResourceReturns404() throws Exception {
        RawResponse r = get("/unknown");
        assertEquals(404, r.status);
        assertEquals("404 Not Found", r.bodyText());
        assertTrue(r.head.contains("Content-Type: text/plain"));
    }

    @Test
    void malformedRequestReturns400AndServerKeepsRunning() throws Exception {
        assertEquals(400, parse(send("THIS IS NOT HTTP\r\n\r\n")).status);
        assertEquals(400, parse(send("GET\r\n\r\n")).status);
        assertEquals(200, get("/pi").status);
    }

    @Test
    void emptyConnectionDoesNotBreakServer() throws Exception {
        try (Socket socket = new Socket("localhost", port)) {
            socket.shutdownOutput();
        }
        assertEquals(200, get("/pi").status);
    }

    @Test
    void failingHandlerReturns500AndServerKeepsRunning() throws Exception {
        assertEquals(500, get("/boom").status);
        assertEquals(200, get("/pi").status);
    }

    @Test
    void unsupportedMethodReturns405() throws Exception {
        RawResponse r = parse(send("POST /hello HTTP/1.1\r\nContent-Length: 0\r\n\r\n"));
        assertEquals(405, r.status);
        assertTrue(r.head.contains("Allow: GET"));
    }

    @Test
    void shutdownAnswersThenStopsGracefully() throws Exception {
        RawResponse r = get("/shutdown");
        assertEquals(200, r.status);
        assertEquals("Server will stop after this response.", r.bodyText());

        serverThread.join(5_000);
        assertFalse(serverThread.isAlive());
        assertFalse(server.isRunning());
        assertThrows(IOException.class, () -> new Socket("localhost", port).close());
    }

    @Test
    void requestsAreProcessedConcurrently() throws Exception {
        // A sequential server would run the first /rendezvous alone, time out and answer 500.
        CompletableFuture<RawResponse> first = CompletableFuture.supplyAsync(() -> getUnchecked("/rendezvous"));
        CompletableFuture<RawResponse> second = CompletableFuture.supplyAsync(() -> getUnchecked("/rendezvous"));

        RawResponse a = first.get(10, TimeUnit.SECONDS);
        RawResponse b = second.get(10, TimeUnit.SECONDS);
        assertEquals(200, a.status);
        assertEquals(200, b.status);
        assertTrue(a.bodyText().startsWith("met on http-worker-"));
        assertNotEquals(a.bodyText(), b.bodyText(), "each request must run on its own worker thread");
    }

    @Test
    void slowRequestDoesNotBlockOtherRequests() throws Exception {
        CompletableFuture<RawResponse> held = CompletableFuture.supplyAsync(() -> getUnchecked("/held"));
        assertTrue(heldStarted.await(5, TimeUnit.SECONDS));

        long start = System.nanoTime();
        assertEquals(200, get("/pi").status);
        assertTrue(Duration.ofNanos(System.nanoTime() - start).toMillis() < 2_000);

        heldRelease.countDown();
        assertEquals(200, held.get(5, TimeUnit.SECONDS).status);
    }

    @Test
    void gracefulShutdownFinishesInFlightRequestAndRefusesNewOnes() throws Exception {
        CompletableFuture<RawResponse> held = CompletableFuture.supplyAsync(() -> getUnchecked("/held"));
        assertTrue(heldStarted.await(5, TimeUnit.SECONDS));
        assertEquals(1, server.getActiveRequests());

        server.stop();
        assertFalse(server.isRunning());
        assertThrows(IOException.class, () -> new Socket("localhost", port).close(), "listening socket must be closed");
        assertTrue(serverThread.isAlive(), "server must wait for the in-flight request");

        heldRelease.countDown();
        RawResponse r = held.get(5, TimeUnit.SECONDS);
        assertEquals(200, r.status);
        assertEquals("finished during shutdown", r.bodyText());

        assertTrue(server.awaitStopped(Duration.ofSeconds(5)));
        serverThread.join(5_000);
        assertFalse(serverThread.isAlive());
    }

    @Test
    void stopWithoutTrafficReturnsPromptly() throws Exception {
        server.stop();
        assertTrue(server.awaitStopped(Duration.ofSeconds(2)), "stop must not wait for another connection");
    }

    // ---- helpers -------------------------------------------------------------------------

    private RawResponse get(String target) throws IOException {
        return parse(send("GET " + target + " HTTP/1.1\r\nHost: localhost\r\n\r\n"));
    }

    private RawResponse getUnchecked(String target) {
        try {
            return get(target);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private byte[] send(String raw) throws IOException {
        try (Socket socket = new Socket("localhost", port)) {
            socket.setSoTimeout(5_000);
            OutputStream out = socket.getOutputStream();
            out.write(raw.getBytes(StandardCharsets.UTF_8));
            out.flush();
            InputStream in = socket.getInputStream();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            in.transferTo(buffer);
            return buffer.toByteArray();
        }
    }

    private RawResponse parse(byte[] raw) {
        int split = indexOf(raw, "\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
        String head = new String(raw, 0, split, StandardCharsets.ISO_8859_1);
        byte[] body = Arrays.copyOfRange(raw, split + 4, raw.length);
        int status = Integer.parseInt(head.split(" ")[1]);
        return new RawResponse(status, head, body);
    }

    private static int indexOf(byte[] data, byte[] pattern) {
        outer:
        for (int i = 0; i <= data.length - pattern.length; i++) {
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) {
                    continue outer;
                }
            }
            return i;
        }
        throw new IllegalStateException("No header/body separator in response");
    }

    private record RawResponse(int status, String head, byte[] body) {
        String bodyText() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }
}
