/*
 * Copyright (c) 2016 Network New Technologies Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.networknt.router;

import com.networknt.TestServer;
import com.networknt.common.DecryptUtil;
import com.networknt.common.SecretConstants;
import com.networknt.config.Config;
import com.networknt.server.ServerConfig;
import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import org.junit.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.util.Map;

import static com.networknt.server.Server.TRUST_ALL_CERTS;
import static io.undertow.Handlers.path;

public class BaseRouterTest {
    public  static final Logger logger = LoggerFactory.getLogger(BaseRouterTest.class);
    public static final String CONFIG_NAME = "server";
    public static final String CONFIG_SECRET = "secret";

    static Undertow server1 = null;
    @ClassRule
    public static TestServer server = TestServer.getInstance();
    public static ServerConfig config = (ServerConfig) Config.getInstance().getJsonObjectConfig(CONFIG_NAME, ServerConfig.class);
    public static Map<String, Object> secret = DecryptUtil.decryptMap(Config.getInstance().getJsonMapConfig(CONFIG_SECRET));
    static SSLContext sslContext = createSSLContext();

    public  static final boolean enableHttp = server.getServerConfig().isEnableHttp();
    public static final boolean enableHttp2 = enableHttp? false : server.getServerConfig().isEnableHttp2();
    public static final int httpPort = server.getServerConfig().getHttpPort();
    public static final int httpsPort = server.getServerConfig().getHttpsPort();
    public static final String url = enableHttp ? "http://localhost:" + httpPort : "https://localhost:" + httpsPort;

    @BeforeClass
    public static void setUp() {
        if(server1 == null) {
            logger.info("starting server1");
            Undertow.Builder builder1 = Undertow.builder()
                    .addHttpsListener(18082, "localhost", sslContext)
                    .setHandler(path().addPrefixPath("/v2/address", new HttpHandler() {
                        @Override
                        public void handleRequest(HttpServerExchange exchange) throws Exception {
                            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
                            exchange.getResponseSender().send("Server1");
                        }
                    }));
            if(enableHttp2) {
                builder1.setServerOption(UndertowOptions.ENABLE_HTTP2, true);
            }
            server1 = builder1.build();
            server1.start();
        }
    }

    @AfterClass
    public static void tearDown() throws Exception {
        if(server1 != null) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {

            }
            server1.stop();
            logger.info("The router server1 is stopped.");
        }
    }

    protected static SSLContext createSSLContext() throws RuntimeException {
        try {
            KeyManager[] keyManagers = buildKeyManagers(loadKeyStore(), ((String)secret.get(SecretConstants.SERVER_KEY_PASS)).toCharArray());
            TrustManager[] trustManagers;
            if(config.isEnableTwoWayTls()) {
                trustManagers = buildTrustManagers(loadTrustStore());
            } else {
                trustManagers = buildTrustManagers(null);
            }

            SSLContext sslContext;
            sslContext = SSLContext.getInstance("TLSv1");
            sslContext.init(keyManagers, trustManagers, null);
            return sslContext;
        } catch (Exception e) {
            logger.error("Unable to create SSLContext", e);
            throw new RuntimeException("Unable to create SSLContext", e);
        }
    }

    private static KeyStore loadKeyStore() {
        String name = config.getKeystoreName();
        try (InputStream stream = Config.getInstance().getInputStreamFromFile(name)) {
            KeyStore loadedKeystore = KeyStore.getInstance("JKS");
            loadedKeystore.load(stream, ((String)secret.get(SecretConstants.SERVER_KEYSTORE_PASS)).toCharArray());
            return loadedKeystore;
        } catch (Exception e) {
            logger.error("Unable to load keystore " + name, e);
            throw new RuntimeException("Unable to load keystore " + name, e);
        }
    }

    protected static KeyStore loadTrustStore() {
        String name = config.getTruststoreName();
        try (InputStream stream = Config.getInstance().getInputStreamFromFile(name)) {
            KeyStore loadedKeystore = KeyStore.getInstance("JKS");
            loadedKeystore.load(stream, ((String)secret.get(SecretConstants.SERVER_TRUSTSTORE_PASS)).toCharArray());
            return loadedKeystore;
        } catch (Exception e) {
            logger.error("Unable to load truststore " + name, e);
            throw new RuntimeException("Unable to load truststore " + name, e);
        }
    }

    private static TrustManager[] buildTrustManagers(final KeyStore trustStore) {
        TrustManager[] trustManagers = null;
        if (trustStore == null) {
            try {
                TrustManagerFactory trustManagerFactory = TrustManagerFactory
                        .getInstance(KeyManagerFactory.getDefaultAlgorithm());
                trustManagerFactory.init(trustStore);
                trustManagers = trustManagerFactory.getTrustManagers();
            }
            catch (NoSuchAlgorithmException | KeyStoreException e) {
                logger.error("Unable to initialise TrustManager[]", e);
                throw new RuntimeException("Unable to initialise TrustManager[]", e);
            }
        }
        else {
            trustManagers = TRUST_ALL_CERTS;
        }
        return trustManagers;
    }

    private static KeyManager[] buildKeyManagers(final KeyStore keyStore, char[] keyPass) {
        KeyManager[] keyManagers;
        try {
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory
                    .getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, keyPass);
            keyManagers = keyManagerFactory.getKeyManagers();
        }
        catch (NoSuchAlgorithmException | UnrecoverableKeyException | KeyStoreException e) {
            logger.error("Unable to initialise KeyManager[]", e);
            throw new RuntimeException("Unable to initialise KeyManager[]", e);
        }
        return keyManagers;
    }

}
