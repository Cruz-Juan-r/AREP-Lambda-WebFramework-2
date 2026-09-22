package co.edu.escuelaing.webframework;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns the raw bytes of a connection into a {@link Request}.
 * It only reads the request line and the headers (GET requests carry no body).
 */
public final class HttpRequestParser {

    private static final int MAX_LINE_LENGTH = 8 * 1024;
    private static final int MAX_HEADERS = 100;

    private HttpRequestParser() {
    }

    /**
     * @return the parsed request, or {@code null} if the client closed the
     *         connection without sending anything (e.g. a browser pre-connect).
     * @throws BadRequestException if the request line or a header is malformed.
     */
    public static Request parse(InputStream in) throws IOException, BadRequestException {
        String requestLine = readLine(in);
        if (requestLine == null) {
            return null;
        }
        if (requestLine.isBlank()) {
            throw new BadRequestException("Empty request line");
        }

        String[] parts = requestLine.trim().split("\\s+");
        if (parts.length != 3) {
            throw new BadRequestException("Malformed request line: " + requestLine);
        }
        String method = parts[0].toUpperCase(Locale.ROOT);
        String target = parts[1];
        String version = parts[2];

        if (!method.chars().allMatch(Character::isLetter)) {
            throw new BadRequestException("Invalid method: " + parts[0]);
        }
        if (!version.startsWith("HTTP/1.")) {
            throw new BadRequestException("Unsupported protocol version: " + version);
        }
        if (!target.startsWith("/")) {
            throw new BadRequestException("Request target must start with '/': " + target);
        }

        int questionMark = target.indexOf('?');
        String rawPath = questionMark >= 0 ? target.substring(0, questionMark) : target;
        String queryString = questionMark >= 0 ? target.substring(questionMark + 1) : "";

        Map<String, String> headers = readHeaders(in);
        return new Request(method, decodePath(rawPath), queryString, version,
                parseQuery(queryString), headers);
    }

    /** Parses {@code a=1&b=hello%20world&flag} into an ordered multimap. */
    public static Map<String, List<String>> parseQuery(String queryString) throws BadRequestException {
        Map<String, List<String>> params = new LinkedHashMap<>();
        if (queryString == null || queryString.isEmpty()) {
            return params;
        }
        for (String pair : queryString.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int equals = pair.indexOf('=');
            String key = decodeComponent(equals >= 0 ? pair.substring(0, equals) : pair);
            String value = equals >= 0 ? decodeComponent(pair.substring(equals + 1)) : "";
            if (key.isEmpty()) {
                continue;
            }
            params.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
        }
        return params;
    }

    private static Map<String, String> readHeaders(InputStream in) throws IOException, BadRequestException {
        Map<String, String> headers = new LinkedHashMap<>();
        String line;
        while ((line = readLine(in)) != null && !line.isEmpty()) {
            if (headers.size() >= MAX_HEADERS) {
                throw new BadRequestException("Too many headers");
            }
            int colon = line.indexOf(':');
            if (colon <= 0) {
                throw new BadRequestException("Malformed header: " + line);
            }
            headers.put(line.substring(0, colon).trim().toLowerCase(Locale.ROOT),
                    line.substring(colon + 1).trim());
        }
        return headers;
    }

    private static String decodeComponent(String value) throws BadRequestException {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid percent-encoding: " + value);
        }
    }

    private static String decodePath(String rawPath) throws BadRequestException {
        // In a path '+' is a literal plus sign, not a space.
        return decodeComponent(rawPath.replace("+", "%2B"));
    }

    /**
     * Reads one line terminated by LF (optionally preceded by CR).
     * Returns {@code null} on end of stream before any byte was read.
     */
    private static String readLine(InputStream in) throws IOException, BadRequestException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int b;
        boolean readAnything = false;
        while ((b = in.read()) != -1) {
            readAnything = true;
            if (b == '\n') {
                break;
            }
            if (buffer.size() >= MAX_LINE_LENGTH) {
                throw new BadRequestException("Line too long");
            }
            buffer.write(b);
        }
        if (!readAnything) {
            return null;
        }
        String line = buffer.toString(StandardCharsets.ISO_8859_1);
        return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    }
}
