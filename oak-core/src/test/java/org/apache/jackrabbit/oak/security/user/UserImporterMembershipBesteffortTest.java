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
package org.apache.jackrabbit.oak.security.user;

import org.apache.jackrabbit.oak.spi.xml.ImportBehavior;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertTrue;

public class UserImporterMembershipBesteffortTest extends UserImporterMembershipIgnoreTest {

    @NotNull
    @Override
    String getImportBehavior() {
        return ImportBehavior.NAME_BESTEFFORT;
    }

    @Test
    public void testUnknownMember() throws Exception {
        importer.startChildInfo(createNodeInfo("memberRef", NT_REP_MEMBER_REFERENCES), List.of(createPropInfo(REP_MEMBERS, unknownContentId)));
        importer.processReferences();

        assertTrue(groupTree.hasProperty(REP_MEMBERS));
    }

    @Test
    public void testMixedMembers() throws Exception {
        importer.startChildInfo(createNodeInfo("memberRef", NT_REP_MEMBER_REFERENCES), List.of(createPropInfo(REP_MEMBERS, unknownContentId, knownMemberContentId)));
        importer.processReferences();

        assertTrue(groupTree.hasProperty(REP_MEMBERS));
    }
}