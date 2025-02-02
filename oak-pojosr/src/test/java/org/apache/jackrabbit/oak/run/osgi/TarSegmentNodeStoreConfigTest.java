/*
 *   Licensed to the Apache Software Foundation (ASF) under one or more
 *   contributor license agreements.  See the NOTICE file distributed with
 *   this work for additional information regarding copyright ownership.
 *   The ASF licenses this file to You under the Apache License, Version 2.0
 *   (the "License"); you may not use this file except in compliance with
 *   the License.  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package org.apache.jackrabbit.oak.run.osgi;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.Map;
import org.apache.felix.connect.launch.PojoServiceRegistry;
import org.apache.jackrabbit.oak.spi.blob.BlobStore;
import org.apache.jackrabbit.oak.spi.state.NodeStore;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.osgi.framework.ServiceRegistration;

public class TarSegmentNodeStoreConfigTest extends AbstractRepositoryFactoryTest {

    private static final String SEGMENT_TAR_BUNDLE_FILTER =
            "(&(|(Bundle-SymbolicName=org.apache.jackrabbit*)(Bundle-SymbolicName=org.apache.sling*)"
                    + "(Bundle-SymbolicName=org.apache.felix*)(Bundle-SymbolicName=org.apache.aries*)"
                    + "(Bundle-SymbolicName=groovy-all))"
                    + "(!(Bundle-SymbolicName=org.apache.jackrabbit.oak-segment)))";

    private PojoServiceRegistry registry;


    @Before
    public void adjustConfig() {
        config.put(OakOSGiRepositoryFactory.REPOSITORY_BUNDLE_FILTER, SEGMENT_TAR_BUNDLE_FILTER);
        registry = repositoryFactory.initializeServiceRegistry(config);
    }

    @Override
    protected PojoServiceRegistry getRegistry() {
        return registry;
    }

    @Test
    public void testDynamicBlobStore() {
        createConfig(
                Map.of("org.apache.jackrabbit.oak.segment.SegmentNodeStoreService",
                        Map.of("customBlobStore", true)));
        assertNull(registry.getServiceReference(NodeStore.class.getName()));
        ServiceRegistration<?> registration = registry.registerService(BlobStore.class.getName(),
                Mockito.mock(BlobStore.class),
                null);
        assertNotNull(getServiceWithWait(NodeStore.class));
        registration.unregister();
        assertNull(registry.getServiceReference(NodeStore.class.getName()));
    }
}
