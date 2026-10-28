package com.networknt.router;

import com.networknt.specification.FaviconHandler;
import com.networknt.config.Config;
import com.networknt.config.yml.YamlUtils;
import java.util.ArrayList;
import java.util.Map;
import java.util.LinkedHashMap;

import com.networknt.header.HeaderHandler;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.ExceptionHandler;
import io.undertow.server.handlers.ResponseCodeHandler;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class SpecificationHstsConfigurationTest {
    private static final String HSTS = "Strict-Transport-Security";
    private static final String CONFIG_NAME = "sidecar-hsts-test-header";
    private static Map<String, Object> application;
    private static Map<String, Object> merged;
    private static final String POLICY = "max-age=31536000";
    // This password protects a temporary, test-generated loopback certificate only.
    private static final String TEST_PASSWORD = "hsts-test-only";
    @TempDir
    static Path directory;
    private static Undertow configured;
    private static Undertow withoutMiddleware;
    private static HttpClient client;

    @BeforeAll
    static void startTlsServers() throws Exception {
        application = readYaml(Path.of("src/main/resources/config/values.yml"));
        merged = new LinkedHashMap<>(application);
        merged.putAll(readYaml(Path.of("config/hsts/values-overlay.yml")));
        assertEquals(Boolean.TRUE, merged.get("server.enableHttps"));
        assertEquals(Boolean.FALSE, merged.get("server.enableHttp"));
        Config.getInstance().putInConfigCache(CONFIG_NAME, Map.of(
                "enabled", merged.get("header.enabled"), "request", Map.of(),
                "response", Map.of("update", merged.get("header.response.update"))));
        SSLContext tls = testTlsContext();
        client = HttpClient.newBuilder().sslContext(tls).version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
        HttpHandler routes = Handlers.routing()
                .get("/favicon.ico", new FaviconHandler())
                .get("/normal", exchange -> exchange.getResponseSender().send("ok"))
                .get("/redirect", exchange -> {
                    exchange.setStatusCode(302);
                    exchange.getResponseHeaders().put(io.undertow.util.Headers.LOCATION, "/favicon.ico");
                    exchange.endExchange();
                })
                .get("/error", exchange -> { throw new IllegalStateException("controlled test failure"); });
        HttpHandler errors = new ExceptionHandler(routes)
                .addExceptionHandler(IllegalStateException.class, ResponseCodeHandler.HANDLE_500);
        HeaderHandler header = new HeaderHandler(CONFIG_NAME);
        assertTrue(header.isEnabled());
        header.setNext(errors);
        configured = start(tls, header);
        withoutMiddleware = start(tls, errors);
    }

    private static Undertow start(SSLContext tls, HttpHandler handler) {
        Undertow server = Undertow.builder().setIoThreads(1).setWorkerThreads(2)
                .addHttpsListener(0, "127.0.0.1", tls).setHandler(handler).build();
        server.start();
        return server;
    }

    @AfterAll
    static void stopServers() {
        if (client != null) client.close();
        if (configured != null) configured.stop();
        if (withoutMiddleware != null) withoutMiddleware.stop();
        Config.getInstance().clearConfigCache(CONFIG_NAME);
    }

    @Test
    void faviconWithoutMiddlewareDoesNotClaimHostPolicy() throws Exception {
        HttpResponse<byte[]> response = get(withoutMiddleware, "/favicon.ico");
        assertEquals(200, response.statusCode());
        assertTrue(response.headers().allValues(HSTS).isEmpty());
    }

    @Test
    void configuredFaviconHasSingleHstsAndPreservesIcon() throws Exception {
        HttpResponse<byte[]> response = get(configured, "/favicon.ico");
        assertPolicy(response, 200);
        assertEquals("image/x-icon", response.headers().firstValue("Content-Type").orElseThrow());
        assertEquals("nosniff", response.headers().firstValue("X-Content-Type-Options").orElseThrow());
        assertArrayEquals(get(withoutMiddleware, "/favicon.ico").body(), response.body());
    }

    @Test
    void normalResponseHasHsts() throws Exception {
        HttpResponse<byte[]> response = get(configured, "/normal");
        assertPolicy(response, 200);
        assertEquals("ok", new String(response.body(), StandardCharsets.UTF_8));
    }

    @Test
    void httpsRedirectHasHsts() throws Exception {
        HttpResponse<byte[]> response = get(configured, "/redirect");
        assertPolicy(response, 302);
        assertEquals("/favicon.ico", response.headers().firstValue("Location").orElseThrow());
    }

    @Test
    void unmatchedRouteHasHsts() throws Exception {
        assertPolicy(get(configured, "/not-found"), 404);
    }

    @Test
    void exceptionResponseHasHsts() throws Exception {
        assertPolicy(get(configured, "/error"), 500);
    }

    @Test
    @SuppressWarnings("unchecked")
    void applicationRoutesRunTheConfiguredHeaderBeforeTerminalHandlers() {
        Map<String, List<String>> chains = (Map<String, List<String>>) merged.get("handler.chains");
        List<Map<String, Object>> paths = (List<Map<String, Object>>) merged.get("handler.paths");
        assertFalse(paths.isEmpty());
        for (Map<String, Object> path : paths) {
            List<String> expanded = new ArrayList<>();
            for (String handler : (List<String>) path.get("exec")) {
                expanded.addAll(chains.getOrDefault(handler, List.of(handler)));
            }
            assertEquals("header", expanded.getFirst(), "Header must run first for " + path.get("path"));
            assertEquals(1L, expanded.stream().filter("header"::equals).count());
        }
        assertTrue(paths.stream().anyMatch(path -> "/favicon.ico".equals(path.get("path"))));
    }

    @Test
    void plaintextApplicationDefaultDoesNotForceHstsPolicy() {
        assertEquals(Boolean.TRUE, application.get("server.enableHttp"));
        assertFalse(String.valueOf(application.get("header.response.update")).contains(HSTS));
    }

    private static Map<String, Object> readYaml(Path path) throws Exception {
        try (InputStream input = Files.newInputStream(path)) {
            return YamlUtils.loadMap(Config.getInstance().getYaml(), input);
        }
    }

    private static void assertPolicy(HttpResponse<?> response, int status) {
        assertEquals(status, response.statusCode());
        assertEquals(List.of(POLICY), response.headers().allValues(HSTS));
    }

    private static HttpResponse<byte[]> get(Undertow server, String path) throws Exception {
        int port = ((InetSocketAddress) server.getListenerInfo().getFirst().getAddress()).getPort();
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://localhost:" + port + path))
                .timeout(Duration.ofSeconds(5)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    private static SSLContext testTlsContext() throws Exception {
        Path store = directory.resolve("loopback.p12");
        Process keytool = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "keytool").toString(),
                "-genkeypair", "-alias", "loopback", "-keyalg", "RSA", "-keysize", "2048",
                "-validity", "1", "-dname", "CN=localhost", "-ext", "SAN=dns:localhost,ip:127.0.0.1",
                "-storetype", "PKCS12", "-keystore", store.toString(), "-storepass", TEST_PASSWORD,
                "-keypass", TEST_PASSWORD, "-noprompt").redirectErrorStream(true).start();
        if (!keytool.waitFor(20, TimeUnit.SECONDS)) {
            keytool.destroyForcibly();
            fail("Test certificate generation timed out");
        }
        assertEquals(0, keytool.exitValue(), new String(keytool.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream input = Files.newInputStream(store)) {
            keyStore.load(input, TEST_PASSWORD.toCharArray());
        }
        KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keys.init(keyStore, TEST_PASSWORD.toCharArray());
        TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trust.init(keyStore);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keys.getKeyManagers(), trust.getTrustManagers(), null);
        return context;
    }
}
