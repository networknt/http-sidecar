package com.networknt.router.middleware;

import com.networknt.config.Config;
import com.networknt.handler.Handler;
import com.networknt.httpstring.HttpStringConstants;
import com.networknt.router.HttpSidecarConfig;
import com.networknt.url.HttpURL;
import com.networknt.utility.Constants;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderValues;

public class SidecarTokenHandler extends TokenHandler{

    public static final String SIDECAR_CONFIG_NAME = "http-sidecar";
    public static HttpSidecarConfig sidecarConfig = (HttpSidecarConfig) Config.getInstance().getJsonObjectConfig(SIDECAR_CONFIG_NAME, HttpSidecarConfig.class);

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        if (Constants.HEADER.equalsIgnoreCase(sidecarConfig.getEgressIngressIndicator())) {
            HeaderValues serviceIdHeader = exchange.getRequestHeaders().get(HttpStringConstants.SERVICE_ID);
            String serviceId = serviceIdHeader != null ? serviceIdHeader.peekFirst() : null;
            String serviceUrl = exchange.getRequestHeaders().getFirst(HttpStringConstants.SERVICE_URL);
            if (serviceId != null || serviceUrl!=null) {
                super.handleRequest(exchange);
            } else {
                Handler.next(exchange, next);
            }
        } else if (Constants.PROTOCOL.equalsIgnoreCase(sidecarConfig.getEgressIngressIndicator()) && HttpURL.PROTOCOL_HTTP.equalsIgnoreCase(exchange.getRequestScheme())){
            super.handleRequest(exchange);
        } else {
            Handler.next(exchange, next);
        }
    }

}
