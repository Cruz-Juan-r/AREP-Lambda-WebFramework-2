package co.edu.escuelaing.webframework;

/**
 * A REST service written by the application developer as a lambda.
 *
 * <pre>{@code
 * get("/pi", (req, resp) -> String.valueOf(Math.PI));
 * }</pre>
 *
 * The returned string becomes the HTTP response body. The handler may use the
 * {@link Response} to change the status code, content type or headers.
 */
@FunctionalInterface
public interface RouteHandler {

    String handle(Request request, Response response) throws Exception;
}
