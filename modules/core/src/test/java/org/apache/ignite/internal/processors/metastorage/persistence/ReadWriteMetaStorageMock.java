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

package org.apache.ignite.internal.processors.metastorage.persistence;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.BiConsumer;
import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.internal.processors.cache.persistence.metastorage.ReadWriteMetastorage;
import org.jetbrains.annotations.NotNull;

import static org.apache.ignite.testframework.junits.common.GridCommonAbstractTest.TEST_JDK_MARSHALLER;

/** */
public class ReadWriteMetaStorageMock implements ReadWriteMetastorage {
    /** */
    public final Map<String, byte[]> cache = new ConcurrentSkipListMap<>();

    /** {@inheritDoc} */
    @Override public void write(@NotNull String key, @NotNull Serializable val) throws IgniteCheckedException {
        assertLockIsHeldByWorkerThread();

        cache.put(key, TEST_JDK_MARSHALLER.marshal(val));
    }

    /** {@inheritDoc} */
    @Override public void writeRaw(String key, byte[] data) {
        assertLockIsHeldByWorkerThread();

        cache.put(key, data);
    }

    /** {@inheritDoc} */
    @Override public void remove(@NotNull String key) {
        assertLockIsHeldByWorkerThread();

        cache.remove(key);
    }

    /** {@inheritDoc} */
    @Override public Serializable read(String key) throws IgniteCheckedException {
        assertLockIsHeldByWorkerThread();

        byte[] bytes = readRaw(key);

        return bytes == null ? null : TEST_JDK_MARSHALLER.unmarshal(bytes, getClass().getClassLoader());
    }

    /** {@inheritDoc} */
    @Override public byte[] readRaw(String key) {
        assertLockIsHeldByWorkerThread();

        return cache.get(key);
    }

    /** {@inheritDoc} */
    @Override public void iterate(
        String keyPrefix,
        BiConsumer<String, ? super Serializable> cb,
        boolean unmarshal
    ) throws IgniteCheckedException {
        assertLockIsHeldByWorkerThread();

        for (Map.Entry<String, byte[]> entry : cache.entrySet()) {
            String key = entry.getKey();

            if (key.startsWith(keyPrefix))
                cb.accept(key, unmarshal ? read(key) : (Serializable)entry.getValue());
        }
    }

    /** */
    protected void assertLockIsHeldByWorkerThread() {
    }
}
