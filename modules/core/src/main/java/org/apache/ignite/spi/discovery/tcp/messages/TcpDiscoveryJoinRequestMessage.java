/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.spi.discovery.tcp.messages;

import org.apache.ignite.internal.util.typedef.internal.S;
import org.apache.ignite.spi.discovery.tcp.internal.DiscoveryDataPacket;
import org.apache.ignite.spi.discovery.tcp.internal.TcpDiscoveryNode;

import static org.apache.ignite.internal.util.lang.ClusterNodeFunc.eqNodes;

/**
 * Initial message sent by a node that wants to enter topology.
 * Sent to random node during SPI start. Then forwarded directly to coordinator.
 */
public class TcpDiscoveryJoinRequestMessage extends TcpDiscoveryAbstractTraceableMessage {
    /** */
    private static final long serialVersionUID = 0L;

    /** New node that wants to join the topology. */
    private final TcpDiscoveryNode node;

    /** Discovery data container. */
    private final DiscoveryDataPacket dataPacket;

    /**
     * Constructor.
     *
     * @param node New node that wants to join.
     * @param dataPacket Discovery data.
     */
    public TcpDiscoveryJoinRequestMessage(TcpDiscoveryNode node, DiscoveryDataPacket dataPacket) {
        super(node.id());

        this.node = node;
        this.dataPacket = dataPacket;
    }

    /**
     * Gets new node that wants to join the topology.
     *
     * @return Node that wants to join the topology.
     */
    public TcpDiscoveryNode node() {
        return node;
    }

    /** @return Discovery data container that collects data from all cluster nodes. */
    public DiscoveryDataPacket gridDiscoveryData() {
        return dataPacket;
    }

    /**
     * @return {@code true} flag.
     */
    public boolean responded() {
        return getFlag(RESPONDED_FLAG_POS);
    }

    /**
     * @param responded Responded flag.
     */
    public void responded(boolean responded) {
        setFlag(RESPONDED_FLAG_POS, responded);
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object obj) {
        // NOTE!
        // Do not call super. As IDs will differ, but we can ignore this.

        if (!(obj instanceof TcpDiscoveryJoinRequestMessage))
            return false;

        TcpDiscoveryJoinRequestMessage other = (TcpDiscoveryJoinRequestMessage)obj;

        return eqNodes(other.node, node);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(TcpDiscoveryJoinRequestMessage.class, this, "super", super.toString());
    }
}
