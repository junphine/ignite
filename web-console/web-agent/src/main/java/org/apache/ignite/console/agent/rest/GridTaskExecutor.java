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

package org.apache.ignite.console.agent.rest;

import static java.lang.String.format;
import static org.apache.ignite.IgniteSystemProperties.IGNITE_REST_GETALL_AS_ARRAY;
import static org.apache.ignite.internal.client.GridClientCacheFlag.KEEP_BINARIES_MASK;
import static org.apache.ignite.internal.processors.rest.GridRestCommand.CACHE_CONTAINS_KEYS;
import static org.apache.ignite.internal.processors.rest.GridRestCommand.CACHE_GET_ALL;
import static org.apache.ignite.internal.processors.rest.GridRestCommand.CACHE_PUT_ALL;
import static org.apache.ignite.internal.processors.rest.GridRestCommand.CACHE_REMOVE_ALL;
import static org.apache.ignite.internal.processors.rest.GridRestCommand.CLUSTER_ACTIVATE;
import static org.apache.ignite.internal.processors.rest.GridRestCommand.CLUSTER_ACTIVE;
import static org.apache.ignite.internal.processors.rest.GridRestCommand.CLUSTER_CURRENT_STATE;
import static org.apache.ignite.internal.processors.rest.GridRestCommand.CLUSTER_STATE;
import static org.apache.ignite.internal.processors.rest.GridRestCommand.EXECUTE_SQL_QUERY;
import static org.apache.ignite.internal.processors.rest.GridRestResponse.STATUS_FAILED;
import static org.apache.ignite.internal.processors.rest.GridRestResponse.STATUS_SUCCESS;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.IgniteLogger;
import org.apache.ignite.IgniteSystemProperties;
import org.apache.ignite.binary.BinaryObject;
import org.apache.ignite.cache.CacheWriteSynchronizationMode;
import org.apache.ignite.cluster.ClusterState;
import org.apache.ignite.internal.IgniteEx;
import org.apache.ignite.internal.jackson.IgniteBinaryObjectJsonDeserializer;
import org.apache.ignite.internal.jackson.IgniteObjectMapper;
import org.apache.ignite.internal.processors.cache.CacheConfigurationOverride;
import org.apache.ignite.internal.processors.rest.GridRestCommand;
import org.apache.ignite.internal.processors.rest.GridRestProcessor;
import org.apache.ignite.internal.processors.rest.GridRestProtocolHandler;
import org.apache.ignite.internal.processors.rest.GridRestResponse;
import org.apache.ignite.internal.processors.rest.protocols.http.jetty.GridCmdRestHandler;
import org.apache.ignite.internal.processors.rest.protocols.http.jetty.StringConverter;
import org.apache.ignite.internal.processors.rest.request.DataStructuresRequest;
import org.apache.ignite.internal.processors.rest.request.GridRestBaselineRequest;
import org.apache.ignite.internal.processors.rest.request.GridRestCacheRequest;
import org.apache.ignite.internal.processors.rest.request.GridRestChangeStateRequest;
import org.apache.ignite.internal.processors.rest.request.GridRestClusterNameRequest;
import org.apache.ignite.internal.processors.rest.request.GridRestClusterStateRequest;
import org.apache.ignite.internal.processors.rest.request.GridRestLogRequest;
import org.apache.ignite.internal.processors.rest.request.GridRestNodeStateBeforeStartRequest;
import org.apache.ignite.internal.processors.rest.request.GridRestRequest;
import org.apache.ignite.internal.processors.rest.request.GridRestTaskRequest;
import org.apache.ignite.internal.processors.rest.request.GridRestTopologyRequest;
import org.apache.ignite.internal.processors.rest.request.GridRestWarmUpRequest;
import org.apache.ignite.internal.processors.rest.request.RestQueryRequest;
import org.apache.ignite.internal.processors.rest.request.RestUserActionRequest;
import org.apache.ignite.internal.util.typedef.F;
import org.apache.ignite.internal.util.typedef.internal.U;
import org.apache.ignite.lang.IgniteBiTuple;
import org.apache.ignite.lang.IgniteUuid;
import org.apache.ignite.logger.slf4j.Slf4jLogger;
import org.apache.ignite.plugin.security.SecurityCredentials;
import org.jetbrains.annotations.Nullable;
import org.slf4j.LoggerFactory;
import io.vertx.core.json.JsonObject;

/**
 * Jetty REST handler. The following URL format is supported: {@code /ignite?cmd=cmdName&param1=abc&param2=123}
 */
public class GridTaskExecutor {
    private static final IgniteLogger log = new Slf4jLogger(LoggerFactory.getLogger(GridTaskExecutor.class));
    /** result format */
    private final boolean getAllAsArray = IgniteSystemProperties.getBoolean(IGNITE_REST_GETALL_AS_ARRAY);

    /**
     * Creates new Task requests handler.
     *
     */
    public GridTaskExecutor() {           
            
    }

    /** {@inheritDoc} */
    public RestResult execRequest(Ignite ignite,String cmd, JsonObject params)
        throws IOException {
        if (log.isDebugEnabled())
            log.debug("Handling request [cmd=" + cmd + ", params=" + params + ']');

        IgniteEx igniteEx = (IgniteEx)ignite;

        IgniteObjectMapper jsonMapper = new IgniteObjectMapper(igniteEx.context());
        GridRestResponse cmdRes = processRequest(ignite, jsonMapper, cmd, params);
        
        if(cmdRes.getSuccessStatus()==STATUS_SUCCESS) {
        	String json = jsonMapper.writer().writeValueAsString(cmdRes.getResponse());
        	return RestResult.success(json, cmdRes.getSessionToken());
        }
        else {
        	return RestResult.fail(cmdRes.getSuccessStatus(),cmdRes.getError());
        }
    }

    /**
     * Process HTTP request.
     * @param act Action.
     * @param params Http params.
     */
    public GridRestResponse processRequest(Ignite ignite, IgniteObjectMapper jsonMapper, String act, JsonObject params) {

        GridRestCommand cmd = command(act,params);
        GridRestResponse cmdRes;
        if (cmd == null) {
        	cmdRes = new GridRestResponse(STATUS_FAILED, "BAD_REQUEST");
            return cmdRes;
        }
        
        Map<String, String> strParams = new HashMap<>();
        params.forEach((ent)->{
        	if(ent.getValue()!=null)
        		strParams.put(ent.getKey(), ent.getValue().toString());
        });

        try {

            IgniteEx igniteEx = (IgniteEx)ignite;
            GridRestRequest cmdReq = GridCmdRestHandler.createRequest(cmd, strParams, jsonMapper);
            cmdReq.address(new InetSocketAddress("127.0.0.1", 1));

            if (log.isDebugEnabled())
                log.debug("Initialized command request: " + cmdReq);

            GridRestProcessor restProcessor = (GridRestProcessor)igniteEx.context().rest();
            /** Request handlers. */
            GridRestProtocolHandler hnd = U.field(restProcessor, "protoHnd");
            cmdRes = hnd.handle(cmdReq);

            if (cmdRes == null)
                throw new IllegalStateException("Received null result from handler: " + hnd);

            if (getAllAsArray && cmd == GridRestCommand.CACHE_GET_ALL) {
                List<IgniteBiTuple> resKeyValue = new ArrayList<>();

                for (Map.Entry<Object, Object> me : ((Map<Object, Object>)cmdRes.getResponse()).entrySet())
                    resKeyValue.add(new IgniteBiTuple<>(me.getKey(), me.getValue()));

                cmdRes.setResponse(resKeyValue);
            }

            byte[] sesTok = cmdRes.sessionTokenBytes();

            if (sesTok != null)
                cmdRes.setSessionToken(U.byteArray2HexString(sesTok));            
            
        }
        catch (Throwable e) {            

            U.error(log, "Failed to process HTTP request [action=" + act + ", req=" + params + ']', e);

            if (e instanceof Error)
                throw (Error)e;

            cmdRes = new GridRestResponse(STATUS_FAILED, e.getMessage());
        }
        return cmdRes;
        
    }

    /**
     * @param req Request.
     * @return Command.
     */
    @Nullable private GridRestCommand command(String act,JsonObject req) {
        String cmd = req.getString("cmd");
        if(cmd == null){
            cmd = act;
        }
        return StringUtils.isBlank(cmd) ? null : GridRestCommand.fromKey(cmd.toLowerCase());
    }
      
}
