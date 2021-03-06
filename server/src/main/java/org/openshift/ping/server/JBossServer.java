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

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import org.jboss.com.sun.net.httpserver.HttpExchange;
import org.jboss.com.sun.net.httpserver.HttpHandler;
import org.jboss.com.sun.net.httpserver.HttpServer;
import org.jgroups.Channel;
import org.jgroups.protocols.PingData;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class JBossServer extends AbstractServer {
    private HttpServer server;

    public JBossServer(int port) {
        super(port);
    }

    public synchronized void start(Channel channel) throws Exception {
        if (server == null) {
            try {
                InetSocketAddress address = new InetSocketAddress("0.0.0.0", port);
                server = HttpServer.create(address, 0);
                server.setExecutor(Executors.newCachedThreadPool());
                server.createContext("/", new Handler(this));
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
                server.stop(0);
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
        public void handle(HttpExchange exchange) throws IOException {
            exchange.sendResponseHeaders(200, 0);
            try {
                String clusterName = exchange.getRequestHeaders().getFirst(CLUSTER_NAME);
                Channel channel = server.getChannel(clusterName);
                PingData data = Utils.createPingData(channel);
                try (OutputStream outputStream = exchange.getResponseBody()) {
                    data.writeTo(new DataOutputStream(outputStream));
                }
            } catch (Exception e) {
                throw new IOException(e);
            }
        }
    }
}
