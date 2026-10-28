package com.networknt.router;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class SpecificationHstsConfigurationTest {
    @TempDir static Path directory;
    static final List<Process> processes = new ArrayList<>();
    static HttpClient client;
    static int[] configured, defaults;

    @BeforeAll static void start() throws Exception {
        client = HttpClient.newBuilder().sslContext(HstsApplicationFixture.tls())
                .connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
        configured = launch("hsts");
        defaults = launch("default");
    }

    static int[] launch(String mode) throws Exception {
        Path config = Files.createDirectory(directory.resolve(mode));
        Path log = directory.resolve(mode + ".log");
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Dlight-4j-config-dir=" + config, "-cp", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                HstsApplicationFixture.class.getName(), mode, config.toString())
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        processes.add(process);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
        while (!Files.exists(config.resolve("ready")) && process.isAlive() && System.nanoTime() < deadline) Thread.sleep(50);
        assertTrue(Files.exists(config.resolve("ready")), () -> {
            try { return Files.readString(log); } catch (Exception e) { return e.toString(); }
        });
        return Arrays.stream(Files.readString(config.resolve("ready")).split(",")).mapToInt(Integer::parseInt).toArray();
    }

    @AfterAll static void stop() throws Exception {
        if (client != null) client.close();
        for (Process process : processes) {
            if (process.isAlive()) {
                process.getOutputStream().write(10);
                process.getOutputStream().flush();
                if (!process.waitFor(10, TimeUnit.SECONDS)) process.destroyForcibly();
            }
        }
    }

    static HttpResponse<byte[]> get(String scheme, int port, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(scheme + "://localhost:" + port + path))
                .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    static void policy(HttpResponse<?> response) {
        assertEquals(List.of("max-age=31536000"), response.headers().allValues("Strict-Transport-Security"));
        assertEquals(List.of("no-store"), response.headers().allValues("Cache-Control"));
        assertEquals(List.of("nosniff"), response.headers().allValues("X-Content-Type-Options"));
        assertTrue(response.headers().allValues("X-Powered-By").isEmpty());
    }

    @Test void faviconPreservesContent() throws Exception {
        var response = get("https", configured[0], "/favicon.ico");
        assertEquals(200, response.statusCode());
        policy(response);
        assertEquals("image/x-icon", response.headers().firstValue("Content-Type").orElseThrow());
        var control = get("https", defaults[0], "/favicon.ico");
        assertTrue(control.headers().allValues("Strict-Transport-Security").isEmpty());
        assertArrayEquals(control.body(), response.body());
    }

    @Test void directAndAdminRoutesReceivePolicy() throws Exception {
        for (String path : List.of("/health", "/spec.yaml", "/specui.html", "/adm/modules", "/adm/logger", "/adm/server/info")) {
            var response = get("https", configured[0], path);
            policy(response);
            assertTrue(response.statusCode() < 500, path + " status " + response.statusCode());
        }
    }

    @Test void backendCannotOverridePolicyIncludingRedirectAndErrors() throws Exception {
        for (var entry : Map.of("/get", 200, "/redirect", 302, "/error", 500, "/missing", 404).entrySet()) {
            var response = get("https", configured[0], entry.getKey());
            assertEquals(entry.getValue(), response.statusCode());
            assertEquals("controlled backend", new String(response.body(), java.nio.charset.StandardCharsets.UTF_8));
            policy(response);
        }
    }

    @Test void packagedDefaultRetainsPlaintextEgress() throws Exception {
        var response = get("http", defaults[1], "/get");
        assertEquals(200, response.statusCode());
        assertEquals("controlled backend", new String(response.body(), java.nio.charset.StandardCharsets.UTF_8));
        assertTrue(get("http", defaults[1], "/favicon.ico").headers().allValues("Strict-Transport-Security").isEmpty());
        assertEquals(List.of("no-store"), get("https", defaults[0], "/adm/logger").headers().allValues("Cache-Control"));
    }

    @Test @SuppressWarnings("unchecked") void allPackagedRoutesStartWithHeaderAndOverlayPreservesChains() throws Exception {
        var values = HstsApplicationFixture.yaml("/hsts-input/values.yml");
        var overlay = HstsApplicationFixture.yaml("/hsts-input/overlay/values-overlay.yml");
        assertFalse(overlay.containsKey("handler.chains"));
        assertFalse(overlay.containsKey("header.response.update"));
        assertEquals("X-Powered-By", values.get("header.response.remove"));
        Map<String, List<String>> chains = (Map<String, List<String>>) values.get("handler.chains");
        for (var route : (List<Map<String, Object>>) values.get("handler.paths")) {
            String first = ((List<String>) route.get("exec")).getFirst();
            assertEquals("header", chains.getOrDefault(first, List.of(first)).getFirst(), route.get("path").toString());
        }
    }
}
