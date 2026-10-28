package com.networknt.router;

import com.networknt.config.Config;
import com.networknt.handler.Handler;
import com.networknt.server.ServerConfig;
import com.networknt.handler.OrchestrationHandler;
import io.undertow.Undertow;
import io.undertow.util.HttpString;
import javax.net.ssl.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.security.KeyStore;
import java.util.*;

/** Isolated JVM: production handler configuration without sharing static caches with other tests. */
public class HstsApplicationFixture {
    static Map<String, Object> yaml(String resource) throws Exception {
        try (InputStream in = HstsApplicationFixture.class.getResourceAsStream(resource)) {
            return Config.getInstance().getYaml().load(Objects.requireNonNull(in, resource));
        }
    }

    static SSLContext tls() throws Exception {
        KeyStore keys = KeyStore.getInstance("JKS");
        try (InputStream in = HstsApplicationFixture.class.getResourceAsStream("/config/server.keystore")) {
            keys.load(in, "password".toCharArray());
        }
        KeyManagerFactory km = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        km.init(keys, "password".toCharArray());
        KeyStore trust = KeyStore.getInstance("JKS");
        try (InputStream in = HstsApplicationFixture.class.getResourceAsStream("/config/client.truststore")) {
            trust.load(in, "password".toCharArray());
        }
        TrustManagerFactory tm = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tm.init(trust);
        SSLContext tls = SSLContext.getInstance("TLS");
        tls.init(km.getKeyManagers(), tm.getTrustManagers(), null);
        return tls;
    }

    static int port(Undertow server) {
        return ((InetSocketAddress) server.getListenerInfo().getFirst().getAddress()).getPort();
    }

    public static void main(String[] args) throws Exception {
        Path directory = Path.of(args[1]);
        Undertow backend = Undertow.builder().setIoThreads(1).setWorkerThreads(2)
                .addHttpListener(0, "127.0.0.1").setHandler(exchange -> {
                    exchange.getResponseHeaders().put(new HttpString("Strict-Transport-Security"), "max-age=0");
                    exchange.getResponseHeaders().put(new HttpString("Cache-Control"), "public");
                    exchange.getResponseHeaders().put(new HttpString("X-Powered-By"), "backend");
                    String path = exchange.getRequestPath();
                    exchange.setStatusCode(switch (path) {
                        case "/redirect" -> 302; case "/error" -> 500; case "/missing" -> 404; default -> 200;
                    });
                    if (path.equals("/redirect")) exchange.getResponseHeaders().put(new HttpString("Location"), "/get");
                    exchange.getResponseSender().send("controlled backend");
                }).build();
        backend.start();
        Map<String, Object> values = new LinkedHashMap<>(yaml("/hsts-input/values.yml"));
        boolean overlay = args[0].equals("hsts");
        if (overlay) values.putAll(yaml("/hsts-input/overlay/values-overlay.yml"));
        // Only external integrations are disabled. Production handlers, chains and routes are retained.
        values.put("server.enableRegistry", false);
        values.put("security.enableVerifyJwt", false);
        values.put("unified-security.enabled", false);
        values.put("openapi-validator.enabled", false);
        values.put("token.enabled", false);
        values.put("openapi-handler.ignoreInvalidPath", true);
        values.put("proxy.hosts", "http://127.0.0.1:" + port(backend));
        values.put("proxy.metricsInjection", false);
        values.put("router.metricsInjection", false);
        Files.writeString(directory.resolve("values.yml"), Config.getInstance().getMapper().writeValueAsString(values));
        System.setProperty("light-4j-config-dir", directory.toString());
        Config.getInstance().clear();
        ServerConfig server = ServerConfig.load();
        if (!server.isEnableHttps() || server.isEnableHttp() == overlay) {
            throw new IllegalStateException("Unexpected listener policy loaded from values");
        }
        Handler.init();
        Undertow.Builder builder = Undertow.builder().setIoThreads(1).setWorkerThreads(4)
                .addHttpsListener(0, "127.0.0.1", tls()).setHandler(new OrchestrationHandler());
        if (server.isEnableHttp()) builder.addHttpListener(0, "127.0.0.1");
        Undertow app = builder.build();
        app.start();
        String ports = Integer.toString(port(app));
        if (!overlay) ports += "," + ((InetSocketAddress) app.getListenerInfo().get(1).getAddress()).getPort();
        Files.writeString(directory.resolve("ready"), ports);
        try { System.in.read(); } finally { app.stop(); backend.stop(); }
        System.exit(0);
    }
}
