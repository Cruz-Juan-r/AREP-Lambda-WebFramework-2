package co.edu.escuelaing.webframework;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WebFrameworkTest {

    @Test
    void portDefaultsTo8080WhenMissingOrBlank() {
        assertEquals(8080, WebFramework.resolvePort(null));
        assertEquals(8080, WebFramework.resolvePort("  "));
    }

    @Test
    void portIsReadFromTheVariable() {
        assertEquals(5000, WebFramework.resolvePort("5000"));
        assertEquals(10000, WebFramework.resolvePort(" 10000 "));
    }

    @Test
    void invalidPortFailsFastWithAClearMessage() {
        assertThrows(IllegalArgumentException.class, () -> WebFramework.resolvePort("abc"));
        assertThrows(IllegalArgumentException.class, () -> WebFramework.resolvePort("70000"));
    }

    @Test
    void workerThreadsComeFromTheVariableOrDefault() {
        assertEquals(HttpServer.DEFAULT_WORKER_THREADS, WebFramework.resolveWorkerThreads(null));
        assertEquals(16, WebFramework.resolveWorkerThreads(" 16 "));
        assertThrows(IllegalArgumentException.class, () -> WebFramework.resolveWorkerThreads("0"));
        assertThrows(IllegalArgumentException.class, () -> WebFramework.resolveWorkerThreads("many"));
    }

    @Test
    void shutdownTimeoutComesFromTheVariableOrDefault() {
        assertEquals(HttpServer.DEFAULT_SHUTDOWN_TIMEOUT, WebFramework.resolveShutdownTimeout(""));
        assertEquals(Duration.ofSeconds(20), WebFramework.resolveShutdownTimeout("20"));
        assertThrows(IllegalArgumentException.class, () -> WebFramework.resolveShutdownTimeout("-1"));
    }
}
