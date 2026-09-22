package co.edu.escuelaing.webframework;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpRequestParserTest {

    static Request parse(String raw) throws Exception {
        return HttpRequestParser.parse(new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void parsesMethodPathAndVersion() throws Exception {
        Request request = parse("GET /index.html HTTP/1.1\r\nHost: localhost\r\n\r\n");
        assertEquals("GET", request.getMethod());
        assertEquals("/index.html", request.getPath());
        assertEquals("HTTP/1.1", request.getHttpVersion());
        assertEquals("", request.getQueryString());
        assertEquals("localhost", request.getHeader("HOST"));
    }

    @Test
    void extractsSingleQueryParameter() throws Exception {
        Request request = parse("GET /hello?name=Pedro HTTP/1.1\r\n\r\n");
        assertEquals("/hello", request.getPath());
        assertEquals("Pedro", request.getValue("name"));
    }

    @Test
    void extractsMultipleQueryParameters() throws Exception {
        Request request = parse("GET /hello?name=Pedro&language=en HTTP/1.1\r\n\r\n");
        assertEquals("Pedro", request.getValue("name"));
        assertEquals("en", request.getValue("language"));
        assertEquals("name=Pedro&language=en", request.getQueryString());
    }

    @Test
    void missingParameterReturnsNullInsteadOfFailing() throws Exception {
        Request request = parse("GET /hello HTTP/1.1\r\n\r\n");
        assertNull(request.getValue("name"));
        assertFalse(request.hasValue("name"));
        assertEquals("world", request.getValueOrDefault("name", "world"));
        assertEquals(List.of(), request.getValues("name"));
    }

    @Test
    void decodesPercentEncodingPlusSignsAndUtf8() throws Exception {
        Request request = parse("GET /hello?name=Juan+Esteban&city=Bogot%C3%A1&msg=a%26b HTTP/1.1\r\n\r\n");
        assertEquals("Juan Esteban", request.getValue("name"));
        assertEquals("Bogotá", request.getValue("city"));
        assertEquals("a&b", request.getValue("msg"));
    }

    @Test
    void supportsRepeatedAndValuelessParameters() throws Exception {
        Request request = parse("GET /search?tag=a&tag=b&flag&=ignored HTTP/1.1\r\n\r\n");
        assertEquals(List.of("a", "b"), request.getValues("tag"));
        assertEquals("", request.getValue("flag"));
        assertTrue(request.hasValue("flag"));
        assertEquals(2, request.getQueryParams().size());
    }

    @Test
    void acceptsBareLineFeeds() throws Exception {
        Request request = parse("GET /pi HTTP/1.0\n\n");
        assertEquals("/pi", request.getPath());
    }

    @Test
    void emptyConnectionReturnsNull() throws Exception {
        assertNull(parse(""));
    }

    @Test
    void rejectsMalformedRequestLines() {
        assertThrows(BadRequestException.class, () -> parse("\r\n\r\n"));
        assertThrows(BadRequestException.class, () -> parse("GET\r\n\r\n"));
        assertThrows(BadRequestException.class, () -> parse("GET /hello\r\n\r\n"));
        assertThrows(BadRequestException.class, () -> parse("GET /a b HTTP/1.1\r\n\r\n"));
        assertThrows(BadRequestException.class, () -> parse("GET hello HTTP/1.1\r\n\r\n"));
        assertThrows(BadRequestException.class, () -> parse("GET /hello FTP/1.0\r\n\r\n"));
        assertThrows(BadRequestException.class, () -> parse("G3T /hello HTTP/1.1\r\n\r\n"));
    }

    @Test
    void rejectsBadEncodingAndBadHeaders() {
        assertThrows(BadRequestException.class, () -> parse("GET /hello?name=%ZZ HTTP/1.1\r\n\r\n"));
        assertThrows(BadRequestException.class, () -> parse("GET /hello HTTP/1.1\r\nNoColonHere\r\n\r\n"));
    }

    @Test
    void rejectsOverlyLongLines() {
        String longPath = "/" + "a".repeat(9000);
        assertThrows(BadRequestException.class, () -> parse("GET " + longPath + " HTTP/1.1\r\n\r\n"));
    }
}
