package com.networknt.router.middleware;

import com.networknt.handler.config.HandlerUtils;
import com.networknt.header.HeaderHandler;
import com.networknt.httpstring.HttpStringConstants;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.RoutingHandler;
import io.undertow.util.Methods;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;

public class SidecarServiceDictHandlerTest extends BaseServiceHandlerTest {
    static final Logger logger = LoggerFactory.getLogger(SidecarServiceDictHandlerTest.class);

    static Undertow server = null;

    @BeforeAll
    public static void setUp() {
        if(server == null) {
            logger.info("starting server");
            HttpHandler handler = getTestHandler();
            SidecarServiceDictHandler serviceDictHandler = new SidecarServiceDictHandler();
            serviceDictHandler.setNext(handler);
            handler = serviceDictHandler;
            HeaderHandler headerHandler = new HeaderHandler();
            headerHandler.setNext(handler);
            handler = headerHandler;
            server = Undertow.builder()
                    .addHttpListener(8080, "localhost")
                    .setHandler(handler)
                    .build();
            server.start();
        }
    }

    @AfterAll
    public static void tearDown() throws Exception {
        if(server != null) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {

            }
            server.stop();
            logger.info("The server is stopped.");
        }
    }

    static RoutingHandler getTestHandler() {
        return Handlers.routing()
                .add(Methods.GET, "/v1/address/{id}", exchange -> {
                    exchange.getResponseHeaders().put(HttpStringConstants.SERVICE_ID, exchange.getRequestHeaders().getFirst(HttpStringConstants.SERVICE_ID));
                    exchange.getResponseHeaders().put(HttpStringConstants.ENV_TAG, exchange.getRequestHeaders().getFirst(HttpStringConstants.ENV_TAG));
                    exchange.getResponseSender().send("OK");
                })
                .add(Methods.GET, "/v2/address", exchange -> {
                    exchange.getResponseHeaders().put(HttpStringConstants.SERVICE_ID, exchange.getRequestHeaders().getFirst(HttpStringConstants.SERVICE_ID));
                    exchange.getResponseHeaders().put(HttpStringConstants.ENV_TAG, exchange.getRequestHeaders().getFirst(HttpStringConstants.ENV_TAG));
                    exchange.getResponseSender().send("OK");
                })
                .add(Methods.POST, "/v1/contact", exchange -> {
                    exchange.getResponseHeaders().put(HttpStringConstants.SERVICE_ID, exchange.getRequestHeaders().getFirst(HttpStringConstants.SERVICE_ID));
                    exchange.getResponseHeaders().put(HttpStringConstants.ENV_TAG, exchange.getRequestHeaders().getFirst(HttpStringConstants.ENV_TAG));
                    exchange.getResponseSender().send("OK");
                });
    }

    @Test
    public void testFindServiceId() throws Exception {
        // Make test parametric when porting to junit5?
        Map<AbstractMap.SimpleEntry<String, String>, String> expected = new HashMap<>();

        // Simple calls
        expected.put(createPair("/v1/address/111", "get"), "party.address-1.0.0");
        expected.put(createPair("/v1/address/whatever", "get"), "party.address-1.0.0");
        expected.put(createPair("/v2/address", "get"), "party.address-2.0.0");
        expected.put(createPair("/v1/contact", "post"), "party.contact-1.0.0");

        // Missing leading slash
        expected.put(createPair("v2/address/irrelevant", "get"), "party.address-2.0.0");
        expected.put(createPair("v1/contact/johnathan.strange", "post"), "party.contact-1.0.0");

        // Unmatched paths
        expected.put(createPair("/v1/very/bad/path", "get"), null);
        expected.put(createPair("/v1/contact-not-really/reject.me", "post"), null);

        Map<AbstractMap.SimpleEntry<String, String>, String> result = new HashMap<>();
        for (AbstractMap.SimpleEntry<String, String> pair : expected.keySet()) {
            String[] serviceEntry = HandlerUtils.findServiceEntry(toKey(pair), ServiceDictConfig.load().getMapping());
            if(serviceEntry != null) {
                result.put(pair, serviceEntry[1]);
            } else {
                result.put(pair, null);
            }
        }

        Assertions.assertEquals(expected, result);
    }

    private AbstractMap.SimpleEntry<String, String> createPair(String path, String method){
    	return new AbstractMap.SimpleEntry<>(path, method);
    }

    private String toKey(AbstractMap.SimpleEntry<String, String> pair) {
    	return HandlerUtils.toInternalKey(pair.getValue(), pair.getKey());
    }
}
