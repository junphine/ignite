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

package org.apache.ignite.ml.knn.ann;

import java.util.TreeMap;

/**
 * The special class for fuzzy labels presenting the probability distribution
 * over the class labels.
 */
public class ProbableLabel<L> {
    /** Key is label, value is probability to be this class */
    public TreeMap<L, Double> clsLbls;

    /** */
    public ProbableLabel() {
    }

    /**
     * The key is class label,
     * the value is the probability to be an item of this class.
     *
     * @param clsLbls Class labels.
     */
    public ProbableLabel(TreeMap<L, Double> clsLbls) {
        this.clsLbls = clsLbls;
    }
}
