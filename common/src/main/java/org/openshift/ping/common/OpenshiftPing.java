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

package org.openshift.ping.common;

import static org.openshift.ping.common.Utils.getSystemEnvInt;
import static org.openshift.ping.common.Utils.trimToNull;

import java.util.List;

import org.jgroups.Event;
import org.jgroups.annotations.Property;
import org.jgroups.protocols.PingData;
import org.jgroups.stack.Protocol;

public abstract class OpenshiftPing extends Protocol {

    private final String _systemEnvPrefix;

    @Property
    private int connectTimeout = 5000;
    private int _connectTimeout;

    @Property
    private int readTimeout = 30000;
    private int _readTimeout;

    @Property
    private int operationAttempts = 3;
    private int _operationAttempts;

    @Property
    private long operationSleep = 1000;
    private long _operationSleep;

    public OpenshiftPing(String systemEnvPrefix) {
        super();
        _systemEnvPrefix = trimToNull(systemEnvPrefix);
    }

    protected final String getSystemEnvName(String systemEnvSuffix) {
        StringBuilder sb = new StringBuilder();
        String suffix = trimToNull(systemEnvSuffix);
        if (suffix != null) {
            if (_systemEnvPrefix != null) {
                sb.append(_systemEnvPrefix);
            }
            sb.append(suffix);
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    protected final int getConnectTimeout() {
        return _connectTimeout;
    }

    protected final int getReadTimeout() {
        return _readTimeout;
    }

    protected final int getOperationAttempts() {
        return _operationAttempts;
    }

    protected final long getOperationSleep() {
        return _operationSleep;
    }

    @Override
    public void init() throws Exception {
        super.init();
        _connectTimeout = getSystemEnvInt(getSystemEnvName("CONNECT_TIMEOUT"), connectTimeout);
        _readTimeout = getSystemEnvInt(getSystemEnvName("READ_TIMEOUT"), readTimeout);
        _operationAttempts = getSystemEnvInt(getSystemEnvName("OPERATION_ATTEMPTS"), operationAttempts);
        _operationSleep = (long) getSystemEnvInt(getSystemEnvName("OPERATION_SLEEP"), (int) operationSleep);
    }

    @Override
    public void destroy() {
        _connectTimeout = 0;
        _readTimeout = 0;
        _operationAttempts = 0;
        _operationSleep = 0l;
        super.destroy();
    }

    @Override
    public Object down(Event evt) {
        if (evt instanceof InternalPingEvent) {
            switch (evt.getType()) {
            case InternalPingEvent.CONFIGURE:
                return new OpenShiftPingConfig(isClusteringEnabled(), getServerPort(), getConnectTimeout(),
                        getReadTimeout(), getOperationAttempts(), getOperationSleep());
            case InternalPingEvent.COLLECT_MEMBERS:
                return doReadAll((String) evt.getArg());
            }
            return null;
        }
        return super.down(evt);
    }

    protected abstract boolean isClusteringEnabled();

    protected abstract int getServerPort();

    protected abstract List<OpenShiftNode> doReadAll(String clusterName);

    /**
     * Interface for pingers. This is intended to mixed in with a Discovery
     * protocol that requests PingData from a list of servers. Ping and
     * discovery are intended to be implemented by a separate protocol
     * facilitated by passing messages up and down the protocol stack. This is
     * to work around incompatibilities in the Discover class between JGroups
     * versions.
     */
    public interface OpenShiftPingDataFactory {

        public PingData createPingData();

    }

    public static final class InternalPingEvent extends Event {

        /*
         * return List<OpenShiftNode>; arg = String(clusterName)
         */
        public static final int COLLECT_MEMBERS = Event.USER_DEFINED + 1111;
        /*
         * return OpenShiftPingConfig; arg = null
         */
        public static final int CONFIGURE = COLLECT_MEMBERS + 1;

        /**
         * Create a new PingEvent.
         * 
         * @param type type of event
         * @param arg protocol should act upon
         */
        public InternalPingEvent(int type, Object arg) {
            super(type, arg);
        }
    }

    /**
     * Represents a node in cluster running on OpenShift.
     */
    public static final class OpenShiftNode {

        private final String host;
        private final int port;

        public OpenShiftNode(String host, int port) {
            this.host = host;
            this.port = port;
        }

        /**
         * Get the host.
         * 
         * @return the host.
         */
        public String getHost() {
            return host;
        }

        /**
         * Get the port.
         * 
         * @return the port.
         */
        public int getPort() {
            return port;
        }
    }

    public static final class OpenShiftPingConfig {
        private final boolean clusteringEnabled;
        private final int serverPort;
        private final int connectTimeout;
        private final int readTimeout;
        private final int operationAttempts;
        private final long operationSleep;

        /**
         * Create a new OpenShiftPingConfig.
         * 
         * @param clusteringEnabled
         * @param serverPort
         * @param connectTimeout
         * @param readTimeout
         * @param operationAttempts
         * @param operationSleep
         */
        public OpenShiftPingConfig(boolean clusteringEnabled, int serverPort, int connectTimeout, int readTimeout,
                int operationAttempts, long operationSleep) {
            super();
            this.clusteringEnabled = clusteringEnabled;
            this.serverPort = serverPort;
            this.connectTimeout = connectTimeout;
            this.readTimeout = readTimeout;
            this.operationAttempts = operationAttempts;
            this.operationSleep = operationSleep;
        }

        /**
         * Get the clusteringEnabled.
         * 
         * @return the clusteringEnabled.
         */
        public boolean isClusteringEnabled() {
            return clusteringEnabled;
        }

        /**
         * Get the serverPort.
         * 
         * @return the serverPort.
         */
        public int getServerPort() {
            return serverPort;
        }

        /**
         * Get the connectTimeout.
         * 
         * @return the connectTimeout.
         */
        public int getConnectTimeout() {
            return connectTimeout;
        }

        /**
         * Get the readTimeout.
         * 
         * @return the readTimeout.
         */
        public int getReadTimeout() {
            return readTimeout;
        }

        /**
         * Get the operationAttempts.
         * 
         * @return the operationAttempts.
         */
        public int getOperationAttempts() {
            return operationAttempts;
        }

        /**
         * Get the operationSleep.
         * 
         * @return the operationSleep.
         */
        public long getOperationSleep() {
            return operationSleep;
        }
    }
}
