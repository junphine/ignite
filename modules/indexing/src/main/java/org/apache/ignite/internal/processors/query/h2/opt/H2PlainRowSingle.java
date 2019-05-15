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

import org.apache.ignite.internal.util.typedef.internal.S;
import org.h2.value.Value;

/**
 * Single value row.
 */
public class H2PlainRowSingle extends H2Row {
    /** */
    private Value v;

    /**
     * @param v Value.
     */
    public H2PlainRowSingle(Value v) {
        this.v = v;
    }

    /** {@inheritDoc} */
    @Override public int getColumnCount() {
        return 1;
    }

    /** {@inheritDoc} */
    @Override public Value getValue(int idx) {
        assert idx == 0 : idx;

        return v;
    }

    /** {@inheritDoc} */
    @Override public void setValue(int idx, Value v) {
        assert idx == 0 : idx;

        this.v = v;
    }

    /** {@inheritDoc} */
    @Override public boolean indexSearchRow() {
        return true;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(H2PlainRowSingle.class, this);
    }
    
    /** {@inheritDoc} */
    @Override public boolean isEmpty() {        	
    	if(v==null) return true;
    	return false;
    }
}
