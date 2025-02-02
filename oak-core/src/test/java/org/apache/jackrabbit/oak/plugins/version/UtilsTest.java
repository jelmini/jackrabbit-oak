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
package org.apache.jackrabbit.oak.plugins.version;

import org.apache.jackrabbit.oak.InitialContentHelper;
import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.plugins.memory.EmptyNodeState;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class UtilsTest {

    @Test
    public void frozenNodeReferenceable() {
        assertTrue(Utils.isFrozenNodeReferenceable(InitialContentHelper.INITIAL_CONTENT_FROZEN_NODE_REFERENCEABLE));
    }

    @Test
    public void frozenNodeNotReferenceable() {
        assertFalse(Utils.isFrozenNodeReferenceable(InitialContentHelper.INITIAL_CONTENT));
    }

    @Test
    public void frozenNodeDefinitionMissing() {
        // assume empty repository on recent Oak without referenceable nt:frozenNode
        assertFalse(Utils.isFrozenNodeReferenceable(EmptyNodeState.EMPTY_NODE));
    }

    @Test(expected = IllegalArgumentException.class)
    public void uuidFromNodeWithNullUuidValue() {
        NodeState nodeState = Mockito.mock(NodeState.class);
        Mockito.when(nodeState.getProperty("jcr:uuid")).thenReturn(null);
        Utils.uuidFromNode(nodeState);
        fail("Shouldn't reach here");
    }

    @Test
    public void uuidFromNodeBuilder() {
        NodeBuilder nodeBuilder = Mockito.mock(NodeBuilder.class);
        NodeState nodeState = Mockito.mock(NodeState.class);
        PropertyState propertyState = Mockito.mock(PropertyState.class);
        Mockito.when(nodeBuilder.getNodeState()).thenReturn(nodeState);
        Mockito.when(nodeState.getProperty("jcr:uuid")).thenReturn(propertyState);
        Mockito.when(propertyState.getValue(Mockito.any())).thenReturn("uuid");
        assertEquals("uuid", Utils.uuidFromNode(nodeState));
    }

    @Test
    public void uuidFromNodeState() {
        NodeState nodeState = Mockito.mock(NodeState.class);
        PropertyState propertyState = Mockito.mock(PropertyState.class);
        Mockito.when(nodeState.getProperty("jcr:uuid")).thenReturn(propertyState);
        Mockito.when(propertyState.getValue(Mockito.any())).thenReturn("uuid");
        assertEquals("uuid", Utils.uuidFromNode(nodeState));
    }
}
