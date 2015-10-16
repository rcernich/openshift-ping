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

package org.openshift.ping.test;

import java.io.DataInputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

import org.jgroups.conf.ClassConfigurator;
import org.jgroups.protocols.PingData;
import org.jgroups.protocols.openshift.PING34;
import org.jgroups.stack.Protocol;
import org.junit.Assert;
import org.junit.Test;
import org.openshift.ping.common.OpenshiftPing;
import org.openshift.ping.common.server.Server;
import org.openshift.ping.kube.Client;
import org.openshift.ping.kube.KubePing;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public abstract class ServerTestBase extends TestBase {
    
    private PING34 pinger;

    @Override
    protected int getNum() {
        return 1;
    }

    protected List<Protocol> createPing() {
        final KubePing ping = new TestKubePing();
        ping.setMasterProtocol("http");
        ping.setMasterHost("localhost");
        ping.setMasterPort(8080);
        ping.setNamespace("default");
        pinger = new PING34();
        applyConfig(pinger);
        List<Protocol> prots = new ArrayList<Protocol>() {{
           add(ping);
           add(pinger);
        }};
        return prots;
    }

    protected abstract void applyConfig(PING34 ping);

    @Test
    public void testResponse() throws Exception {
        URL url = new URL("http://localhost:8888");
        URLConnection conn = url.openConnection();
        conn.addRequestProperty(Server.CLUSTER_NAME, TestBase.CLUSTER_NAME);
        InputStream stream = conn.getInputStream();
        PingData data = new PingData();
        data.readFrom(new DataInputStream(stream));
        Assert.assertEquals(pinger.createPingData(), data);
    }

    private static final class TestKubePing extends KubePing {
        static {
            ClassConfigurator.addProtocol(JGROUPS_KUBE_PING_ID, TestKubePing.class);
        }

        @Override
        protected Client getClient() {
            return new TestClient();
        }
    }
}
