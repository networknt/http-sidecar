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

public class SidecarServiceDictHandler extends ServiceDictHandler {

    public static final String SIDECAR_CONFIG_NAME = "http-sidecar";
    public static HttpSidecarConfig sidecarConfig = (HttpSidecarConfig) Config.getInstance().getJsonObjectConfig(SIDECAR_CONFIG_NAME, HttpSidecarConfig.class);

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {

        if (Constants.HEADER.equalsIgnoreCase(sidecarConfig.getEgressIngressIndicator())) {
            serviceDict(exchange);
        } else if (Constants.PROTOCOL.equalsIgnoreCase(sidecarConfig.getEgressIngressIndicator()) && HttpURL.PROTOCOL_HTTP.equalsIgnoreCase(exchange.getRequestScheme())){
            serviceDict(exchange);
        } else {
            Handler.next(exchange, next);
        }
    }

    protected void serviceDict(HttpServerExchange exchange) throws Exception {
        HeaderValues serviceIdHeader = exchange.getRequestHeaders().get(HttpStringConstants.SERVICE_ID);
        String serviceId = serviceIdHeader != null ? serviceIdHeader.peekFirst() : null;
        if (serviceId == null) {
            String requestPath = exchange.getRequestURI();
            String httpMethod = exchange.getRequestMethod().toString().toLowerCase();
            serviceId = HandlerUtils.findServiceId(toInternalKey(httpMethod, requestPath), mappings);
            if (serviceId != null) {
                exchange.getRequestHeaders().put(HttpStringConstants.SERVICE_ID, serviceId);
            }
        }

        Handler.next(exchange, this.next);
    }


}
