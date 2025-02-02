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
package org.apache.jackrabbit.oak.spi.security.authorization;

import java.util.Set;

import org.apache.jackrabbit.oak.api.Root;
import org.apache.jackrabbit.oak.namepath.NamePathMapper;
import org.apache.jackrabbit.oak.spi.security.authorization.permission.OpenPermissionProvider;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertSame;

public class OpenAuthorizationConfigurationTest {

    private final OpenAuthorizationConfiguration config = new OpenAuthorizationConfiguration();

    @Test(expected = UnsupportedOperationException.class)
    public void testGetAccessControlManager() {
        config.getAccessControlManager(Mockito.mock(Root.class), NamePathMapper.DEFAULT);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetRestrictionProvider() {
        config.getRestrictionProvider();
    }

    @Test
    public void testGetPermissionProvider() {
        assertSame(OpenPermissionProvider.getInstance(), config.getPermissionProvider(Mockito.mock(Root.class), "default", Set.of()));
    }
}