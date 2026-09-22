package co.edu.escuelaing.webframework;

import java.util.Locale;
import java.util.Map;

/** Maps file extensions to Content-Type values. */
public final class MimeTypes {

    private static final String DEFAULT = "application/octet-stream";

    private static final Map<String, String> TYPES = Map.ofEntries(
            Map.entry("html", "text/html; charset=UTF-8"),
            Map.entry("htm", "text/html; charset=UTF-8"),
            Map.entry("css", "text/css; charset=UTF-8"),
            Map.entry("js", "text/javascript; charset=UTF-8"),
            Map.entry("mjs", "text/javascript; charset=UTF-8"),
            Map.entry("json", "application/json; charset=UTF-8"),
            Map.entry("txt", "text/plain; charset=UTF-8"),
            Map.entry("xml", "application/xml; charset=UTF-8"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"),
            Map.entry("ico", "image/x-icon"),
            Map.entry("pdf", "application/pdf"),
            Map.entry("woff", "font/woff"),
            Map.entry("woff2", "font/woff2")
    );

    private MimeTypes() {
    }

    public static String forPath(String path) {
        if (path == null) {
            return DEFAULT;
        }
        int slash = path.lastIndexOf('/');
        int dot = path.lastIndexOf('.');
        if (dot < 0 || dot < slash) {
            return DEFAULT;
        }
        return TYPES.getOrDefault(path.substring(dot + 1).toLowerCase(Locale.ROOT), DEFAULT);
    }
}
