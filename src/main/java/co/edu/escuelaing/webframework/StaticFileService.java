package co.edu.escuelaing.webframework;

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Serves files from a folder. The folder can live on the classpath
 * (e.g. {@code /webroot} inside {@code src/main/resources}) or on disk.
 * Binary files (images, fonts, PDFs) are read as raw bytes.
 */
public final class StaticFileService {

    private static final String INDEX = "index.html";

    private final String classpathRoot;
    private final Path directoryRoot;

    private StaticFileService(String classpathRoot, Path directoryRoot) {
        this.classpathRoot = classpathRoot;
        this.directoryRoot = directoryRoot;
    }

    /** Serves files packaged in the jar under the given classpath folder. */
    public static StaticFileService fromClasspath(String folder) {
        String root = folder.startsWith("/") ? folder : "/" + folder;
        while (root.length() > 1 && root.endsWith("/")) {
            root = root.substring(0, root.length() - 1);
        }
        return new StaticFileService(root.equals("/") ? "" : root, null);
    }

    /** Serves files from a directory on the file system. */
    public static StaticFileService fromDirectory(Path directory) {
        return new StaticFileService(null, directory.toAbsolutePath().normalize());
    }

    /**
     * Uses a file-system directory when {@code location} exists on disk,
     * otherwise treats it as a classpath folder.
     */
    public static StaticFileService of(String location) {
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException("Static files location must not be empty");
        }
        try {
            Path candidate = Path.of(location);
            if (Files.isDirectory(candidate)) {
                return fromDirectory(candidate);
            }
        } catch (RuntimeException ignored) {
            // Not a valid file-system path: fall back to the classpath.
        }
        return fromClasspath(location);
    }

    public String describe() {
        return directoryRoot != null ? "directory " + directoryRoot : "classpath:" + (classpathRoot.isEmpty() ? "/" : classpathRoot);
    }

    public Optional<StaticResource> find(String requestPath) {
        String relative = toSafeRelativePath(requestPath);
        if (relative == null) {
            return Optional.empty();
        }
        try {
            Optional<byte[]> bytes = directoryRoot != null ? readFromDirectory(relative) : readFromClasspath(relative);
            return bytes.map(content -> new StaticResource(content, MimeTypes.forPath(relative)));
        } catch (IOException e) {
            System.err.println("[static] Could not read " + relative + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Converts "/", "/docs/" and "/app.js" into a path relative to the root.
     * Returns null for anything that tries to escape the root.
     */
    static String toSafeRelativePath(String requestPath) {
        if (requestPath == null || !requestPath.startsWith("/")
                || requestPath.indexOf('\0') >= 0 || requestPath.contains("\\")) {
            return null;
        }
        String path = requestPath.endsWith("/") ? requestPath + INDEX : requestPath;
        for (String segment : path.split("/")) {
            if (segment.equals("..") || segment.equals(".")) {
                return null;
            }
        }
        String relative = path.replaceAll("/+", "/").substring(1);
        return relative.isEmpty() ? null : relative;
    }

    private Optional<byte[]> readFromDirectory(String relative) throws IOException {
        Path file = directoryRoot.resolve(relative).normalize();
        if (!file.startsWith(directoryRoot) || !Files.isRegularFile(file)) {
            return Optional.empty();
        }
        return Optional.of(Files.readAllBytes(file));
    }

    private Optional<byte[]> readFromClasspath(String relative) throws IOException {
        String resourceName = (classpathRoot + "/" + relative).substring(1);
        URL url = Thread.currentThread().getContextClassLoader().getResource(resourceName);
        if (url == null) {
            url = StaticFileService.class.getClassLoader().getResource(resourceName);
        }
        if (url == null || !isRegularFile(url)) {
            return Optional.empty();
        }
        try (InputStream in = url.openStream()) {
            return Optional.of(in.readAllBytes());
        }
    }

    /** Directories on the classpath must not be served as files. */
    private static boolean isRegularFile(URL url) throws IOException {
        switch (url.getProtocol()) {
            case "file":
                try {
                    return Files.isRegularFile(Path.of(url.toURI()));
                } catch (URISyntaxException e) {
                    return false;
                }
            case "jar":
                URLConnection connection = url.openConnection();
                connection.setUseCaches(false);
                if (connection instanceof JarURLConnection jar) {
                    return jar.getJarEntry() != null && !jar.getJarEntry().isDirectory();
                }
                return false;
            default:
                return true;
        }
    }
}
