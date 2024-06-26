/*
 * Copyright 2019 GridGain Systems, Inc. and Contributors.
 *
 * Licensed under the GridGain Community Edition License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.gridgain.com/products/software/community-edition/gridgain-community-edition-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.console.agent.db;

import java.util.Collection;
import org.apache.ignite.internal.util.typedef.internal.S;

/**
 * Database schema names with catalog name.
 */
public class DbSchema {
    /** Catalog name. */
    private final String catalog;

    /** Schema names. */
    private final Collection<String> schemas;

    /**
     * @param catalog Catalog name.
     * @param schemas Schema names.
     */
    public DbSchema(String catalog, Collection<String> schemas) {
        this.catalog = catalog;
        this.schemas = schemas;
    }

    /**
     * @return Catalog name.
     */
    public String getCatalog() {
        return catalog;
    }

    /**
     * @return Schema names.
     */
    public Collection<String> getSchemas() {
        return schemas;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(DbSchema.class, this);
    }
}
