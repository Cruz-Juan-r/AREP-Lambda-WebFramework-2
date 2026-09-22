package co.edu.escuelaing.app;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppConfigTest {

    @Test
    void usesLocalDefaultsWhenVariablesAreMissing() {
        AppConfig config = AppConfig.from(Map.of());
        assertEquals("development", config.appEnv());
        assertEquals("Hello", config.greetingPrefix());
        assertEquals("/webroot", config.staticFilesPath());
        assertTrue(config.shutdownEnabled());
    }

    @Test
    void readsValuesFromEnvironment() {
        AppConfig config = AppConfig.from(Map.of(
                "APP_ENV", "production",
                "GREETING_PREFIX", "Hola",
                "STATIC_FILES_PATH", "/srv/www"));
        assertEquals("production", config.appEnv());
        assertEquals("Hola", config.greetingPrefix());
        assertEquals("/srv/www", config.staticFilesPath());
    }

    @Test
    void shutdownIsDisabledOutsideDevelopment() {
        assertFalse(AppConfig.from(Map.of("APP_ENV", "production")).shutdownEnabled());
        assertFalse(AppConfig.from(Map.of("APP_ENV", "staging")).shutdownEnabled());
    }

    @Test
    void blankValuesFallBackToDefaults() {
        AppConfig config = AppConfig.from(Map.of("GREETING_PREFIX", "   ", "APP_ENV", ""));
        assertEquals("Hello", config.greetingPrefix());
        assertEquals("development", config.appEnv());
    }
}
