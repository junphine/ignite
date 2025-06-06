/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements. See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License. You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.ignite.internal.processors.cache.verify;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.IgniteException;
import org.apache.ignite.cache.CacheEntryVersion;
import org.apache.ignite.internal.IgniteEx;
import org.apache.ignite.internal.binary.BinaryObjectEx;
import org.apache.ignite.internal.management.cache.PartitionKey;
import org.apache.ignite.internal.pagemem.PageIdAllocator;
import org.apache.ignite.internal.pagemem.PageIdUtils;
import org.apache.ignite.internal.processors.cache.CacheGroupContext;
import org.apache.ignite.internal.processors.cache.CacheObject;
import org.apache.ignite.internal.processors.cache.DynamicCacheDescriptor;
import org.apache.ignite.internal.processors.cache.KeyCacheObject;
import org.apache.ignite.internal.processors.cache.PartitionUpdateCounter;
import org.apache.ignite.internal.processors.cache.distributed.dht.topology.GridDhtLocalPartition;
import org.apache.ignite.internal.processors.cache.distributed.dht.topology.GridDhtPartitionState;
import org.apache.ignite.internal.processors.cache.distributed.dht.topology.GridDhtPartitionTopology;
import org.apache.ignite.internal.processors.cache.persistence.CacheDataRow;
import org.apache.ignite.internal.processors.cache.persistence.file.FilePageStore;
import org.apache.ignite.internal.processors.cache.persistence.snapshot.IncrementalSnapshotVerificationTask.HashHolder;
import org.apache.ignite.internal.util.lang.GridIterator;
import org.apache.ignite.internal.util.lang.IgniteThrowableSupplier;
import org.apache.ignite.internal.util.typedef.F;
import org.apache.ignite.internal.util.typedef.internal.SB;
import org.apache.ignite.lang.IgniteInClosure;
import org.jetbrains.annotations.Nullable;

import static org.apache.ignite.internal.binary.BinaryUtils.FLAG_COMPACT_FOOTER;
import static org.apache.ignite.internal.pagemem.PageIdAllocator.FLAG_AUX;
import static org.apache.ignite.internal.pagemem.PageIdAllocator.FLAG_DATA;
import static org.apache.ignite.internal.pagemem.PageIdAllocator.FLAG_IDX;
import static org.apache.ignite.internal.processors.cache.CacheObject.TYPE_BINARY;
import static org.apache.ignite.internal.processors.cache.persistence.filename.NodeFileTree.cacheName;

/**
 * Utility class for idle verify command.
 */
public class IdleVerifyUtility {
    /** Cluster not idle message. */
    public static final String GRID_NOT_IDLE_MSG =
        "Cluster not idle. Modifications found in caches or groups: ";

    /** */
    public static final String CRC_CHECK_ERR_MSG = "CRC check of partition failed";

    /**
     * Checks CRC sum of pages with {@code pageType} page type stored in partition with {@code partId} id
     * and associated with cache group.
     *
     * @param pageStoreSup Page store supplier.
     * @param partId Partition id.
     * @param pageType Page type. Possible types {@link PageIdAllocator#FLAG_DATA}, {@link PageIdAllocator#FLAG_IDX}
     *      and {@link PageIdAllocator#FLAG_AUX}.
     * @param cancelled Supplier of cancelled status.
     */
    public static void checkPartitionsPageCrcSum(
        IgniteThrowableSupplier<FilePageStore> pageStoreSup,
        int partId,
        byte pageType,
        @Nullable BooleanSupplier cancelled
    ) {
        checkPartitionsPageCrcSum(pageStoreSup, partId, pageType, null, cancelled);
    }

    /**
     * Checks CRC sum of pages with {@code pageType} page type stored in partition with {@code partId} id
     * and associated with cache group.
     *
     * @param pageStoreSup Page store supplier.
     * @param partId Partition id.
     * @param pageType Page type. Possible types {@link PageIdAllocator#FLAG_DATA}, {@link PageIdAllocator#FLAG_IDX}
     *      and {@link PageIdAllocator#FLAG_AUX}.
     * @param pagePostProc Page post processor closure.
     * @param cancelled Supplier of cancelled status.
     */
    public static void checkPartitionsPageCrcSum(
        IgniteThrowableSupplier<FilePageStore> pageStoreSup,
        int partId,
        byte pageType,
        @Nullable BiConsumer<Long, ByteBuffer> pagePostProc,
        @Nullable BooleanSupplier cancelled
    ) {
        assert pageType == FLAG_DATA || pageType == FLAG_IDX || pageType == FLAG_AUX : pageType;

        FilePageStore pageStore = null;

        try {
            pageStore = pageStoreSup.get();

            long pageId = PageIdUtils.pageId(partId, (byte)0, 0);

            ByteBuffer buf = ByteBuffer.allocateDirect(pageStore.getPageSize()).order(ByteOrder.nativeOrder());

            for (int pageNo = 0; pageNo < pageStore.pages(); pageId++, pageNo++) {
                if (cancelled != null && cancelled.getAsBoolean())
                    throw new IgniteException();

                buf.clear();

                pageStore.read(pageId, buf, true, true);

                if (pagePostProc != null)
                    pagePostProc.accept(pageId, buf);
            }
        }
        catch (Throwable e) {
            String msg0 = CRC_CHECK_ERR_MSG + " [partId=" + partId +
                ", grpName=" + (pageStore == null ? "" : cacheName(new File(pageStore.getFileAbsolutePath()).getParentFile())) +
                ", part=" + (pageStore == null ? "" : pageStore.getFileAbsolutePath()) + ']';

            throw new IgniteException(msg0, e);
        }
    }

    /**
     * Gather updateCounters info.
     * Holds {@link org.apache.ignite.internal.processors.cache.PartitionUpdateCounter#copy} of update counters.
     *
     * @param ign Ignite instance.
     * @param grpIds Group Id`s.
     * @return Current groups distribution with update counters per partitions.
     */
    public static Map<Integer, Map<Integer, PartitionUpdateCounter>> getUpdateCountersSnapshot(
        IgniteEx ign,
        Set<Integer> grpIds
    ) {
        Map<Integer, Map<Integer, PartitionUpdateCounter>> partsWithCountersPerGrp = new HashMap<>();

        for (Integer grpId : grpIds) {
            CacheGroupContext grpCtx = ign.context().cache().cacheGroup(grpId);

            if (grpCtx == null)
                throw new GridNotIdleException("Group not found: " + grpId + "."
                        + " Possible reasons: rebalance in progress or concurrent cache destroy.");

            GridDhtPartitionTopology top = grpCtx.topology();

            Map<Integer, PartitionUpdateCounter> partsWithCounters =
                partsWithCountersPerGrp.computeIfAbsent(grpId, k -> new HashMap<>());

            for (GridDhtLocalPartition part : top.currentLocalPartitions()) {
                if (part.state() != GridDhtPartitionState.OWNING)
                    continue;

                @Nullable PartitionUpdateCounter updCntr = part.dataStore().partUpdateCounter();

                partsWithCounters.put(part.id(), updCntr == null ? null : updCntr.copy());
            }
        }

        return partsWithCountersPerGrp;
    }

    /**
     * Prints diff between incoming update counters snapshots.
     *
     * @param ig Ignite instance.
     * @param diff Compared groups diff.
     * @return Formatted diff representation.
     */
    public static String formatUpdateCountersDiff(IgniteEx ig, List<Integer> diff) {
        SB sb = null;

        if (!diff.isEmpty()) {
            sb = new SB();

            for (int grpId0 : diff) {
                if (sb.length() != 0)
                    sb.a(", ");
                else
                    sb.a("\"");

                DynamicCacheDescriptor desc = ig.context().cache().cacheDescriptor(grpId0);

                CacheGroupContext grpCtx = ig.context().cache().cacheGroup(desc == null ? grpId0 : desc.groupId());

                sb.a(grpCtx.cacheOrGroupName());
            }

            sb.a("\"");
        }

        return sb != null ? sb.toString() : "";
    }

    /**
     * Compares two sets with partitions and upd counters per group distribution.
     *
     * @param ign Ignite instance.
     * @param cntrsIn Group id`s with counters per partitions per groups distribution.
     * @param grpId Group id to compare.
     * @return Diff with grpId info between two sets.
     */
    public static List<Integer> compareUpdateCounters(
        IgniteEx ign,
        Map<Integer, Map<Integer, PartitionUpdateCounter>> cntrsIn,
        Integer grpId
    ) {
        Map<Integer, Map<Integer, PartitionUpdateCounter>> curCntrs =
            getUpdateCountersSnapshot(ign, Collections.singleton(grpId));

        if (curCntrs.isEmpty())
            throw new GridNotIdleException("No OWNING partitions for group: " + grpId);

        return compareUpdateCounters(ign, cntrsIn, curCntrs);
    }

    /**
     * Compares two sets with partitions and upd counters per group distribution.
     *
     * @param ign Ignite instance.
     * @param cntrsEth Ethalon group id`s with counters per partitions per groups distribution.
     * @param curCntrs Group id`s with counters per partitions per groups distribution compare with.
     * @return Diff with grpId info between two sets.
     */
    public static List<Integer> compareUpdateCounters(
        IgniteEx ign,
        Map<Integer, Map<Integer, PartitionUpdateCounter>> cntrsEth,
        Map<Integer, Map<Integer, PartitionUpdateCounter>> curCntrs
    ) {
        List<Integer> diff = new ArrayList<>();

        Integer grpId;
        for (Map.Entry<Integer, Map<Integer, PartitionUpdateCounter>> curEntry : curCntrs.entrySet()) {
            Map<Integer, PartitionUpdateCounter> partsWithCntrsCur = curEntry.getValue();

            grpId = curEntry.getKey();

            if (partsWithCntrsCur == null)
                throw new GridNotIdleException("Group not found: " + grpId + "."
                    + " Possible reasons: rebalance in progress or concurrent cache destroy.");

            Map<Integer, PartitionUpdateCounter> partsWithCntrsIn = cntrsEth.get(grpId);

            if (!partsWithCntrsIn.equals(partsWithCntrsCur))
                diff.add(grpId);
        }

        return diff;
    }

    /**
     * @param partKey Partition key.
     * @param updCntr Partition update counter prior check.
     * @param consId Local node consistent id.
     * @param state Partition state to check.
     * @param isPrimary {@code true} if partition is primary.
     * @param partSize Partition size on disk.
     * @param it Iterator though partition data rows.
     * @param cancelled Supplier of cancelled status.
     * @throws IgniteCheckedException If fails.
     * @return Map of calculated partition.
     */
    public static @Nullable PartitionHashRecord calculatePartitionHash(
        PartitionKey partKey,
        Object updCntr,
        Object consId,
        GridDhtPartitionState state,
        boolean isPrimary,
        long partSize,
        GridIterator<CacheDataRow> it,
        @Nullable BooleanSupplier cancelled
    ) throws IgniteCheckedException {
        if (state == GridDhtPartitionState.MOVING || state == GridDhtPartitionState.LOST) {
            return new PartitionHashRecord(partKey,
                isPrimary,
                consId,
                updCntr,
                state == GridDhtPartitionState.MOVING ?
                    PartitionHashRecord.MOVING_PARTITION_SIZE : 0,
                state == GridDhtPartitionState.MOVING ?
                    PartitionHashRecord.PartitionState.MOVING : PartitionHashRecord.PartitionState.LOST,
                new VerifyPartitionContext()
            );
        }

        if (state != GridDhtPartitionState.OWNING)
            return null;

        VerifyPartitionContext ctx = new VerifyPartitionContext();

        while (it.hasNextX()) {
            if (cancelled != null && cancelled.getAsBoolean())
                throw new IgniteCheckedException("Caclulate partition hash cancelled.");

            CacheDataRow row = it.nextX();

            if (row.expireTime() > 0)
                continue;

            ctx.update(row.key(), row.value(), row.version());
        }

        return new PartitionHashRecord(
            partKey,
            isPrimary,
            consId,
            updCntr,
            partSize,
            PartitionHashRecord.PartitionState.OWNING,
            ctx
        );
    }

    /**
     * Idle checker.
     */
    public static class IdleChecker implements IgniteInClosure<Integer> {
        /** */
        private static final long serialVersionUID = 0L;

        /** */
        private final IgniteEx ig;

        /** Group id`s snapshot with partitions and counters distrubution. */
        private final Map<Integer, Map<Integer, PartitionUpdateCounter>> partsWithCntrsPerGrp;

        /** */
        public IdleChecker(IgniteEx ig, Map<Integer, Map<Integer, PartitionUpdateCounter>> partsWithCntrsPerGrp) {
            this.ig = ig;
            this.partsWithCntrsPerGrp = partsWithCntrsPerGrp;
        }

        /** */
        @Override public void apply(Integer grpId) {
            List<Integer> diff;

            diff = compareUpdateCounters(ig, partsWithCntrsPerGrp, grpId);

            if (!F.isEmpty(diff)) {
                String res = formatUpdateCountersDiff(ig, diff);

                if (!res.isEmpty())
                    throw new GridNotIdleException(GRID_NOT_IDLE_MSG + "[" + res + "]");
            }
        }
    }

    /** */
    private IdleVerifyUtility() {
        /* No-op. */
    }

    /** */
    public static class VerifyPartitionContext {
        /** */
        public int partHash;

        /** */
        public int partVerHash;

        /** */
        public int cf;

        /** */
        public int noCf;

        /** */
        public int binary;

        /** */
        public int regular;

        /** */
        public VerifyPartitionContext() {
            // No-op.
        }

        /**
         * @param hash Incremental snapshot hash holder.
         */
        public VerifyPartitionContext(HashHolder hash) {
            this.partHash = hash.hash;
            this.partVerHash = hash.verHash;
        }

        /** */
        public void update(
            KeyCacheObject key,
            CacheObject val,
            CacheEntryVersion ver
        ) throws IgniteCheckedException {
            partHash += key.hashCode();

            if (ver != null)
                partVerHash += ver.hashCode(); // Detects ABA problem.

            // Object context is not required since the valueBytes have been read directly from page.
            partHash += Arrays.hashCode(val.valueBytes(null));

            if (key.cacheObjectType() == TYPE_BINARY) {
                binary++;

                if (((BinaryObjectEx)key).isFlagSet(FLAG_COMPACT_FOOTER))
                    cf++;
                else
                    noCf++;
            }
            else
                regular++;
        }
    }
}
