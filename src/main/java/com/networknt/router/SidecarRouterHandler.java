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

import com.networknt.config.Config;
import com.networknt.handler.Handler;
import com.networknt.handler.MiddlewareHandler;
import com.networknt.httpstring.HttpStringConstants;
import com.networknt.url.HttpURL;
import com.networknt.utility.ModuleRegistry;
import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderValues;

import java.util.Map;

/**
 * In k8s Sidecar container, the main service container to sidecar container communication will use http protocol.
 * If it is http request, sidecar container will treat it as egress traffic and will work as router to delegate client feature and forward to request to server API
 *
 * @author Gavin Chen
 */
public class SidecarRouterHandler extends RouterHandler implements MiddlewareHandler{


    private volatile HttpHandler next;
    public static final String ROUTER_CONFIG_NAME = "router";
    public static final String SIDECAR_CONFIG_NAME = "http-sidecar";

    public static Map<String, Object> config = Config.getInstance().getJsonMapConfigNoCache(ROUTER_CONFIG_NAME);
    public static HttpSidecarConfig sidecarConfig = (HttpSidecarConfig)Config.getInstance().getJsonObjectConfig(SIDECAR_CONFIG_NAME, HttpSidecarConfig.class);

    @Override
    public void handleRequest(HttpServerExchange httpServerExchange) throws Exception {
        if (sidecarConfig.isRouteByServiceId()) {
            HeaderValues serviceIdHeader = httpServerExchange.getRequestHeaders().get(HttpStringConstants.SERVICE_ID);
            String serviceId = serviceIdHeader != null ? serviceIdHeader.peekFirst() : null;
            String serviceUrl = httpServerExchange.getRequestHeaders().getFirst(HttpStringConstants.SERVICE_URL);
            if (serviceId != null || serviceUrl!=null) {
                proxyHandler.handleRequest(httpServerExchange);
            } else {
                Handler.next(httpServerExchange, next);
            }
        } else if (HttpURL.PROTOCOL_HTTP.equalsIgnoreCase(httpServerExchange.getRequestScheme())){
            proxyHandler.handleRequest(httpServerExchange);
        } else {
            Handler.next(httpServerExchange, next);
        }
    }

    @Override
    public HttpHandler getNext() {
        return next;
    }

    @Override
    public MiddlewareHandler setNext(final HttpHandler next) {
        Handlers.handlerNotNull(next);
        this.next = next;
        return this;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void register() {
        ModuleRegistry.registerModule(SidecarRouterHandler.class.getName(), config, null);
    }

}
