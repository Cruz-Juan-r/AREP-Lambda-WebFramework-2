package co.edu.escuelaing.app;

import java.util.Map;

/**
 * Deployment-specific, non-sensitive settings read from environment variables.
 * Nothing here is hard-coded per environment; only safe local defaults.
 */
public record AppConfig(String appEnv, String greetingPrefix, String staticFilesPath) {

    public static final String DEFAULT_ENV = "development";
    public static final String DEFAULT_GREETING = "Hello";
    public static final String DEFAULT_STATIC_FILES = "/webroot";

    public static AppConfig fromEnvironment() {
        return from(System.getenv());
    }

    public static AppConfig from(Map<String, String> env) {
        return new AppConfig(
                valueOrDefault(env, "APP_ENV", DEFAULT_ENV),
                valueOrDefault(env, "GREETING_PREFIX", DEFAULT_GREETING),
                valueOrDefault(env, "STATIC_FILES_PATH", DEFAULT_STATIC_FILES));
    }

    /** The /shutdown route only exists in development. */
    public boolean shutdownEnabled() {
        return DEFAULT_ENV.equalsIgnoreCase(appEnv);
    }

    private static String valueOrDefault(Map<String, String> env, String key, String defaultValue) {
        String value = env.get(key);
        return (value == null || value.isBlank()) ? defaultValue : value.trim();
    }
}
