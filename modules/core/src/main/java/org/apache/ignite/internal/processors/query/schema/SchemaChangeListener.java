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

package org.apache.ignite.internal.processors.query.schema;

import java.lang.reflect.Method;
import org.apache.ignite.internal.cache.query.index.Index;
import org.apache.ignite.internal.processors.cache.GridCacheContextInfo;
import org.apache.ignite.internal.processors.query.GridQueryIndexDescriptor;
import org.apache.ignite.internal.processors.query.GridQueryTypeDescriptor;
import org.apache.ignite.spi.systemview.view.SystemView;
import org.jetbrains.annotations.Nullable;

/**
 *
 */
public interface SchemaChangeListener {
    /**
     * Callback method.
     *
     * @param schemaName Schema name.
     */
    public void onSchemaCreated(String schemaName);

    /**
     * Callback method.
     *
     * @param schemaName Schema name.
     */
    public void onSchemaDropped(String schemaName);

    /**
     * Callback method.
     *
     * @param schemaName Schema name.
     * @param typeDesc Type descriptor.
     * @param cacheInfo Cache info.
     */
    public void onSqlTypeCreated(String schemaName, GridQueryTypeDescriptor typeDesc,
        GridCacheContextInfo<?, ?> cacheInfo);

    /**
     * Callback method.
     *
     * @param schemaName Schema name.
     * @param typeDesc Type descriptor.
     * @param cacheInfo Cache info.
     */
    public void onSqlTypeUpdated(String schemaName, GridQueryTypeDescriptor typeDesc,
        GridCacheContextInfo<?, ?> cacheInfo);

    /**
     * Callback method.
     *
     * @param schemaName Schema name.
     * @param typeDesc Type descriptor.
     */
    public void onSqlTypeDropped(String schemaName, GridQueryTypeDescriptor typeDesc);

    /**
     * Callback on index creation.
     *
     * @param schemaName Schema name.
     * @param tblName Table name.
     * @param idxName Index name.
     * @param idxDesc Index descriptor.
     * @param idx Index.
     */
    public void onIndexCreated(String schemaName, String tblName, String idxName, GridQueryIndexDescriptor idxDesc,
        @Nullable Index idx);

    /**
     * Callback on index drop.
     *
     * @param schemaName Schema name.
     * @param tblName Table name.
     * @param idxName Index name.
     */
    public void onIndexDropped(String schemaName, String tblName, String idxName);

    /**
     * Callback on function creation.
     *
     * @param schemaName Schema name.
     * @param name Function name.
     * @param method Public static method, implementing this function.
     */
    public void onFunctionCreated(String schemaName, String name, Method method);

    /**
     * Callback method.
     *
     * @param schemaName Schema name.
     * @param sysView System view.
     */
    public void onSystemViewCreated(String schemaName, SystemView<?> sysView);
}
