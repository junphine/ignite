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

package org.apache.ignite.internal.processors.security.impl;

import java.net.InetSocketAddress;
import java.util.UUID;
import org.apache.ignite.plugin.security.SecurityPermissionSet;
import org.apache.ignite.plugin.security.SecuritySubject;
import org.apache.ignite.plugin.security.SecuritySubjectType;

/**
 * Security subject for tests.
 */
public class SimpleSecuritySubject implements SecuritySubject {
    /** Id. */
    private UUID id;

    /** Type. */
    private SecuritySubjectType type = SecuritySubjectType.REMOTE_NODE;

    /** Login. */
    private Object login;

    /** Address. */
    private InetSocketAddress addr;

    /** Permissions. */
    private SecurityPermissionSet perms;

    /**
     * Default constructor.
     */
    public SimpleSecuritySubject() {
        // No-op.
    }

    /**
     * @param id Id.
     * @param login Login.
     * @param addr Address.
     * @param perms Permissions.
     */
    public SimpleSecuritySubject(UUID id,
        Object login,
        InetSocketAddress addr,
        SecurityPermissionSet perms) {
        this.id = id;
        this.login = login;
        this.addr = addr;
        this.perms = perms;
    }

    /** {@inheritDoc} */
    @Override public UUID id() {
        return id;
    }

    /**
     * @param id Id.
     */
    public SimpleSecuritySubject setId(UUID id) {
        this.id = id;

        return this;
    }

    /** {@inheritDoc} */
    @Override public SecuritySubjectType type() {
        return type;
    }

    /**
     * @param type Type.
     */
    public SimpleSecuritySubject setType(SecuritySubjectType type) {
        this.type = type;

        return this;
    }

    /** {@inheritDoc} */
    @Override public Object login() {
        return login;
    }

    /**
     * @param login Login.
     */
    public SimpleSecuritySubject setLogin(Object login) {
        this.login = login;

        return this;
    }

    /** {@inheritDoc} */
    @Override public InetSocketAddress address() {
        return addr;
    }

    /**
     * @param addr Address.
     */
    public SimpleSecuritySubject setAddr(InetSocketAddress addr) {
        this.addr = addr;

        return this;
    }

    /** {@inheritDoc} */
    @Override public SecurityPermissionSet permissions() {
        return perms;
    }

    /**
     * @param perms Permissions.
     */
    public SimpleSecuritySubject setPerms(SecurityPermissionSet perms) {
        this.perms = perms;

        return this;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return "TestSecuritySubject{" +
            "id=" + id +  ", type=" + type +  ", login=" + login + '}';
    }
}
