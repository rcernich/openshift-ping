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

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.ReceiverAdapter;
import org.jgroups.protocols.TCP;
import org.jgroups.protocols.UNICAST2;
import org.jgroups.protocols.pbcast.GMS;
import org.jgroups.protocols.pbcast.NAKACK2;
import org.jgroups.protocols.pbcast.STABLE;
import org.jgroups.stack.Protocol;
import org.jgroups.util.Util;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public abstract class TestBase {
    private static final int NUM = 2;
    protected static final String CLUSTER_NAME = "test";

    protected JChannel[] channels;
    protected MyReceiver[] receivers;

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    protected int getNum() {
        return NUM;
    }

    @Before
    public void setUp() throws Exception {
        channels = new JChannel[getNum()];
        receivers = new MyReceiver[getNum()];

        for (int i = 0; i < getNum(); i++) {
            final List<Protocol> ping = createPing();

            List<Protocol> stack = new ArrayList<Protocol>() {{
                add(new TCP().setValue("bind_addr", InetAddress.getLoopbackAddress()));
                addAll(ping);
                add(new NAKACK2());
                add(new UNICAST2());
                add(new STABLE());
                add(new GMS());
            }};
            channels[i] = new JChannel(stack);
            channels[i].setName(Character.toString((char) ('A' + i)));
            channels[i].connect(CLUSTER_NAME);
            channels[i].setReceiver(receivers[i] = new MyReceiver());
        }
    }

    @After
    public void tearDown() throws Exception {
        for (JChannel channel: channels) {
            Util.shutdown(channel);
        }
        Util.close(channels);
    }

    protected abstract List<Protocol> createPing();

    protected void clearReceivers() {
        for (MyReceiver r : receivers) r.getList().clear();
    }

    protected static class MyReceiver extends ReceiverAdapter {
        protected final List<Integer> list = new ArrayList<>();

        public List<Integer> getList() {
            return list;
        }

        public void receive(Message msg) {
            synchronized (list) {
                list.add((Integer) msg.getObject());
            }
        }
    }
}
