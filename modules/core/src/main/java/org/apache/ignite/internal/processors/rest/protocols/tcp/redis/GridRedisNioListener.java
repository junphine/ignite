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

package org.apache.ignite.internal.processors.rest.protocols.tcp.redis;


import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.apache.ignite.IgniteCache;
import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.IgniteLogger;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.internal.GridKernalContext;
import org.apache.ignite.internal.IgniteInternalFuture;
import org.apache.ignite.internal.processors.rest.GridRestProtocolHandler;
import org.apache.ignite.internal.processors.rest.handlers.redis.GridRedisCommandHandler;
import org.apache.ignite.internal.processors.rest.handlers.redis.GridRedisConnectionCommandHandler;
import org.apache.ignite.internal.processors.rest.handlers.redis.key.GridRedisDelCommandHandler;
import org.apache.ignite.internal.processors.rest.handlers.redis.key.GridRedisExistsCommandHandler;
import org.apache.ignite.internal.processors.rest.handlers.redis.key.GridRedisExpireCommandHandler;
import org.apache.ignite.internal.processors.rest.handlers.redis.key.GridRedisKeysCommandHandler;
import org.apache.ignite.internal.processors.rest.handlers.redis.list.GridRedisListAddCommandHandler;
import org.apache.ignite.internal.processors.rest.handlers.redis.list.GridRedisSetsCommandHandler;
import org.apache.ignite.internal.processors.rest.handlers.redis.list.GridRedisListPopCommandHandler;
import org.apache.ignite.internal.processors.rest.handlers.redis.list.GridRedisListRemCommandHandler;
import org.apache.ignite.internal.processors.rest.handlers.redis.list.GridRedisListsCommandHandler;
import org.apache.ignite.internal.processors.rest.handlers.redis.list.GridRedisSortedSetsCommandHandler;
import org.apache.ignite.internal.processors.rest.handlers.redis.pubsub.GridRedisSubscribeCommandHandler;
import org.apache.ignite.internal.processors.rest.handlers.redis.server.GridRedisDbSizeCommandHandler;
import org.apache.ignite.internal.processors.rest.handlers.redis.server.GridRedisFlushCommandHandler;
import org.apache.ignite.internal.processors.rest.handlers.redis.server.GridRedisTransactionCommandHandler;
import org.apache.ignite.internal.processors.rest.handlers.redis.string.GridRedisAppendCommandHandler;
import org.apache.ignite.internal.processors.rest.handlers.redis.string.GridRedisGetCommandHandler;
import org.apache.ignite.internal.processors.rest.handlers.redis.string.GridRedisGetRangeCommandHandler;
import org.apache.ignite.internal.processors.rest.handlers.redis.string.GridRedisGetSetCommandHandler;
import org.apache.ignite.internal.processors.rest.handlers.redis.string.GridRedisIncrDecrCommandHandler;
import org.apache.ignite.internal.processors.rest.handlers.redis.string.GridRedisMGetCommandHandler;
import org.apache.ignite.internal.processors.rest.handlers.redis.string.GridRedisMSetCommandHandler;
import org.apache.ignite.internal.processors.rest.handlers.redis.string.GridRedisSetCommandHandler;
import org.apache.ignite.internal.processors.rest.handlers.redis.string.GridRedisSetRangeCommandHandler;
import org.apache.ignite.internal.processors.rest.handlers.redis.string.GridRedisStrlenCommandHandler;
import org.apache.ignite.internal.util.future.GridFinishedFuture;
import org.apache.ignite.internal.util.nio.GridNioFuture;
import org.apache.ignite.internal.util.nio.GridNioServerListenerAdapter;
import org.apache.ignite.internal.util.nio.GridNioSession;
import org.apache.ignite.internal.util.nio.GridNioSessionMetaKey;
import org.apache.ignite.internal.util.typedef.CIX1;
import org.apache.ignite.internal.util.typedef.internal.U;
import org.apache.ignite.transactions.Transaction;
import org.jetbrains.annotations.Nullable;

/**
 * Listener for Redis protocol requests.
 */
public class GridRedisNioListener extends GridNioServerListenerAdapter<GridRedisMessage> {
    /** Logger. */
    private final IgniteLogger log;
    
    private final GridKernalContext ctx;
    
    private final GridRedisSubscribeCommandHandler subscribeHandler;

    /** Redis-specific handlers. */
    protected final Map<GridRedisCommand, GridRedisCommandHandler> handlers = new EnumMap<>(GridRedisCommand.class);

    /** Connection-related metadata key. Used for cache name only. */
    public static final int CONN_CTX_META_KEY = GridNioSessionMetaKey.nextUniqueKey();
    public static final int CONN_NAME_META_KEY = GridNioSessionMetaKey.nextUniqueKey();
    public static final int SESS_TX_META_KEY = GridNioSessionMetaKey.nextUniqueKey();
    public static final int SESS_TX_QUEUED_META_KEY = GridNioSessionMetaKey.nextUniqueKey();

    /**
     * @param log Logger.
     * @param hnd REST protocol handler.
     * @param ctx Context.
     */
    public GridRedisNioListener(IgniteLogger log, GridRestProtocolHandler hnd, GridKernalContext ctx) {
        this.log = log;
        this.ctx = ctx;
        this.subscribeHandler = new GridRedisSubscribeCommandHandler(log,ctx);

        // connection commands.
        addCommandHandler(new GridRedisConnectionCommandHandler(log, hnd, ctx));

        // string commands.
        addCommandHandler(new GridRedisGetCommandHandler(log, hnd, ctx));
        addCommandHandler(new GridRedisSetCommandHandler(log, hnd, ctx));
        addCommandHandler(new GridRedisMSetCommandHandler(log, hnd, ctx));
        addCommandHandler(new GridRedisMGetCommandHandler(log, hnd, ctx));
        addCommandHandler(new GridRedisIncrDecrCommandHandler(log, hnd, ctx));
        addCommandHandler(new GridRedisAppendCommandHandler(log, hnd, ctx));
        addCommandHandler(new GridRedisGetSetCommandHandler(log, hnd, ctx));
        addCommandHandler(new GridRedisStrlenCommandHandler(log, hnd, ctx));
        addCommandHandler(new GridRedisSetRangeCommandHandler(log, hnd, ctx));
        addCommandHandler(new GridRedisGetRangeCommandHandler(log, hnd, ctx));

        // key commands.
        addCommandHandler(new GridRedisKeysCommandHandler(log, hnd, ctx));
        addCommandHandler(new GridRedisDelCommandHandler(log, hnd, ctx));
        addCommandHandler(new GridRedisExistsCommandHandler(log, hnd, ctx));
        addCommandHandler(new GridRedisExpireCommandHandler(log, hnd, ctx));

        // server commands.
        addCommandHandler(new GridRedisDbSizeCommandHandler(log, hnd, ctx));
        addCommandHandler(new GridRedisFlushCommandHandler(log, hnd, ctx));
        addCommandHandler(new GridRedisTransactionCommandHandler(log, ctx, handlers));
        
        
        // list commands
        addCommandHandler(new GridRedisListAddCommandHandler(log,ctx));
        addCommandHandler(new GridRedisListPopCommandHandler(log,ctx));
        addCommandHandler(new GridRedisListRemCommandHandler(log,ctx));
        addCommandHandler(new GridRedisListsCommandHandler(log,ctx));
        addCommandHandler(new GridRedisSetsCommandHandler(log,ctx));
        addCommandHandler(new GridRedisSortedSetsCommandHandler(log,ctx));        
        
        // pubsub commands
        addCommandHandler(subscribeHandler);
        
    }

    /**
     * Adds Redis-specific command handlers.
     * <p>
     * Generic commands are treated by REST.
     *
     * @param hnd Redis-specific command handler.
     */
    private void addCommandHandler(GridRedisCommandHandler hnd) {
        assert !handlers.containsValue(hnd);

        if (log.isDebugEnabled())
            log.debug("Added Redis command handler: " + hnd);

        for (GridRedisCommand cmd : hnd.supportedCommands()) {
            assert !handlers.containsKey(cmd) : cmd;

            handlers.put(cmd, hnd);
        }
    }

    /** {@inheritDoc} */
    @Override public void onConnected(GridNioSession ses) {
        // No-op, never called.       
    }

    /** {@inheritDoc} */
    @Override public void onDisconnected(GridNioSession ses, @Nullable Exception e) {
        // No-op, never called.
    	subscribeHandler.removeChanelInfoOfClient(ses.remoteAddress().toString());        
    }

    /** {@inheritDoc} */
    @Override public void onMessage(final GridNioSession ses, final GridRedisMessage msg) {
        if (handlers.get(msg.command()) == null) {
            U.warn(log, "Cannot find the corresponding command (session will be closed) [ses=" + ses +
                ", command=" + msg.aux(0) + ']');

            ses.close();

            return;
        }
        else {
            String cacheName = ses.meta(CONN_CTX_META_KEY);

            if (cacheName != null)
                msg.cacheName(cacheName);
            
            //add@byron
            String cmd = msg.aux(0);
            if(cmd.charAt(0)=='h' || cmd.charAt(0)=='H') { //Hashsets
            	cacheName = msg.standardizeParams(cmd);     
            	msg.cacheName(cacheName); // hash_name as cachename
            	
            	if(cacheName!=null) {
            		CacheConfiguration<String,?> ccfg0 = ctx.cache().cacheConfiguration(GridRedisMessage.DFLT_CACHE_NAME);
            		CacheConfiguration<String,?> ccfg = new CacheConfiguration<>(ccfg0);
            		ccfg.setName(cacheName);
            		ccfg.setGroupName(GridRedisMessage.CACHE_NAME_PREFIX);
            		IgniteCache<String,?> cache = ctx.grid().getOrCreateCache(ccfg);
            	}                
            }
            //end@
            
            // 开始事务，事务往往和pipeline同时开启
            List<GridRedisMessage> queued = ses.meta(SESS_TX_QUEUED_META_KEY);
        	if(queued!=null && !cmd.equals("MULTI") && !cmd.equals("EXEC") && !cmd.equals("DISCARD")) {
        		queued.add(msg);
        		msg.setResponse(GridRedisProtocolParser.toSimpleString("QUEUED"));
        		sendResponse(ses, msg);
        	}
        	else {

	            IgniteInternalFuture<GridRedisMessage> f = handlers.get(msg.command()).handleAsync(ses, msg);
	            
	            f.listen(new CIX1<IgniteInternalFuture<GridRedisMessage>>() {
	                @Override public void applyx(IgniteInternalFuture<GridRedisMessage> f) throws IgniteCheckedException {
	                    GridRedisMessage res = f.get();
	
	                    sendResponse(ses, res);
	                }
	            });
        	}
        }
    }

    /**
     * Sends a response to be encoded and sent to the Redis client.
     *
     * @param ses NIO session.
     * @param res Response.
     * @return NIO send future.
     */
    private GridNioFuture<?> sendResponse(GridNioSession ses, GridRedisMessage res) {
        return ses.send(res);
    }
}
