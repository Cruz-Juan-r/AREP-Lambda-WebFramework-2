package co.edu.escuelaing.webframework;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mutable response metadata that a lambda handler can adjust. The body is the
 * value returned by the handler; the server turns everything into bytes.
 */
public final class Response {

    public static final String TEXT_PLAIN = "text/plain; charset=UTF-8";
    public static final String APPLICATION_JSON = "application/json; charset=UTF-8";

    private int status = 200;
    private String contentType = TEXT_PLAIN;
    private final Map<String, String> headers = new LinkedHashMap<>();

    public Response status(int status) {
        if (status < 100 || status > 599) {
            throw new IllegalArgumentException("Invalid HTTP status: " + status);
        }
        this.status = status;
        return this;
    }

    public Response type(String contentType) {
        if (contentType != null && !contentType.isBlank()) {
            this.contentType = contentType;
        }
        return this;
    }

    public Response header(String name, String value) {
        headers.put(name, value);
        return this;
    }

    public int getStatus() {
        return status;
    }

    public String getContentType() {
        return contentType;
    }

    public Map<String, String> getHeaders() {
        return Collections.unmodifiableMap(headers);
    }
}
