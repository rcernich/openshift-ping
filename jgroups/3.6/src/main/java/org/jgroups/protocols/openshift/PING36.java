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

package org.jgroups.protocols.openshift;

import static org.openshift.ping.common.Utils.openStream;

import java.io.DataInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.jgroups.Address;
import org.jgroups.Event;
import org.jgroups.PhysicalAddress;
import org.jgroups.conf.ClassConfigurator;
import org.jgroups.protocols.Discovery;
import org.jgroups.protocols.PingData;
import org.jgroups.util.Responses;
import org.jgroups.util.UUID;
import org.openshift.ping.common.OpenshiftPing.InternalPingEvent;
import org.openshift.ping.common.OpenshiftPing.OpenShiftNode;
import org.openshift.ping.common.OpenshiftPing.OpenShiftPingConfig;
import org.openshift.ping.common.OpenshiftPing.OpenShiftPingDataFactory;
import org.openshift.ping.common.server.Server;
import org.openshift.ping.common.server.ServerFactory;
import org.openshift.ping.common.server.Servers;

public class PING36 extends Discovery implements OpenShiftPingDataFactory {

    static {
        ClassConfigurator.addProtocol((short) 2036, PING36.class);
    }

    private ServerFactory _serverFactory;
    private Server _server;
    private String _serverName;
    private int _connectTimeout;
    private int _readTimeout;
    private int _operationAttempts;
    private long _operationSleep;

    public PING36() {
        super();
    }

    public final void setServerFactory(ServerFactory serverFactory) {
        _serverFactory = serverFactory;
    }

    @Override
    public void start() throws Exception {
        OpenShiftPingConfig config = (OpenShiftPingConfig) down(new InternalPingEvent(InternalPingEvent.CONFIGURE, null));
        if (config == null) {
            log.warn("Could not resolve OpenShift clustering configuration.  Clustering will be disabled.");
            return;
        }
        if (config.isClusteringEnabled()) {
            _connectTimeout = config.getConnectTimeout();
            _readTimeout = config.getReadTimeout();
            _operationAttempts = config.getOperationAttempts();
            _operationSleep = config.getOperationSleep();

            int serverPort = config.getServerPort();
            if (_serverFactory != null) {
                _server = _serverFactory.getServer(serverPort);
            } else {
                _server = Servers.getServer(serverPort);
            }
            _serverName = _server.getClass().getSimpleName();
            if (log.isInfoEnabled()) {
                log.info(String.format("Starting %s on port %s for channel address: %s", _serverName, serverPort, stack
                        .getChannel().getAddress()));
            }
            boolean started = _server.start(stack.getChannel());
            if (log.isInfoEnabled()) {
                log.info(String.format("%s %s.", _serverName, started ? "started" : "reused (pre-existing)"));
            }
        }
    }

    @Override
    public void stop() {
        try {
            if (_server != null) {
                if (log.isInfoEnabled()) {
                    log.info(String.format("Stopping server: %s", _serverName));
                }
                boolean stopped = _server.stop(stack.getChannel());
                if (log.isInfoEnabled()) {
                    log.info(String.format("%s %s.", _serverName, stopped ? "stopped" : "not stopped (still in use)"));
                }
            }
        } finally {
            super.stop();
        }
    }

    @Override
    public boolean isDynamic() {
        return true;
    }

    /**
     * Mostly copied from FILE_PING
     */
    @Override
    public void findMembers(final List<Address> members, final boolean initial_discovery, Responses responses) {
        try {
            readAll(members, cluster_name, responses);

            PhysicalAddress phys_addr = (PhysicalAddress) down_prot.down(new Event(Event.GET_PHYSICAL_ADDRESS,
                    local_addr));
            PingData data = responses.findResponseFrom(local_addr);
            // the logical addr *and* IP address:port have to match
            if (data != null && data.getPhysicalAddr().equals(phys_addr)) {
                if (data.isCoord() && initial_discovery)
                    responses.clear();
                else
                    ; // use case #1 if we have predefined files: most members
                      // join but are not coord
            } else {
                sendDiscoveryResponse(local_addr, phys_addr, UUID.get(local_addr), null, false);
            }
        } finally {
            responses.done();
        }
    }

    protected void readAll(List<Address> members, String clusterName, Responses responses) {
        final List<PingData> pings;
        if (_server != null) {
            pings = doReadAll(clusterName);
        } else {
            PingData pingData = createPingData();
            pings = Collections.singletonList(pingData);
        }
        for (PingData data : pings) {
            if (members == null || members.contains(data.getAddress()))
                responses.addResponse(data, true);
            if (local_addr != null && !local_addr.equals(data.getAddress()))
                addDiscoveryResponseToCaches(data.getAddress(), data.getLogicalName(), data.getPhysicalAddr());
        }
    }

    protected List<PingData> doReadAll(String clusterName) {
        @SuppressWarnings("unchecked")
        List<OpenShiftNode> nodes = (List<OpenShiftNode>) down(new InternalPingEvent(InternalPingEvent.COLLECT_MEMBERS,
                clusterName));
        if (nodes == null) {
            return Collections.emptyList();
        }
        List<PingData> retval = new ArrayList<PingData>();
        boolean localAddrPresent = false;
        for (OpenShiftNode node : nodes) {
            try {
                PingData pingData = getPingData(node.getHost(), node.getPort(), clusterName);
                localAddrPresent = localAddrPresent || pingData.getAddress().equals(local_addr);
                retval.add(pingData);
            } catch (Exception e) {
                if (log.isInfoEnabled()) {
                    log.info(String.format(
                            "PingData not available for cluster [%s], host [%s], port [%s]; encountered [%s: %s]",
                            clusterName, node.getHost(), node.getPort(), e.getClass().getName(), e.getMessage()));
                }
            }
        }
        if (localAddrPresent) {
            if (log.isDebugEnabled()) {
                for (PingData pingData : retval) {
                    log.debug(String.format("Returning PingData [%s]", pingData));
                }
            }
            return retval;
        } else {
            if (log.isDebugEnabled()) {
                log.debug("Local address not discovered, returning empty list");
            }
            return Collections.emptyList();
        }
    }

    protected final PingData getPingData(String targetHost, int targetPort, String clusterName) throws Exception {
        String pingUrl = String.format("http://%s:%s", targetHost, targetPort);
        PingData pingData = new PingData();
        Map<String, String> pingHeaders = Collections.singletonMap(Server.CLUSTER_NAME, clusterName);
        try (InputStream pingStream = openStream(pingUrl, pingHeaders, _connectTimeout, _readTimeout,
                _operationAttempts, _operationSleep)) {
            pingData.readFrom(new DataInputStream(pingStream));
        }
        return pingData;
    }

    @Override
    public PingData createPingData() {
        PhysicalAddress paddr = (PhysicalAddress) down(new Event(Event.GET_PHYSICAL_ADDRESS, local_addr));
        return new PingData(local_addr, is_server, UUID.get(local_addr), paddr).coord(is_coord);
    }
}
