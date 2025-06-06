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

package org.apache.ignite.internal.processors.query.h2;

import java.sql.BatchUpdateException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.cache.CacheException;
import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.IgniteDataStreamer;
import org.apache.ignite.IgniteException;
import org.apache.ignite.IgniteLogger;
import org.apache.ignite.IgniteSystemProperties;
import org.apache.ignite.cache.query.FieldsQueryCursor;
import org.apache.ignite.cache.query.QueryCancelledException;
import org.apache.ignite.cache.query.SqlFieldsQuery;
import org.apache.ignite.cache.query.SqlQuery;
import org.apache.ignite.cluster.ClusterNode;
import org.apache.ignite.events.DiscoveryEvent;
import org.apache.ignite.events.EventType;
import org.apache.ignite.events.SqlQueryExecutionEvent;
import org.apache.ignite.failure.FailureContext;
import org.apache.ignite.failure.FailureType;
import org.apache.ignite.indexing.IndexingQueryEngineConfiguration;
import org.apache.ignite.internal.GridKernalContext;
import org.apache.ignite.internal.GridTopic;
import org.apache.ignite.internal.IgniteInternalFuture;
import org.apache.ignite.internal.binary.BinaryMarshaller;
import org.apache.ignite.internal.binary.BinaryUtils;
import org.apache.ignite.internal.managers.communication.GridMessageListener;
import org.apache.ignite.internal.managers.eventstorage.GridLocalEventListener;
import org.apache.ignite.internal.processors.affinity.AffinityTopologyVersion;
import org.apache.ignite.internal.processors.cache.CacheObjectValueContext;
import org.apache.ignite.internal.processors.cache.CacheOperationContext;
import org.apache.ignite.internal.processors.cache.DynamicCacheDescriptor;
import org.apache.ignite.internal.processors.cache.GridCacheContext;
import org.apache.ignite.internal.processors.cache.GridCacheContextInfo;
import org.apache.ignite.internal.processors.cache.QueryCursorImpl;
import org.apache.ignite.internal.processors.cache.distributed.dht.IgniteClusterReadOnlyException;
import org.apache.ignite.internal.processors.cache.distributed.dht.preloader.GridDhtPartitionsExchangeFuture;
import org.apache.ignite.internal.processors.cache.distributed.dht.topology.PartitionReservationManager;
import org.apache.ignite.internal.processors.cache.persistence.CacheDataRow;
import org.apache.ignite.internal.processors.cache.query.GridCacheQueryMarshallable;
import org.apache.ignite.internal.processors.cache.query.GridCacheQueryType;
import org.apache.ignite.internal.processors.cache.query.IgniteQueryErrorCode;
import org.apache.ignite.internal.processors.cache.tree.CacheDataTree;
import org.apache.ignite.internal.processors.odbc.jdbc.JdbcParameterMeta;
import org.apache.ignite.internal.processors.query.GridQueryCacheObjectsIterator;
import org.apache.ignite.internal.processors.query.GridQueryCancel;
import org.apache.ignite.internal.processors.query.GridQueryFieldMetadata;
import org.apache.ignite.internal.processors.query.GridQueryFieldsResult;
import org.apache.ignite.internal.processors.query.GridQueryFieldsResultAdapter;
import org.apache.ignite.internal.processors.query.GridQueryFinishedInfo;
import org.apache.ignite.internal.processors.query.GridQueryIndexing;
import org.apache.ignite.internal.processors.query.GridQueryStartedInfo;
import org.apache.ignite.internal.processors.query.GridQueryTypeDescriptor;
import org.apache.ignite.internal.processors.query.IgniteSQLException;
import org.apache.ignite.internal.processors.query.QueryField;
import org.apache.ignite.internal.processors.query.QueryUtils;
import org.apache.ignite.internal.processors.query.SqlClientContext;
import org.apache.ignite.internal.processors.query.h2.affinity.H2PartitionResolver;
import org.apache.ignite.internal.processors.query.h2.affinity.PartitionExtractor;
import org.apache.ignite.internal.processors.query.h2.dml.DmlDistributedPlanInfo;
import org.apache.ignite.internal.processors.query.h2.dml.DmlUtils;
import org.apache.ignite.internal.processors.query.h2.dml.UpdateMode;
import org.apache.ignite.internal.processors.query.h2.dml.UpdatePlan;
import org.apache.ignite.internal.processors.query.h2.opt.GridH2Table;
import org.apache.ignite.internal.processors.query.h2.opt.QueryContext;
import org.apache.ignite.internal.processors.query.h2.opt.QueryContextRegistry;
import org.apache.ignite.internal.processors.query.h2.sql.GridSqlStatement;
import org.apache.ignite.internal.processors.query.h2.twostep.GridMapQueryExecutor;
import org.apache.ignite.internal.processors.query.h2.twostep.GridReduceQueryExecutor;
import org.apache.ignite.internal.processors.query.h2.twostep.messages.GridQueryCancelRequest;
import org.apache.ignite.internal.processors.query.h2.twostep.messages.GridQueryFailResponse;
import org.apache.ignite.internal.processors.query.h2.twostep.messages.GridQueryNextPageRequest;
import org.apache.ignite.internal.processors.query.h2.twostep.messages.GridQueryNextPageResponse;
import org.apache.ignite.internal.processors.query.h2.twostep.msg.GridH2DmlRequest;
import org.apache.ignite.internal.processors.query.h2.twostep.msg.GridH2DmlResponse;
import org.apache.ignite.internal.processors.query.h2.twostep.msg.GridH2QueryRequest;
import org.apache.ignite.internal.processors.query.running.HeavyQueriesTracker;
import org.apache.ignite.internal.processors.query.running.RunningQueryManager;
import org.apache.ignite.internal.processors.query.schema.AbstractSchemaChangeListener;
import org.apache.ignite.internal.processors.tracing.MTC;
import org.apache.ignite.internal.processors.tracing.MTC.TraceSurroundings;
import org.apache.ignite.internal.processors.tracing.Span;
import org.apache.ignite.internal.sql.SqlParseException;
import org.apache.ignite.internal.sql.command.SqlCommand;
import org.apache.ignite.internal.sql.optimizer.affinity.PartitionResult;
import org.apache.ignite.internal.util.GridEmptyCloseableIterator;
import org.apache.ignite.internal.util.GridSpinBusyLock;
import org.apache.ignite.internal.util.lang.GridCloseableIterator;
import org.apache.ignite.internal.util.lang.GridPlainRunnable;
import org.apache.ignite.internal.util.lang.IgniteInClosure2X;
import org.apache.ignite.internal.util.lang.IgniteSingletonIterator;
import org.apache.ignite.internal.util.lang.IgniteThrowableSupplier;
import org.apache.ignite.internal.util.typedef.F;
import org.apache.ignite.internal.util.typedef.X;
import org.apache.ignite.internal.util.typedef.internal.S;
import org.apache.ignite.internal.util.typedef.internal.U;
import org.apache.ignite.lang.IgniteBiClosure;
import org.apache.ignite.lang.IgniteBiTuple;
import org.apache.ignite.lang.IgniteFuture;
import org.apache.ignite.lang.IgniteInClosure;
import org.apache.ignite.plugin.extensions.communication.Message;
import org.apache.ignite.plugin.security.SecurityPermission;
import org.apache.ignite.resources.LoggerResource;
import org.apache.ignite.spi.indexing.IndexingQueryFilter;
import org.apache.ignite.spi.indexing.IndexingQueryFilterImpl;
import org.h2.api.ErrorCode;
import org.h2.api.JavaObjectSerializer;
import org.h2.engine.Session;
import org.h2.engine.SysProperties;
import org.h2.util.JdbcUtils;
import org.h2.value.CompareMode;
import org.jetbrains.annotations.Nullable;

import static java.util.Collections.singletonList;
import static org.apache.ignite.events.EventType.EVT_SQL_QUERY_EXECUTION;
import static org.apache.ignite.internal.processors.cache.query.GridCacheQueryType.TEXT;
import static org.apache.ignite.internal.processors.query.h2.H2Utils.UPDATE_RESULT_META;
import static org.apache.ignite.internal.processors.query.h2.H2Utils.generateFieldsQueryString;
import static org.apache.ignite.internal.processors.query.h2.H2Utils.session;
import static org.apache.ignite.internal.processors.query.h2.H2Utils.sqlWithoutConst;
import static org.apache.ignite.internal.processors.query.h2.H2Utils.zeroCursor;
import static org.apache.ignite.internal.processors.tracing.SpanTags.ERROR;
import static org.apache.ignite.internal.processors.tracing.SpanTags.SQL_QRY_TEXT;
import static org.apache.ignite.internal.processors.tracing.SpanTags.SQL_SCHEMA;
import static org.apache.ignite.internal.processors.tracing.SpanType.SQL_CMD_QRY_EXECUTE;
import static org.apache.ignite.internal.processors.tracing.SpanType.SQL_CURSOR_OPEN;
import static org.apache.ignite.internal.processors.tracing.SpanType.SQL_DML_QRY_EXECUTE;
import static org.apache.ignite.internal.processors.tracing.SpanType.SQL_ITER_OPEN;
import static org.apache.ignite.internal.processors.tracing.SpanType.SQL_QRY;
import static org.apache.ignite.internal.processors.tracing.SpanType.SQL_QRY_EXECUTE;

/**
 * Indexing implementation based on H2 database engine. In this implementation main query language is SQL,
 * fulltext indexing can be performed using Lucene.
 * <p>
 * For each registered {@link GridQueryTypeDescriptor} this SPI will create respective SQL table with
 * {@code '_key'} and {@code '_val'} fields for key and value, and fields from
 * {@link GridQueryTypeDescriptor#fields()}.
 * For each table it will create indexes declared in {@link GridQueryTypeDescriptor#indexes()}.
 */
public class IgniteH2Indexing implements GridQueryIndexing {
    /** Default number of attempts to re-run DELETE and UPDATE queries in case of concurrent modifications of values. */
    private static final int DFLT_UPDATE_RERUN_ATTEMPTS = 4;

    /** Cached value of {@code IgniteSystemProperties.IGNITE_ALLOW_DML_INSIDE_TRANSACTION}. */
    private final boolean updateInTxAllowed =
        Boolean.getBoolean(IgniteSystemProperties.IGNITE_ALLOW_DML_INSIDE_TRANSACTION);

    static {
        // Required to skip checks of forbidden H2 settings, otherwise Ignite fails to start.
        //
        // Note, H2 system properties must be overriden here, because the properties are finalized while the class
        // org.h2.engine.SysProperties is loaded in the IgniteH2Indexing.start(...) method.
        //
        // @see ConnectionManager#forbidH2DbSettings(String...)
        System.setProperty("h2.check", "false");
    }

    /** Logger. */
    @LoggerResource
    private IgniteLogger log;

    /** Node ID. */
    private UUID nodeId;

    /** */
    private BinaryMarshaller marshaller;

    /** */
    private GridMapQueryExecutor mapQryExec;

    /** */
    private GridReduceQueryExecutor rdcQryExec;

    /** */
    private GridSpinBusyLock busyLock;

    /** */
    protected volatile GridKernalContext ctx;

    /** Query context registry. */
    private final QueryContextRegistry qryCtxRegistry = new QueryContextRegistry();

    /** Processor to execute commands which are neither SELECT, nor DML. */
    private CommandProcessor cmdProc;

    /** Partition reservation manager. */
    private PartitionReservationManager partReservationMgr;

    /** Partition extractor. */
    private PartitionExtractor partExtractor;

    /** Parser. */
    private QueryParser parser;

    /** */
    private final IgniteInClosure<? super IgniteInternalFuture<?>> logger = new IgniteInClosure<IgniteInternalFuture<?>>() {
        @Override public void apply(IgniteInternalFuture<?> fut) {
            try {
                fut.get();
            }
            catch (IgniteCheckedException e) {
                U.error(log, e.getMessage(), e);
            }
        }
    };

    /** Query executor. */
    private ConnectionManager connMgr;

    /** Schema manager. */
    private H2SchemaManager schemaMgr;

    /** Heavy queries tracker. */
    private HeavyQueriesTracker heavyQryTracker;

    /** Discovery event listener. */
    private GridLocalEventListener discoLsnr;

    /** Query message listener. */
    private GridMessageListener qryLsnr;

    /** Distributed config. */
    private DistributedIndexingConfiguration distrCfg;

    /** Functions manager. */
    private FunctionsManager funcMgr;

    /**
     * @return Kernal context.
     */
    public GridKernalContext kernalContext() {
        return ctx;
    }

    /** {@inheritDoc} */
    @Override public List<JdbcParameterMeta> parameterMetaData(String schemaName, SqlFieldsQuery qry)
        throws IgniteSQLException {
        assert qry != null;

        ArrayList<JdbcParameterMeta> metas = new ArrayList<>();

        SqlFieldsQuery curQry = qry;

        while (curQry != null) {
            QueryParserResult parsed = parser.parse(schemaName, curQry, true);

            metas.addAll(parsed.parametersMeta());

            curQry = parsed.remainingQuery();
        }

        return metas;
    }

    /** {@inheritDoc} */
    @Override public List<GridQueryFieldMetadata> resultMetaData(String schemaName, SqlFieldsQuery qry)
        throws IgniteSQLException {
        QueryParserResult parsed = parser.parse(schemaName, qry, true);

        if (parsed.remainingQuery() != null)
            return null;

        if (parsed.isSelect())
            return parsed.select().meta();

        return null;
    }

    /** {@inheritDoc} */
    @Override public void store(GridCacheContext cctx,
        GridQueryTypeDescriptor type,
        CacheDataRow row,
        @Nullable CacheDataRow prevRow,
        boolean prevRowAvailable
    ) throws IgniteCheckedException {
        GridH2Table tbl = schemaMgr.dataTable(type.schemaName(), type.tableName());

        if (tbl == null)
            return; // Type was rejected.

        if (tbl.tableDescriptor().luceneIndex() != null) {
            long expireTime = row.expireTime();

            if (expireTime == 0L)
                expireTime = Long.MAX_VALUE;

            tbl.tableDescriptor().luceneIndex().store(row.key(), row.value(), row.version(), expireTime);
        }

        tbl.update(row, prevRow);
    }

    /** {@inheritDoc} */
    @Override public void remove(GridCacheContext cctx, GridQueryTypeDescriptor type, CacheDataRow row)
        throws IgniteCheckedException {
        if (log.isDebugEnabled()) {
            log.debug("Removing key from cache query index [locId=" + nodeId +
                ", key=" + row.key() +
                ", val=" + row.value() + ']');
        }

        GridH2Table tbl = schemaMgr.dataTable(type.schemaName(), type.tableName());

        if (tbl == null)
            return;

        if (tbl.tableDescriptor().luceneIndex() != null)
            tbl.tableDescriptor().luceneIndex().remove(row.key());

        tbl.remove(row);
    }

    /** {@inheritDoc} */
    @Override public <K, V> GridCloseableIterator<IgniteBiTuple<K, V>> queryLocalText(String schemaName,
        String cacheName, String qry, String typeName, IndexingQueryFilter filters, int limit) throws IgniteCheckedException {
        H2TableDescriptor tbl = schemaMgr.tableForType(schemaName, cacheName, typeName);

        if (tbl != null && tbl.luceneIndex() != null) {
            long qryId = runningQueryManager().register(
                qry,
                TEXT,
                schemaName,
                true,
                null,
                null,
                false,
                false,
                false
            );

            Throwable failReason = null;
            try {
                return tbl.luceneIndex().query(qry, filters, limit);
            }
            catch (Throwable t) {
                failReason = t;

                throw t;
            }
            finally {
                runningQueryManager().unregister(qryId, failReason);
            }
        }

        return new GridEmptyCloseableIterator<>();
    }

    /**
     * Queries individual fields (generally used by JDBC drivers).
     *
     * @param qryId Query id.
     * @param qryDesc Query descriptor.
     * @param qryParams Query parameters.
     * @param select Select.
     * @param filter Cache name and key filter.
     * @param cancel Query cancel.
     * @param timeout Timeout.
     * @return Query result.
     */
    private GridQueryFieldsResult executeSelectLocal(
        long qryId,
        QueryDescriptor qryDesc,
        QueryParameters qryParams,
        QueryParserResultSelect select,
        final IndexingQueryFilter filter,
        GridQueryCancel cancel,
        int timeout
    ) {
        String qry = qryDesc.sql();

        assert select != null;

        if (ctx.security().enabled())
            checkSecurity(select.cacheIds());

        final QueryContext qctx = new QueryContext(
            0,
            filter,
            null,
            null,
            true
        );

        return new GridQueryFieldsResultAdapter(select.meta(), null) {
            @Override public GridCloseableIterator<List<?>> iterator() throws IgniteCheckedException {
                H2PooledConnection conn = connections().connection(qryDesc.schemaName());

                H2QueryInfo qryInfo = null;

                try (TraceSurroundings ignored = MTC.support(ctx.tracing().create(SQL_ITER_OPEN, MTC.span()))) {
                    H2Utils.setupConnection(conn, qctx,
                        qryDesc.distributedJoins(), qryDesc.enforceJoinOrder(), qryParams.lazy());

                    PreparedStatement stmt = conn.prepareStatement(qry, H2StatementCache.queryFlags(qryDesc));

                    // Convert parameters into BinaryObjects.
                    BinaryMarshaller m = ctx.marshaller();
                    byte[] paramsBytes = U.marshal(m, qryParams.arguments());
                    final ClassLoader ldr = U.resolveClassLoader(ctx.config());

                    Object[] params = BinaryUtils.rawArrayFromBinary(m.binaryMarshaller()
                        .unmarshal(paramsBytes, ldr));

                    H2Utils.bindParameters(stmt, F.asList(params));

                    qryInfo = new H2QueryInfo(H2QueryInfo.QueryType.LOCAL, stmt, qry,
                        ctx.localNodeId(), qryId);

                    heavyQryTracker.startTracking(qryInfo);

                    if (ctx.performanceStatistics().enabled()) {
                        ctx.performanceStatistics().queryProperty(
                            GridCacheQueryType.SQL_FIELDS,
                            qryInfo.nodeId(),
                            qryInfo.queryId(),
                            "Local plan",
                            qryInfo.plan()
                        );
                    }

                    ResultSet rs = executeWithResumableTimeTracking(
                        () -> executeSqlQueryWithTimer(
                            stmt,
                            conn,
                            qry,
                            timeout,
                            cancel,
                            qryParams.dataPageScanEnabled(),
                            null
                        ),
                        qryInfo
                    );

                    if (runningQueryManager().planHistoryTracker().enabled()) {
                        H2QueryInfo qryInfo0 = qryInfo;

                        ctx.pools().getSystemExecutorService().submit(() ->
                            runningQueryManager().planHistoryTracker().addPlan(
                                qryInfo0.plan(),
                                qryInfo0.sql(),
                                qryInfo0.schema(),
                                true,
                                IndexingQueryEngineConfiguration.ENGINE_NAME));
                    }

                    return new H2FieldsIterator(
                        rs,
                        conn,
                        qryParams.pageSize(),
                        log,
                        IgniteH2Indexing.this,
                        qryInfo,
                        ctx.tracing()
                    );
                }
                catch (IgniteCheckedException | RuntimeException | Error e) {
                    conn.close();

                    if (qryInfo != null)
                        heavyQryTracker.stopTracking(qryInfo, e);

                    throw e;
                }
            }
        };
    }

    /** {@inheritDoc} */
    @Override public long streamUpdateQuery(
        String schemaName,
        String qry,
        @Nullable Object[] params,
        IgniteDataStreamer<?, ?> streamer,
        String qryInitiatorId
    ) throws IgniteCheckedException {
        QueryParserResultDml dml = streamerParse(schemaName, qry);

        return streamQuery0(qry, schemaName, streamer, dml, params, qryInitiatorId);
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"ForLoopReplaceableByForEach", "ConstantConditions"})
    @Override public List<Long> streamBatchedUpdateQuery(
        String schemaName,
        String qry,
        List<Object[]> params,
        SqlClientContext cliCtx,
        String qryInitiatorId
    ) throws IgniteCheckedException {
        if (cliCtx == null || !cliCtx.isStream()) {
            U.warn(log, "Connection is not in streaming mode.");

            return zeroBatchedStreamedUpdateResult(params.size());
        }

        QueryParserResultDml dml = streamerParse(schemaName, qry);

        IgniteDataStreamer<?, ?> streamer = cliCtx.streamerForCache(dml.streamTable().cacheName());

        assert streamer != null;

        List<Long> ress = new ArrayList<>(params.size());

        for (int i = 0; i < params.size(); i++) {
            long res = streamQuery0(qry, schemaName, streamer, dml, params.get(i), qryInitiatorId);

            ress.add(res);
        }

        return ress;
    }

    /**
     * Perform given statement against given data streamer. Only rows based INSERT is supported.
     *
     * @param qry Query.
     * @param schemaName Schema name.
     * @param streamer Streamer to feed data to.
     * @param dml DML statement.
     * @param args Statement arguments.
     * @return Number of rows in given INSERT statement.
     * @throws IgniteCheckedException if failed.
     */
    @SuppressWarnings({"unchecked"})
    private long streamQuery0(String qry, String schemaName, IgniteDataStreamer streamer, QueryParserResultDml dml,
        final Object[] args, String qryInitiatorId) throws IgniteCheckedException {
        long qryId = runningQueryManager().register(
            QueryUtils.INCLUDE_SENSITIVE ? qry : sqlWithoutConst(dml.statement()),
            GridCacheQueryType.SQL_FIELDS,
            schemaName,
            true,
            null,
            qryInitiatorId,
            false,
            false,
            false
        );

        Exception failReason = null;

        try {
            UpdatePlan plan = dml.plan();

            Iterator<List<?>> iter = new GridQueryCacheObjectsIterator(updateQueryRows(qryId, schemaName, plan, args),
                objectContext(), true);

            if (!iter.hasNext())
                return 0;

            IgniteBiTuple<?, ?> t = plan.processRow(iter.next());

            if (!iter.hasNext()) {
                streamer.addData(t.getKey(), t.getValue());

                return 1;
            }
            else {
                Map<Object, Object> rows = new LinkedHashMap<>(plan.rowCount());

                rows.put(t.getKey(), t.getValue());

                while (iter.hasNext()) {
                    List<?> row = iter.next();

                    t = plan.processRow(row);

                    rows.put(t.getKey(), t.getValue());
                }

                streamer.addData(rows);

                return rows.size();
            }
        }
        catch (IgniteException | IgniteCheckedException e) {
            failReason = e;

            throw e;
        }
        finally {
            runningQueryManager().unregister(qryId, failReason);
        }
    }

    /**
     * Calculates rows for update query.
     *
     * @param qryId Query id.
     * @param schemaName Schema name.
     * @param plan Update plan.
     * @param args Statement arguments.
     * @return Rows for update.
     * @throws IgniteCheckedException If failed.
     */
    private Iterator<List<?>> updateQueryRows(long qryId, String schemaName, UpdatePlan plan, Object[] args)
        throws IgniteCheckedException {
        Object[] params = args != null ? args : X.EMPTY_OBJECT_ARRAY;

        if (!F.isEmpty(plan.selectQuery())) {
            SqlFieldsQuery selectQry = new SqlFieldsQuery(plan.selectQuery())
                .setArgs(params)
                .setLocal(true);

            QueryParserResult selectParseRes = parser.parse(schemaName, selectQry, false);

            GridQueryFieldsResult res = executeSelectLocal(
                qryId,
                selectParseRes.queryDescriptor(),
                selectParseRes.queryParameters(),
                selectParseRes.select(),
                null,
                null,
                0
            );

            return res.iterator();
        }
        else
            return plan.createRows(params).iterator();
    }

    /**
     * Parse statement for streamer.
     *
     * @param schemaName Schema name.
     * @param qry Query.
     * @return DML.
     */
    private QueryParserResultDml streamerParse(String schemaName, String qry) {
        QueryParserResult parseRes = parser.parse(schemaName, new SqlFieldsQuery(qry), false);

        QueryParserResultDml dml = parseRes.dml();

        if (dml == null || !dml.streamable()) {
            throw new IgniteSQLException("Streaming mode supports only INSERT commands without subqueries.",
                IgniteQueryErrorCode.UNSUPPORTED_OPERATION);
        }

        return dml;
    }

    /**
     * @param size Result size.
     * @return List of given size filled with 0Ls.
     */
    private static List<Long> zeroBatchedStreamedUpdateResult(int size) {
        Long[] res = new Long[size];

        Arrays.fill(res, 0L);

        return Arrays.asList(res);
    }

    /**
     * Executes sql query statement.
     *
     * @param conn Connection,.
     * @param stmt Statement.
     * @param timeoutMillis Query timeout.
     * @param cancel Query cancel.
     * @return Result.
     * @throws IgniteCheckedException If failed.
     */
    private ResultSet executeSqlQuery(final H2PooledConnection conn, final PreparedStatement stmt,
        int timeoutMillis, @Nullable GridQueryCancel cancel) throws IgniteCheckedException {
        if (cancel != null)
            cancel.add(() -> cancelStatement(stmt));

        Session ses = session(conn);

        if (timeoutMillis >= 0)
            ses.setQueryTimeout(timeoutMillis);
        else
            ses.setQueryTimeout((int)distrCfg.defaultQueryTimeout());

        try {
            return stmt.executeQuery();
        }
        catch (SQLException e) {
            // Throw special exception.
            if (e.getErrorCode() == ErrorCode.STATEMENT_WAS_CANCELED)
                throw new QueryCancelledException();

            if (e.getErrorCode() == ErrorCode.OUT_OF_MEMORY) {
                ctx.failure().process(new FailureContext(FailureType.CRITICAL_ERROR, e));
            }

            if (e.getCause() instanceof IgniteSQLException)
                throw (IgniteSQLException)e.getCause();

            throw new IgniteSQLException(e);
        }
    }

    /**
     * Cancel prepared statement.
     *
     * @param stmt Statement.
     */
    private static void cancelStatement(PreparedStatement stmt) {
        try {
            stmt.cancel();
        }
        catch (SQLException ignored) {
            // No-op.
        }
    }

    /**
     * Executes sql query and prints warning if query is too slow..
     *
     * @param conn Connection,
     * @param sql Sql query.
     * @param params Parameters.
     * @param timeoutMillis Query timeout.
     * @param cancel Query cancel.
     * @param dataPageScanEnabled If data page scan is enabled.
     * @return Result.
     * @throws IgniteCheckedException If failed.
     */
    public ResultSet executeSqlQueryWithTimer(
        H2PooledConnection conn,
        String sql,
        @Nullable Collection<Object> params,
        int timeoutMillis,
        @Nullable GridQueryCancel cancel,
        Boolean dataPageScanEnabled,
        final H2QueryInfo qryInfo
    ) throws IgniteCheckedException {
        PreparedStatement stmt = conn.prepareStatementNoCache(sql);

        H2Utils.bindParameters(stmt, params);

        return executeSqlQueryWithTimer(stmt,
            conn, sql, timeoutMillis, cancel, dataPageScanEnabled, qryInfo);
    }

    /**
     * @param dataPageScanEnabled If data page scan is enabled.
     */
    public void enableDataPageScan(Boolean dataPageScanEnabled) {
        // Data page scan is enabled by default for SQL.
        // TODO https://issues.apache.org/jira/browse/IGNITE-11998
        CacheDataTree.setDataPageScanEnabled(false);
    }

    /**
     * Executes sql query and prints warning if query is too slow.
     *
     * @param stmt Prepared statement for query.
     * @param conn Connection.
     * @param sql Sql query.
     * @param timeoutMillis Query timeout.
     * @param cancel Query cancel.
     * @param dataPageScanEnabled If data page scan is enabled.
     * @return Result.
     * @throws IgniteCheckedException If failed.
     */
    public ResultSet executeSqlQueryWithTimer(
        PreparedStatement stmt,
        H2PooledConnection conn,
        String sql,
        int timeoutMillis,
        @Nullable GridQueryCancel cancel,
        Boolean dataPageScanEnabled,
        final H2QueryInfo qryInfo
    ) throws IgniteCheckedException {
        if (qryInfo != null)
            heavyQryTracker.startTracking(qryInfo);

        enableDataPageScan(dataPageScanEnabled);

        Throwable err = null;
        try (
            TraceSurroundings ignored = MTC.support(ctx.tracing()
                .create(SQL_QRY_EXECUTE, MTC.span())
                .addTag(SQL_QRY_TEXT, () -> sql))
        ) {
            return executeSqlQuery(conn, stmt, timeoutMillis, cancel);
        }
        catch (Throwable e) {
            err = e;

            throw e;
        }
        finally {
            CacheDataTree.setDataPageScanEnabled(false);

            if (qryInfo != null)
                heavyQryTracker.stopTracking(qryInfo, err);
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings("deprecation")
    @Override public SqlFieldsQuery generateFieldsQuery(String cacheName, SqlQuery qry) {
        String schemaName = ctx.query().schemaManager().schemaName(cacheName);

        String type = qry.getType();

        H2TableDescriptor tblDesc = schemaMgr.tableForType(schemaName, cacheName, type);

        if (tblDesc == null)
            throw new IgniteSQLException("Failed to find SQL table for type: " + type,
                IgniteQueryErrorCode.TABLE_NOT_FOUND);

        String sql;

        try {
            sql = generateFieldsQueryString(qry.getSql(), qry.getAlias(), tblDesc);
        }
        catch (IgniteCheckedException e) {
            throw new IgniteException(e);
        }

        SqlFieldsQuery res = QueryUtils.withQueryTimeout(new SqlFieldsQuery(sql), qry.getTimeout(), TimeUnit.MILLISECONDS);
        res.setArgs(qry.getArgs());
        res.setDistributedJoins(qry.isDistributedJoins());
        res.setLocal(qry.isLocal());
        res.setPageSize(qry.getPageSize());
        res.setPartitions(qry.getPartitions());
        res.setReplicatedOnly(qry.isReplicatedOnly());
        res.setSchema(schemaName);
        res.setSql(sql);

        return res;
    }

    /**
     * Execute command.
     *
     * @param qryDesc Query descriptor.
     * @param qryParams Query parameters.
     * @param cliCtx CLient context.
     * @param cmd Command (native).
     * @return Result.
     */
    private FieldsQueryCursor<List<?>> executeCommand(
        QueryDescriptor qryDesc,
        QueryParameters qryParams,
        @Nullable SqlClientContext cliCtx,
        QueryParserResultCommand cmd
    ) {
        if (cmd.noOp())
            return zeroCursor();

        SqlCommand cmdNative = cmd.commandNative();
        GridSqlStatement cmdH2 = cmd.commandH2();

        if (qryDesc.local()) {
            throw new IgniteSQLException("DDL statements are not supported for execution on local nodes only",
                IgniteQueryErrorCode.UNSUPPORTED_OPERATION);
        }

        long qryId = registerRunningQuery(qryDesc, qryParams, null, null);

        CommandResult res = null;

        Exception failReason = null;

        try (TraceSurroundings ignored = MTC.support(ctx.tracing().create(SQL_CMD_QRY_EXECUTE, MTC.span()))) {
            res = cmdProc.runCommand(qryDesc.sql(), cmdNative, cmdH2, qryParams, cliCtx, qryId);

            return res.cursor();
        }
        catch (IgniteException e) {
            failReason = e;

            throw e;
        }
        catch (IgniteCheckedException e) {
            failReason = e;

            throw new IgniteSQLException("Failed to execute DDL statement [stmt=" + qryDesc.sql() +
                ", err=" + e.getMessage() + ']', e);
        }
        finally {
            if (res == null || res.unregisterRunningQuery())
                runningQueryManager().unregister(qryId, failReason);
        }
    }

    /**
     * Check cluster state.
     */
    private void checkClusterState() {
        if (!ctx.state().publicApiActiveState(true)) {
            throw new IgniteException("Can not perform the operation because the cluster is inactive. Note, " +
                "that the cluster is considered inactive by default if Ignite Persistent Store is used to " +
                "let all the nodes join the cluster. To activate the cluster call" +
                " Ignite.cluster().state(ClusterState.ACTIVE).");
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"StringEquality"})
    @Override public List<FieldsQueryCursor<List<?>>> querySqlFields(
        String schemaName,
        SqlFieldsQuery qry,
        @Nullable SqlClientContext cliCtx,
        boolean keepBinary,
        boolean failOnMultipleStmts,
        GridQueryCancel cancel
    ) {
        List<FieldsQueryCursor<List<?>>> res = new ArrayList<>(1);

        SqlFieldsQuery remainingQry = qry;

        while (remainingQry != null) {
            Span qrySpan = ctx.tracing().create(SQL_QRY, MTC.span())
                .addTag(SQL_SCHEMA, () -> schemaName);

            try (TraceSurroundings ignored = MTC.supportContinual(qrySpan)) {
                // Parse.
                QueryParserResult parseRes = parser.parse(schemaName, remainingQry, !failOnMultipleStmts);

                qrySpan.addTag(SQL_QRY_TEXT, () -> parseRes.queryDescriptor().sql());

                remainingQry = parseRes.remainingQuery();

                // Get next command.
                QueryDescriptor newQryDesc = parseRes.queryDescriptor();
                QueryParameters newQryParams = parseRes.queryParameters();

                // Check if there is enough parameters. Batched statements are not checked at this point
                // since they pass parameters differently.
                if (!newQryDesc.batched()) {
                    int qryParamsCnt = F.isEmpty(newQryParams.arguments()) ? 0 : newQryParams.arguments().length;

                    if (qryParamsCnt < parseRes.parametersCount())
                        throw new IgniteSQLException("Invalid number of query parameters [expected=" +
                            parseRes.parametersCount() + ", actual=" + qryParamsCnt + ']');
                }

                // Check if cluster state is valid.
                checkClusterState();

                // Execute.
                if (parseRes.isCommand()) {
                    QueryParserResultCommand cmd = parseRes.command();

                    assert cmd != null;

                    if (cmd.noOp() && remainingQry == null && newQryDesc.sql().isEmpty())
                        continue;

                    FieldsQueryCursor<List<?>> cmdRes = executeCommand(
                        newQryDesc,
                        newQryParams,
                        cliCtx,
                        cmd
                    );

                    res.add(cmdRes);
                }
                else if (parseRes.isDml()) {
                    QueryParserResultDml dml = parseRes.dml();

                    assert dml != null;

                    List<? extends FieldsQueryCursor<List<?>>> dmlRes = executeDml(
                        newQryDesc,
                        newQryParams,
                        dml,
                        cancel
                    );

                    res.addAll(dmlRes);
                }
                else {
                    assert parseRes.isSelect();

                    QueryParserResultSelect select = parseRes.select();

                    assert select != null;

                    List<? extends FieldsQueryCursor<List<?>>> qryRes = executeSelect(
                        newQryDesc,
                        newQryParams,
                        select,
                        keepBinary,
                        cancel
                    );

                    res.addAll(qryRes);
                }
            }
            catch (Throwable th) {
                qrySpan.addTag(ERROR, th::getMessage).end();

                throw th;
            }
        }

        if (res.isEmpty())
            throw new SqlParseException(qry.getSql(), 0, IgniteQueryErrorCode.PARSING, "Invalid SQL query.");

        return res;
    }

    /**
     * Execute an all-ready {@link SqlFieldsQuery}.
     *
     * @param qryDesc Plan key.
     * @param qryParams Parameters.
     * @param dml DML.
     * @param cancel Query cancel state holder.
     * @return Query result.
     */
    private List<? extends FieldsQueryCursor<List<?>>> executeDml(
        QueryDescriptor qryDesc,
        QueryParameters qryParams,
        QueryParserResultDml dml,
        GridQueryCancel cancel
    ) {
        IndexingQueryFilter filter = (qryDesc.local() ? backupFilter(null, qryParams.partitions()) : null);

        long qryId = registerRunningQuery(qryDesc, qryParams, cancel, dml.statement());

        Exception failReason = null;

        H2DmlInfo dmlInfo = null;

        try (TraceSurroundings ignored = MTC.support(ctx.tracing().create(SQL_DML_QRY_EXECUTE, MTC.span()))) {
            if (!updateInTxAllowed && ctx.cache().context().tm().inUserTx()) {
                throw new IgniteSQLException("DML statements are not allowed inside a transaction over " +
                    "cache(s) with TRANSACTIONAL atomicity mode (disable this error message with system property " +
                    "\"-DIGNITE_ALLOW_DML_INSIDE_TRANSACTION=true\")");
            }

            dmlInfo = new H2DmlInfo(
                U.currentTimeMillis(),
                qryId,
                ctx.localNodeId(),
                qryDesc.schemaName(),
                qryDesc.sql()
            );

            heavyQueriesTracker().startTracking(dmlInfo);

            if (!qryDesc.local()) {
                return executeUpdateDistributed(
                    qryId,
                    qryDesc,
                    qryParams,
                    dml,
                    cancel
                );
            }
            else {
                UpdateResult updRes = executeUpdate(
                    qryId,
                    qryDesc,
                    qryParams,
                    dml,
                    true,
                    filter,
                    cancel
                );

                return singletonList(new QueryCursorImpl<>(new Iterable<List<?>>() {
                    @Override public Iterator<List<?>> iterator() {
                        return new IgniteSingletonIterator<>(singletonList(updRes.counter()));
                    }
                }, cancel, true, false));
            }
        }
        catch (IgniteException e) {
            failReason = e;

            throw e;
        }
        catch (IgniteCheckedException e) {
            failReason = e;

            IgniteClusterReadOnlyException roEx = X.cause(e, IgniteClusterReadOnlyException.class);

            if (roEx != null) {
                throw new IgniteSQLException(
                    "Failed to execute DML statement. Cluster in read-only mode [stmt=" + qryDesc.sql() +
                    ", params=" + S.toString(QueryParameters.class, qryParams) + "]",
                    IgniteQueryErrorCode.CLUSTER_READ_ONLY_MODE_ENABLED,
                    e
                );
            }

            throw new IgniteSQLException("Failed to execute DML statement [stmt=" + qryDesc.sql() +
                    ", params=" + S.toString(QueryParameters.class, qryParams) + "]", e);
        }
        finally {
            if (dmlInfo != null)
                heavyQueriesTracker().stopTracking(dmlInfo, failReason);

            runningQueryManager().unregister(qryId, failReason);
        }
    }

    /**
     * Execute an all-ready {@link SqlFieldsQuery}.
     *
     * @param qryDesc Plan key.
     * @param qryParams Parameters.
     * @param select Select.
     * @param keepBinary Whether binary objects must not be deserialized automatically.
     * @param cancel Query cancel state holder.
     * @return Query result.
     */
    private List<? extends FieldsQueryCursor<List<?>>> executeSelect(
        QueryDescriptor qryDesc,
        QueryParameters qryParams,
        QueryParserResultSelect select,
        boolean keepBinary,
        GridQueryCancel cancel
    ) {
        assert cancel != null;

        // Register query.
        long qryId = registerRunningQuery(qryDesc, qryParams, cancel, select.statement());

        try (TraceSurroundings ignored = MTC.support(ctx.tracing().create(SQL_CURSOR_OPEN, MTC.span()))) {
            Iterable<List<?>> iter = executeSelect0(
                qryId,
                qryDesc,
                qryParams,
                select,
                keepBinary,
                cancel,
                qryParams.timeout());

            RegisteredQueryCursor<List<?>> cursor = new RegisteredQueryCursor<>(iter, cancel, runningQueryManager(),
                qryParams.lazy(), qryId, ctx.tracing());

            cancel.add(cursor::cancel);

            cursor.fieldsMeta(select.meta());

            cursor.partitionResult(select.twoStepQuery() != null ? select.twoStepQuery().derivedPartitions() : null);

            return singletonList(cursor);
        }
        catch (Exception e) {
            runningQueryManager().unregister(qryId, e);

            if (e instanceof IgniteCheckedException)
                throw U.convertException((IgniteCheckedException)e);

            if (e instanceof RuntimeException)
                throw (RuntimeException)e;

            throw new IgniteSQLException("Failed to execute SELECT statement: " + qryDesc.sql(), e);
        }
    }

    /**
     * Execute SELECT statement for DML.
     *
     * @param qryId Query id.
     * @param schema Schema.
     * @param selectQry Select query.
     * @param cancel Cancel.
     * @param timeout Timeout.
     * @return Fields query.
     */
    private QueryCursorImpl<List<?>> executeSelectForDml(
        long qryId,
        String schema,
        SqlFieldsQuery selectQry,
        GridQueryCancel cancel,
        int timeout
    ) {
        QueryParserResult parseRes = parser.parse(schema, selectQry, false);

        QueryParserResultSelect select = parseRes.select();

        assert select != null;

        Iterable<List<?>> iter = executeSelect0(
            qryId,
            parseRes.queryDescriptor(),
            parseRes.queryParameters(),
            select,
            true,
            cancel,
            timeout
        );

        QueryCursorImpl<List<?>> cursor = new QueryCursorImpl<>(iter, cancel, true, parseRes.queryParameters().lazy());

        cursor.fieldsMeta(select.meta());

        cursor.partitionResult(select.twoStepQuery() != null ? select.twoStepQuery().derivedPartitions() : null);

        return cursor;
    }

    /**
     * Execute an all-ready {@link SqlFieldsQuery}.
     *
     * @param qryId Query id.
     * @param qryDesc Plan key.
     * @param qryParams Parameters.
     * @param select Select.
     * @param keepBinary Whether binary objects must not be deserialized automatically.
     * @param cancel Query cancel state holder.
     * @param timeout Timeout.
     * @return Query result.
     */
    private Iterable<List<?>> executeSelect0(
        long qryId,
        QueryDescriptor qryDesc,
        QueryParameters qryParams,
        QueryParserResultSelect select,
        boolean keepBinary,
        GridQueryCancel cancel,
        int timeout
    ) {
        // Check security.
        if (ctx.security().enabled())
            checkSecurity(select.cacheIds());

        Iterable<List<?>> iter;

        if (select.splitNeeded()) {
            // Distributed query.
            GridCacheTwoStepQuery twoStepQry = select.twoStepQuery();

            assert twoStepQry != null;

            iter = executeSelectDistributed(
                qryId,
                qryDesc,
                qryParams,
                twoStepQry,
                keepBinary,
                cancel,
                timeout
            );
        }
        else {
            // Local query.
            IndexingQueryFilter filter = (qryDesc.local() ? backupFilter(null, qryParams.partitions()) : null);

            GridQueryFieldsResult res = executeSelectLocal(
                qryId,
                qryDesc,
                qryParams,
                select,
                filter,
                cancel,
                timeout
            );

            iter = () -> {
                try {
                    return new GridQueryCacheObjectsIterator(res.iterator(), objectContext(), keepBinary);
                }
                catch (IgniteCheckedException | IgniteSQLException e) {
                    throw new CacheException(e);
                }
            };
        }

        return iter;
    }

    /**
     * Register running query.
     *
     * @param qryDesc Query descriptor.
     * @param qryParams Query parameters.
     * @param cancel Query cancel state holder.
     * @param stmnt Parsed statement.
     * @return Id of registered query or {@code null} if query wasn't registered.
     */
    private long registerRunningQuery(
        QueryDescriptor qryDesc,
        QueryParameters qryParams,
        GridQueryCancel cancel,
        @Nullable GridSqlStatement stmnt
    ) {
        String qry = QueryUtils.INCLUDE_SENSITIVE || stmnt == null ? qryDesc.sql() : sqlWithoutConst(stmnt);

        long res = runningQueryManager().register(
            qry,
            GridCacheQueryType.SQL_FIELDS,
            qryDesc.schemaName(),
            qryDesc.local(),
            cancel,
            qryDesc.queryInitiatorId(),
            qryDesc.enforceJoinOrder(),
            qryParams.lazy(),
            qryDesc.distributedJoins()
        );

        if (ctx.event().isRecordable(EVT_SQL_QUERY_EXECUTION)) {
            ctx.event().record(new SqlQueryExecutionEvent(
                ctx.discovery().localNode(),
                GridCacheQueryType.SQL_FIELDS.name() + " query execution.",
                qry,
                qryParams.arguments(),
                ctx.security().enabled() ? ctx.security().securityContext().subject().id() : null));
        }

        return res;
    }

    /**
     * Check security access for caches.
     *
     * @param cacheIds Cache IDs.
     */
    private void checkSecurity(Collection<Integer> cacheIds) {
        if (F.isEmpty(cacheIds))
            return;

        for (Integer cacheId : cacheIds) {
            DynamicCacheDescriptor desc = ctx.cache().cacheDescriptor(cacheId);

            if (desc != null)
                ctx.security().authorize(desc.cacheName(), SecurityPermission.CACHE_READ);
        }
    }

    /**
     * @param lsnr Listener.
     */
    public void registerQueryStartedListener(Consumer<GridQueryStartedInfo> lsnr) {
        runningQueryManager().registerQueryStartedListener(lsnr);
    }

    /**
     * @param lsnr Listener.
     */
    public boolean unregisterQueryStartedListener(Object lsnr) {
        return runningQueryManager().unregisterQueryStartedListener(lsnr);
    }

    /**
     * @param lsnr Listener.
     */
    public void registerQueryFinishedListener(Consumer<GridQueryFinishedInfo> lsnr) {
        runningQueryManager().registerQueryFinishedListener(lsnr);
    }

    /**
     * @param lsnr Listener.
     */
    public boolean unregisterQueryFinishedListener(Object lsnr) {
        return runningQueryManager().unregisterQueryFinishedListener(lsnr);
    }

    /**
     * Run distributed query on detected set of partitions.
     *
     * @param qryId Query id.
     * @param qryDesc Query descriptor.
     * @param qryParams Query parameters.
     * @param twoStepQry Two-step query.
     * @param keepBinary Keep binary flag.
     * @param cancel Cancel handler.
     * @param timeout Timeout.
     * @return Cursor representing distributed query result.
     */
    @SuppressWarnings("IfMayBeConditional")
    private Iterable<List<?>> executeSelectDistributed(
        final long qryId,
        final QueryDescriptor qryDesc,
        final QueryParameters qryParams,
        final GridCacheTwoStepQuery twoStepQry,
        final boolean keepBinary,
        final GridQueryCancel cancel,
        int timeout
    ) {
        // When explicit partitions are set, there must be an owning cache they should be applied to.
        PartitionResult derivedParts = twoStepQry.derivedPartitions();

        final int[] parts = PartitionResult.calculatePartitions(
            qryParams.partitions(),
            derivedParts,
            qryParams.arguments()
        );

        Iterable<List<?>> iter;

        if (parts != null && parts.length == 0) {
            iter = new Iterable<List<?>>() {
                @Override public Iterator<List<?>> iterator() {
                    return new Iterator<List<?>>() {
                        @Override public boolean hasNext() {
                            return false;
                        }

                        @SuppressWarnings("IteratorNextCanNotThrowNoSuchElementException")
                        @Override public List<?> next() {
                            return null;
                        }
                    };
                }
            };
        }
        else {
            iter = new Iterable<List<?>>() {
                @Override public Iterator<List<?>> iterator() {
                    try (TraceSurroundings ignored = MTC.support(ctx.tracing().create(SQL_ITER_OPEN, MTC.span()))) {
                        return IgniteH2Indexing.this.rdcQryExec.query(
                            qryId,
                            qryDesc.schemaName(),
                            twoStepQry,
                            keepBinary,
                            qryDesc.enforceJoinOrder(),
                            timeout,
                            cancel,
                            qryParams.arguments(),
                            parts,
                            qryParams.lazy(),
                            qryParams.dataPageScanEnabled(),
                            qryParams.pageSize()
                        );
                    }
                }
            };
        }

        return iter;
    }

    /**
     * Executes DML request on map node. Happens only for "skip reducer" mode.
     *
     * @param schemaName Schema name.
     * @param qry Query.
     * @param filter Filter.
     * @param cancel Cancel state.
     * @param loc Locality flag.
     * @return Update result.
     * @throws IgniteCheckedException if failed.
     */
    public UpdateResult executeUpdateOnDataNode(
        String schemaName,
        SqlFieldsQuery qry,
        IndexingQueryFilter filter,
        GridQueryCancel cancel,
        boolean loc
    ) throws IgniteCheckedException {
        QueryParserResult parseRes = parser.parse(schemaName, qry, false);

        assert parseRes.remainingQuery() == null;

        QueryParserResultDml dml = parseRes.dml();

        assert dml != null;

        return executeUpdate(
            RunningQueryManager.UNDEFINED_QUERY_ID,
            parseRes.queryDescriptor(),
            parseRes.queryParameters(),
            dml,
            loc,
            filter,
            cancel
        );
    }

    /** {@inheritDoc} */
    @Override public boolean isStreamableInsertStatement(String schemaName, SqlFieldsQuery qry) throws SQLException {
        QueryParserResult parsed = parser.parse(schemaName, qry, true);

        return parsed.isDml() && parsed.dml().streamable() && parsed.remainingQuery() == null;
    }

    /**
     * @return Busy lock.
     */
    public GridSpinBusyLock busyLock() {
        return busyLock;
    }

    /**
     * @return Map query executor.
     */
    public GridMapQueryExecutor mapQueryExecutor() {
        return mapQryExec;
    }

    /**
     * @return Reduce query executor.
     */
    public GridReduceQueryExecutor reduceQueryExecutor() {
        return rdcQryExec;
    }

    /** {@inheritDoc} */
    @Override public RunningQueryManager runningQueryManager() {
        return ctx.query().runningQueryManager();
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"deprecation", "AssignmentToStaticFieldFromInstanceMethod"})
    @Override public void start(GridKernalContext ctx, GridSpinBusyLock busyLock) throws IgniteCheckedException {
        if (log.isDebugEnabled())
            log.debug("Starting cache query index...");

        // During this check we also load necessary spatial index utils class.
        if (H2Utils.checkSpatialIndexEnabled() && log.isDebugEnabled())
            log.debug("Spatial indexes are enabled.");

        this.busyLock = busyLock;

        if (SysProperties.serializeJavaObject) {
            U.warn(log, "Serialization of Java objects in H2 was enabled.");

            SysProperties.serializeJavaObject = false;
        }

        this.ctx = ctx;

        partReservationMgr = ctx.query().partitionReservationManager();

        connMgr = new ConnectionManager(ctx);

        heavyQryTracker = ctx.query().runningQueryManager().heavyQueriesTracker();

        parser = new QueryParser(this, connMgr, cmd -> cmdProc.isCommandSupported(cmd));

        schemaMgr = new H2SchemaManager(ctx, this, connMgr);
        schemaMgr.start();

        nodeId = ctx.localNodeId();
        marshaller = ctx.marshaller();

        mapQryExec = new GridMapQueryExecutor();
        rdcQryExec = new GridReduceQueryExecutor();

        mapQryExec.start(ctx, this);
        rdcQryExec.start(ctx, this);

        discoLsnr = evt -> {
            mapQryExec.onNodeLeft((DiscoveryEvent)evt);
            rdcQryExec.onNodeLeft((DiscoveryEvent)evt);
        };

        ctx.event().addLocalEventListener(discoLsnr, EventType.EVT_NODE_FAILED, EventType.EVT_NODE_LEFT);

        qryLsnr = (nodeId, msg, plc) -> onMessage(nodeId, msg);

        ctx.io().addMessageListener(GridTopic.TOPIC_QUERY, qryLsnr);

        partExtractor = new PartitionExtractor(new H2PartitionResolver(this), ctx);

        cmdProc = new CommandProcessor(ctx, schemaMgr, this);

        if (JdbcUtils.serializer != null)
            U.warn(log, "Custom H2 serialization is already configured, will override.");

        JdbcUtils.serializer = h2Serializer();

        distrCfg = new DistributedIndexingConfiguration(ctx, log);

        funcMgr = new FunctionsManager(distrCfg);

        // Setup default index key type settings.
        CompareMode compareMode = connMgr.dataHandler().getCompareMode();

        ctx.indexProcessor().keyTypeSettings()
            .stringOptimizedCompare(CompareMode.OFF.equals(compareMode.getName()))
            .binaryUnsigned(compareMode.isBinaryUnsigned());

        ctx.internalSubscriptionProcessor().registerSchemaChangeListener(new AbstractSchemaChangeListener() {
            /** */
            @Override public void onColumnsAdded(
                String schemaName,
                GridQueryTypeDescriptor typeDesc,
                GridCacheContextInfo<?, ?> cacheInfo,
                List<QueryField> cols
            ) {
                clearPlanCache();
            }

            /** */
            @Override public void onColumnsDropped(
                String schemaName,
                GridQueryTypeDescriptor typeDesc,
                GridCacheContextInfo<?, ?> cacheInfo,
                List<String> cols
            ) {
                clearPlanCache();
            }
        });
    }

    /**
     * @param nodeId Node ID.
     * @param msg Message.
     */
    public void onMessage(UUID nodeId, Object msg) {
        assert msg != null;

        ClusterNode node = ctx.discovery().node(nodeId);

        if (node == null)
            return; // Node left, ignore.

        if (!busyLock.enterBusy())
            return;

        try {
            if (msg instanceof GridCacheQueryMarshallable)
                ((GridCacheQueryMarshallable)msg).unmarshall(ctx);

            try {
                boolean processed = true;

                boolean tracebleMsg = false;

                if (msg instanceof GridQueryNextPageRequest) {
                    mapQueryExecutor().onNextPageRequest(node, (GridQueryNextPageRequest)msg);

                    tracebleMsg = true;
                }
                else if (msg instanceof GridQueryNextPageResponse) {
                    reduceQueryExecutor().onNextPage(node, (GridQueryNextPageResponse)msg);

                    tracebleMsg = true;
                }
                else if (msg instanceof GridH2QueryRequest)
                    mapQueryExecutor().onQueryRequest(node, (GridH2QueryRequest)msg);
                else if (msg instanceof GridH2DmlRequest)
                    mapQueryExecutor().onDmlRequest(node, (GridH2DmlRequest)msg);
                else if (msg instanceof GridH2DmlResponse)
                    reduceQueryExecutor().onDmlResponse(node, (GridH2DmlResponse)msg);
                else if (msg instanceof GridQueryFailResponse)
                    reduceQueryExecutor().onFail(node, (GridQueryFailResponse)msg);
                else if (msg instanceof GridQueryCancelRequest)
                    mapQueryExecutor().onCancel(node, (GridQueryCancelRequest)msg);
                else
                    processed = false;

                if (processed && log.isDebugEnabled() && (!tracebleMsg || log.isTraceEnabled()))
                    log.debug("Processed message: [srcNodeId=" + nodeId + ", msg=" + msg + ']');
            }
            catch (Throwable th) {
                U.error(log, "Failed to process message: [srcNodeId=" + nodeId + ", msg=" + msg + ']', th);
            }
        }
        finally {
            busyLock.leaveBusy();
        }
    }

    /**
     * @return Value object context.
     */
    public CacheObjectValueContext objectContext() {
        return ctx.query().objectContext();
    }

    /**
     * @param topic Topic.
     * @param topicOrd Topic ordinal for {@link GridTopic}.
     * @param nodes Nodes.
     * @param msg Message.
     * @param specialize Optional closure to specialize message for each node.
     * @param locNodeHnd Handler for local node.
     * @param plc Policy identifying the executor service which will process message.
     * @param runLocParallel Run local handler in parallel thread.
     * @return {@code true} If all messages sent successfully.
     */
    public boolean send(
        Object topic,
        int topicOrd,
        Collection<ClusterNode> nodes,
        Message msg,
        @Nullable IgniteBiClosure<ClusterNode, Message, Message> specialize,
        @Nullable final IgniteInClosure2X<ClusterNode, Message> locNodeHnd,
        byte plc,
        boolean runLocParallel
    ) {
        boolean ok = true;

        if (specialize == null && msg instanceof GridCacheQueryMarshallable)
            ((GridCacheQueryMarshallable)msg).marshall(marshaller);

        ClusterNode locNode = null;

        for (ClusterNode node : nodes) {
            if (node.isLocal()) {
                if (locNode != null)
                    throw new IllegalStateException();

                locNode = node;

                continue;
            }

            try {
                if (specialize != null) {
                    msg = specialize.apply(node, msg);

                    if (msg instanceof GridCacheQueryMarshallable)
                        ((GridCacheQueryMarshallable)msg).marshall(marshaller);
                }

                ctx.io().sendGeneric(node, topic, topicOrd, msg, plc);
            }
            catch (IgniteCheckedException e) {
                ok = false;

                U.warn(log, "Failed to send message [node=" + node + ", msg=" + msg +
                    ", errMsg=" + e.getMessage() + "]");
            }
        }

        // Local node goes the last to allow parallel execution.
        if (locNode != null) {
            assert locNodeHnd != null;

            if (specialize != null) {
                msg = specialize.apply(locNode, msg);

                if (msg instanceof GridCacheQueryMarshallable)
                    ((GridCacheQueryMarshallable)msg).marshall(marshaller);
            }

            if (runLocParallel) {
                final ClusterNode finalLocNode = locNode;
                final Message finalMsg = msg;

                try {
                    // We prefer runLocal to runLocalSafe, because the latter can produce deadlock here.
                    ctx.closure().runLocal(new GridPlainRunnable() {
                        @Override public void run() {
                            if (!busyLock.enterBusy())
                                return;

                            try {
                                locNodeHnd.apply(finalLocNode, finalMsg);
                            }
                            finally {
                                busyLock.leaveBusy();
                            }
                        }
                    }, plc).listen(logger);
                }
                catch (IgniteCheckedException e) {
                    ok = false;

                    U.error(log, "Failed to execute query locally.", e);
                }
            }
            else
                locNodeHnd.apply(locNode, msg);
        }

        return ok;
    }

    /**
     * @return Serializer.
     */
    private JavaObjectSerializer h2Serializer() {
        return new H2JavaObjectSerializer();
    }

    /** {@inheritDoc} */
    @Override public void stop() {
        if (log.isDebugEnabled())
            log.debug("Stopping cache query index...");

        mapQryExec.stop();

        qryCtxRegistry.clearSharedOnLocalNodeStop();

        connMgr.stop();

        if (log.isDebugEnabled())
            log.debug("Cache query index stopped.");
    }

    /** {@inheritDoc} */
    @Override public void onClientDisconnect() {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public void registerCache(String cacheName, String schemaName, GridCacheContextInfo<?, ?> cacheInfo) {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public void unregisterCache(GridCacheContextInfo<?, ?> cacheInfo) {
        // Unregister connection.
        connMgr.onCacheDestroyed();

        // Clear query cache.
        clearPlanCache();
    }

    /**
     * Remove all cached queries from cached two-steps queries.
     */
    private void clearPlanCache() {
        parser.clearCache();
    }

    /** {@inheritDoc} */
    @Override public IndexingQueryFilter backupFilter(@Nullable final AffinityTopologyVersion topVer,
        @Nullable final int[] parts) {
        return backupFilter(topVer, parts, false);
    }

    /**
     * Returns backup filter.
     *
     * @param topVer Topology version.
     * @param parts Partitions.
     * @param treatReplicatedAsPartitioned true if need to treat replicated as partitioned (for outer joins).
     * @return Backup filter.
     */
    public IndexingQueryFilter backupFilter(@Nullable final AffinityTopologyVersion topVer, @Nullable final int[] parts,
            boolean treatReplicatedAsPartitioned) {
        return new IndexingQueryFilterImpl(ctx, topVer, parts, treatReplicatedAsPartitioned);
    }

    /**
     * @return Ready topology version.
     */
    public AffinityTopologyVersion readyTopologyVersion() {
        return ctx.cache().context().exchange().readyAffinityVersion();
    }

    /**
     * @param readyVer Ready topology version.
     *
     * @return {@code true} If pending distributed exchange exists because server topology is changed.
     */
    public boolean serverTopologyChanged(AffinityTopologyVersion readyVer) {
        GridDhtPartitionsExchangeFuture fut = ctx.cache().context().exchange().lastTopologyFuture();

        if (fut.isDone())
            return false;

        AffinityTopologyVersion initVer = fut.initialVersion();

        return initVer.compareTo(readyVer) > 0 && !fut.firstEvent().node().isClient();
    }

    /**
     * @param topVer Topology version.
     * @throws IgniteCheckedException If failed.
     */
    public void awaitForReadyTopologyVersion(AffinityTopologyVersion topVer) throws IgniteCheckedException {
        ctx.cache().context().exchange().affinityReadyFuture(topVer).get();
    }

    /** {@inheritDoc} */
    @Override public void onDisconnected(IgniteFuture<?> reconnectFut) {
        rdcQryExec.onDisconnected(reconnectFut);
    }

    /** {@inheritDoc} */
    @Override public void onKernalStop() {
        connMgr.onKernalStop();

        ctx.io().removeMessageListener(GridTopic.TOPIC_QUERY, qryLsnr);
        ctx.event().removeLocalEventListener(discoLsnr);
    }

    /**
     * @return Query context registry.
     */
    public QueryContextRegistry queryContextRegistry() {
        return qryCtxRegistry;
    }

    /**
     * @return Connection manager.
     */
    public ConnectionManager connections() {
        return connMgr;
    }

    /**
     * @return Parser.
     */
    public QueryParser parser() {
        return parser;
    }

    /**
     * @return Schema manager.
     */
    public H2SchemaManager schemaManager() {
        return schemaMgr;
    }

    /**
     * @return Partition extractor.
     */
    public PartitionExtractor partitionExtractor() {
        return partExtractor;
    }

    /**
     * @return Partition reservation manager.
     */
    public PartitionReservationManager partitionReservationManager() {
        return partReservationMgr;
    }

    /**
     * @param qryId Query id.
     * @param qryDesc Query descriptor.
     * @param qryParams Query parameters.
     * @param dml DML statement.
     * @param cancel Query cancel.
     * @return Update result wrapped into {@link GridQueryFieldsResult}
     * @throws IgniteCheckedException if failed.
     */
    @SuppressWarnings("unchecked")
    private List<QueryCursorImpl<List<?>>> executeUpdateDistributed(
        long qryId,
        QueryDescriptor qryDesc,
        QueryParameters qryParams,
        QueryParserResultDml dml,
        GridQueryCancel cancel
    ) throws IgniteCheckedException {
        if (qryDesc.batched()) {
            Collection<UpdateResult> ress;

            List<Object[]> argss = qryParams.batchedArguments();

            UpdatePlan plan = dml.plan();

            GridCacheContext<?, ?> cctx = plan.cacheContext();

            if (plan.hasRows() && plan.mode() == UpdateMode.INSERT) {
                CacheOperationContext opCtx = DmlUtils.setKeepBinaryContext(cctx);

                try {
                    List<List<List<?>>> cur = plan.createRows(argss);

                    //TODO: IGNITE-11176 - Need to support cancellation
                    ress = DmlUtils.processSelectResultBatched(plan, cur, qryParams.updateBatchSize());
                }
                finally {
                    DmlUtils.restoreKeepBinaryContext(cctx, opCtx);
                }
            }
            else {
                // Fallback to previous mode.
                ress = new ArrayList<>(argss.size());

                SQLException batchEx = null;

                int[] cntPerRow = new int[argss.size()];

                int cntr = 0;

                for (Object[] args : argss) {
                    UpdateResult res;

                    try {
                        res = executeUpdate(
                            qryId,
                            qryDesc,
                            qryParams.toSingleBatchedArguments(args),
                            dml,
                            false,
                            null,
                            cancel
                        );

                        cntPerRow[cntr++] = (int)res.counter();

                        ress.add(res);
                    }
                    catch (Exception e ) {
                        SQLException sqlEx = QueryUtils.toSqlException(e);

                        batchEx = DmlUtils.chainException(batchEx, sqlEx);

                        cntPerRow[cntr++] = Statement.EXECUTE_FAILED;
                    }
                }

                if (batchEx != null) {
                    BatchUpdateException e = new BatchUpdateException(batchEx.getMessage(),
                        batchEx.getSQLState(), batchEx.getErrorCode(), cntPerRow, batchEx);

                    throw new IgniteCheckedException(e);
                }
            }

            ArrayList<QueryCursorImpl<List<?>>> resCurs = new ArrayList<>(ress.size());

            for (UpdateResult res : ress) {
                res.throwIfError();

                QueryCursorImpl<List<?>> resCur = (QueryCursorImpl<List<?>>)new QueryCursorImpl(singletonList(
                    singletonList(res.counter())), cancel, false, false);

                resCur.fieldsMeta(UPDATE_RESULT_META);

                resCurs.add(resCur);
            }

            return resCurs;
        }
        else {
            UpdateResult res = executeUpdate(
                qryId,
                qryDesc,
                qryParams,
                dml,
                false,
                null,
                cancel
            );

            res.throwIfError();

            QueryCursorImpl<List<?>> resCur = (QueryCursorImpl<List<?>>)new QueryCursorImpl(singletonList(
                singletonList(res.counter())), cancel, false, false);

            resCur.fieldsMeta(UPDATE_RESULT_META);

            resCur.partitionResult(res.partitionResult());

            return singletonList(resCur);
        }
    }

    /**
     * Execute DML statement, possibly with few re-attempts in case of concurrent data modifications.
     *
     * @param qryId Query id.
     * @param qryDesc Query descriptor.
     * @param qryParams Query parameters.
     * @param dml DML command.
     * @param loc Query locality flag.
     * @param filters Cache name and key filter.
     * @param cancel Cancel.
     * @return Update result (modified items count and failed keys).
     * @throws IgniteCheckedException if failed.
     */
    @SuppressWarnings("IfMayBeConditional")
    private UpdateResult executeUpdate(
        long qryId,
        QueryDescriptor qryDesc,
        QueryParameters qryParams,
        QueryParserResultDml dml,
        boolean loc,
        IndexingQueryFilter filters,
        GridQueryCancel cancel
    ) throws IgniteCheckedException {
        Object[] errKeys = null;

        long items = 0;

        PartitionResult partRes = null;

        GridCacheContext<?, ?> cctx = dml.plan().cacheContext();

        for (int i = 0; i < DFLT_UPDATE_RERUN_ATTEMPTS; i++) {
            CacheOperationContext opCtx = cctx != null ? DmlUtils.setKeepBinaryContext(cctx) : null;

            UpdateResult r;

            try {
                r = executeUpdate0(
                    qryId,
                    qryDesc,
                    qryParams,
                    dml,
                    loc,
                    filters,
                    cancel
                );
            }
            finally {
                if (opCtx != null)
                    DmlUtils.restoreKeepBinaryContext(cctx, opCtx);
            }

            items += r.counter();
            errKeys = r.errorKeys();
            partRes = r.partitionResult();

            if (F.isEmpty(errKeys))
                break;
        }

        if (F.isEmpty(errKeys) && partRes == null) {
            if (items == 1L)
                return UpdateResult.ONE;
            else if (items == 0L)
                return UpdateResult.ZERO;
        }

        return new UpdateResult(items, errKeys, partRes);
    }

    /**
     * Execute update.
     *
     * @param qryId Query id.
     * @param qryDesc Query descriptor.
     * @param qryParams Query parameters.
     * @param dml Plan.
     * @param loc Local flag.
     * @param filters Filters.
     * @param cancel Cancel hook.
     * @return Update result.
     * @throws IgniteCheckedException If failed.
     */
    private UpdateResult executeUpdate0(
        long qryId,
        QueryDescriptor qryDesc,
        QueryParameters qryParams,
        QueryParserResultDml dml,
        boolean loc,
        IndexingQueryFilter filters,
        GridQueryCancel cancel
    ) throws IgniteCheckedException {
        StringBuilder dmlPlanInfo = new StringBuilder("As part of this DML command ");

        try {
            UpdatePlan plan = dml.plan();

            UpdateResult fastUpdateRes = plan.processFast(qryParams.arguments());

            if (fastUpdateRes != null) {
                dmlPlanInfo.append("no SELECT queries have been executed.");

                return fastUpdateRes;
            }

            DmlDistributedPlanInfo distributedPlan = loc ? null : plan.distributedPlan();

            if (distributedPlan != null) {
                if (cancel == null)
                    cancel = new GridQueryCancel();

                UpdateResult result = rdcQryExec.update(
                    qryDesc.schemaName(),
                    distributedPlan.getCacheIds(),
                    qryDesc.sql(),
                    qryParams.arguments(),
                    qryDesc.enforceJoinOrder(),
                    qryParams.pageSize(),
                    qryParams.timeout(),
                    qryParams.partitions(),
                    distributedPlan.isReplicatedOnly(),
                    cancel
                );

                // Null is returned in case not all nodes support distributed DML.
                if (result != null) {
                    dmlPlanInfo = new StringBuilder("This DML command has been executed with a distibuted plan " +
                        "(requests for separate DML commands have been sent to respective cluster nodes).");

                    return result;
                }
            }

            final GridQueryCancel selectCancel = (cancel != null) ? new GridQueryCancel() : null;

            if (cancel != null)
                cancel.add(selectCancel::cancel);

            SqlFieldsQuery selectFieldsQry = new SqlFieldsQuery(plan.selectQuery(), qryDesc.collocated())
                .setArgs(qryParams.arguments())
                .setDistributedJoins(qryDesc.distributedJoins())
                .setEnforceJoinOrder(qryDesc.enforceJoinOrder())
                .setLocal(qryDesc.local())
                .setPageSize(qryParams.pageSize())
                .setTimeout(qryParams.timeout(), TimeUnit.MILLISECONDS)
                // We cannot use lazy mode when UPDATE query contains updated columns
                // in WHERE condition because it may be cause of update one entry several times
                // (when index for such columns is selected for scan):
                // e.g. : UPDATE test SET val = val + 1 WHERE val >= ?
                .setLazy(qryParams.lazy() && plan.canSelectBeLazy());

            Iterable<List<?>> cur;

            // Do a two-step query only if locality flag is not set AND if plan's SELECT corresponds to an actual
            // sub-query and not some dummy stuff like "select 1, 2, 3;"
            if (!loc && !plan.isLocalSubquery()) {
                assert !F.isEmpty(plan.selectQuery());

                cur = executeSelectForDml(
                    qryId,
                    qryDesc.schemaName(),
                    selectFieldsQry,
                    selectCancel,
                    qryParams.timeout()
                );

                dmlPlanInfo
                    .append("the following query has been executed:").append(U.nl())
                    .append(selectFieldsQry.getSql()).append(";").append(U.nl())
                    .append("check map nodes for map phase query plans");
            }
            else if (plan.hasRows()) {
                cur = plan.createRows(qryParams.arguments());

                dmlPlanInfo.append("no SELECT queries have been executed.");
            }
            else {
                selectFieldsQry.setLocal(true);

                QueryParserResult selectParseRes = parser.parse(qryDesc.schemaName(), selectFieldsQry, false);

                final GridQueryFieldsResult res = executeSelectLocal(
                    qryId,
                    selectParseRes.queryDescriptor(),
                    selectParseRes.queryParameters(),
                    selectParseRes.select(),
                    filters,
                    selectCancel,
                    qryParams.timeout()
                );

                cur = new QueryCursorImpl<>(new Iterable<List<?>>() {
                    @Override public Iterator<List<?>> iterator() {
                        try {
                            return new GridQueryCacheObjectsIterator(res.iterator(), objectContext(), true);
                        }
                        catch (IgniteCheckedException e) {
                            throw new IgniteException(e);
                        }
                    }
                }, cancel, true, qryParams.lazy());

                dmlPlanInfo
                    .append("the following local query has been executed:").append(U.nl())
                    .append(selectFieldsQry.getSql());
            }

            int pageSize = qryParams.updateBatchSize();

            // TODO: IGNITE-11176 - Need to support cancellation
            try {
                return DmlUtils.processSelectResult(plan, cur, pageSize);
            }
            finally {
                if (cur instanceof AutoCloseable)
                    U.closeQuiet((AutoCloseable)cur);
            }
        }
        finally {
            if (runningQueryManager().planHistoryTracker().enabled()) {
                runningQueryManager().planHistoryTracker().addPlan(
                    dmlPlanInfo.toString(),
                    qryDesc.sql(),
                    qryDesc.schemaName(),
                    loc,
                    IndexingQueryEngineConfiguration.ENGINE_NAME
                );
            }
        }
    }

    /**
     * @return Heavy queries tracker.
     */
    public HeavyQueriesTracker heavyQueriesTracker() {
        return heavyQryTracker;
    }

    /**
     * @return Distributed SQL configuration.
     */
    public DistributedIndexingConfiguration distributedConfiguration() {
        return distrCfg;
    }

    /**
     * Resumes time tracking before the task (if needed) and suspends time tracking after the task is finished.
     *
     * @param task Query/fetch to execute.
     * @param qryInfo Query info.
     * @throws IgniteCheckedException If failed.
     */
    public <T> T executeWithResumableTimeTracking(
        IgniteThrowableSupplier<T> task,
        final H2QueryInfo qryInfo
    ) throws IgniteCheckedException {
        qryInfo.resumeTracking();

        try {
            return task.get();
        }
        finally {
            qryInfo.suspendTracking();
        }
    }
}
