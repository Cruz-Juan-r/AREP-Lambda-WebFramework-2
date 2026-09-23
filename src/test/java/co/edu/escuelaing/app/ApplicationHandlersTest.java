package co.edu.escuelaing.app;

import co.edu.escuelaing.webframework.HttpRequestParser;
import co.edu.escuelaing.webframework.Request;
import co.edu.escuelaing.webframework.Response;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The lambdas are tested in isolation, without starting any server. */
class ApplicationHandlersTest {

    private static Request request(String target) throws Exception {
        String raw = "GET " + target + " HTTP/1.1\r\n\r\n";
        return HttpRequestParser.parse(new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void helloUsesNameAndConfiguredPrefix() throws Exception {
        AppConfig config = AppConfig.from(Map.of("GREETING_PREFIX", "Hola"));
        assertEquals("Hola Pedro", Application.hello(config).handle(request("/hello?name=Pedro"), new Response()));
    }

    @Test
    void helloDefaultsToWorld() throws Exception {
        AppConfig config = AppConfig.from(Map.of());
        assertEquals("Hello world", Application.hello(config).handle(request("/hello"), new Response()));
        assertEquals("Hello world", Application.hello(config).handle(request("/hello?name="), new Response()));
    }

    @Test
    void sumReadsTwoParameters() throws Exception {
        Response response = new Response();
        String body = Application.sum().handle(request("/api/sum?a=2&b=3.5"), response);
        assertEquals(200, response.getStatus());
        assertEquals(Response.APPLICATION_JSON, response.getContentType());
        assertEquals("{\"a\":2.0,\"b\":3.5,\"sum\":5.5}", body);
    }

    @Test
    void sumValidatesInput() throws Exception {
        Response missing = new Response();
        Application.sum().handle(request("/api/sum?a=2"), missing);
        assertEquals(400, missing.getStatus());

        Response notNumber = new Response();
        String body = Application.sum().handle(request("/api/sum?a=x&b=1"), notNumber);
        assertEquals(400, notNumber.getStatus());
        assertTrue(body.contains("must be numbers"));
    }

    @Test
    void slowReportsSleepAndThreadAndCapsTheWait() throws Exception {
        String body = Application.slow().handle(request("/api/slow?ms=10"), new Response());
        assertTrue(body.contains("\"sleptMs\":10"));
        assertTrue(body.contains("\"thread\":\"" + Thread.currentThread().getName() + "\""));

        Response bad = new Response();
        Application.slow().handle(request("/api/slow?ms=abc"), bad);
        assertEquals(400, bad.getStatus());

        assertTrue(Application.slow().handle(request("/api/slow?ms=-5"), new Response()).contains("\"sleptMs\":0"));
    }

    @Test
    void infoExposesOnlyNonSensitiveConfiguration() throws Exception {
        AppConfig config = AppConfig.from(Map.of("APP_ENV", "production", "GREETING_PREFIX", "Hi \"there\""));
        String body = Application.info(config).handle(request("/api/info"), new Response());
        assertTrue(body.contains("\"appEnv\":\"production\""));
        assertTrue(body.contains("\"greetingPrefix\":\"Hi \\\"there\\\"\""));
        assertTrue(body.contains("\"shutdownEnabled\":false"));
    }
}
