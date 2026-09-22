package co.edu.escuelaing.webframework;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Immutable representation of an HTTP request as seen by a lambda handler.
 * Instances are created by {@link HttpRequestParser}.
 */
public final class Request {

    private final String method;
    private final String path;
    private final String queryString;
    private final String httpVersion;
    private final Map<String, List<String>> queryParams;
    private final Map<String, String> headers;

    public Request(String method, String path, String queryString, String httpVersion,
                   Map<String, List<String>> queryParams, Map<String, String> headers) {
        this.method = method;
        this.path = path;
        this.queryString = queryString == null ? "" : queryString;
        this.httpVersion = httpVersion;
        this.queryParams = Collections.unmodifiableMap(new LinkedHashMap<>(queryParams));
        this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(headers));
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    /** Raw query string without the leading '?', or an empty string. */
    public String getQueryString() {
        return queryString;
    }

    public String getHttpVersion() {
        return httpVersion;
    }

    /**
     * Returns the first (URL-decoded) value of a query parameter, or {@code null}
     * when the parameter is not present. Never throws for missing parameters.
     */
    public String getValue(String name) {
        List<String> values = queryParams.get(name);
        return (values == null || values.isEmpty()) ? null : values.get(0);
    }

    /** Returns the value of a query parameter or a default when it is missing or blank. */
    public String getValueOrDefault(String name, String defaultValue) {
        String value = getValue(name);
        return (value == null || value.isBlank()) ? defaultValue : value;
    }

    /** All values of a repeated parameter, e.g. {@code ?tag=a&tag=b}. */
    public List<String> getValues(String name) {
        return queryParams.getOrDefault(name, List.of());
    }

    public boolean hasValue(String name) {
        return queryParams.containsKey(name);
    }

    public Map<String, List<String>> getQueryParams() {
        return queryParams;
    }

    /** Header lookup is case-insensitive. */
    public String getHeader(String name) {
        return name == null ? null : headers.get(name.toLowerCase(Locale.ROOT));
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    @Override
    public String toString() {
        return method + " " + path + (queryString.isEmpty() ? "" : "?" + queryString);
    }
}
