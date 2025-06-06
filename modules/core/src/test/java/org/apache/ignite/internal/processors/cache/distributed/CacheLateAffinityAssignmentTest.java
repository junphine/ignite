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

package org.apache.ignite.internal.processors.cache.distributed;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.cache.processor.EntryProcessor;
import javax.cache.processor.MutableEntry;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.IgniteDataStreamer;
import org.apache.ignite.IgniteException;
import org.apache.ignite.IgniteServices;
import org.apache.ignite.IgniteTransactions;
import org.apache.ignite.cache.CacheAtomicityMode;
import org.apache.ignite.cache.affinity.Affinity;
import org.apache.ignite.cache.affinity.AffinityFunction;
import org.apache.ignite.cache.affinity.AffinityFunctionContext;
import org.apache.ignite.cache.affinity.rendezvous.RendezvousAffinityFunction;
import org.apache.ignite.cluster.ClusterNode;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.DataRegionConfiguration;
import org.apache.ignite.configuration.DataStorageConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.events.DiscoveryEvent;
import org.apache.ignite.internal.DiscoverySpiTestListener;
import org.apache.ignite.internal.GridKernalContext;
import org.apache.ignite.internal.IgniteEx;
import org.apache.ignite.internal.IgniteInternalFuture;
import org.apache.ignite.internal.IgniteKernal;
import org.apache.ignite.internal.IgniteNodeAttributes;
import org.apache.ignite.internal.TestRecordingCommunicationSpi;
import org.apache.ignite.internal.cluster.ClusterTopologyServerNotFoundException;
import org.apache.ignite.internal.cluster.NodeOrderComparator;
import org.apache.ignite.internal.managers.discovery.IgniteDiscoverySpi;
import org.apache.ignite.internal.processors.affinity.AffinityTopologyVersion;
import org.apache.ignite.internal.processors.affinity.GridAffinityFunctionContextImpl;
import org.apache.ignite.internal.processors.cache.CacheAffinityChangeMessage;
import org.apache.ignite.internal.processors.cache.DynamicCacheDescriptor;
import org.apache.ignite.internal.processors.cache.GridCacheContext;
import org.apache.ignite.internal.processors.cache.GridCacheSharedContext;
import org.apache.ignite.internal.processors.cache.distributed.dht.preloader.GridDhtForceKeysRequest;
import org.apache.ignite.internal.processors.cache.distributed.dht.preloader.GridDhtForceKeysResponse;
import org.apache.ignite.internal.processors.cache.distributed.dht.preloader.GridDhtPartitionSupplyMessage;
import org.apache.ignite.internal.processors.cache.distributed.dht.preloader.GridDhtPartitionsAbstractMessage;
import org.apache.ignite.internal.processors.cache.distributed.dht.preloader.GridDhtPartitionsExchangeFuture;
import org.apache.ignite.internal.processors.cache.distributed.dht.preloader.GridDhtPartitionsFullMessage;
import org.apache.ignite.internal.processors.cache.distributed.dht.preloader.GridDhtPartitionsSingleMessage;
import org.apache.ignite.internal.util.future.GridFinishedFuture;
import org.apache.ignite.internal.util.lang.GridAbsPredicate;
import org.apache.ignite.internal.util.typedef.F;
import org.apache.ignite.internal.util.typedef.G;
import org.apache.ignite.internal.util.typedef.PA;
import org.apache.ignite.internal.util.typedef.X;
import org.apache.ignite.internal.util.typedef.internal.CU;
import org.apache.ignite.internal.util.typedef.internal.U;
import org.apache.ignite.lang.IgniteBiPredicate;
import org.apache.ignite.lang.IgniteClosure;
import org.apache.ignite.lang.IgnitePredicate;
import org.apache.ignite.plugin.extensions.communication.Message;
import org.apache.ignite.resources.IgniteInstanceResource;
import org.apache.ignite.services.Service;
import org.apache.ignite.services.ServiceContext;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.testframework.GridTestUtils;
import org.apache.ignite.testframework.junits.common.GridCommonAbstractTest;
import org.apache.ignite.transactions.Transaction;
import org.apache.ignite.transactions.TransactionConcurrency;
import org.apache.ignite.transactions.TransactionIsolation;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

import static org.apache.ignite.cache.CacheAtomicityMode.ATOMIC;
import static org.apache.ignite.cache.CacheAtomicityMode.TRANSACTIONAL;
import static org.apache.ignite.cache.CacheRebalanceMode.ASYNC;
import static org.apache.ignite.cache.CacheRebalanceMode.SYNC;
import static org.apache.ignite.cache.CacheWriteSynchronizationMode.FULL_SYNC;
import static org.apache.ignite.internal.TestRecordingCommunicationSpi.blockSingleExhangeMessage;
import static org.apache.ignite.internal.util.lang.ClusterNodeFunc.nodeIds;
import static org.apache.ignite.testframework.GridTestUtils.runAsync;
import static org.apache.ignite.testframework.GridTestUtils.waitForCondition;

/**
 *
 */
public class CacheLateAffinityAssignmentTest extends GridCommonAbstractTest {
    /** */
    private static final String CACHE_NAME1 = "testCache1";

    /** */
    private static final String CACHE_NAME2 = "testCache2";

    /** */
    private IgniteClosure<String, CacheConfiguration[]> cacheC;

    /** */
    private IgnitePredicate<ClusterNode> cacheNodeFilter;

    /** */
    private IgniteClosure<String, TestRecordingCommunicationSpi> spiC;

    /** */
    private IgniteClosure<String, Boolean> clientC;

    /** Expected ideal affinity assignments. */
    private Map<Long, Map<Integer, List<List<ClusterNode>>>> idealAff = new HashMap<>();

    /** */
    private boolean skipCheckOrder;

    /** {@inheritDoc} */
    @Override protected IgniteConfiguration getConfiguration(String igniteInstanceName) throws Exception {
        IgniteConfiguration cfg = super.getConfiguration(igniteInstanceName);

        TestRecordingCommunicationSpi commSpi;

        if (spiC != null)
            commSpi = spiC.apply(igniteInstanceName);
        else
            commSpi = new TestRecordingCommunicationSpi();

        cfg.setCommunicationSpi(commSpi);

        TcpDiscoverySpi discoSpi = (TcpDiscoverySpi)cfg.getDiscoverySpi();

        discoSpi.setNetworkTimeout(60_000);

        cfg.setClientFailureDetectionTimeout(100000);

        CacheConfiguration[] ccfg;

        if (cacheC != null)
            ccfg = cacheC.apply(igniteInstanceName);
        else
            ccfg = new CacheConfiguration[]{cacheConfiguration()};

        if (ccfg != null)
            cfg.setCacheConfiguration(ccfg);

        if (clientC != null) {
            cfg.setClientMode(clientC.apply(igniteInstanceName));

            discoSpi.setJoinTimeout(30_000);
        }

        DataStorageConfiguration cfg1 = new DataStorageConfiguration();

        cfg1.setDefaultDataRegionConfiguration(new DataRegionConfiguration().setMaxSize(512L * 1024 * 1024));

        cfg.setDataStorageConfiguration(cfg1);

        return cfg;
    }

    /**
     * @return Cache configuration.
     */
    private CacheConfiguration cacheConfiguration() {
        CacheConfiguration ccfg = new CacheConfiguration();

        ccfg.setName(CACHE_NAME1);
        ccfg.setNodeFilter(cacheNodeFilter);
        ccfg.setAffinity(affinityFunction(null));
        ccfg.setWriteSynchronizationMode(FULL_SYNC);
        ccfg.setBackups(0);

        return ccfg;
    }

    /** {@inheritDoc} */
    @Override protected void beforeTest() throws Exception {
        super.beforeTest();

        cleanPersistenceDir();
    }

    /**
     * @param parts Number of partitions.
     * @return Affinity function.
     */
    protected AffinityFunction affinityFunction(@Nullable Integer parts) {
        return new RendezvousAffinityFunction(false,
            parts == null ? RendezvousAffinityFunction.DFLT_PARTITION_COUNT : parts);
    }

    /** {@inheritDoc} */
    @Override protected void afterTest() throws Exception {
        try {
            checkCaches();
        }
        finally {
            stopAllGrids();
        }
    }

    /**
     * Checks that new joined primary is not assigned immediately.
     *
     * @throws Exception If failed.
     */
    @Test
    public void testDelayedAffinityCalculation() throws Exception {
        Ignite ignite0 = startServer(0, 1);

        checkAffinity(1, topVer(1, 0), true);

        GridCacheContext cctx = ((IgniteKernal)ignite0).context().cache().internalCache(CACHE_NAME1).context();

        AffinityFunction func = cctx.config().getAffinity();

        AffinityFunctionContext ctx = new GridAffinityFunctionContextImpl(
            new ArrayList<>(ignite0.cluster().nodes()),
            null,
            null,
            topVer(1, 0),
            cctx.config().getBackups());

        List<List<ClusterNode>> calcAff1_0 = func.assignPartitions(ctx);

        startServer(1, 2);

        ctx = new GridAffinityFunctionContextImpl(
            new ArrayList<>(ignite0.cluster().nodes()),
            calcAff1_0,
            null,
            topVer(1, 0),
            cctx.config().getBackups());

        List<List<ClusterNode>> calcAff2_0 = func.assignPartitions(ctx);

        checkAffinity(2, topVer(2, 0), false);

        List<List<ClusterNode>> aff2_0 = affinity(ignite0, topVer(2, 0), CACHE_NAME1);

        for (int p = 0; p < calcAff1_0.size(); p++) {
            List<ClusterNode> a1 = calcAff1_0.get(p);
            List<ClusterNode> a2 = calcAff2_0.get(p);

            List<ClusterNode> a = aff2_0.get(p);

            // Primary did not change.
            assertEquals(a1.get(0), a.get(0));

            // New primary is backup.
            if (!a1.get(0).equals(a2.get(0)))
                assertTrue(a.contains(a2.get(0)));
        }

        checkAffinity(2, topVer(2, 1), true);

        List<List<ClusterNode>> aff2_1 = affinity(ignite0, topVer(2, 1), CACHE_NAME1);

        assertEquals(calcAff2_0, aff2_1);
    }

    /**
     * Simple test, node join.
     *
     * @throws Exception If failed.
     */
    @Test
    public void testAffinitySimpleSequentialStart() throws Exception {
        startServer(0, 1);

        startServer(1, 2);

        checkAffinity(2, topVer(2, 0), false);

        checkAffinity(2, topVer(2, 1), true);

        startServer(2, 3);

        checkAffinity(3, topVer(3, 0), false);

        checkAffinity(3, topVer(3, 1), true);

        awaitPartitionMapExchange();
    }

    /**
     * @throws Exception If failed.
     */
    @Test
    public void testAffinitySimpleSequentialStartNoCacheOnCoordinator() throws Exception {
        cacheC = new IgniteClosure<String, CacheConfiguration[]>() {
            @Override public CacheConfiguration[] apply(String igniteInstanceName) {
                if (igniteInstanceName.equals(getTestIgniteInstanceName(0)))
                    return null;

                return new CacheConfiguration[]{cacheConfiguration()};
            }
        };

        cacheNodeFilter = new TestCacheNodeExcludingFilter(F.asList(getTestIgniteInstanceName(0)));

        testAffinitySimpleSequentialStart();

        assertNull(((IgniteKernal)ignite(0)).context().cache().internalCache(CACHE_NAME1));
    }

    /**
     * @throws Exception If failed.
     */
    @Test
    public void testAffinitySimpleNoCacheOnCoordinator1() throws Exception {
        cacheC = new IgniteClosure<String, CacheConfiguration[]>() {
            @Override public CacheConfiguration[] apply(String igniteInstanceName) {
                if (igniteInstanceName.equals(getTestIgniteInstanceName(1)))
                    return null;

                return new CacheConfiguration[]{cacheConfiguration()};
            }
        };

        cacheNodeFilter = new TestCacheNodeExcludingFilter(F.asList(getTestIgniteInstanceName(1)));

        startServer(0, 1);

        startServer(1, 2);

        checkAffinity(2, topVer(2, 1), true);

        startServer(2, 3);

        startServer(3, 4);

        Map<String, List<List<ClusterNode>>> aff = checkAffinity(4, topVer(4, 1), true);

        stopGrid(0); // Kill coordinator, now coordinator node1 without cache.

        boolean primaryChanged = calculateAffinity(5, false, aff);

        checkAffinity(3, topVer(5, 0), !primaryChanged);

        if (primaryChanged)
            checkAffinity(3, topVer(5, 1), true);

        assertNull(((IgniteKernal)ignite(1)).context().cache().internalCache(CACHE_NAME1));
    }

    /**
     * @throws Exception If failed.
     */
    @Test
    public void testCreateCloseClientCacheOnCoordinator1() throws Exception {
        cacheC = new IgniteClosure<String, CacheConfiguration[]>() {
            @Override public CacheConfiguration[] apply(String igniteInstanceName) {
                return null;
            }
        };

        cacheNodeFilter = new TestCacheNodeExcludingFilter(F.asList(getTestIgniteInstanceName(0)));

        Ignite ignite0 = startServer(0, 1);

        ignite0.createCache(cacheConfiguration());

        ignite0.cache(CACHE_NAME1);

        ignite0.cache(CACHE_NAME1).close();

        startServer(1, 2);

        startServer(2, 3);
    }

    /**
     * @throws Exception If failed.
     */
    @Test
    public void testCreateCloseClientCacheOnCoordinator2() throws Exception {
        cacheC = new IgniteClosure<String, CacheConfiguration[]>() {
            @Override public CacheConfiguration[] apply(String igniteInstanceName) {
                if (igniteInstanceName.equals(getTestIgniteInstanceName(0)))
                    return null;

                return new CacheConfiguration[]{cacheConfiguration()};
            }
        };

        cacheNodeFilter = new TestCacheNodeExcludingFilter(F.asList(getTestIgniteInstanceName(0)));

        Ignite ignite0 = startServer(0, 1);

        int topVer = 1;

        int nodes = 1;

        for (int i = 0; i < 3; i++) {
            log.info("Iteration [iter=" + i + ", topVer=" + topVer + ']');

            topVer++;

            startServer(nodes++, topVer);

            checkAffinity(nodes, topVer(topVer, 1), true);

            ignite0.cache(CACHE_NAME1);

            checkAffinity(nodes, topVer(topVer, 1), true);

            topVer++;

            startServer(nodes++, topVer);

            checkAffinity(nodes, topVer(topVer, 1), true);

            ignite0.cache(CACHE_NAME1).close();

            checkAffinity(nodes, topVer(topVer, 1), true);
        }
    }

    /**
     * @throws Exception If failed.
     */
    @Test
    public void testCacheDestroyAndCreate1() throws Exception {
        cacheDestroyAndCreate(true);
    }

    /**
     * @throws Exception If failed.
     */
    @Test
    public void testCacheDestroyAndCreate2() throws Exception {
        cacheDestroyAndCreate(false);
    }

    /**
     * @param cacheOnCrd If {@code false} does not create cache on coordinator.
     * @throws Exception If failed.
     */
    private void cacheDestroyAndCreate(boolean cacheOnCrd) throws Exception {
        if (!cacheOnCrd)
            cacheNodeFilter = new TestCacheNodeExcludingFilter(Collections.singletonList(getTestIgniteInstanceName(0)));

        startServer(0, 1);

        startServer(1, 2);

        startServer(2, 3);

        checkAffinity(3, topVer(3, 1), true);

        startClient(3, 4);

        checkAffinity(4, topVer(4, 0), true);

        CacheConfiguration ccfg = cacheConfiguration();
        ccfg.setName(CACHE_NAME2);

        ignite(1).createCache(ccfg);

        calculateAffinity(4);

        checkAffinity(4, topVer(4, 1), true);

        ignite(1).destroyCache(CACHE_NAME2);

        idealAff.get(4L).remove(CU.cacheId(CACHE_NAME2));

        ccfg = cacheConfiguration();
        ccfg.setName(CACHE_NAME2);
        ccfg.setAffinity(affinityFunction(10));

        ignite(1).createCache(ccfg);

        calculateAffinity(4);

        checkAffinity(4, topVer(4, 3), true);

        checkCaches();

        ignite(1).destroyCache(CACHE_NAME2);

        idealAff.get(4L).remove(CU.cacheId(CACHE_NAME2));

        ccfg = cacheConfiguration();
        ccfg.setName(CACHE_NAME2);
        ccfg.setAffinity(affinityFunction(20));

        ignite(1).createCache(ccfg);

        calculateAffinity(4);

        checkAffinity(4, topVer(4, 5), true);
    }

    /**
     * Simple test, node leaves.
     *
     * @throws Exception If failed.
     */
    @Test
    public void testAffinitySimpleNodeLeave1() throws Exception {
        affinitySimpleNodeLeave(2);
    }

    /**
     * Simple test, node leaves.
     *
     * @throws Exception If failed.
     */
    @Test
    public void testAffinitySimpleNodeLeave2() throws Exception {
        affinitySimpleNodeLeave(4);
    }

    /**
     * @param cnt Count of server nodes.
     * @throws Exception If failed.
     */
    private void affinitySimpleNodeLeave(int cnt) throws Exception {
        int topVer = 1;

        startServer(topVer - 1, topVer++);

        for (int i = 0; i < cnt - 1; i++, topVer++) {
            startServer(topVer - 1, topVer);

            checkAffinity(topVer, topVer(topVer, 0), false);

            checkAffinity(topVer, topVer(topVer, 1), true);
        }

        stopNode(1, topVer);

        checkAffinity(cnt - 1, topVer(topVer, 0), true);

        checkNoExchange(cnt - 1, topVer(topVer, 1));

        awaitPartitionMapExchange();
    }

    /**
     * Simple test, node leaves.
     *
     * @throws Exception If failed.
     */
    @Test
    public void testAffinitySimpleNodeLeaveClientAffinity() throws Exception {
        startServer(0, 1);

        startServer(1, 2);

        checkAffinity(2, topVer(2, 1), true);

        startClient(2, 3);

        checkAffinity(3, topVer(3, 0), true);

        stopNode(1, 4);

        checkAffinity(2, topVer(4, 0), true);

        awaitPartitionMapExchange();
    }

    /**
     * Simple test, client node joins/leaves.
     *
     * @throws Exception If failed.
     */
    @Test
    public void testAffinitySimpleClientNodeEvents1() throws Exception {
        affinitySimpleClientNodeEvents(1);
    }

    /**
     * Simple test, client node joins/leaves.
     *
     * @throws Exception If failed.
     */
    @Test
    public void testAffinitySimpleClientNodeEvents2() throws Exception {
        affinitySimpleClientNodeEvents(3);
    }

    /**
     * Simple test, client node joins/leaves.
     *
     * @param srvs Number of server nodes.
     * @throws Exception If failed.
     */
    private void affinitySimpleClientNodeEvents(int srvs) throws Exception {
        long topVer = 0;

        for (int i = 0; i < srvs; i++)
            startServer(i, ++topVer);

        if (srvs == 1)
            checkAffinity(srvs, topVer(srvs, 0), true);
        else
            checkAffinity(srvs, topVer(srvs, 1), true);

        startClient(srvs, ++topVer);

        checkAffinity(srvs + 1, topVer(srvs + 1, 0), true);

        stopNode(srvs, ++topVer);

        checkAffinity(srvs, topVer(srvs + 2, 0), true);
    }

    /**
     * Wait for rebalance, 2 nodes join.
     *
     * @throws Exception If failed.
     */
    @Test
    public void testDelayAssignmentMultipleJoin1() throws Exception {
        delayAssignmentMultipleJoin(2);
    }

    /**
     * Wait for rebalance, 4 nodes join.
     *
     * @throws Exception If failed.
     */
    @Test
    public void testDelayAssignmentMultipleJoin2() throws Exception {
        delayAssignmentMultipleJoin(4);
    }

    /**
     * @param joinCnt Number of joining nodes.
     * @throws Exception If failed.
     */
    private void delayAssignmentMultipleJoin(int joinCnt) throws Exception {
        Ignite ignite0 = startServer(0, 1);

        TestRecordingCommunicationSpi spi =
            (TestRecordingCommunicationSpi)ignite0.configuration().getCommunicationSpi();

        blockSupplySend(spi, CACHE_NAME1);

        int majorVer = 1;

        for (int i = 0; i < joinCnt; i++) {
            majorVer++;

            startServer(i + 1, majorVer);

            checkAffinity(majorVer, topVer(majorVer, 0), false);
        }

        List<IgniteInternalFuture<?>> futs = affFutures(majorVer, topVer(majorVer, 1));

        U.sleep(1000);

        for (IgniteInternalFuture<?> fut : futs)
            assertFalse(fut.isDone());

        spi.stopBlock();

        checkAffinity(majorVer, topVer(majorVer, 1), true);

        for (IgniteInternalFuture<?> fut : futs)
            assertTrue(fut.isDone());

        awaitPartitionMapExchange();
    }

    /**
     * Wait for rebalance, client node joins.
     *
     * @throws Exception If failed.
     */
    @Test
    public void testDelayAssignmentClientJoin() throws Exception {
        Ignite ignite0 = startServer(0, 1);

        TestRecordingCommunicationSpi spi =
            (TestRecordingCommunicationSpi)ignite0.configuration().getCommunicationSpi();

        blockSupplySend(spi, CACHE_NAME1);

        startServer(1, 2);

        startClient(2, 3);

        checkAffinity(3, topVer(3, 0), false);

        spi.stopBlock();

        checkAffinity(3, topVer(3, 1), true);
    }

    /**
     * Wait for rebalance, client node leaves.
     *
     * @throws Exception If failed.
     */
    @Test
    public void testDelayAssignmentClientLeave() throws Exception {
        Ignite ignite0 = startServer(0, 1);

        startClient(1, 2);

        checkAffinity(2, topVer(2, 0), true);

        TestRecordingCommunicationSpi spi =
            (TestRecordingCommunicationSpi)ignite0.configuration().getCommunicationSpi();

        blockSupplySend(spi, CACHE_NAME1);

        startServer(2, 3);

        checkAffinity(3, topVer(3, 0), false);

        stopNode(1, 4);

        checkAffinity(2, topVer(4, 0), false);

        spi.stopBlock();

        checkAffinity(2, topVer(4, 1), true);
    }

    /**
     * Wait for rebalance, client cache is started.
     *
     * @throws Exception If failed.
     */
    @Test
    public void testDelayAssignmentClientCacheStart() throws Exception {
        Ignite ignite0 = startServer(0, 1);

        TestRecordingCommunicationSpi spi =
                (TestRecordingCommunicationSpi)ignite0.configuration().getCommunicationSpi();

        blockSupplySend(spi, CACHE_NAME1);

        startServer(1, 2);

        startServer(2, 3);

        cacheC = new IgniteClosure<String, CacheConfiguration[]>() {
            @Override public CacheConfiguration[] apply(String nodeName) {
                return null;
            }
        };

        Ignite client = startClient(3, 4);

        checkAffinity(4, topVer(4, 0), false);

        assertNotNull(client.cache(CACHE_NAME1));

        checkAffinity(4, topVer(4, 0), false);

        spi.stopBlock();

        checkAffinity(4, topVer(4, 1), true);
    }

    /**
     * Wait for rebalance, cache is started.
     *
     * @throws Exception If failed.
     */
    @Test
    public void testDelayAssignmentCacheStart() throws Exception {
        Ignite ignite0 = startServer(0, 1);

        TestRecordingCommunicationSpi spi =
            (TestRecordingCommunicationSpi)ignite0.configuration().getCommunicationSpi();

        blockSupplySend(spi, CACHE_NAME1);

        startServer(1, 2);

        startServer(2, 3);

        checkAffinity(3, topVer(3, 0), false);

        CacheConfiguration ccfg = cacheConfiguration();

        ccfg.setName(CACHE_NAME2);

        ignite0.createCache(ccfg);

        calculateAffinity(3);

        checkAffinity(3, topVer(3, 1), false);

        spi.stopBlock();

        checkAffinity(3, topVer(3, 2), true);
    }

    /**
     * Wait for rebalance, cache is destroyed.
     *
     * @throws Exception If failed.
     */
    @Test
    public void testDelayAssignmentCacheDestroy() throws Exception {
        Ignite ignite0 = startServer(0, 1);

        CacheConfiguration ccfg = cacheConfiguration();

        ccfg.setName(CACHE_NAME2);

        ignite0.createCache(ccfg);

        TestRecordingCommunicationSpi spi =
            (TestRecordingCommunicationSpi)ignite0.configuration().getCommunicationSpi();

        blockSupplySend(spi, CACHE_NAME2);

        startServer(1, 2);

        startServer(2, 3);

        checkAffinity(3, topVer(3, 0), false);

        ignite0.destroyCache(CACHE_NAME2);

        checkAffinity(3, topVer(3, 1), false);

        checkAffinity(3, topVer(3, 2), true);

        spi.stopBlock();
    }

    /**
     * Simple test, stop random node.
     *
     * @throws Exception If failed.
     */
    @Test
    public void testAffinitySimpleStopRandomNode() throws Exception {
        //fail("IGNITE-GG-12292");

        final int ITERATIONS = 3;

        for (int iter = 0; iter < 3; iter++) {
            log.info("Iteration: " + iter);

            final int NODES = 5;

            for (int i = 0; i < NODES; i++)
                startServer(i, i + 1);

            int majorVer = NODES;

            checkAffinity(majorVer, topVer(majorVer, 1), true);

            Set<Integer> stopOrder = new HashSet<>();

            while (stopOrder.size() != NODES - 1)
                stopOrder.add(ThreadLocalRandom.current().nextInt(NODES));

            int nodes = NODES;

            for (Integer idx : stopOrder) {
                log.info("Stop node: " + idx);

                majorVer++;

                stopNode(idx, majorVer);

                checkAffinity(--nodes, topVer(majorVer, 0), false);

                awaitPartitionMapExchange();
            }

            if (iter < ITERATIONS - 1) {
                stopAllGrids();

                idealAff.clear();
            }
        }
    }

    /**
     * Wait for rebalance, coordinator leaves, 2 nodes.
     *
     * @throws Exception If failed.
     */
    @Test
    public void testDelayAssignmentCoordinatorLeave1() throws Exception {
        Ignite ignite0 = startServer(0, 1);

        TestRecordingCommunicationSpi spi =
            (TestRecordingCommunicationSpi)ignite0.configuration().getCommunicationSpi();

        blockSupplySend(spi, CACHE_NAME1);

        startServer(1, 2);

        stopNode(0, 3);

        checkAffinity(1, topVer(3, 0), true);

        checkNoExchange(1, topVer(3, 1));

        awaitPartitionMapExchange();
    }

    /**
     * Wait for rebalance, coordinator leaves, 3 nodes.
     *
     * @throws Exception If failed.
     */
    @Test
    public void testDelayAssignmentCoordinatorLeave2() throws Exception {
        Ignite ignite0 = startServer(0, 1);

        Ignite ignite1 = startServer(1, 2);

        checkAffinity(2, topVer(2, 1), true);

        TestRecordingCommunicationSpi spi0 =
            (TestRecordingCommunicationSpi)ignite0.configuration().getCommunicationSpi();
        TestRecordingCommunicationSpi spi1 =
            (TestRecordingCommunicationSpi)ignite1.configuration().getCommunicationSpi();

        blockSupplySend(spi0, CACHE_NAME1);
        blockSupplySend(spi1, CACHE_NAME1);

        startServer(2, 3);

        stopNode(0, 4);

        checkAffinity(2, topVer(4, 0), false);

        spi1.stopBlock();

        checkAffinity(2, topVer(4, 1), true);
    }

    /**
     * Checks LAA absent on owner left.
     */
    @Test
    public void testSinglePartitionCacheOwnerLeft() throws Exception {
        testSinglePartitionCacheNodeLeft(true);
    }

    /**
     * Checks LAA absent on non owner left.
     */
    @Test
    public void testSinglePartitionCacheNonOwnerLeft() throws Exception {
        testSinglePartitionCacheNodeLeft(false);
    }

    /**
     * Since we have only 1 partition, at each node left it will be lost (no rebalance needed) or it will be still
     * located at second node (thanks to special affinity) (no rebalance neeeded). So, LAA should never happen.
     *
     * @param ownerLeft Kill owner flag.
     */
    private void testSinglePartitionCacheNodeLeft(boolean ownerLeft) throws Exception {
        String cacheName = "single-partitioned";

        cacheC = new IgniteClosure<String, CacheConfiguration[]>() {
            @Override public CacheConfiguration[] apply(String igniteInstanceName) {
                CacheConfiguration ccfg = new CacheConfiguration();

                AffinityFunction aff;

                ccfg.setName(cacheName);
                ccfg.setWriteSynchronizationMode(FULL_SYNC);
                ccfg.setBackups(0);

                aff = ownerLeft ? affinityFunction(1) : new MapSinglePartitionToSecondNodeAffinityFunction();

                ccfg.setAffinity(aff);

                return new CacheConfiguration[] {ccfg};
            }
        };

        int top = 0;
        int nodes = 0;

        startServer(nodes++, ++top);

        checkAffinity(nodes, topVer(top, 0), true);

        checkNoExchange(nodes, topVer(top, 1)); // Checks LAA is absent on initial topology.

        for (int i = 0; i < 10; i++)
            startServer(nodes++, ++top);

        awaitPartitionMapExchange();

        Ignite primary = primaryNode(0, cacheName);

        boolean laaOnJoin = primary.cluster().localNode().order() != 1;

        boolean leftHappen = false;

        while (nodes > 1) {
            Map<String, List<List<ClusterNode>>> aff =
                checkAffinity(nodes, topVer(top, leftHappen ? 0 : (laaOnJoin ? 1 : 0)), true);

            ClusterNode owner = aff.get(cacheName).get(/*part*/0).get(/*primary*/0);

            Ignite actualOwner = primaryNode(0, cacheName);

            assertEquals(actualOwner.cluster().localNode().order(), owner.order());

            for (Ignite node : G.allGrids()) {
                ClusterNode locNode = node.cluster().localNode();

                boolean equals = locNode.order() == owner.order();

                if (equals == ownerLeft) {
                    if (!ownerLeft)
                        assertNotSame(locNode.order(), 2);

                    grid(locNode).close();

                    calculateAffinity(++top);

                    leftHappen = true;

                    break;
                }
            }

            checkAffinity(--nodes, topVer(top, 0), true);

            checkNoExchange(nodes, topVer(top, 1));
        }
    }

    /**
     *
     */
    private static class MapSinglePartitionToSecondNodeAffinityFunction extends RendezvousAffinityFunction {
        /**
         * Default constructor.
         */
        public MapSinglePartitionToSecondNodeAffinityFunction() {
            super(false, 1);
        }

        /** {@inheritDoc} */
        @Override public List<List<ClusterNode>> assignPartitions(AffinityFunctionContext affCtx) {
            for (ClusterNode node : affCtx.currentTopologySnapshot() ) {
                // Always aims to map to second started node to avoid rebalance.
                if (node.order() == 2 || affCtx.currentTopologySnapshot().size() == 1)
                    return Collections.singletonList(Collections.singletonList(node));
            }

            fail("Should not happen.");

            return null;
        }
    }

    /**
     * @throws Exception If failed.
     */
    @Test
    public void testBlockedFinishMsg1() throws Exception {
        doTestCoordLeaveBlockedFinishExchangeMessage(4, 3, false, 2);
    }

    /**
     *
     * @throws Exception If failed.
     */
    @Test
    public void testBlockedFinishMsg2() throws Exception {
        doTestCoordLeaveBlockedFinishExchangeMessage(4, 3, false);
    }

    /**
     *
     * @throws Exception If failed.
     */
    @Test
    public void testBlockedFinishMsg3() throws Exception {
        doTestCoordLeaveBlockedFinishExchangeMessage(4, 3, false, 1);
    }

    /**
     *
     * @throws Exception If failed.
     */
    @Test
    public void testBlockedFinishMsg4() throws Exception {
        doTestCoordLeaveBlockedFinishExchangeMessage(5, 3, false);
    }

    /**
     *
     * @throws Exception If failed.
     */
    @Test
    public void testBlockedFinishMsg5() throws Exception {
        doTestCoordLeaveBlockedFinishExchangeMessage(5, 3, false, 1);
    }

    /**
     *
     * @throws Exception If failed.
     */
    @Test
    public void testBlockedFinishMsg6() throws Exception {
        doTestCoordLeaveBlockedFinishExchangeMessage(5, 3, false, 2);
    }

    /**
     *
     * @throws Exception If failed.
     */
    @Test
    public void testBlockedFinishMsg7() throws Exception {
        doTestCoordLeaveBlockedFinishExchangeMessage(5, 3, false, 2, 4);
    }

    /**
     *
     * @throws Exception If failed.
     */
    @Test
    public void testBlockedFinishMsg8() throws Exception {
        doTestCoordLeaveBlockedFinishExchangeMessage(6, 3, false, 2, 4);
    }

    /**
     *
     * @throws Exception If failed.
     */
    @Test
    public void testBlockedFinishMsg9() throws Exception {
        doTestCoordLeaveBlockedFinishExchangeMessage(5, 1, false, 4);
    }

    /**
     *
     * @throws Exception If failed.
     */
    @Test
    public void testBlockedFinishMsgForClient() throws Exception {
        doTestCoordLeaveBlockedFinishExchangeMessage(5, 1, true, 4);
    }

    /**
     * Coordinator leaves without sending all {@link GridDhtPartitionsFullMessage} messages,
     * exchange must be completed.
     *
     * @param cnt Number of nodes.
     * @param stopId Node to stop.
     * @param lastClient {@code True} if last started node is client.
     * @param blockedIds Nodes not receiving exchange finish message.
     * @throws Exception If failed.
     */
    private void doTestCoordLeaveBlockedFinishExchangeMessage(int cnt,
        int stopId,
        boolean lastClient,
        int... blockedIds
    ) throws Exception {
        int ord = 1;

        for (int i = 0; i < cnt; i++) {
            if (i == cnt - 1 && lastClient)
                startClient(ord - 1, ord++);
            else
                startServer(ord - 1, ord++);
        }

        awaitPartitionMapExchange();

        TestRecordingCommunicationSpi spi0 = TestRecordingCommunicationSpi.spi(grid(0));

        final Set<String> blocked = new HashSet<>();

        for (int id : blockedIds) {
            String name = grid(id).name();

            blocked.add(name);
        }

        spi0.blockMessages(new IgniteBiPredicate<ClusterNode, Message>() {
            @Override public boolean apply(ClusterNode node, Message msg) {
                return blocked.contains(node.attribute(IgniteNodeAttributes.ATTR_IGNITE_INSTANCE_NAME))
                    && (msg instanceof GridDhtPartitionsFullMessage)
                    && (((GridDhtPartitionsFullMessage)msg).exchangeId() != null);
            }
        });

        AffinityTopologyVersion curTop = ignite(0).context().cache().context().exchange().readyAffinityVersion();

        checkAffinity(cnt, curTop, true);

        stopNode(stopId, ord);

        AffinityTopologyVersion topVer = topVer(ord, 0);

        List<IgniteInternalFuture<?>> futs = new ArrayList<>(cnt);

        List<Ignite> grids = G.allGrids();

        for (Ignite ignite : grids)
            futs.add(affinityReadyFuture(topVer, ignite));

        assertEquals(futs.size(), grids.size());

        for (int i = 0; i < futs.size(); i++) {
            final IgniteInternalFuture<?> fut = futs.get(i);

            Ignite ignite = grids.get(i);

            if (!blocked.contains(ignite.name())) {
                waitForCondition(new GridAbsPredicate() {
                    @Override public boolean apply() {
                        return fut.isDone();
                    }
                }, 5000);

                assertTrue(ignite.name(), fut.isDone());
            }
            else
                assertFalse(ignite.name(), fut.isDone());
        }

        ord++;

        stopNode(0, ord); // Triggers exchange completion from new coordinator.

        checkAffinity(cnt - 2, topVer(ord - 1, 0), true, false);

        checkAffinity(cnt - 2, topVer(ord, 0), true);

        awaitPartitionMapExchange();
    }

    /**
     * Assignment is delayed, coordinator leaves, nodes must complete exchange with same assignments.
     *
     * @throws Exception If failed.
     */
    @Test
    public void testCoordinatorLeaveAfterNodeLeavesDelayAssignment() throws Exception {
        Ignite ignite0 = startServer(0, 1);

        startServer(1, 2);

        Ignite ignite2 = startServer(2, 3);

        Ignite ignite3 = startServer(3, 4);

        // Wait for topVer=(4,1)
        awaitPartitionMapExchange();

        TestRecordingCommunicationSpi spi0 =
            (TestRecordingCommunicationSpi)ignite0.configuration().getCommunicationSpi(), spi2, spi3;

        // Prevent exchange completion.
        spi0.blockMessages(GridDhtPartitionsFullMessage.class, ignite2.name());

        // Block rebalance.
        blockSupplySend(spi0, CACHE_NAME1);
        blockSupplySend((spi2 = TestRecordingCommunicationSpi.spi(ignite2)), CACHE_NAME1);
        blockSupplySend((spi3 = TestRecordingCommunicationSpi.spi(ignite3)), CACHE_NAME1);

        stopNode(1, 5);

        AffinityTopologyVersion topVer = topVer(5, 0);

        IgniteInternalFuture<?> fut0 = affinityReadyFuture(topVer, ignite0);
        IgniteInternalFuture<?> fut2 = affinityReadyFuture(topVer, ignite2);
        IgniteInternalFuture<?> fut3 = affinityReadyFuture(topVer, ignite3);

        U.sleep(1_000);

        assertTrue(fut0.isDone());
        assertFalse(fut2.isDone());
        assertTrue(fut3.isDone());

        // Finish rebalance on ignite3.
        spi2.stopBlock(true);

        stopNode(0, 6);

        spi3.stopBlock(true);

        checkAffinity(2, topVer, false);
    }

    /**
     * @throws Exception If failed.
     */
    @Test
    public void testJoinExchangeBecomeCoordinator() throws Exception {
        long topVer = 0;

        final int NODES = 3;

        for (int i = 0; i < NODES; i++)
            startServer(i, ++topVer);

        checkAffinity(NODES, topVer(topVer, 1), true);

        AtomicBoolean joined = new AtomicBoolean();

        for (int i = 0; i < NODES; i++) {
            TestRecordingCommunicationSpi spi =
                (TestRecordingCommunicationSpi)ignite(i).configuration().getCommunicationSpi();

            spi.blockMessages(new IgniteBiPredicate<ClusterNode, Message>() {
                @Override public boolean apply(ClusterNode node, Message msg) {
                    if (msg.getClass().equals(GridDhtPartitionsSingleMessage.class) &&
                        ((GridDhtPartitionsAbstractMessage)msg).exchangeId() != null)
                        joined.set(true); // Join exchange started.

                    return msg.getClass().equals(GridDhtPartitionsSingleMessage.class) ||
                        msg.getClass().equals(GridDhtPartitionsFullMessage.class);
                }
            });
        }

        IgniteInternalFuture<?> stopFut = runAsync(new Callable<Void>() {
            @Override public Void call() throws Exception {
                for (int j = 1; j < NODES; j++) {
                    TestRecordingCommunicationSpi spi =
                        (TestRecordingCommunicationSpi)ignite(j).configuration().getCommunicationSpi();

                    spi.waitForBlocked();
                }

                for (int i = 0; i < NODES; i++)
                    stopGrid(getTestIgniteInstanceName(i), false, false);

                return null;
            }
        }, "stop-thread");

        Ignite node = startGrid(NODES);

        assertEquals(NODES + 1, node.cluster().localNode().order());

        stopFut.get();

        for (int i = 0; i < NODES + 1; i++)
            calculateAffinity(++topVer);

        checkAffinity(1, topVer(topVer, 0), true);

        for (int i = 0; i < NODES; i++)
            startServer(i, ++topVer);

        checkAffinity(NODES + 1, topVer(topVer, 1), true);
    }

    /**
     * Wait for rebalance, send affinity change message, but affinity already changed
     * (new nodes joined: server + client). Checks that tere is no race that could lead to
     * unexpected partition map exchange on the client node.
     *
     * @throws Exception If failed.
     */
    @Test
    public void testDelayAssignmentAffinityChangedUnexpectedPME() throws Exception {
        Ignite ignite0 = startServer(0, 1);

        for (int i = 0; i < 1024; i++)
            ignite0.cache(CACHE_NAME1).put(i, i);

        DiscoverySpiTestListener lsnr = new DiscoverySpiTestListener();

        ((IgniteDiscoverySpi)ignite0.configuration().getDiscoverySpi()).setInternalListener(lsnr);

        TestRecordingCommunicationSpi commSpi0 =
            (TestRecordingCommunicationSpi)ignite0.configuration().getCommunicationSpi();

        // Starting a new client node should not lead to a rebalance obviously.
        // So, it is expected that data distribution is ideal (ideal assignment).
        startClient(1, 2);

        checkAffinity(2, topVer(2, 0), true);

        // Block late affinity assignment. (*)
        lsnr.blockCustomEvent(CacheAffinityChangeMessage.class);

        // Starting a new server node triggers data rebalancing.
        // [3, 0] - is not ideal (expected)
        startServer(2, 3);

        checkAffinity(3, topVer(3, 0), false);

        // Wait for sending late affinity assignment message (1) from the coordinator node.
        // This message will be blocked (*)
        lsnr.waitCustomEvent();

        // Block rebalance messages.
        blockSupplySend(commSpi0, CACHE_NAME1);

        // Starting a new server node means that the late affinity assignment message (1) should be skipped.
        startServer(3, 4);

        TestRecordingCommunicationSpi clientSpi = new TestRecordingCommunicationSpi();
        clientSpi.blockMessages(blockSingleExhangeMessage());
        spiC = igniteInstanceName -> clientSpi;

        IgniteInternalFuture<?> startClientFut = runAsync(() -> {
            startClient(4, 5);
        });

        clientSpi.waitForBlocked();

        // Unblock the late affinity assignment message (1).
        lsnr.stopBlockCustomEvents();

        clientSpi.stopBlock();

        startClientFut.get(15_000);

        // [5, 0] - is not ideal (expected)
        checkAffinity(5, topVer(5, 0), false);

        // Rebalance is blocked at this moment, so [5, 1] is not ready.
        checkNoExchange(5, topVer(5, 1));

        // Unblock rebalancing.
        // The late affinity assignments message (2) should be fired after all.
        commSpi0.stopBlock();

        // [5, 1] should be ideal
        checkAffinity(5, topVer(5, 1), true);

        // The following output demonstrates the issue.
        // The coordinator node and client initiate PME on the same toplogy version,
        // but it relies to different custom messages.
        // client:
        //      Started exchange init [
        //          topVer=AffinityTopologyVersion [topVer=5, minorTopVer=1],
        //          crd=false,
        //          evt=DISCOVERY_CUSTOM_EVT, evtNode=00ac9434-fd34-4aae-95d3-ceb477700000,
        //          customEvt=CacheAffinityChangeMessage [
        //              id=3ccc8984181-ea41279c-71cb-4b8c-8b48-1dee1baa6fe0,                       <<< (1)
        //              topVer=AffinityTopologyVersion [topVer=3, minorTopVer=0], ...]             <<< !!!
        // coordinator:
        //      Started exchange init
        //          [topVer=AffinityTopologyVersion [topVer=5, minorTopVer=1],
        //          crd=true,
        //          evt=DISCOVERY_CUSTOM_EVT, evtNode=00ac9434-fd34-4aae-95d3-ceb477700000,
        //          customEvt=CacheAffinityChangeMessage [
        //              id=d2ec8984181-ea41279c-71cb-4b8c-8b48-1dee1baa6fe0,                        <<< (2)
        //              topVer=AffinityTopologyVersion [topVer=4, minorTopVer=0], ...]              <<< !!!
        awaitPartitionMapExchange(true, true, null, false);

        assertPartitionsSame(idleVerify(grid(0), CACHE_NAME1));
    }

    /**
     * Wait for rebalance, send affinity change message, but affinity already changed (new node joined).
     *
     * @throws Exception If failed.
     */
    @Test
    public void testDelayAssignmentAffinityChanged() throws Exception {
        Ignite ignite0 = startServer(0, 1);

        for (int i = 0; i < 1024; i++)
            ignite0.cache(CACHE_NAME1).put(i, i);

        DiscoverySpiTestListener lsnr = new DiscoverySpiTestListener();

        ((IgniteDiscoverySpi)ignite0.configuration().getDiscoverySpi()).setInternalListener(lsnr);

        TestRecordingCommunicationSpi commSpi0 =
            (TestRecordingCommunicationSpi)ignite0.configuration().getCommunicationSpi();

        startClient(1, 2);

        checkAffinity(2, topVer(2, 0), true);

        lsnr.blockCustomEvent(CacheAffinityChangeMessage.class);

        startServer(2, 3);

        checkAffinity(3, topVer(3, 0), false);

        lsnr.waitCustomEvent();

        blockSupplySend(commSpi0, CACHE_NAME1);

        startServer(3, 4);

        lsnr.stopBlockCustomEvents();

        checkAffinity(4, topVer(4, 0), false);

        checkNoExchange(4, topVer(4, 1));

        commSpi0.stopBlock();

        checkAffinity(4, topVer(4, 1), true);

        awaitPartitionMapExchange(true, true, null, false);

        assertPartitionsSame(idleVerify(grid(0), CACHE_NAME1));
    }

    /**
     * Wait for rebalance, cache is destroyed and created again.
     *
     * @throws Exception If failed.
     */
    @Test
    public void testDelayAssignmentCacheDestroyCreate() throws Exception {
        Ignite ignite0 = startServer(0, 1);

        CacheConfiguration ccfg = cacheConfiguration();

        ccfg.setName(CACHE_NAME2);

        ignite0.createCache(ccfg);

        DiscoverySpiTestListener lsnr = new DiscoverySpiTestListener();

        ((IgniteDiscoverySpi)ignite0.configuration().getDiscoverySpi()).setInternalListener(lsnr);

        TestRecordingCommunicationSpi spi =
            (TestRecordingCommunicationSpi)ignite0.configuration().getCommunicationSpi();

        blockSupplySend(spi, CACHE_NAME2);

        lsnr.blockCustomEvent(CacheAffinityChangeMessage.class);

        startServer(1, 2);

        startGrid(3);

        checkAffinity(3, topVer(3, 0), false);

        spi.stopBlock();

        lsnr.waitCustomEvent();

        ignite0.destroyCache(CACHE_NAME2);

        ccfg = cacheConfiguration();
        ccfg.setName(CACHE_NAME2);
        ccfg.setAffinity(affinityFunction(10));

        ignite0.createCache(ccfg);

        lsnr.stopBlockCustomEvents();

        checkAffinity(3, topVer(3, 1), false);
        checkAffinity(3, topVer(3, 2), false);

        idealAff.get(2L).remove(CU.cacheId(CACHE_NAME2));

        calculateAffinity(3);

        checkAffinity(3, topVer(3, 3), true);
    }

    /**
     * @throws Exception If failed.
     */
    @Test
    public void testClientCacheStartClose() throws Exception {
        cacheC = new IgniteClosure<String, CacheConfiguration[]>() {
            @Override public CacheConfiguration[] apply(String igniteInstanceName) {
                if (igniteInstanceName.equals(getTestIgniteInstanceName(1)))
                    return null;

                return new CacheConfiguration[]{cacheConfiguration()};
            }
        };

        startServer(0, 1);

        Ignite client = startClient(1, 2);

        checkAffinity(2, topVer(2, 0), true);

        IgniteCache cache = client.cache(CACHE_NAME1);

        checkAffinity(2, topVer(2, 0), true);

        cache.close();

        checkAffinity(2, topVer(2, 0), true);
    }

    /**
     * @throws Exception If failed.
     */
    @Test
    public void testCacheStartDestroy() throws Exception {
        startGridsMultiThreaded(3, false);

        for (int i = 0; i < 3; i++)
            calculateAffinity(i + 1);

        checkAffinity(3, topVer(3, 1), true);

        Ignite client = startClient(3, 4);

        checkAffinity(4, topVer(4, 0), true);

        CacheConfiguration ccfg = cacheConfiguration();

        ccfg.setName(CACHE_NAME2);

        ignite(0).createCache(ccfg);

        calculateAffinity(4);

        checkAffinity(4, topVer(4, 1), true);

        client.cache(CACHE_NAME2);

        checkAffinity(4, topVer(4, 1), true);

        client.destroyCache(CACHE_NAME2);

        checkAffinity(4, topVer(4, 2), true);
    }

    /**
     * @throws Exception If failed.
     */
    @Test
    public void testInitCacheReceivedOnJoin() throws Exception {
        cacheC = s -> null;

        startServer(0, 1);

        startServer(1, 2);

        checkAffinity(2, topVer(2, 1), true);

        cacheC = s -> new CacheConfiguration[]{cacheConfiguration()};

        startServer(2, 3);

        checkAffinity(3, topVer(3, 0), false);

        checkAffinity(3, topVer(3, 1), true);

        cacheC = s -> {
            CacheConfiguration ccfg = cacheConfiguration();

            ccfg.setName(CACHE_NAME2);

            return new CacheConfiguration[]{ccfg};
        };

        startClient(3, 4);

        checkAffinity(4, topVer(4, 0), true);
    }

    /**
     * @throws Exception If failed.
     */
    @Test
    public void testRandomOperations() throws Exception {
        final int MAX_SRVS = 10;
        final int MAX_CLIENTS = 10;
        final int MAX_CACHES = 15;

        List<String> srvs = new ArrayList<>();
        List<String> clients = new ArrayList<>();

        int srvIdx = 0;
        int clientIdx = 0;
        int cacheIdx = 0;

        List<String> caches = new ArrayList<>();

        long seed = System.currentTimeMillis();

        Random rnd = new Random(seed);

        log.info("Random seed: " + seed);

        long topVer = 0;

        for (int i = 0; i < 100; i++) {
            int op = i == 0 ? 0 : rnd.nextInt(7);

            log.info("Iteration [iter=" + i + ", op=" + op + ']');

            switch (op) {
                case 0: {
                    if (srvs.size() < MAX_SRVS) {
                        srvIdx++;

                        String srvName = "server-" + srvIdx;

                        log.info("Start server: " + srvName);

                        if (rnd.nextBoolean()) {
                            cacheIdx++;

                            String cacheName = "join-cache-" + cacheIdx;

                            log.info("Cache for joining node: " + cacheName);

                            cacheClosure(rnd, caches, cacheName, srvs, srvIdx);
                        }
                        else
                            cacheClosure(rnd, caches, DEFAULT_CACHE_NAME, srvs, srvIdx);

                        startNode(srvName, ++topVer, false);

                        srvs.add(srvName);
                    }
                    else
                        log.info("Skip start server.");

                    break;
                }

                case 1: {
                    if (srvs.size() > 1) {
                        String srvName = srvs.get(rnd.nextInt(srvs.size()));

                        log.info("Stop server: " + srvName);

                        stopNode(srvName, ++topVer);

                        srvs.remove(srvName);
                    }
                    else
                        log.info("Skip stop server.");

                    break;
                }

                case 2: {
                    if (clients.size() < MAX_CLIENTS) {
                        clientIdx++;

                        String clientName = "client-" + clientIdx;

                        log.info("Start client: " + clientName);

                        if (rnd.nextBoolean()) {
                            cacheIdx++;

                            String cacheName = "join-cache-" + cacheIdx;

                            log.info("Cache for joining node: " + cacheName);

                            cacheClosure(rnd, caches, cacheName, srvs, srvIdx);
                        }
                        else
                            cacheClosure(rnd, caches, DEFAULT_CACHE_NAME, srvs, srvIdx);

                        startNode(clientName, ++topVer, true);

                        clients.add(clientName);
                    }
                    else
                        log.info("Skip start client.");

                    break;
                }

                case 3: {
                    if (clients.size() > 1) {
                        String clientName = clients.get(rnd.nextInt(clients.size()));

                        log.info("Stop client: " + clientName);

                        stopNode(clientName, ++topVer);

                        clients.remove(clientName);
                    }
                    else
                        log.info("Skip stop client.");

                    break;
                }

                case 4: {
                    if (!caches.isEmpty()) {
                        String cacheName = caches.get(rnd.nextInt(caches.size()));

                        Ignite node = randomNode(rnd, srvs, clients);

                        log.info("Destroy cache [cache=" + cacheName + ", node=" + node.name() + ']');

                        node.destroyCache(cacheName);

                        caches.remove(cacheName);
                    }
                    else
                        log.info("Skip destroy cache.");

                    break;
                }

                case 5: {
                    if (caches.size() < MAX_CACHES) {
                        cacheIdx++;

                        String cacheName = "cache-" + cacheIdx;

                        Ignite node = randomNode(rnd, srvs, clients);

                        log.info("Create cache [cache=" + cacheName + ", node=" + node.name() + ']');

                        node.createCache(randomCacheConfiguration(rnd, cacheName, srvs, srvIdx));

                        calculateAffinity(topVer);

                        caches.add(cacheName);
                    }
                    else
                        log.info("Skip create cache.");

                    break;
                }

                case 6: {
                    if (!caches.isEmpty()) {
                        for (int j = 0; j < 3; j++) {
                            String cacheName = caches.get(rnd.nextInt(caches.size()));

                            for (int k = 0; k < 3; k++) {
                                Ignite node = randomNode(rnd, srvs, clients);

                                log.info("Get/closes cache [cache=" + cacheName + ", node=" + node.name() + ']');

                                node.cache(cacheName).close();
                            }
                        }
                    }
                    else
                        log.info("Skip get/close cache.");

                    break;
                }

                default:
                    fail();
            }

            IgniteKernal node = (IgniteKernal)grid(srvs.get(0));

            checkAffinity(srvs.size() + clients.size(),
                node.context().cache().context().exchange().readyAffinityVersion(),
                false);
        }

        srvIdx++;

        String srvName = "server-" + srvIdx;

        log.info("Start server: " + srvName);

        cacheClosure(rnd, caches, DEFAULT_CACHE_NAME, srvs, srvIdx);

        startNode(srvName, ++topVer, false);

        srvs.add(srvName);

        checkAffinity(srvs.size() + clients.size(), topVer(topVer, 1), true);
    }

    /**
     * @throws Exception If failed.
     */
    @Test
    public void testConcurrentStartStaticCaches() throws Exception {
        concurrentStartStaticCaches(false);
    }

    /**
     * @throws Exception If failed.
     */
    @Test
    public void testConcurrentStartStaticCachesWithClientNodes() throws Exception {
        concurrentStartStaticCaches(true);
    }

    /**
     * @param withClients If {@code true} also starts client nodes.
     * @throws Exception If failed.
     */
    private void concurrentStartStaticCaches(boolean withClients) throws Exception {
        cacheC = new IgniteClosure<String, CacheConfiguration[]>() {
            @Override public CacheConfiguration[] apply(String igniteInstanceName) {
                int caches = getTestIgniteInstanceIndex(igniteInstanceName) + 1;

                CacheConfiguration[] ccfgs = new CacheConfiguration[caches];

                for (int i = 0; i < caches; i++) {
                    CacheConfiguration ccfg = cacheConfiguration();

                    ccfg.setName("cache-" + i);

                    ccfgs[i] = ccfg;
                }

                return ccfgs;
            }
        };

        if (withClients) {
            clientC = new IgniteClosure<String, Boolean>() {
                @Override public Boolean apply(String igniteInstanceName) {
                    int idx = getTestIgniteInstanceIndex(igniteInstanceName);

                    return idx % 3 == 2;
                }
            };
        }

        int ITERATIONS = 3;

        int NODES = withClients ? 8 : 5;

        for (int i = 0; i < ITERATIONS; i++) {
            log.info("Iteration: " + i);

            TestRecordingCommunicationSpi[] testSpis = new TestRecordingCommunicationSpi[NODES];

            for (int j = 0; j < NODES; j++) {
                testSpis[j] = new TestRecordingCommunicationSpi();

                testSpis[j].blockMessages((node, msg) -> msg instanceof GridDhtPartitionsSingleMessage);
            }

            //Ensure exchanges merge.
            spiC = igniteInstanceName -> testSpis[getTestIgniteInstanceIndex(igniteInstanceName)];

            runAsync(() -> {
                try {
                    for (int j = 1; j < NODES; j++)
                        testSpis[j].waitForBlocked();
                }
                catch (InterruptedException e) {
                    log.error("Thread interrupted.", e);
                }

                for (TestRecordingCommunicationSpi testSpi : testSpis)
                    testSpi.stopBlock();
            });

            startGridsMultiThreaded(NODES);

            for (int t = 0; t < NODES; t++)
                calculateAffinity(t + 1, true, null);

            if (withClients) {
                skipCheckOrder = true;

                checkAffinity(NODES, topVer(NODES, 0), false);
            }
            else
                checkAffinity(NODES, topVer(NODES, 1), true);

            if (i < ITERATIONS - 1) {
                checkCaches();

                awaitPartitionMapExchange();

                stopAllGrids();

                idealAff.clear();
            }
        }
    }

    /**
     * @throws Exception If failed.
     */
    @Test
    public void testServiceReassign() throws Exception {
        skipCheckOrder = true;

        Ignite ignite0 = startServer(0, 1);

        IgniteServices svcs = ignite0.services();

        for (int i = 0; i < 10; i++)
            svcs.deployKeyAffinitySingleton("service-" + i, new TestServiceImpl(i), CACHE_NAME1, i);

        startServer(1, 2);

        startServer(2, 3);

        Map<String, List<List<ClusterNode>>> assignments = checkAffinity(3, topVer(3, 1), true);

        checkServicesDeploy(ignite(0), assignments.get(CACHE_NAME1));

        stopGrid(0);

        boolean primaryChanged = calculateAffinity(4, false, assignments);

        assignments = checkAffinity(2, topVer(4, 0), !primaryChanged);

        if (primaryChanged)
            checkAffinity(2, topVer(4, 1), true);

        checkServicesDeploy(ignite(1), assignments.get(CACHE_NAME1));
    }

    /**
     * @throws Exception If failed.
     */
    @Test
    public void testNoForceKeysRequests() throws Exception {
        cacheC = new IgniteClosure<String, CacheConfiguration[]>() {
            @Override public CacheConfiguration[] apply(String s) {
                return null;
            }
        };

        final AtomicBoolean fail = new AtomicBoolean();

        spiC = new IgniteClosure<String, TestRecordingCommunicationSpi>() {
            @Override public TestRecordingCommunicationSpi apply(String s) {
                TestRecordingCommunicationSpi spi = new TestRecordingCommunicationSpi();

                spi.blockMessages(new IgniteBiPredicate<ClusterNode, Message>() {
                    @Override public boolean apply(ClusterNode node, Message msg) {
                        if (msg instanceof GridDhtForceKeysRequest || msg instanceof GridDhtForceKeysResponse) {
                            fail.set(true);

                            U.dumpStack(log, "Unexpected message: " + msg);
                        }

                        return false;
                    }
                });

                return spi;
            }
        };

        final int SRVS = 3;

        for (int i = 0; i < SRVS; i++)
            startGrid(i);

        startClientGrid(SRVS);

        final List<CacheConfiguration> ccfgs = new ArrayList<>();

        ccfgs.add(cacheConfiguration("tc1", TRANSACTIONAL, 0));
        ccfgs.add(cacheConfiguration("tc2", TRANSACTIONAL, 1));
        ccfgs.add(cacheConfiguration("tc3", TRANSACTIONAL, 2));

        for (CacheConfiguration ccfg : ccfgs)
            ignite(0).createCache(ccfg);

        final int NODES = SRVS + 1;

        final AtomicInteger nodeIdx = new AtomicInteger();

        final long stopTime = System.currentTimeMillis() + 60_000;

        IgniteInternalFuture<?> updateFut = GridTestUtils.runMultiThreadedAsync(new Callable<Void>() {
            @Override public Void call() throws Exception {
                int idx = nodeIdx.getAndIncrement();

                Ignite node = grid(idx);

                List<IgniteCache<Object, Object>> caches = new ArrayList<>();

                for (CacheConfiguration ccfg : ccfgs)
                    caches.add(node.cache(ccfg.getName()));

                while (!fail.get() && System.currentTimeMillis() < stopTime) {
                    for (IgniteCache<Object, Object> cache : caches)
                        cacheOperations(cache);
                }

                return null;
            }
        }, NODES, "update-thread");

        IgniteInternalFuture<?> srvRestartFut = runAsync(new Callable<Void>() {
            @Override public Void call() throws Exception {
                while (!fail.get() && System.currentTimeMillis() < stopTime) {
                    Ignite node = startGrid(NODES);

                    List<IgniteCache<Object, Object>> caches = new ArrayList<>();

                    for (CacheConfiguration ccfg : ccfgs)
                        caches.add(node.cache(ccfg.getName()));

                    for (int i = 0; i < 2; i++) {
                        for (IgniteCache<Object, Object> cache : caches)
                            cacheOperations(cache);
                    }

                    U.sleep(500);

                    stopGrid(NODES);

                    U.sleep(500);
                }

                return null;
            }
        }, "srv-restart");

        srvRestartFut.get();
        updateFut.get();

        assertFalse("Unexpected messages.", fail.get());
    }

    /**
     * @throws Exception If failed.
     */
    @Test
    public void testStreamer1() throws Exception {
        cacheC = new IgniteClosure<String, CacheConfiguration[]>() {
            @Override public CacheConfiguration[] apply(String s) {
                return null;
            }
        };

        startServer(0, 1);

        cacheC = null;
        cacheNodeFilter = new TestCacheNodeExcludingFilter(Collections.singletonList(getTestIgniteInstanceName(0)));

        startServer(1, 2);

        IgniteDataStreamer<Object, Object> streamer = ignite(0).dataStreamer(CACHE_NAME1);

        streamer.addData(1, 1);
        streamer.flush();
    }

    /**
     * @param cache Cache
     */
    private void cacheOperations(IgniteCache<Object, Object> cache) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        final int KEYS = 10_000;

        try {
            cache.get(rnd.nextInt(KEYS));

            cache.put(rnd.nextInt(KEYS), rnd.nextInt(10));

            cache.getAndPut(rnd.nextInt(KEYS), rnd.nextInt(10));

            cache.remove(rnd.nextInt(KEYS));

            cache.getAndRemove(rnd.nextInt(KEYS));

            cache.remove(rnd.nextInt(KEYS), rnd.nextInt(10));

            cache.putIfAbsent(rnd.nextInt(KEYS), rnd.nextInt(10));

            cache.replace(rnd.nextInt(KEYS), rnd.nextInt(10));

            cache.replace(rnd.nextInt(KEYS), rnd.nextInt(10), rnd.nextInt(10));

            cache.invoke(rnd.nextInt(KEYS), new TestEntryProcessor(rnd.nextInt(10)));

            if (cache.getConfiguration(CacheConfiguration.class).getAtomicityMode() == TRANSACTIONAL) {
                IgniteTransactions txs = cache.unwrap(Ignite.class).transactions();

                for (TransactionConcurrency concurrency : TransactionConcurrency.values()) {
                    for (TransactionIsolation isolation : TransactionIsolation.values()) {
                        try (Transaction tx = txs.txStart(concurrency, isolation)) {
                            Integer key = rnd.nextInt(KEYS);

                            cache.getAndPut(key, rnd.nextInt(10));

                            cache.invoke(key + 1, new TestEntryProcessor(rnd.nextInt(10)));

                            cache.get(key + 2);

                            tx.commit();
                        }
                    }
                }
            }
        }
        catch (Exception e) {
            log.info("Cache operation failed: " + e);
        }
    }

    /**
     * @param name Cache name.
     * @param atomicityMode Cache atomicity mode.
     * @param backups Number of backups.
     * @return Cache configuration.
     */
    private CacheConfiguration cacheConfiguration(String name, CacheAtomicityMode atomicityMode, int backups) {
        CacheConfiguration ccfg = cacheConfiguration();

        ccfg.setName(name);
        ccfg.setAtomicityMode(atomicityMode);
        ccfg.setBackups(backups);

        return ccfg;
    }

    /**
     * @param ignite Node.
     * @param affinity Affinity.
     * @throws Exception If failed.
     */
    private void checkServicesDeploy(Ignite ignite, final List<List<ClusterNode>> affinity) throws Exception {
        Affinity<Object> aff = ignite.affinity(CACHE_NAME1);

        for (int i = 0; i < 10; i++) {
            final int part = aff.partition(i);

            final String srvcName = "service-" + i;

            final ClusterNode srvcNode = affinity.get(part).get(0);

            boolean wait = waitForCondition(new PA() {
                @Override public boolean apply() {
                    TestService srvc = grid(srvcNode).services().service(srvcName);

                    if (srvc == null)
                        return false;

                    assertEquals(srvcNode, srvc.serviceNode());

                    return true;
                }
            }, 5000);

            assertTrue(wait);
        }
    }

    /**
     * @param rnd Random generator.
     * @param srvs Server.
     * @param clients Clients.
     * @return Random node.
     */
    private Ignite randomNode(Random rnd, List<String> srvs, List<String> clients) {
        String name = null;

        if (rnd.nextBoolean()) {
            if (!clients.isEmpty())
                name = clients.get(rnd.nextInt(clients.size()));
        }

        if (name == null)
            name = srvs.get(rnd.nextInt(srvs.size()));

        Ignite node = grid(name);

        assert node != null;

        return node;
    }

    /**
     * @param rnd Random generator.
     * @param caches Caches list.
     * @param cacheName Cache name.
     * @param srvs Server nodes.
     * @param srvIdx Current servers index.
     */
    private void cacheClosure(Random rnd, List<String> caches, String cacheName, List<String> srvs, int srvIdx) {
        if (!DEFAULT_CACHE_NAME.equals(cacheName)) {
            final CacheConfiguration ccfg = randomCacheConfiguration(rnd, cacheName, srvs, srvIdx);

            cacheC = new IgniteClosure<String, CacheConfiguration[]>() {
                @Override public CacheConfiguration[] apply(String s) {
                    return new CacheConfiguration[]{ccfg};
                }
            };

            caches.add(cacheName);
        }
        else {
            cacheC = new IgniteClosure<String, CacheConfiguration[]>() {
                @Override public CacheConfiguration[] apply(String s) {
                    return null;
                }
            };
        }
    }

    /**
     * @param rnd Random generator.
     * @param name Cache name.
     * @param srvs Server nodes.
     * @param srvIdx Current servers index.
     * @return Cache configuration.
     */
    private CacheConfiguration randomCacheConfiguration(Random rnd, String name, List<String> srvs, int srvIdx) {
        CacheConfiguration ccfg = cacheConfiguration();

        ccfg.setAtomicityMode(rnd.nextBoolean() ? TRANSACTIONAL : ATOMIC);
        ccfg.setBackups(rnd.nextInt(10));
        ccfg.setRebalanceMode(rnd.nextBoolean() ? SYNC : ASYNC);
        ccfg.setAffinity(affinityFunction(rnd.nextInt(2048) + 10));

        if (rnd.nextBoolean()) {
            Set<String> exclude = new HashSet<>();

            for (int i = 0; i < 10; i++) {
                if (i % 2 == 0 && !srvs.isEmpty())
                    exclude.add(srvs.get(rnd.nextInt(srvs.size())));
                else
                    exclude.add("server-" + (srvIdx + rnd.nextInt(10)));
            }

            ccfg.setNodeFilter(new TestCacheNodeExcludingFilter(exclude));
        }

        ccfg.setName(name);

        return ccfg;
    }

    /**
     * @param node Node.
     * @param topVer Topology version.
     * @param cache Cache name.
     * @return Affinity assignments.
     */
    private List<List<ClusterNode>> affinity(Ignite node, AffinityTopologyVersion topVer, String cache) {
        GridCacheContext cctx = ((IgniteKernal)node).context().cache().internalCache(cache).context();

        return cctx.affinity().assignments(topVer);
    }

    /**
     * @param spi SPI.
     * @param cacheName Cache name.
     */
    private void blockSupplySend(TestRecordingCommunicationSpi spi, final String cacheName) {
        final int grpId = groupIdForCache(spi.ignite(), cacheName);

        spi.blockMessages(new IgniteBiPredicate<ClusterNode, Message>() {
            @Override public boolean apply(ClusterNode node, Message msg) {
                if (!msg.getClass().equals(GridDhtPartitionSupplyMessage.class))
                    return false;

                return ((GridDhtPartitionSupplyMessage)msg).groupId() == grpId;
            }
        });
    }

    /**
     * @param expNodes Expected nodes number.
     * @param topVer Topology version.
     * @return Affinity futures.
     */
    private List<IgniteInternalFuture<?>> affFutures(int expNodes, AffinityTopologyVersion topVer) {
        List<Ignite> nodes = G.allGrids();

        assertEquals(expNodes, nodes.size());

        List<IgniteInternalFuture<?>> futs = new ArrayList<>(nodes.size());

        for (Ignite node : nodes) {
            IgniteInternalFuture<?>
                fut = ((IgniteKernal)node).context().cache().context().exchange().affinityReadyFuture(topVer);

            futs.add(fut);
        }

        return futs;
    }

    /**
     * @param topVer Topology version.
     * @param node Node.
     * @return Exchange future.
     */
    private IgniteInternalFuture<?> affinityReadyFuture(AffinityTopologyVersion topVer, Ignite node) {
        IgniteInternalFuture<?> fut = ((IgniteKernal)node).context().cache().context().exchange().
            affinityReadyFuture(topVer);

        return fut != null ? fut : new GridFinishedFuture<>();
    }

    /**
     * @param major Major version.
     * @param minor Minor version.
     * @return Topology version.
     */
    private static AffinityTopologyVersion topVer(long major, int minor) {
        return new AffinityTopologyVersion(major, minor);
    }

    /**
     *
     */
    private void checkCaches() {
        List<Ignite> nodes = G.allGrids();

        assertFalse(nodes.isEmpty());

        for (Ignite node : nodes) {
            Collection<String> cacheNames = node.cacheNames();

            assertFalse(cacheNames.isEmpty());

            for (String cacheName : cacheNames) {
                try {
                    IgniteCache<Object, Object> cache = node.cache(cacheName);

                    assertNotNull(cache);

                    Long val = System.currentTimeMillis();

                    ThreadLocalRandom rnd = ThreadLocalRandom.current();

                    for (int i = 0; i < 100; i++) {
                        int key = rnd.nextInt(100_000);

                        cache.put(key, val);

                        assertEquals(val, cache.get(key));

                        cache.remove(key);

                        assertNull(cache.get(key));
                    }
                }
                catch (Exception e) {
                    assertTrue("Unexpected error: " + e, X.hasCause(e, ClusterTopologyServerNotFoundException.class));

                    Affinity<Object> aff = node.affinity(cacheName);

                    assert aff.partitions() > 0;

                    for (int p = 0; p > aff.partitions(); p++) {
                        Collection<ClusterNode> partNodes = aff.mapPartitionToPrimaryAndBackups(p);

                        assertTrue(partNodes.isEmpty());
                    }
                }
            }
        }
    }

    /**
     * @param expNode Expected nodes number.
     * @param topVer Topology version.
     * @throws Exception If failed.
     */
    private void checkNoExchange(int expNode, AffinityTopologyVersion topVer) throws Exception {
        List<IgniteInternalFuture<?>> futs = affFutures(expNode, topVer);

        U.sleep(1000);

        for (IgniteInternalFuture<?> fut : futs)
            assertFalse(fut.isDone());
    }

    /**
     * @param expNodes Expected nodes number.
     * @param topVer Topology version.
     * @throws Exception If failed.
     */
    private void checkOrderCounters(int expNodes, AffinityTopologyVersion topVer) throws Exception {
        List<Ignite> nodes = G.allGrids();

        Long order = null;

        for (Ignite node : nodes) {
            IgniteKernal node0 = (IgniteKernal)node;

            if (node0.configuration().isClientMode())
                continue;

            IgniteInternalFuture<?> fut = node0.context().cache().context().exchange().affinityReadyFuture(topVer);

            if (fut != null)
                fut.get();

            AtomicLong orderCntr = GridTestUtils.getFieldValue(node0.context().cache().context().versions(), "order");

            log.info("Order [node=" + node0.name() + ", order=" + orderCntr.get() + ']');

            if (order == null)
                order = orderCntr.get();
            else
                assertEquals(order, (Long)orderCntr.get());
        }

        assertEquals(expNodes, nodes.size());
    }

    /**
     * @param expNodes Expected nodes number.
     * @param topVer Topology version.
     * @param expIdeal If {@code true} expect ideal affinity assignment.
     * @throws Exception If failed.
     * @return Affinity assignments.
     */
    private Map<String, List<List<ClusterNode>>> checkAffinity(int expNodes,
        AffinityTopologyVersion topVer,
        boolean expIdeal) throws Exception {
        return checkAffinity(expNodes, topVer, expIdeal, true);
    }

    /**
     * @param expNodes Expected nodes number.
     * @param topVer Topology version.
     * @param expIdeal If {@code true} expect ideal affinity assignment.
     * @param checkPublicApi {@code True} to check {@link Affinity} API.
     * @throws Exception If failed.
     * @return Affinity assignments.
     */
    private Map<String, List<List<ClusterNode>>> checkAffinity(int expNodes,
        AffinityTopologyVersion topVer,
        boolean expIdeal,
        boolean checkPublicApi
    ) throws Exception {
        List<Ignite> nodes = G.allGrids();

        Map<String, List<List<ClusterNode>>> aff = new HashMap<>();

        GridDhtPartitionsExchangeFuture exchFut = null;

        for (Ignite node : nodes) {
            log.info("Check affinity [node=" + node.name() + ", topVer=" + topVer + ", expIdeal=" + expIdeal + ']');

            IgniteKernal node0 = (IgniteKernal)node;

            IgniteInternalFuture<?> fut = node0.context().cache().context().exchange().affinityReadyFuture(topVer);

            if (fut != null)
                fut.get();

            List<GridDhtPartitionsExchangeFuture> exchFuts =
                ((IgniteEx)node).context().cache().context().exchange().exchangeFutures();

            for (GridDhtPartitionsExchangeFuture f : exchFuts) {
                if (f.exchangeDone() && !f.isMerged() && f.topologyVersion().equals(topVer)) {
                    if (exchFut != null) // Compare with previous node.
                        assertEquals(f.rebalanced(), exchFut.rebalanced()); // Check homogeneity.

                    assertNotSame(exchFut, f);

                    exchFut = f;

                    break;
                }
            }

            assertNotNull(exchFut);

            for (GridCacheContext cctx : node0.context().cache().context().cacheContexts()) {
                if (cctx.startTopologyVersion().compareTo(topVer) > 0)
                    continue;

                List<List<ClusterNode>> aff1 = aff.get(cctx.name());
                List<List<ClusterNode>> aff2 = cctx.affinity().assignments(topVer);

                if (aff1 == null)
                    aff.put(cctx.name(), aff2);
                else
                    assertAffinity(aff1, aff2, node, cctx.name(), topVer);

                if (expIdeal) {
                    assertEquals(
                        "Rebalance state not as expected [node=" + node.name() + ", top=" + topVer + "]",
                        true,
                        exchFut.rebalanced());

                    List<List<ClusterNode>> ideal = idealAssignment(topVer, cctx.cacheId());

                    assertAffinity(ideal, aff2, node, cctx.name(), topVer);

                    if (checkPublicApi) {
                        Affinity<Object> cacheAff = node.affinity(cctx.name());

                        for (int i = 0; i < 10; i++) {
                            int part = cacheAff.partition(i);

                            List<ClusterNode> partNodes = ideal.get(part);

                            if (partNodes.isEmpty()) {
                                try {
                                    cacheAff.mapKeyToNode(i);

                                    fail();
                                }
                                catch (IgniteException ignore) {
                                    // No-op.
                                }
                            }
                            else {
                                ClusterNode primary = cacheAff.mapKeyToNode(i);

                                assertEquals(primary, partNodes.get(0));
                            }
                        }

                        for (int p = 0; p < ideal.size(); p++) {
                            List<ClusterNode> exp = ideal.get(p);
                            Collection<ClusterNode> partNodes = cacheAff.mapPartitionToPrimaryAndBackups(p);

                            assertEqualsCollections(exp, partNodes);
                        }
                    }
                }
            }
        }

        assertEquals(expNodes, nodes.size());

        if (!skipCheckOrder)
            checkOrderCounters(expNodes, topVer);

        return aff;
    }

    /**
     * @param aff1 Affinity 1.
     * @param aff2 Affinity 2.
     * @param node Node.
     * @param cacheName Cache name.
     * @param topVer Topology version.
     */
    private void assertAffinity(List<List<ClusterNode>> aff1,
        List<List<ClusterNode>> aff2,
        Ignite node,
        String cacheName,
        AffinityTopologyVersion topVer) {
        assertEquals(aff1.size(), aff2.size());

        if (!aff1.equals(aff2)) {
            for (int i = 0; i < aff1.size(); i++) {
                Collection<UUID> n1 = new ArrayList<>(nodeIds(aff1.get(i)));
                Collection<UUID> n2 = new ArrayList<>(nodeIds(aff2.get(i)));

                assertEquals("Wrong affinity [node=" + node.name() +
                    ", topVer=" + topVer +
                    ", cache=" + cacheName +
                    ", part=" + i + ']',
                    n1, n2);
            }

            fail();
        }
    }

    /**
     * @param idx Node index.
     * @param topVer New topology version.
     * @return Started node.
     * @throws Exception If failed.
     */
    private Ignite startClient(int idx, long topVer) throws Exception {
        Ignite ignite = startClientGrid(idx);

        assertTrue(ignite.configuration().isClientMode());

        calculateAffinity(topVer);

        return ignite;
    }

    /**
     * @param idx Node index.
     * @param topVer New topology version.
     * @throws Exception If failed.
     * @return Started node.
     */
    private Ignite startServer(int idx, long topVer) throws Exception {
        Ignite node = startGrid(idx);

        assertFalse(node.configuration().isClientMode());

        calculateAffinity(topVer);

        return node;
    }

    /**
     * @param name Node name.
     * @param topVer Topology version.
     * @param client Client flag.
     * @throws Exception If failed.
     */
    private void startNode(String name, long topVer, boolean client) throws Exception {
        if (client)
            startClientGrid(name);
        else
            startGrid(name);

        calculateAffinity(topVer);
    }

    /**
     * @param name Node name.
     * @param topVer Topology version.
     * @throws Exception If failed.
     */
    private void stopNode(String name, long topVer) throws Exception {
        stopGrid(name);

        calculateAffinity(topVer);
    }

    /**
     * @param idx Node index.
     * @param topVer New topology version.
     * @throws Exception If failed.
     */
    private void stopNode(int idx, long topVer) throws Exception {
        stopNode(getTestIgniteInstanceName(idx), topVer);
    }

    /**
     * @param topVer Topology version.
     * @param cacheId Cache ID.
     * @return Ideal assignment.
     */
    private List<List<ClusterNode>> idealAssignment(AffinityTopologyVersion topVer, Integer cacheId) {
        Map<Integer, List<List<ClusterNode>>> assignments = idealAff.get(topVer.topologyVersion());

        assert assignments != null : "No assignments [topVer=" + topVer + ", cache=" + cacheId + ']';

        List<List<ClusterNode>> cacheAssignments = assignments.get(cacheId);

        assert cacheAssignments != null : "No cache assignments [topVer=" + topVer + ", cache=" + cacheId + ']';

        return cacheAssignments;
    }

    /**
     * @param topVer Topology version.
     * @throws Exception If failed.
     */
    private void calculateAffinity(long topVer) throws Exception {
        calculateAffinity(topVer, false, null);
    }

    /**
     * @param topVer Topology version.
     * @param filterByRcvd If {@code true} filters caches by 'receivedFrom' property.
     * @param cur Optional current affinity.
     * @throws Exception If failed.
     * @return {@code True} if some primary node changed comparing to given affinity.
     */
    private boolean calculateAffinity(long topVer,
        boolean filterByRcvd,
        @Nullable Map<String, List<List<ClusterNode>>> cur) throws Exception {
        List<Ignite> all = G.allGrids();

        IgniteKernal ignite = (IgniteKernal)Collections.min(all, new Comparator<Ignite>() {
            @Override public int compare(Ignite n1, Ignite n2) {
                return Long.compare(n1.cluster().localNode().order(), n2.cluster().localNode().order());
            }
        });

        assert !all.isEmpty();

        Map<Integer, List<List<ClusterNode>>> assignments = idealAff.get(topVer);

        if (assignments == null)
            idealAff.put(topVer, assignments = new HashMap<>());

        GridKernalContext ctx = ignite.context();

        GridCacheSharedContext cctx = ctx.cache().context();

        AffinityTopologyVersion topVer0 = new AffinityTopologyVersion(topVer);

        cctx.discovery().topologyFuture(topVer).get();

        List<GridDhtPartitionsExchangeFuture> futs = cctx.exchange().exchangeFutures();

        DiscoveryEvent evt = null;

        long stopTime = System.currentTimeMillis() + 10_000;

        boolean primaryChanged = false;

        do {
            for (int i = futs.size() - 1; i >= 0; i--) {
                GridDhtPartitionsExchangeFuture fut = futs.get(i);

                if (fut.initialVersion().equals(topVer0)) {
                    evt = fut.firstEvent();

                    break;
                }
            }

            if (evt == null) {
                U.sleep(500);

                futs = cctx.exchange().exchangeFutures();
            }
            else
                break;
        } while (System.currentTimeMillis() < stopTime);

        assertNotNull("Failed to find exchange future:", evt);

        Collection<ClusterNode> allNodes = ctx.discovery().serverNodes(topVer0);

        for (DynamicCacheDescriptor cacheDesc : ctx.cache().cacheDescriptors().values()) {
            if (assignments.get(cacheDesc.cacheId()) != null)
                continue;

            if (filterByRcvd && cacheDesc.receivedFrom() != null &&
                ctx.discovery().node(topVer0, cacheDesc.receivedFrom()) == null)
                continue;

            AffinityFunction func = cacheDesc.cacheConfiguration().getAffinity();

            func = cctx.cache().clone(func);

            cctx.kernalContext().resource().injectGeneric(func);

            List<ClusterNode> affNodes = new ArrayList<>();

            IgnitePredicate<ClusterNode> filter = cacheDesc.cacheConfiguration().getNodeFilter();

            for (ClusterNode n : allNodes) {
                if (!n.isClient() && (filter == null || filter.apply(n)))
                    affNodes.add(n);
            }

            Collections.sort(affNodes, NodeOrderComparator.getInstance());

            AffinityFunctionContext affCtx = new GridAffinityFunctionContextImpl(
                affNodes,
                previousAssignment(topVer, cacheDesc.cacheId()),
                evt,
                topVer0,
                cacheDesc.cacheConfiguration().getBackups());

            List<List<ClusterNode>> assignment = func.assignPartitions(affCtx);

            if (cur != null) {
                List<List<ClusterNode>> prev = cur.get(cacheDesc.cacheConfiguration().getName());

                assertEquals(prev.size(), assignment.size());

                if (!primaryChanged) {
                    for (int p = 0; p < prev.size(); p++) {
                        List<ClusterNode> nodes0 = prev.get(p);
                        List<ClusterNode> nodes1 = assignment.get(p);

                        if (!nodes0.isEmpty() && !nodes1.isEmpty()) {
                            ClusterNode p0 = nodes0.get(0);
                            ClusterNode p1 = nodes1.get(0);

                            if (allNodes.contains(p0) && !p0.equals(p1)) {
                                primaryChanged = true;

                                log.info("Primary changed [cache=" + cacheDesc.cacheConfiguration().getName() +
                                    ", part=" + p +
                                    ", prev=" + nodeIds(nodes0) +
                                    ", new=" + nodeIds(nodes1) + ']');

                                break;
                            }
                        }
                    }
                }
            }

            assignments.put(cacheDesc.cacheId(), assignment);
        }

        return primaryChanged;
    }

    /**
     * @param topVer Topology version.
     * @param cacheId Cache ID.
     * @return Previous assignment.
     */
    @Nullable private List<List<ClusterNode>> previousAssignment(long topVer, Integer cacheId) {
        if (topVer == 1)
            return null;

        Map<Integer, List<List<ClusterNode>>> assignments = idealAff.get(topVer - 1);

        assertNotNull(assignments);

        return assignments.get(cacheId);
    }

    /**
     *
     */
    interface TestService {
        /**
         * @return Node.
         */
        ClusterNode serviceNode();
    }

    /**
     *
     */
    private static class TestServiceImpl implements Service, TestService {
        /** */
        @IgniteInstanceResource
        private Ignite ignite;

        /** */
        private int key;

        /**
         * @param key Key.
         */
        public TestServiceImpl(int key) {
            this.key = key;
        }

        /** {@inheritDoc} */
        @Override public void cancel(ServiceContext ctx) {
            // No-op.
        }

        /** {@inheritDoc} */
        @Override public void init(ServiceContext ctx) throws Exception {
            // No-op.
        }

        /** {@inheritDoc} */
        @Override public void execute(ServiceContext ctx) throws Exception {
            ignite.log().info("Execute service [key=" + key + ", node=" + ignite.name() + ']');
        }

        /** {@inheritDoc} */
        @Override public ClusterNode serviceNode() {
            return ignite.cluster().localNode();
        }
    }

    /**
     *
     */
    static class TestEntryProcessor implements EntryProcessor<Object, Object, Object> {
        /** */
        private Object val;

        /**
         * @param val Value.
         */
        public TestEntryProcessor(Object val) {
            this.val = val;
        }

        /** {@inheritDoc} */
        @Override public Object process(MutableEntry<Object, Object> e, Object... args) {
            e.setValue(val);

            return null;
        }
    }
}
