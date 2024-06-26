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

package org.apache.ignite.internal.igfs.common;

import java.io.DataOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.OutputStream;

/**
 * Data output stream implementing ObjectOutput but throwing exceptions on methods working with objects.
 */
public class IgfsDataOutputStream extends DataOutputStream implements ObjectOutput {
    /**
     * Creates a new data output stream to write data to the specified
     * underlying output stream. The counter <code>written</code> is
     * set to zero.
     *
     * @param   out   the underlying output stream, to be saved for later
     *                use.
     * @see     FilterOutputStream#out
     */
    public IgfsDataOutputStream(OutputStream out) {
        super(out);
    }

    /** {@inheritDoc} */
    @Override public void writeObject(Object obj) throws IOException {
        throw new IOException("This method must not be invoked on IGFS data output stream.");
    }
}
