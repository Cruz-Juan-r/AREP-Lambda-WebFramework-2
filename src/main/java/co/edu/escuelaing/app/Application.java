package co.edu.escuelaing.app;

import co.edu.escuelaing.webframework.Response;
import co.edu.escuelaing.webframework.RouteHandler;

import static co.edu.escuelaing.webframework.WebFramework.get;
import static co.edu.escuelaing.webframework.WebFramework.start;
import static co.edu.escuelaing.webframework.WebFramework.staticfiles;
import static co.edu.escuelaing.webframework.WebFramework.stop;

/**
 * Example application built on the framework. Note that it never touches a
 * socket: it only declares static files and lambda services.
 */
public class Application {

    public static void main(String[] args) throws Exception {
        AppConfig config = AppConfig.fromEnvironment();
        System.out.println("[app] APP_ENV=" + config.appEnv()
                + ", GREETING_PREFIX=" + config.greetingPrefix()
                + ", STATIC_FILES_PATH=" + config.staticFilesPath());

        staticfiles(config.staticFilesPath());

        get("/hello", hello(config));
        get("/pi", (req, resp) -> String.valueOf(Math.PI));
        get("/api/sum", sum());
        get("/api/info", info(config));

        if (config.shutdownEnabled()) {
            get("/shutdown", (req, resp) -> {
                stop();
                return "Server will stop after this response.";
            });
        } else {
            System.out.println("[app] /shutdown is disabled because APP_ENV=" + config.appEnv());
        }

        start(); // Reads PORT from the environment, 8080 by default.
    }

    /** GET /hello?name=Pedro -> "Hello Pedro" (prefix comes from GREETING_PREFIX). */
    static RouteHandler hello(AppConfig config) {
        return (req, resp) -> {
            String name = req.getValueOrDefault("name", "world");
            return config.greetingPrefix() + " " + name;
        };
    }

    /** GET /api/sum?a=2&b=3.5 -> {"a":2.0,"b":3.5,"sum":5.5}. Shows multiple query params. */
    static RouteHandler sum() {
        return (req, resp) -> {
            resp.type(Response.APPLICATION_JSON);
            String a = req.getValue("a");
            String b = req.getValue("b");
            if (a == null || b == null) {
                resp.status(400);
                return "{\"error\":\"Query parameters 'a' and 'b' are required, e.g. /api/sum?a=2&b=3\"}";
            }
            try {
                double x = Double.parseDouble(a.trim());
                double y = Double.parseDouble(b.trim());
                return "{\"a\":" + x + ",\"b\":" + y + ",\"sum\":" + (x + y) + "}";
            } catch (NumberFormatException e) {
                resp.status(400);
                return "{\"error\":\"'a' and 'b' must be numbers\"}";
            }
        };
    }

    /** GET /api/info -> non-sensitive runtime configuration, used as deployment evidence. */
    static RouteHandler info(AppConfig config) {
        return (req, resp) -> {
            resp.type(Response.APPLICATION_JSON);
            return "{"
                    + "\"appEnv\":\"" + json(config.appEnv()) + "\","
                    + "\"greetingPrefix\":\"" + json(config.greetingPrefix()) + "\","
                    + "\"staticFiles\":\"" + json(config.staticFilesPath()) + "\","
                    + "\"shutdownEnabled\":" + config.shutdownEnabled() + ","
                    + "\"javaVersion\":\"" + json(System.getProperty("java.version")) + "\""
                    + "}";
        };
    }

    static String json(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
