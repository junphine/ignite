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

package org.apache.ignite.internal.processors.query.h2.opt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.IgniteException;
import org.apache.ignite.IgniteInterruptedException;
import org.apache.ignite.IgniteLogger;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.cache.CachePeekMode;
import org.apache.ignite.cache.query.QueryRetryException;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.internal.GridKernalContext;
import org.apache.ignite.internal.processors.cache.GridCacheContext;
import org.apache.ignite.internal.processors.cache.GridCacheContextInfo;
import org.apache.ignite.internal.processors.cache.persistence.CacheDataRow;
import org.apache.ignite.internal.processors.query.IgniteSQLException;
import org.apache.ignite.internal.processors.query.QueryField;
import org.apache.ignite.internal.processors.query.QueryUtils;
import org.apache.ignite.internal.processors.query.h2.H2TableDescriptor;
import org.apache.ignite.internal.processors.query.h2.H2Utils;
import org.apache.ignite.internal.processors.query.h2.QueryTable;
import org.apache.ignite.internal.processors.query.h2.database.H2TreeIndex;
import org.apache.ignite.internal.processors.query.stat.ObjectStatistics;
import org.apache.ignite.internal.processors.query.stat.StatisticsKey;
import org.apache.ignite.internal.util.tostring.GridToStringExclude;
import org.apache.ignite.internal.util.typedef.F;
import org.apache.ignite.internal.util.typedef.internal.U;
import org.h2.command.ddl.CreateTableData;
import org.h2.command.dml.Insert;
import org.h2.engine.DbObject;
import org.h2.engine.Session;
import org.h2.engine.SysProperties;
import org.h2.index.Index;
import org.h2.index.IndexType;
import org.h2.message.DbException;
import org.h2.result.Row;
import org.h2.result.SortOrder;
import org.h2.schema.SchemaObject;
import org.h2.table.Column;
import org.h2.table.IndexColumn;
import org.h2.table.Table;
import org.h2.table.TableBase;
import org.h2.table.TableType;
import org.h2.value.DataType;
import org.jetbrains.annotations.Nullable;

import static org.apache.ignite.cache.CacheMode.PARTITIONED;
import static org.apache.ignite.internal.processors.affinity.AffinityTopologyVersion.NONE;

/**
 * H2 Table implementation.
 */
public class GridH2Table extends TableBase {
    /** Insert hack flag. */
    private static final ThreadLocal<Boolean> INSERT_HACK = new ThreadLocal<>();

    /** Exclusive lock constant. */
    private static final long EXCLUSIVE_LOCK = -1;

    /** 'rebuildFromHashInProgress' field updater */
    private static final AtomicIntegerFieldUpdater<GridH2Table> rebuildFromHashInProgressFiledUpdater =
        AtomicIntegerFieldUpdater.newUpdater(GridH2Table.class, "rebuildFromHashInProgress");

    /** False representation */
    private static final int FALSE = 0;

    /** True representation */
    private static final int TRUE = 1;

    /**
     * Row count statistics update threshold. Stats will be updated when the actual
     * table size change exceeds this threshold. Should be the number in interval (0,1).
     */
    private static final double STATS_UPDATE_THRESHOLD = 0.1; // 10%.

    /** Cache context info. */
    private final GridCacheContextInfo cacheInfo;

    /** */
    private final GridH2RowDescriptor desc;

    /** */
    private final H2TableDescriptor tblDesc;

    /** */
    private volatile ArrayList<Index> idxs;

    /** */
    private volatile int pkIdxPos;

    /** Total number of system indexes. */
    private volatile int sysIdxsCnt;

    /** */
    private final Map<String, H2IndexCostedBase> tmpIdxs = new HashMap<>();

    /** */
    private final ReentrantReadWriteLock lock;

    /** */
    private volatile boolean destroyed;

    /**
     * Map of sessions locks.
     * Session -> EXCLUSIVE_LOCK (-1L) - for exclusive locks.
     * Session -> (table version) - for shared locks.
     */
    private final ConcurrentMap<Session, SessionLock> sessions = new ConcurrentHashMap<>();

    /** */
    private final IndexColumn affKeyCol;

    /** Whether affinity key column is the whole cache key. */
    private final boolean affKeyColIsKey;

    /** */
    private final LongAdder size = new LongAdder();

    /** */
    private volatile int rebuildFromHashInProgress = FALSE;

    /** Identifier. */
    private final QueryTable identifier;

    /** Identifier as string. */
    private final String identifierStr;

    /** Columns with thread-safe access. */
    private volatile Column[] safeColumns;

    /** Table version. The version is changed when exclusive lock is acquired (DDL operation is started). */
    private final AtomicLong ver = new AtomicLong();

    /** Table statistics. */
    private volatile TableStatistics tblStats;

    /** Logger. */
    @GridToStringExclude
    private IgniteLogger log;

    /**
     * Creates table.
     *
     * @param createTblData Table description.
     * @param desc Row descriptor.
     * @param tblDesc Indexes factory.
     * @param cacheInfo Cache context info.
     */
    @SuppressWarnings("ConstantConditions")
    public GridH2Table(
        CreateTableData createTblData,
        GridH2RowDescriptor desc,
        H2TableDescriptor tblDesc,
        GridCacheContextInfo<?, ?> cacheInfo
    ) {
        super(createTblData);

        assert tblDesc != null;

        this.desc = desc;
        this.tblDesc = tblDesc;
        this.cacheInfo = cacheInfo;

        affKeyCol = calculateAffinityKeyColumn();
        affKeyColIsKey = affKeyCol != null && desc.isKeyColumn(affKeyCol.column.getColumnId());

        identifier = new QueryTable(getSchema().getName(), getName());

        identifierStr = identifier.schema() + "." + identifier.table();

        tblDesc.createHashIndex(this);
        tblDesc.createTextIndex(this);
        idxs = new ArrayList<>();

        if (tblDesc.hashIndex() != null)
            idxs.add(tblDesc.hashIndex());

        sysIdxsCnt = idxs.size();

        lock = new ReentrantReadWriteLock();

        if (cacheInfo.affinityNode()) {
            long totalTblSize = cacheSize(CachePeekMode.PRIMARY, CachePeekMode.BACKUP);

            size.add(totalTblSize);
        }

        // Init stats with the default values.
        tblStats = new TableStatistics(10_000, 10_000);

        if (desc != null && desc.context() != null) {
            GridKernalContext ctx = desc.context().kernalContext();

            log = ctx.log(getClass());
        }
    }

    /** */
    public void addSystemIndex(Index idx) {
        lock(true);

        try {
            if (idx.getIndexType().isPrimaryKey()) {
                // Add SCAN index at 0 which is required by H2.
                idxs.add(0, new H2TableScanIndex(this, (GridH2IndexBase)idx, (GridH2IndexBase)tblDesc.hashIndex()));
                pkIdxPos = idxs.size();
            }

            idxs.add(idx);

            sysIdxsCnt = idxs.size();
        }
        finally {
            unlock(true);
        }
    }

    /**
     * Calculate affinity key column which will be used for partition pruning and distributed joins.
     *
     * @return Affinity column or {@code null} if none can be used.
     */
    private IndexColumn calculateAffinityKeyColumn() {
        // If custome affinity key mapper is set, we do not know how to convert _KEY to partition, return null.
        if (desc.type().customAffinityKeyMapper())
            return null;

        String affKeyFieldName = desc.type().affinityKey();

        // If explicit affinity key field is not set, then use _KEY.
        if (affKeyFieldName == null)
            return indexColumn(QueryUtils.KEY_COL, SortOrder.ASCENDING);

        // If explicit affinity key field is set, but is not found in the table, do not use anything.
        if (!doesColumnExist(affKeyFieldName))
            return null;

        int colId = getColumn(affKeyFieldName).getColumnId();

        // If affinity key column is either _KEY or it's alias (QueryEntity.keyFieldName), normalize it to _KEY.
        if (desc.isKeyColumn(colId))
            return indexColumn(QueryUtils.KEY_COL, SortOrder.ASCENDING);

        // Otherwise use column as is.
        return indexColumn(colId, SortOrder.ASCENDING);
    }

    /**
     * @return {@code true} If this is a partitioned table.
     */
    public boolean isPartitioned() {
        return desc != null && cacheInfo.config().getCacheMode() == PARTITIONED;
    }

    /**
     * @return Affinity key column or {@code null} if not available.
     */
    @Nullable public IndexColumn getAffinityKeyColumn() {
        return affKeyCol;
    }

    /**
     * @return Explicit affinity key column or {@code null} if not available (skip _KEY column or it's alias).
     */
    @Nullable public IndexColumn getExplicitAffinityKeyColumn() {
        // Only explicit affinity column should be shown. Do not do this for _KEY or it's alias.
        if (affKeyCol == null || affKeyColIsKey)
            return null;

        return affKeyCol;
    }

    /**
     * Check whether passed column can be used for partition pruning.
     *
     * @param col Column.
     * @return {@code True} if affinity key column.
     */
    public boolean isColumnForPartitionPruning(Column col) {
        return isColumnForPartitionPruning0(col, false);
    }

    /**
     * Check whether passed column could be used for partition transfer during partition pruning on joined tables and
     * for external affinity calculation (e.g. on thin clients).
     * <p>
     * Note that it is different from {@link #isColumnForPartitionPruning(Column)} method in that not every column
     * which qualifies for partition pruning can be used by thin clients or join partition pruning logic.
     * <p>
     * Consider the following schema:
     * <pre>
     * CREATE TABLE dept (id PRIMARY KEY);
     * CREATE TABLE emp (id, dept_id AFFINITY KEY, PRIMARY KEY(id, dept_id));
     * </pre>
     * For expression-based partition pruning on "emp" table on the <b>server side</b> we may use both "_KEY" and
     * "dept_id" columns, as passing them through standard affinity workflow will yield the same result:
     * dept_id -> part
     * _KEY -> dept_id -> part
     * <p>
     * But we cannot use "_KEY" on thin client side, as it doesn't know how to extract affinity key field properly.
     * Neither we can perform partition transfer in JOINs when "_KEY" is used.
     * <p>
     * This is OK as data is collocated, so we can merge partitions extracted from both tables:
     * <pre>
     * SELECT * FROM dept d INNER JOIN emp e ON d.id = e.dept_id WHERE e.dept_id=? AND d.id=?
     * </pre>
     * But this is not OK as joined data is not collocated, and tables form distinct collocation groups:
     * <pre>
     * SELECT * FROM dept d INNER JOIN emp e ON d.id = e._KEY WHERE e.dept_id=? AND d.id=?
     * </pre>
     * NB: The last query is not logically correct and will produce empty result. However, it is correct from SQL
     * perspective, so we should make incorrect assumptions about partitions as it may make situation even worse.
     *
     * @param col Column.
     * @return {@code True} if column could be used for partition extraction on both server and client sides and for
     *     partition transfer in joins.
     */
    public boolean isColumnForPartitionPruningStrict(Column col) {
        return isColumnForPartitionPruning0(col, true);
    }

    /**
     * Internal logic to check whether column qualifies for partition extraction or not.
     *
     * @param col Column.
     * @param strict Strict flag.
     * @return {@code True} if column could be used for partition.
     */
    private boolean isColumnForPartitionPruning0(Column col, boolean strict) {
        if (affKeyCol == null)
            return false;

        int colId = col.getColumnId();

        if (colId == affKeyCol.column.getColumnId())
            return true;

        return (affKeyColIsKey || !strict) && desc.isKeyColumn(colId);
    }

    /**
     * @return Whether custom affintiy mapper is used.
     */
    public boolean isCustomAffinityMapper() {
        return desc.type().customAffinityKeyMapper();
    }

    /** {@inheritDoc} */
    @Override public long getDiskSpaceUsed() {
        return 0;
    }

    /**
     * @return Row descriptor.
     */
    public GridH2RowDescriptor rowDescriptor() {
        return desc;
    }

    /**
     * @return Table descriptor.
     */
    public H2TableDescriptor tableDescriptor() {
        return tblDesc;
    }

    /**
     * @return Cache name.
     */
    public String cacheName() {
        return cacheInfo.name();
    }

    /**
     * @return Cache ID.
     */
    public int cacheId() {
        return cacheInfo.cacheId();
    }

    /**
     * @return Cache context info.
     */
    public GridCacheContextInfo cacheInfo() {
        return cacheInfo;
    }

    /**
     * Get actual table statistics if exists.
     *
     * @return Table statistics or {@code null} if there is no statistics available.
     */
    public ObjectStatistics tableStatistics() {
        GridCacheContext cacheCtx = cacheInfo.cacheContext();

        if (cacheCtx == null)
            return null;

        return cacheCtx.kernalContext().query().statsManager()
            .getLocalStatistics(new StatisticsKey(identifier.schema(), identifier.table()));
    }

    /**
     * @return Cache context.
     */
    @Nullable public GridCacheContext cacheContext() {
        return cacheInfo.cacheContext();
    }

    /** {@inheritDoc} */
    @Override public boolean lock(Session ses, boolean exclusive, boolean force) {
        // In accordance with base method semantics, we'll return true if we were already exclusively locked.
        SessionLock sesLock = sessions.get(ses);

        if (sesLock != null) {
            if (sesLock.isExclusive())
                return true;

            if (ver.get() != sesLock.version())
                throw new QueryRetryException(getName());

            return false;
        }

        // Acquire the lock.
        // not modify@byron for debug
        // lock(exclusive, false);
        lock(exclusive, true);

        if (destroyed) {
            unlock(exclusive);

            throw new IllegalStateException("Table " + identifierString() + " already destroyed.");
        }

        // Mutate state.
        sessions.put(ses, exclusive ? SessionLock.exclusiveLock() : SessionLock.sharedLock(ver.longValue()));

        ses.addLock(this);

        return false;
    }

    /** {@inheritDoc} */
    @Override public void unlock(Session ses) {
        SessionLock sesLock = sessions.remove(ses);

        if (sesLock.locked)
            unlock(sesLock.isExclusive());
    }

    /**
     * @param ses H2 session.
     */
    private void readLockInternal(Session ses) {
        SessionLock sesLock = sessions.get(ses);

        assert sesLock != null && !sesLock.isExclusive()
            : "Invalid table lock [name=" + getName() + ", lock=" + sesLock == null ? "null" : sesLock.ver + ']';

        if (!sesLock.locked) {
            lock(false);

            sesLock.locked = true;
        }
    }

    /**
     * Release table lock.
     *
     * @param ses H2 session.
     */
    private void unlockReadInternal(Session ses) {
        SessionLock sesLock = sessions.get(ses);

        assert sesLock != null && !sesLock.isExclusive()
            : "Invalid table unlock [name=" + getName() + ", lock=" + sesLock == null ? "null" : sesLock.ver + ']';

        if (sesLock.locked) {
            sesLock.locked = false;

            unlock(false);
        }
    }

    /**
     * Acquire table lock.
     *
     * @param exclusive Exclusive flag.
     */
    private void lock(boolean exclusive) {
        lock(exclusive, false);
    }

    /**
     * Acquire table lock.
     *
     * @param exclusive Exclusive flag.
     * @param interruptibly Acquires interruptibly lock or not interruplible lock flag.
     */
    @SuppressWarnings({"LockAcquiredButNotSafelyReleased", "CallToThreadYield"})
    private void lock(boolean exclusive, boolean interruptibly) {
        Lock l = exclusive ? lock.writeLock() : lock.readLock();

        try {
            if (!exclusive) {
                if (interruptibly)
                    l.lockInterruptibly();
                else
                    l.lock();
            }
            else {
                for (;;) {
                    if (l.tryLock(200, TimeUnit.MILLISECONDS))
                        break;
                    else
                        Thread.yield();
                }

                ver.incrementAndGet();
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            throw new IgniteInterruptedException("Thread got interrupted while trying to acquire table lock.", e);
        }
    }

    /**
     * Release table lock.
     *
     * @param exclusive Exclusive flag.
     */
    private void unlock(boolean exclusive) {
        Lock l = exclusive ? lock.writeLock() : lock.readLock();

        l.unlock();
    }

    /**
     * @param ses H2 session.
     */
    private void checkVersion(Session ses) {
        SessionLock sesLock = sessions.get(ses);

        assert sesLock != null && !sesLock.isExclusive()
            : "Invalid table check version  [name=" + getName() + ", lock=" + sesLock.ver + ']';

        if (ver.longValue() != sesLock.version())
            throw new QueryRetryException(getName());
    }

    /**
     * @return Table identifier.
     */
    public QueryTable identifier() {
        return identifier;
    }

    /**
     * @return Table identifier as string.
     */
    public String identifierString() {
        return identifierStr;
    }

    /**
     * Check if table is not destroyed.
     */
    private void ensureNotDestroyed() {
        if (destroyed)
            throw new IllegalStateException("Table " + identifierString() + " already destroyed.");
    }

    /** {@inheritDoc} */
    @Override public void close(Session ses) {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public void removeChildrenAndResources(Session ses) {
        lock(true);

        try {
            super.removeChildrenAndResources(ses);

            // Clear all user indexes registered in schema.
            while (idxs.size() > sysIdxsCnt) {
                Index idx = idxs.get(sysIdxsCnt);

                if (idx.getName() != null && idx.getSchema().findIndex(ses, idx.getName()) == idx) {
                    // This call implicitly removes both idx and its proxy, if any, from idxs.
                    database.removeSchemaObject(ses, idx);

                    // We have to call destroy here if we are who has removed this index from the table.
                    destroyIndex(idx);
                }
            }

            if (SysProperties.CHECK) {
                for (SchemaObject obj : database.getAllSchemaObjects(DbObject.INDEX)) {
                    Index idx = (Index)obj;
                    if (idx.getTable() == this)
                        DbException.throwInternalError("index not dropped: " + idx.getName());
                }
            }

            database.removeMeta(ses, getId());
            invalidate();

        }
        finally {
            unlock(true);
        }
    }

    /**
     * Destroy the table.
     */
    public void destroy() {
        lock(true);

        try {
            ensureNotDestroyed();

            destroyed = true;

            for (int i = 1, len = idxs.size(); i < len; i++)
                destroyIndex(idxs.get(i));
        }
        finally {
            unlock(true);
        }
    }

    /**
     * Destroy index with GridIndexManager.
     */
    private void destroyIndex(Index idx) {
        if (idx instanceof GridH2IndexBase) {
            GridH2IndexBase h2idx = (GridH2IndexBase)idx;

            // If H2 index stores some state.
            h2idx.destroy();
        }
    }

    /**
     * Gets index by index.
     *
     * @param idx Index in list.
     * @return Index.
     */
    private <T extends Index> T index(int idx) {
        return (T)idxs.get(idx);
    }

    /**
     * Gets primary key.
     *
     * @return Primary key.
     */
    private GridH2IndexBase pk() {
        return (GridH2IndexBase)idxs.get(2);
    }

    /**
     * Updates table for given key. If value is null then row with given key will be removed from table,
     * otherwise value and expiration time will be updated or new row will be added.
     *
     * @param row Row to be updated.
     * @param prevRow Previous row.
     * @throws IgniteCheckedException If failed.
     */
    public void update(CacheDataRow row, @Nullable CacheDataRow prevRow) {
        // Size of a table bases on PK index size. PK index key equals to a cache key, so we can rely on this condition.
        // Table size shows approximate count of rows.
        if (prevRow == null)
            size.increment();
    }

    /**
     * Remove row.
     *
     * @param row Row.
     */
    public void remove(CacheDataRow row) {
        // Size of a table bases on PK index size. PK index key equals to a cache key, so we can rely on this condition.
        // Table size shows approximate count of rows.
        size.decrement();
    }

    /**
     * Mark or unmark index rebuild state.
     */
    public void markRebuildFromHashInProgress(boolean value) {
        assert !value || (idxs.size() >= 2 && index(1).getIndexType().isHash()) : "Table has no hash index.";

        if (rebuildFromHashInProgressFiledUpdater.compareAndSet(this, value ? FALSE : TRUE, value ? TRUE : FALSE)) {
            lock.writeLock().lock();

            try {
                incrementModificationCounter();
            }
            finally {
                lock.writeLock().unlock();
            }
        }
    }

    /**
     *
     */
    public boolean rebuildFromHashInProgress() {
        return rebuildFromHashInProgress == TRUE;
    }

    /** {@inheritDoc} */
    @Override public Index addIndex(Session ses, String idxName, int idxId, IndexColumn[] cols, IndexType idxType,
        boolean create, String idxComment) {
        return commitUserIndex(ses, idxName);
    }

    /**
     * Checks index presence, return {@link Index} if index with same name or same fields and search direction already
     * exist or {@code null} othervise.
     *
     * @param curIdx Index to check.
     * @return Index if equal or subset index exist.
     * @throws IgniteCheckedException If failed.
     */
    private @Nullable Index checkIndexPresence(Index curIdx) throws IgniteCheckedException {
        IndexColumn[] curColumns = curIdx.getIndexColumns();

        Index registredIdx = null;

        for (Index idx : idxs) {
            if (Objects.equals(curIdx.getName(), idx.getName()))
                throw new IgniteCheckedException("Index already exists: " + idx.getName());

            if (!(curIdx instanceof H2TreeIndex) || !(idx instanceof H2TreeIndex))
                continue;

            IndexColumn[] idxColumns = idx.getIndexColumns();

            for (int i = 0; i < Math.min(idxColumns.length, curColumns.length); ++i) {
                IndexColumn idxCol = idxColumns[i];
                IndexColumn curCol = curColumns[i];

                // pk attach at the end of listed fields.
                if (curCol.column.getColumnId() == 0 && registredIdx != null)
                    continue;

                if (H2Utils.equals(idxCol, curCol) && idxCol.sortType == curCol.sortType)
                    registredIdx = idx;
                else {
                    registredIdx = null;

                    break;
                }
            }

            if (registredIdx != null)
                return registredIdx;
        }

        return null;
    }

    /**
     * Add index that is in an intermediate state and is still being built, thus is not used in queries until it is
     * promoted.
     *
     * @param idx Index to add.
     * @throws IgniteCheckedException If failed.
     */
    public void proposeUserIndex(Index idx) throws IgniteCheckedException {
        assert idx instanceof H2IndexCostedBase;

        lock(true);

        try {
            ensureNotDestroyed();

            Index idxExist = checkIndexPresence(idx);

            if (idxExist != null) {
                String idxCols = Stream.of(idxExist.getIndexColumns())
                    .map(k -> k.columnName).collect(Collectors.joining(", "));

                U.warn(log, "Index with the given set or subset of columns already exists " +
                    "(consider dropping either new or existing index) [cacheName=" + cacheInfo.name() + ", " +
                    "schemaName=" + getSchema().getName() + ", tableName=" + getName() +
                    ", newIndexName=" + idx.getName() + ", existingIndexName=" + idxExist.getName() +
                    ", existingIndexColumns=[" + idxCols + "]]");
            }

            Index oldTmpIdx = tmpIdxs.put(idx.getName(), (H2IndexCostedBase)idx);

            assert oldTmpIdx == null;
        }
        finally {
            unlock(true);
        }
    }

    /**
     * Promote temporary index to make it usable in queries.
     *
     * @param ses H2 session.
     * @param idxName Index name.
     * @return Temporary index with given name.
     */
    private Index commitUserIndex(Session ses, String idxName) {
        lock(true);

        try {
            ensureNotDestroyed();

            Index idx = tmpIdxs.remove(idxName);

            assert idx != null;

            ArrayList<Index> newIdxs = new ArrayList<>(idxs.size() + 1);

            newIdxs.addAll(idxs);
            newIdxs.add(idx);

            idxs = newIdxs;

            database.addSchemaObject(ses, idx);

            incrementModificationCounter();

            return idx;
        }
        finally {
            unlock(true);
        }
    }

    /**
     * Remove user index without promoting it.
     *
     * @param idxName Index name.
     */
    public void rollbackUserIndex(String idxName) {
        lock(true);

        try {
            ensureNotDestroyed();

            H2IndexCostedBase rmvIdx = tmpIdxs.remove(idxName);

            assert rmvIdx != null;
        }
        finally {
            unlock(true);
        }
    }

    /** {@inheritDoc} */
    @Override public void removeIndex(Index h2Idx) {
        lock(true);

        try {
            ensureNotDestroyed();

            ArrayList<Index> idxs = new ArrayList<>(this.idxs);

            for (int i = pkIdxPos; i < idxs.size();) {
                Index idx = idxs.get(i);

                if (idx == h2Idx) {
                    idxs.remove(i);

                    if (idx instanceof GridH2IndexBase)
                        destroyIndex(idx);

                    continue;
                }

                i++;
            }

            this.idxs = idxs;
        }
        finally {
            unlock(true);
        }
    }

    /** {@inheritDoc} */
    @Override public void removeRow(Session ses, Row row) {
        throw DbException.getUnsupportedException("removeRow");
    }

    /** {@inheritDoc} */
    @Override public void truncate(Session ses) {
        throw DbException.getUnsupportedException("truncate");
    }

    /** {@inheritDoc} */
    @Override public void addRow(Session ses, Row row) {
        throw DbException.getUnsupportedException("addRow");
    }

    /** {@inheritDoc} */
    @Override public void checkSupportAlter() {
        throw DbException.getUnsupportedException("alter");
    }

    /** {@inheritDoc} */
    @Override public TableType getTableType() {
        return TableType.TABLE;
    }

    /** {@inheritDoc} */
    @Override public Index getScanIndex(Session ses) {
        return getIndexes().get(0); // Scan must be always first index.
    }

    /** {@inheritDoc} */
    @Override public Index getUniqueIndex() {
        if (rebuildFromHashInProgress == TRUE)
            return index(1);
        else
            return index(2);
    }

    /** {@inheritDoc} */
    @Override public ArrayList<Index> getIndexes() {
        if (rebuildFromHashInProgress == FALSE)
            return idxs;

        ArrayList<Index> idxs = new ArrayList<>(2);

        idxs.add(this.idxs.get(0));
        idxs.add(this.idxs.get(1));

        return idxs;
    }

    /** {@inheritDoc} */
    @Override public boolean isLockedExclusively() {
        return false;
    }

    /** {@inheritDoc} */
    @Override public boolean isLockedExclusivelyBy(Session ses) {
        return false;
    }

    /** {@inheritDoc} */
    @Override public long getMaxDataModificationId() {
        return 0;
    }

    /** {@inheritDoc} */
    @Override public boolean isDeterministic() {
        return true;
    }

    /** {@inheritDoc} */
    @Override public boolean canGetRowCount() {
        return true;
    }

    /** {@inheritDoc} */
    @Override public boolean canDrop() {
        return true;
    }

    /** {@inheritDoc} */
    @Override public long getRowCount(@Nullable Session ses) {
        return getUniqueIndex().getRowCount(ses);
    }

    /** {@inheritDoc} */
    @Override public long getRowCountApproximation() {
        if (!localQuery(QueryContext.threadLocal()))
            return 10_000; // Fallback to the previous behaviour.

        refreshStatsIfNeeded();

        return tblStats.localRowCount();
    }

    /**
     * @param qctx Context.
     *
     * @return {@code True} if the current query is a local query.
     */
    private boolean localQuery(QueryContext qctx) {
        assert qctx != null;

        return qctx.local();
    }

    /**
     * Refreshes table stats if they are outdated.
     */
    private void refreshStatsIfNeeded() {
        TableStatistics stats = tblStats;

        long statsTotalRowCnt = stats.totalRowCount();
        long curTotalRowCnt = size.sum();

        // Update stats if total table size changed significantly since the last stats update.
        if (needRefreshStats(statsTotalRowCnt, curTotalRowCnt) && cacheInfo.affinityNode()) {
            CacheConfiguration ccfg = cacheContext().config();

            int backups = ccfg.getCacheMode() == CacheMode.REPLICATED ? 0 : cacheContext().config().getBackups();

            // After restart of node with persistence and before affinity exchange - PRIMARY partitions are empty.
            // Try to predict local row count take into account ideal distribution.
            long locOwnerRowCnt = cacheSize(CachePeekMode.PRIMARY, CachePeekMode.BACKUP) / (backups + 1);

            int owners = cacheContext().discovery().cacheNodes(cacheContext().name(), NONE).size();

            long totalRowCnt = owners * locOwnerRowCnt;

            size.reset();
            size.add(totalRowCnt);

            tblStats = new TableStatistics(totalRowCnt, locOwnerRowCnt);
        }
    }

    /**
     * @param statsRowCnt Row count from statistics.
     * @param actualRowCnt Actual row count.
     * @return {@code True} if actual table size has changed more than the threshold since last stats update.
     */
    private static boolean needRefreshStats(long statsRowCnt, long actualRowCnt) {
        double delta = U.safeAbs(statsRowCnt - actualRowCnt);

        double relativeChange = delta / (statsRowCnt + 1); // Add 1 to avoid division by zero.

        // Return true if an actual table size has changed more than the threshold since the last stats update.
        return relativeChange > STATS_UPDATE_THRESHOLD;
    }

    /**
     * Retrieves partitions size.
     *
     * @return Rows count.
     */
    private long cacheSize(CachePeekMode... modes) {
        try {
            return cacheInfo.cacheContext().cache().localSizeLong(modes);
        }
        catch (IgniteCheckedException e) {
            throw new IgniteException(e);
        }
    }

    /** {@inheritDoc} */
    @Override public void checkRename() {
        throw DbException.getUnsupportedException("rename");
    }

    /**
     * Creates index column for table.
     *
     * @param col Column index.
     * @param sorting Sorting order {@link SortOrder}
     * @return Created index column.
     */
    public IndexColumn indexColumn(int col, int sorting) {
        IndexColumn res = new IndexColumn();

        res.column = getColumn(col);
        res.columnName = res.column.getName();
        res.sortType = sorting;

        return res;
    }

    /**
     * Add new columns to this table.
     *
     * @param cols Columns to add.
     */
    public void addColumns(List<QueryField> cols) {
        lock(true);

        try {
            Column[] safeColumns0 = safeColumns;

            int pos = safeColumns0.length;

            Column[] newCols = new Column[safeColumns0.length + cols.size()];

            // First, let's copy existing columns to new array
            System.arraycopy(safeColumns0, 0, newCols, 0, safeColumns0.length);

            // And now, let's add new columns
            for (QueryField col : cols) {
                if (doesColumnExist(col.name()))
                    return;

                try {
                    Column c = new Column(col.name(), DataType.getTypeFromClass(Class.forName(col.typeName())));

                    c.setNullable(col.isNullable());

                    newCols[pos++] = c;
                }
                catch (ClassNotFoundException e) {
                    throw new IgniteSQLException("H2 data type not found for class: " + col.typeName(), e);
                }
            }

            setColumns(newCols);

            desc.onMetadataUpdated();

            incrementModificationCounter();
        }
        finally {
            unlock(true);
        }
    }

    /**
     * Drop columns.
     *
     * @param cols Columns.
     */
    @SuppressWarnings("ForLoopReplaceableByForEach")
    public void dropColumns(List<String> cols) {
        lock(true);

        try {
            Column[] safeColumns0 = safeColumns;

            int size = safeColumns0.length;

            for (String name : cols) {
                if (!doesColumnExist(name))
                    return;

                size--;
            }

            assert size > QueryUtils.DEFAULT_COLUMNS_COUNT;

            Column[] newCols = new Column[size];

            int dst = 0;

            for (int i = 0; i < safeColumns0.length; i++) {
                Column column = safeColumns0[i];

                for (String name : cols) {
                    if (Objects.equals(name, column.getName())) {
                        column = null;

                        break;
                    }
                }

                if (column != null)
                    newCols[dst++] = column;
            }

            setColumns(newCols);

            desc.onMetadataUpdated();

            for (Index idx : getIndexes()) {
                if (idx instanceof GridH2IndexBase)
                    ((GridH2IndexBase)idx).refreshColumnIds();
            }

            incrementModificationCounter();
        }
        finally {
            unlock(true);
        }
    }

    /** {@inheritDoc} */
    @Override protected void setColumns(Column[] columns) {
        this.safeColumns = columns;

        super.setColumns(columns);
    }

    /** {@inheritDoc} */
    @Override public Column[] getColumns() {
        Column[] safeColumns0 = safeColumns;

        Boolean insertHack = INSERT_HACK.get();

        if (insertHack != null && insertHack) {
            StackTraceElement[] elems = Thread.currentThread().getStackTrace();

            StackTraceElement elem = elems[2];

            if (Objects.equals(elem.getClassName(), Insert.class.getName()) && Objects.equals(elem.getMethodName(), "prepare")) {
                Column[] columns0 = new Column[safeColumns0.length - QueryUtils.DEFAULT_COLUMNS_COUNT];

                System.arraycopy(safeColumns0, QueryUtils.DEFAULT_COLUMNS_COUNT, columns0, 0, columns0.length);

                return columns0;
            }
        }

        return safeColumns0;
    }

    /**
     * Increment modification counter to force recompilation of existing prepared statements.
     */
    private void incrementModificationCounter() {
        assert lock.isWriteLockedByCurrentThread();

        setModified();
    }

    /**
     * Set insert hack flag.
     *
     * @param val Value.
     */
    public static void insertHack(boolean val) {
        INSERT_HACK.set(val);
    }

    /**
     * Check whether insert hack is required. This is true in case statement contains "INSERT INTO ... VALUES".
     *
     * @param sql SQL statement.
     * @return {@code True} if target combination is found.
     */
    @SuppressWarnings("RedundantIfStatement")
    public static boolean insertHackRequired(String sql) {
        if (F.isEmpty(sql))
            return false;

        sql = sql.toLowerCase();

        int idxInsert = sql.indexOf("insert");

        if (idxInsert < 0)
            return false;

        int idxInto = sql.indexOf("into", idxInsert);

        if (idxInto < 0)
            return false;

        return true;
    }

    /**
     * @param s H2 session.
     */
    public static void unlockTables(Session s) {
        for (Table t : s.getLocks()) {
            if (t instanceof GridH2Table)
                ((GridH2Table)t).unlockReadInternal(s);
        }
    }

    /**
     * @param s H2 session.
     */
    public static void readLockTables(Session s) {
        for (Table t : s.getLocks()) {
            if (t instanceof GridH2Table)
                ((GridH2Table)t).readLockInternal(s);
        }
    }

    /**
     * @param s H2 session.
     */
    public static void checkTablesVersions(Session s) {
        for (Table t : s.getLocks()) {
            if (t instanceof GridH2Table)
                ((GridH2Table)t).checkVersion(s);
        }
    }

    /**
     *
     */
    private static class SessionLock {
        /** Version. */
        final long ver;

        /** Locked by current thread flag. */
        boolean locked;

        /**
         * Constructor for shared lock.
         *
         * @param ver Table version.
         */
        private SessionLock(long ver) {
            this.ver = ver;
            locked = true;
        }

        /**
         * @param ver Locked table version.
         * @return Shared lock instance.
         */
        static SessionLock sharedLock(long ver) {
            return new SessionLock(ver);
        }

        /**
         * @return Exclusive lock instance.
         */
        static SessionLock exclusiveLock() {
            return new SessionLock(EXCLUSIVE_LOCK);
        }

        /**
         * @return {@code true} if exclusive lock.
         */
        boolean isExclusive() {
            return ver == EXCLUSIVE_LOCK;
        }

        /**
         * @return Table version of the first lock.
         */
        long version() {
            return ver;
        }
    }
}
