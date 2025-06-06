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

package org.apache.ignite.internal.processors.platform.client.cache;

import org.apache.ignite.cache.CachePeekMode;
import org.apache.ignite.internal.binary.BinaryReaderEx;
import org.apache.ignite.internal.processors.platform.client.ClientConnectionContext;
import org.apache.ignite.internal.processors.platform.client.ClientObjectResponse;
import org.apache.ignite.internal.processors.platform.client.ClientResponse;

/**
 * Cache local peek request.
 * Only should be used in testing purposes.
 */
public class ClientCacheLocalPeekRequest extends ClientCacheKeyRequest {
    /**
     * Constructor.
     *
     * @param reader Reader.
     */
    public ClientCacheLocalPeekRequest(BinaryReaderEx reader) {
        super(reader);
    }

    /** {@inheritDoc} */
    @Override public ClientResponse process0(ClientConnectionContext ctx) {
        Object val = cache(ctx).localPeek(key(), CachePeekMode.ALL);

        return new ClientObjectResponse(requestId(), val);
    }

    /** {@inheritDoc} */
    @Override public boolean isAsync(ClientConnectionContext ctx) {
        return false;
    }
}
