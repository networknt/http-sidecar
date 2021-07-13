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

import com.networknt.client.Http2Client;
import com.networknt.exception.ClientException;
import com.networknt.httpstring.HttpStringConstants;
import io.undertow.UndertowOptions;
import io.undertow.client.ClientConnection;
import io.undertow.client.ClientRequest;
import io.undertow.client.ClientResponse;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import org.junit.*;
import org.xnio.IoUtils;
import org.xnio.OptionMap;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class RouterHttpTest extends BaseRouterTest{

    /**
     * Calling server1 directly to ensure that the endpoint is working.
     * @throws Exception
     */
    @Test
    public void testServer1() throws Exception {
        final Http2Client client = Http2Client.getInstance();
        final CountDownLatch latch = new CountDownLatch(1);
        final ClientConnection connection;
        try {
            connection = client.connect(new URI("https://localhost:8082"), Http2Client.WORKER, Http2Client.SSL, Http2Client.BUFFER_POOL, OptionMap.create(UndertowOptions.ENABLE_HTTP2, true)).get();
        } catch (Exception e) {
            throw new ClientException(e);
        }
        final AtomicReference<ClientResponse> reference = new AtomicReference<>();
        try {
            ClientRequest request = new ClientRequest().setPath("/v2/address").setMethod(Methods.GET);
            request.getRequestHeaders().put(Headers.HOST, "localhost");
            connection.sendRequest(request, client.createClientCallback(reference, latch));
            latch.await();
        } catch (Exception e) {
            logger.error("Exception: ", e);
            throw new ClientException(e);
        } finally {
            IoUtils.safeClose(connection);
        }
        int statusCode = reference.get().getResponseCode();
        String body = reference.get().getAttachment(Http2Client.RESPONSE_BODY);
        Assert.assertEquals(200, statusCode);
        if (statusCode == 200) {
            Assert.assertEquals("Server1", body);
        }
    }


    @Test
    public void testGet() throws Exception {
        final Http2Client client = Http2Client.getInstance();
        final CountDownLatch latch = new CountDownLatch(10);
        final ClientConnection connection;
        logger.debug("url = " + url);
        try {
            connection = client.connect(new URI(url), Http2Client.WORKER, Http2Client.SSL, Http2Client.BUFFER_POOL, enableHttp2 ? OptionMap.create(UndertowOptions.ENABLE_HTTP2, true): OptionMap.EMPTY).get();
        } catch (Exception e) {
            throw new ClientException(e);
        }
        final List<AtomicReference<ClientResponse>> references = new CopyOnWriteArrayList<>();
        try {
            connection.getIoThread().execute(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < 10; i++) {
                        AtomicReference<ClientResponse> reference = new AtomicReference<>();
                        references.add(i, reference);
                        final ClientRequest request = new ClientRequest().setMethod(Methods.GET).setPath("/v2/address");
                        request.getRequestHeaders().put(HttpStringConstants.SERVICE_ID, "com.networknt.test-1.0.0");
                        request.getRequestHeaders().put(Headers.HOST, "localhost");
                        connection.sendRequest(request, client.createClientCallback(reference, latch));
                    }
                }

            });

            latch.await();
        } catch (Exception e) {
            logger.error("Exception: ", e);
            throw new ClientException(e);
        } finally {
            IoUtils.safeClose(connection);
        }
        for (final AtomicReference<ClientResponse> reference : references) {
            String body = reference.get().getAttachment(Http2Client.RESPONSE_BODY);
            if(logger.isDebugEnabled()) logger.debug("body = " + body);
            Assert.assertTrue(body.contains("Server"));
        }
    }



}
