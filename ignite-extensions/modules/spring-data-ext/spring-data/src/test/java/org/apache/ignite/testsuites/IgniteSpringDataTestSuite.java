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

package org.apache.ignite.testsuites;

import org.apache.ignite.springdata.IgniteClientSpringDataCompoundKeyTest;
import org.apache.ignite.springdata.IgniteClientSpringDataCrudSelfTest;
import org.apache.ignite.springdata.IgniteClientSpringDataQueriesSelfTest;
import org.apache.ignite.springdata.IgniteSpringDataCompoundExtendedKeyTest;
import org.apache.ignite.springdata.IgniteSpringDataCompoundKeyTest;
import org.apache.ignite.springdata.IgniteSpringDataConnectionConfigurationTest;
import org.apache.ignite.springdata.IgniteSpringDataCrudSelfExpressionTest;
import org.apache.ignite.springdata.IgniteSpringDataCrudSelfTest;
import org.apache.ignite.springdata.IgniteSpringDataQueriesSelfTest;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 * Ignite Spring Data test suite.
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({
    IgniteSpringDataCrudSelfTest.class,
    IgniteSpringDataQueriesSelfTest.class,
    IgniteSpringDataCrudSelfExpressionTest.class,
    IgniteSpringDataCompoundKeyTest.class,
    IgniteSpringDataCompoundExtendedKeyTest.class,
    IgniteSpringDataConnectionConfigurationTest.class,
    IgniteClientSpringDataCrudSelfTest.class,
    IgniteClientSpringDataQueriesSelfTest.class,
    IgniteClientSpringDataCompoundKeyTest.class
})
public class IgniteSpringDataTestSuite {
}
