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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.apache.ignite.IgniteCheckedException;

/**
 * Parser to decode/encode Redis protocol (RESP) requests.
 */
public class GridRedisProtocolParser {
    /** + prefix. */
    private static final byte SIMPLE_STRING = 43;

    /** $ */
    private static final byte BULK_STRING = 36;

    /** : */
    private static final byte INTEGER = 58;

    /** * */
    static final byte ARRAY = 42;

    /** - */
    private static final byte ERROR = 45;

    /** Carriage return code. */
    private static final byte CR = 13;

    /** Line feed code. */
    private static final byte LF = 10;

    /** CRLF. */
    static final byte[] CRLF = new byte[] {13, 10};

    /** Generic error prefix. */
    private static final byte[] ERR_GENERIC = "ERR ".getBytes();

    /** Prefix for errors on operations with the wrong type. */
    private static final byte[] ERR_TYPE = "WRONGTYPE ".getBytes();

    /** Prefix for errors on authentication. */
    private static final byte[] ERR_AUTH = "NOAUTH ".getBytes();

    /** Null bulk string for nil response. */
    private static final byte[] NIL = "$-1\r\n".getBytes();

    /** OK response. */
    private static final byte[] OK = "OK".getBytes();

    /**
     * Error while reading int.
     * {@code -1} used to mark null array.
     * @see #NIL
     */
    public static final int ERROR_INT = -2;
    
    static MethodHandles.Lookup lookup = MethodHandles.lookup();
    static MethodHandle isLatin1Handle = null;
    static {
   
		try {
			Method isLatin1 = String.class.getDeclaredMethod("isLatin1");
			isLatin1.setAccessible(true);
	    	isLatin1Handle = lookup.unreflect(isLatin1);
		} catch (NoSuchMethodException | SecurityException | IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	
    }

    /**
     * Checks first byte is {@link #ARRAY}.
     * @param buf Buffer.
     * @return {@code False} if no data available in buffer.
     * @throws IgniteCheckedException If failed.
     */
    public static boolean ensureArrayStart(ByteBuffer buf) throws IgniteCheckedException {
        if (!buf.hasRemaining())
            return false;

        byte b = buf.get();

        if (b != ARRAY)
            throw new IgniteCheckedException("Invalid request byte! " + b);

        return true;
    }

    /**
     * Reads a bulk string.
     *
     * @param buf Buffer.
     * @return Bulk string.
     * @throws IgniteCheckedException If failed.
     */
    public static String readBulkStr(ByteBuffer buf) throws IgniteCheckedException {        

        byte[] bulkStr = readBulkBytes(buf);
        if (bulkStr==null)
            return null;
        
        return new String(bulkStr,StandardCharsets.ISO_8859_1);
    }
    
    /**
     * Reads a bulk string.
     *
     * @param buf Buffer.
     * @return Bulk string.
     * @throws IgniteCheckedException If failed.
     */
    public static byte[] readBulkBytes(ByteBuffer buf) throws IgniteCheckedException {
        if (!buf.hasRemaining())
            return null;

        byte b = buf.get();

        if (b != BULK_STRING)
            throw new IgniteCheckedException("Invalid bulk string prefix! " + b);

        if (!buf.hasRemaining())
            return null;

        int len = readInt(buf);

        if (len == ERROR_INT || buf.remaining() < len)
            return null;

        byte[] bulkStr = new byte[len];

        buf.get(bulkStr, 0, len);

        if (buf.remaining() < 2)
            return null;

        byte b0 = buf.get();
        byte b1 = buf.get();

        if (b0 != CR || b1 != LF)
            throw new IgniteCheckedException("Invalid request syntax[len=" + len + ']');

        return bulkStr;
    }

    /**
     * Counts elements in buffer.
     *
     * @param buf Buffer.
     * @return Count of elements.
     */
    public static int readInt(ByteBuffer buf) throws IgniteCheckedException {
        if (!buf.hasRemaining())
            return ERROR_INT;

        byte[] arrLen = new byte[9];

        int idx = 0;
        byte b = buf.get();

        while (b != CR) {
            if (!buf.hasRemaining())
                return ERROR_INT;

            arrLen[idx++] = b;
            b = buf.get();
        }

        if (!buf.hasRemaining())
            return ERROR_INT;

        if (buf.get() != LF)
            throw new IgniteCheckedException("Invalid request syntax!");

        return Integer.parseInt(new String(arrLen, 0, idx));
    }

    /**
     * Converts a simple string data to a {@link ByteBuffer}.
     *
     * @param val String to be converted to a simple string.
     * @return Redis simple string.
     */
    public static ByteBuffer toSimpleString(String val) {
        byte[] b;
        boolean isLatin = false;
        if(isLatin1Handle!=null) {
        	try {
				isLatin = (Boolean)isLatin1Handle.invoke(val);
			} catch (Throwable e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        }
        if(isLatin) {
        	b = val.getBytes(StandardCharsets.ISO_8859_1);
        }
        else {
        	b = val.getBytes(StandardCharsets.UTF_8);
        }

        return toSimpleString(b);
    }

    /**
     * Creates a simple string data as a {@link ByteBuffer}.
     *
     * @param b Bytes for a simple string.
     * @return Redis simple string.
     */
    public static ByteBuffer toSimpleString(byte[] b) {
        ByteBuffer buf = ByteBuffer.allocate(b.length + 3);
        buf.put(SIMPLE_STRING);
        buf.put(b);
        buf.put(CRLF);

        buf.flip();

        return buf;
    }

    /**
     * @return Standard OK string.
     */
    public static ByteBuffer oKString() {
        return toSimpleString(OK);
    }

    /**
     * Creates a generic error response.
     *
     * @param errMsg Error message.
     * @return Error response.
     */
    public static ByteBuffer toGenericError(String errMsg) {
        return toError(errMsg, ERR_GENERIC);
    }

    /**
     * Creates an error response on operation against the wrong data type.
     *
     * @param errMsg Error message.
     * @return Error response.
     */
    public static ByteBuffer toTypeError(String errMsg) {
        return toError(errMsg, ERR_TYPE);
    }

    /**
     * Creates an error response.
     *
     * @param errMsg Error message.
     * @param errPrefix Error prefix.
     * @return Error response.
     */
    private static ByteBuffer toError(String errMsg, byte[] errPrefix) {
        byte[] b = errMsg.getBytes(StandardCharsets.UTF_8);

        ByteBuffer buf = ByteBuffer.allocate(b.length + errPrefix.length + 3);
        buf.put(ERROR);
        buf.put(errPrefix);
        buf.put(b);
        buf.put(CRLF);

        buf.flip();

        return buf;
    }

    /**
     * Converts an integer result to a RESP integer.
     *
     * @param integer Integer result.
     * @return REDIS integer.
     */
    public static ByteBuffer toInteger(String integer) {
        byte[] b = integer.getBytes();

        ByteBuffer buf = ByteBuffer.allocate(b.length + 3);
        buf.put(INTEGER);
        buf.put(b);
        buf.put(CRLF);

        buf.flip();

        return buf;
    }

    /**
     * Converts an integer result to a RESP integer.
     *
     * @param integer Integer result.
     * @return REDIS integer.
     */
    public static ByteBuffer toInteger(int integer) {
        return toInteger(String.valueOf(integer));
    }

    /**
     * Creates Nil response.
     *
     * @return Nil response.
     */
    public static ByteBuffer nil() {
        ByteBuffer buf = ByteBuffer.allocate(NIL.length);
        buf.put(NIL);

        buf.flip();

        return buf;
    }

    /**
     * Converts a resultant object to a bulk string.
     *
     * @param val Object.
     * @return Bulk string.
     */
    public static ByteBuffer toBulkString(Object val) {
        assert val != null;        
        
        if(val instanceof Short || val instanceof Integer || val instanceof Long) {
        	return toInteger(val.toString());
        }
        
        byte[] b;
        byte[] l;
        if(val instanceof byte[]) {
        	b = (byte[]) val;
            l = String.valueOf(b.length).getBytes();
        }
        else if(val instanceof String) {
        	boolean isLatin = false;
            if(isLatin1Handle!=null) {
            	try {
    				isLatin = (Boolean)isLatin1Handle.invoke(val);
    			} catch (Throwable e) {
    				// TODO Auto-generated catch block
    				e.printStackTrace();
    			}
            }
            if(isLatin) {
            	b = val.toString().getBytes(StandardCharsets.ISO_8859_1);
            }
            else {
            	b = val.toString().getBytes(StandardCharsets.UTF_8);
            }        	
            l = String.valueOf(b.length).getBytes();
        }
        else {
        	b = String.valueOf(val).getBytes();
            l = String.valueOf(b.length).getBytes();
        }

        ByteBuffer buf = ByteBuffer.allocate(b.length + l.length + 5);
        buf.put(BULK_STRING);
        buf.put(l);
        buf.put(CRLF);
        buf.put(b);
        buf.put(CRLF);

        buf.flip();

        return buf;
    }
	
    /**
     * Converts a resultant object to a bulk string.
     *
     * @param val Object.
     * @return Bulk string.
     */
    public static ByteBuffer toBulkList(Collection<Object[]> multResult) {
        assert multResult != null;
        ArrayList<ByteBuffer> fullRes = new ArrayList<>();
        int fullCapacity = 0;
        for(Object[] val: multResult ) {
        	int capacity = 0;
        	ArrayList<ByteBuffer> res = new ArrayList<>();
        	Object[] list = (Object[]) val;
        	for(Object item: list) {
        		ByteBuffer buf = toBulkString(item);
        		res.add(buf);
        		capacity += buf.limit();
        	}
        	byte[] arrSize = String.valueOf(res.size()).getBytes();

            ByteBuffer buf = ByteBuffer.allocateDirect(capacity + arrSize.length + 1 + CRLF.length);
            buf.put(ARRAY);
            buf.put(arrSize);
            buf.put(CRLF);
            res.forEach(o -> buf.put(o));
            fullRes.add(buf);
            fullCapacity += buf.limit();
        	
        }
        ByteBuffer buf = ByteBuffer.allocateDirect(fullCapacity);        
        fullRes.forEach(o -> buf.put(o));
        buf.flip();
        return buf;
    }
	
    /**
     * Converts a resultant map response to an array.
     *
     * @param vals Map.
     * @return Array response.
     */
    public static ByteBuffer toArray(Map<Object, Object> vals,List<String> params) {
    	ArrayList<Object> values = new ArrayList<>(vals.size()*2);
    	if(params!=null && params.size()>0) { //add@byron    		
    		params.forEach((k)->values.add(vals.get(k)));    		
    	} 
    	else {    		
        	vals.forEach((k,v)->{ values.add(k); values.add(v);});
    	}
        return toArray(values);
    }
	

    /**
     * Converts a resultant map response to an array.
     *
     * @param vals Map.
     * @return Array response.
     */
    public static ByteBuffer toArray(Map<Object, Object> vals) {
        return toArray(vals.values());
    }

    /**
     * Converts a resultant map response to an array,
     * the order of elements in the resulting array is defined by the order of elements in the {@code origin} collection.
     *
     * @param vals Map.
     * @param origin List that defines the order of the resulting array.
     * @return Array response.
     */
    public static ByteBuffer toOrderedArray(Map<Object, Object> vals, List<?> origin) {
        assert vals != null : "The resulting map is null.";
        assert origin != null : "The origin list is null.";

        int capacity = 0;

        ArrayList<ByteBuffer> res = new ArrayList<>();
        for (Object o : origin) {
            Object val = vals.get(o);

            if (val != null) {
                ByteBuffer b = toBulkString(val);
                res.add(b);
                capacity += b.limit();
            }
        }

        byte[] arrSize = String.valueOf(res.size()).getBytes();

        ByteBuffer buf = ByteBuffer.allocateDirect(capacity + arrSize.length + 1 + CRLF.length);
        buf.put(ARRAY);
        buf.put(arrSize);
        buf.put(CRLF);
        res.forEach(o -> buf.put(o));

        buf.flip();

        return buf;
    }

    /**
     * Converts a resultant collection response to an array.
     *
     * @param vals Array elements.
     * @return Array response.
     */
    public static ByteBuffer toArray(Collection<?> vals) {
        assert vals != null;
        int capacity = 0;
        ArrayList<ByteBuffer> res = new ArrayList<>();
        for (Object val : vals) {
            if (val != null) {
            	if(val instanceof Collection) {
            		ByteBuffer b = toArray((Collection)val);
	                res.add(b);
	                capacity += b.limit();
            	}
            	else if(val instanceof ByteBuffer) {
            		ByteBuffer b = (ByteBuffer)val;
	                res.add(b);
	                capacity += b.limit();
            	}
            	else {
	                ByteBuffer b = toBulkString(val);
	                res.add(b);
	                capacity += b.limit();
            	}
            }
        }
        

        byte[] arrSize = String.valueOf(res.size()).getBytes();

        ByteBuffer buf = ByteBuffer.allocateDirect(capacity + arrSize.length + 1 + CRLF.length);
        buf.put(ARRAY);
        buf.put(arrSize);
        buf.put(CRLF);

        for (ByteBuffer val : res) {
        	buf.put(val);
        }

        buf.flip();

        return buf;
    }
}
