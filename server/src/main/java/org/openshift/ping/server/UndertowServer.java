/**
 *  Copyright 2014 Red Hat, Inc.
 *
 *  Red Hat licenses this file to you under the Apache License, version
 *  2.0 (the "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *  implied.  See the License for the specific language governing
 *  permissions and limitations under the License.
 */

package org.openshift.ping.server;

import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

import java.io.DataOutputStream;

import org.jgroups.Channel;
import org.jgroups.protocols.PingData;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class UndertowServer extends AbstractServer {
    private Undertow server;

    public UndertowServer(int port) {
        super(port);
    }

    public synchronized void start(Channel channel) throws Exception {
        if (server == null) {
            try {
                Undertow.Builder builder = Undertow.builder();
                builder.addHttpListener(port, "0.0.0.0");
                builder.setHandler(new Handler(this));
                server = builder.build();
                server.start();
            } catch (Exception e) {
                server = null;
                throw e;
            }
        }
        addChannel(channel);
    }

    public synchronized void stop(Channel channel) {
        removeChannel(channel);
        if (server != null && !hasChannels()) {
            try {
                server.stop();
            } finally {
                server = null;
            }
        }
    }

    private class Handler implements HttpHandler {
        private final Server server;
        private Handler(Server server) {
            this.server = server;
        }
        public void handleRequest(HttpServerExchange exchange) throws Exception {
            exchange.startBlocking();
            String clusterName = exchange.getRequestHeaders().getFirst(CLUSTER_NAME);
            Channel channel = server.getChannel(clusterName);
            PingData data = Utils.createPingData(channel);
            data.writeTo(new DataOutputStream(exchange.getOutputStream()));
        }
    }
}
