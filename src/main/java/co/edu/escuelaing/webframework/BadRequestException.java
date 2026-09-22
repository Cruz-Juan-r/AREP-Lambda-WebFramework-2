package co.edu.escuelaing.webframework;

/** Raised when the incoming bytes are not a valid HTTP/1.x request. */
public class BadRequestException extends Exception {

    private static final long serialVersionUID = 1L;

    public BadRequestException(String message) {
        super(message);
    }
}
