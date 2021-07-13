package com.networknt.router.middleware;

import com.networknt.handler.Handler;
import com.networknt.url.HttpURL;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

public class SidecarServiceDictHandler extends ServiceDictHandler {

    private volatile HttpHandler next;

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        if (HttpURL.PROTOCOL_HTTP.equalsIgnoreCase(exchange.getRequestScheme())) {
            super.handleRequest(exchange);
        } else {
            Handler.next(exchange, next);
        }
    }

}
