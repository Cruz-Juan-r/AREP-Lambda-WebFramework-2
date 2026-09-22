package co.edu.escuelaing.webframework;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouterTest {

    @Test
    void findsRegisteredHandler() {
        Router router = new Router();
        RouteHandler handler = (req, resp) -> "pong";
        router.register("GET", "/ping", handler);
        assertSame(handler, router.find("GET", "/ping").orElseThrow());
    }

    @Test
    void unknownPathOrMethodIsEmpty() {
        Router router = new Router();
        router.register("GET", "/ping", (req, resp) -> "pong");
        assertTrue(router.find("GET", "/unknown").isEmpty());
        assertTrue(router.find("POST", "/ping").isEmpty());
        assertTrue(router.find("GET", null).isEmpty());
        assertTrue(router.find("GET", "no-slash").isEmpty());
    }

    @Test
    void trailingSlashIsIgnoredAndMethodIsCaseInsensitive() {
        Router router = new Router();
        router.register("get", "/hello/", (req, resp) -> "hi");
        assertTrue(router.find("GET", "/hello").isPresent());
        assertTrue(router.find("GET", "/hello/").isPresent());
    }

    @Test
    void reRegisteringReplacesTheRoute() {
        Router router = new Router();
        RouteHandler second = (req, resp) -> "two";
        router.register("GET", "/x", (req, resp) -> "one");
        router.register("GET", "/x", second);
        assertSame(second, router.find("GET", "/x").orElseThrow());
        assertEquals(1, router.routes().size());
    }

    @Test
    void rejectsInvalidRegistrations() {
        Router router = new Router();
        assertThrows(IllegalArgumentException.class, () -> router.register("GET", "hello", (req, resp) -> ""));
        assertThrows(IllegalArgumentException.class, () -> router.register("GET", "/hello", null));
    }
}
