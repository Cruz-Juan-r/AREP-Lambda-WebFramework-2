package co.edu.escuelaing.webframework;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaticFileServiceTest {

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private final StaticFileService classpath = StaticFileService.of("/webroot");

    @Test
    void servesHtmlCssAndJavaScriptFromClasspath() {
        assertEquals("text/html; charset=UTF-8", classpath.find("/index.html").orElseThrow().contentType());
        assertEquals("text/css; charset=UTF-8", classpath.find("/styles.css").orElseThrow().contentType());
        assertEquals("text/javascript; charset=UTF-8", classpath.find("/app.js").orElseThrow().contentType());
    }

    @Test
    void servesBinaryImagesByteForByte() {
        StaticResource logo = classpath.find("/images/logo.png").orElseThrow();
        assertEquals("image/png", logo.contentType());
        byte[] header = new byte[8];
        System.arraycopy(logo.content(), 0, header, 0, 8);
        assertArrayEquals(PNG_SIGNATURE, header);
    }

    @Test
    void rootPathServesIndex() {
        String html = new String(classpath.find("/").orElseThrow().content(), StandardCharsets.UTF_8);
        assertTrue(html.contains("<title>Lambda Web Framework</title>"));
    }

    @Test
    void missingFilesAndDirectoriesAreEmpty() {
        assertTrue(classpath.find("/missing.html").isEmpty());
        assertTrue(classpath.find("/images").isEmpty());
    }

    @Test
    void pathTraversalIsBlocked() {
        assertTrue(classpath.find("/../co/edu/escuelaing/app/Application.class").isEmpty());
        assertTrue(classpath.find("/images/../../secret").isEmpty());
        assertNull(StaticFileService.toSafeRelativePath("/a\\b"));
        assertNull(StaticFileService.toSafeRelativePath("relative"));
    }

    @Test
    void servesFromDirectoryOnDisk(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("index.html"), "<h1>disk</h1>");
        Files.createDirectories(dir.resolve("css"));
        Files.writeString(dir.resolve("css/site.css"), "body{}");

        StaticFileService disk = StaticFileService.of(dir.toString());
        assertTrue(disk.describe().startsWith("directory"));
        assertEquals("<h1>disk</h1>", new String(disk.find("/").orElseThrow().content(), StandardCharsets.UTF_8));
        assertEquals("text/css; charset=UTF-8", disk.find("/css/site.css").orElseThrow().contentType());
        assertTrue(disk.find("/css").isEmpty());
        assertTrue(disk.find("/../outside.txt").isEmpty());
    }

    @Test
    void mimeTypes() {
        assertEquals("image/jpeg", MimeTypes.forPath("/a/photo.JPG"));
        assertEquals("application/octet-stream", MimeTypes.forPath("/noextension"));
        assertEquals("application/octet-stream", MimeTypes.forPath("/dir.v2/file"));
    }
}
