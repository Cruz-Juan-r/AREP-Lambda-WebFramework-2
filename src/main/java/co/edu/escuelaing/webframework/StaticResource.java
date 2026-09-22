package co.edu.escuelaing.webframework;

/** Bytes of a static file plus the MIME type used to send it. */
public record StaticResource(byte[] content, String contentType) {
}
