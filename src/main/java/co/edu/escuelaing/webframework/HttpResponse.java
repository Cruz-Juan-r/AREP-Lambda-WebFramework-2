package co.edu.escuelaing.webframework;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** A complete HTTP response ready to be written to a socket. */
public final class HttpResponse {

    private static final Map<Integer, String> REASONS = Map.of(
            200, "OK",
            201, "Created",
            204, "No Content",
            400, "Bad Request",
            404, "Not Found",
            405, "Method Not Allowed",
            500, "Internal Server Error",
            503, "Service Unavailable");

    private final int status;
    private final String contentType;
    private final Map<String, String> headers;
    private final byte[] body;

    public HttpResponse(int status, String contentType, Map<String, String> headers, byte[] body) {
        this.status = status;
        this.contentType = contentType;
        this.headers = new LinkedHashMap<>(headers);
        this.body = body == null ? new byte[0] : body;
    }

    public static HttpResponse text(int status, String message) {
        return new HttpResponse(status, Response.TEXT_PLAIN, Map.of(),
                message.getBytes(StandardCharsets.UTF_8));
    }

    /** Standard error body, e.g. "404 Not Found". */
    public static HttpResponse error(int status) {
        return text(status, status + " " + reason(status));
    }

    public static String reason(int status) {
        return REASONS.getOrDefault(status, status < 400 ? "OK" : "Error");
    }

    public int getStatus() {
        return status;
    }

    public String getContentType() {
        return contentType;
    }

    public byte[] getBody() {
        return body;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void writeTo(OutputStream out) throws IOException {
        StringBuilder head = new StringBuilder();
        head.append("HTTP/1.1 ").append(status).append(' ').append(reason(status)).append("\r\n");
        head.append("Content-Type: ").append(contentType).append("\r\n");
        head.append("Content-Length: ").append(body.length).append("\r\n");
        head.append("Server: LambdaWebFramework/1.0\r\n");
        head.append("Connection: close\r\n");
        headers.forEach((name, value) -> head.append(name).append(": ").append(value).append("\r\n"));
        head.append("\r\n");

        ByteArrayOutputStream bytes = new ByteArrayOutputStream(head.length() + body.length);
        bytes.write(head.toString().getBytes(StandardCharsets.ISO_8859_1));
        bytes.write(body);
        out.write(bytes.toByteArray());
        out.flush();
    }
}
