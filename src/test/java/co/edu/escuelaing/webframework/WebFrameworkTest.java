package co.edu.escuelaing.webframework;

import org.junit.jupiter.api.Test;

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
}
