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

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Comparator;
import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.internal.binary.BinaryUtils;
import org.apache.ignite.internal.binary.BinaryEnumObjectImpl;
import org.apache.ignite.internal.binary.BinaryObjectImpl;
import org.apache.ignite.internal.processors.cache.CacheObject;
import org.apache.ignite.internal.processors.cache.CacheObjectValueContext;
import org.h2.engine.Mode;
import org.h2.message.DbException;
import org.h2.util.Bits;
import org.h2.util.JdbcUtils;
import org.h2.util.Utils;
import org.h2.value.CompareMode;
import org.h2.value.Value;
import org.h2.value.ValueEnum;
import org.h2.value.ValueEnumBase;
import org.h2.value.ValueInt;
import org.h2.value.ValueJavaObject;
import org.h2.value.ValueString;

/**
 * H2 Value over {@link CacheObject}. Replacement for {@link ValueJavaObject}.
 */
public class GridH2ValueCacheObject extends Value {
    /** */
    private CacheObject obj;

    /** Object value context. */
    private CacheObjectValueContext valCtx;

    /**
     * Constructor.
     *
     * @param obj Object.
     * @param valCtx Object value context.
     */
    public GridH2ValueCacheObject(CacheObject obj, CacheObjectValueContext valCtx) {
        assert obj != null;

        if (BinaryUtils.isBinaryObjectImpl(obj))
            obj = (CacheObject)BinaryUtils.detach(obj);

        this.obj = obj;
        this.valCtx = valCtx;
    }

    /**
     * @return Cache object.
     */
    public CacheObject getCacheObject() {
        return obj;
    }

    /**
     * @return Value context.
     */
    public CacheObjectValueContext valueContext() {
        return valCtx;
    }

    /** {@inheritDoc} */
    @Override public String getSQL() {
        throw new UnsupportedOperationException();
    }

    /** {@inheritDoc} */
    @Override public int getType() {
        return Value.JAVA_OBJECT;
    }

    /** {@inheritDoc} */
    @Override public long getPrecision() {
        return 0;
    }

    /** {@inheritDoc} */
    @Override public int getDisplaySize() {
        return 64;
    }

    /** {@inheritDoc} */
    @Override public String getString() {
        return getObject().toString();
    }

    /** {@inheritDoc} */
    @Override public byte[] getBytes() {
        return Utils.cloneByteArray(getBytesNoCopy());
    }

    /** {@inheritDoc} */
    @Override public byte[] getBytesNoCopy() {
        if (obj.cacheObjectType() == CacheObject.TYPE_REGULAR) {
            // Result must be the same as `marshaller.marshall(obj.value(coctx, false));`
            try {
                return obj.valueBytes(valCtx);
            }
            catch (IgniteCheckedException e) {
                throw DbException.convert(e);
            }
        }

        // For user-provided and array types.
        return JdbcUtils.serialize(obj, null);
    }

    /** {@inheritDoc} */
    @Override public Object getObject() {
        return getObject(false);
    }

    /**
     * @param cpy Copy flag.
     * @return Value.
     */
    public Object getObject(boolean cpy) {
        return obj.isPlatformType() ? obj.value(valCtx, cpy) : obj;
    }

    /** {@inheritDoc} */
    @Override public void set(PreparedStatement prep, int parameterIndex) throws SQLException {
        prep.setObject(parameterIndex, getObject(), Types.JAVA_OBJECT);
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Override protected int compareSecure(Value v, CompareMode mode) {
        Object o1 = getObject();
        Object o2 = v.getObject();

        boolean o1Comparable = o1 instanceof Comparable;
        boolean o2Comparable = o2 instanceof Comparable;

        if (o1Comparable && o2Comparable &&
            Utils.haveCommonComparableSuperclass(o1.getClass(), o2.getClass())) {
            Comparable<Object> c1 = (Comparable<Object>)o1;

            return c1.compareTo(o2);
        }

        // Group by types.
        if (o1.getClass() != o2.getClass()) {
            if (o1Comparable != o2Comparable)
                return o1Comparable ? -1 : 1;

            return o1.getClass().getName().compareTo(o2.getClass().getName());
        }

        // Compare hash codes.
        int h1 = hashCode();
        int h2 = v.hashCode();

        if (h1 == h2) {
            if (o1.equals(o2))
                return 0;

            return Bits.compareNotNullSigned(getBytesNoCopy(), v.getBytesNoCopy());
        }

        return h1 > h2 ? 1 : -1;
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return getObject().hashCode();
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object other) {
        if (!(other instanceof Value))
            return false;

        Value otherVal = (Value)other;

        return otherVal.getType() == Value.JAVA_OBJECT
            && getObject().equals(otherVal.getObject());
    }

    /** {@inheritDoc} */
    @Override public Value convertPrecision(long precision, boolean force) {
        return this;
    }

    /** {@inheritDoc} */
    @Override public int getMemory() {
        return 0;
    }
    
    /** {@inheritDoc} add@byron */
    @Override public int getInt() {
    	if(obj	instanceof BinaryEnumObjectImpl) {
    		BinaryEnumObjectImpl enumObj = (BinaryEnumObjectImpl) obj;
    		return enumObj.enumOrdinal();
    	}
        return super.getInt();
    }
    
    /**
     * Compare a value to the specified type.
     * add@byron
     * @param targetType the type of the returned value
     * @return the converted value
     */
    @Override public Value convertTo(int targetType, int precision, Mode mode, Object column, String[] enumerators) {
        if(obj	instanceof BinaryEnumObjectImpl) {
        	BinaryEnumObjectImpl enumObj = (BinaryEnumObjectImpl) obj;
        	if(Value.ENUM == targetType) {            	
            	return ValueEnum.get(enumObj.enumName(), enumObj.enumOrdinal());
            }
        	if(Value.INT == targetType) {            	
            	return ValueInt.get(enumObj.enumOrdinal());
            }
        	if(Value.STRING == targetType) {            	
            	return ValueString.get(enumObj.enumName());
            }
        }
        return super.convertTo(targetType, precision, mode,column,enumerators);
    }
}
