/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.release.tool.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ComponentTypeTest {

    @Test
    void detectsPluginFromParent() {
        assertEquals(ComponentType.PLUGIN, ComponentType.fromParentArtifactId("maven-plugins"));
    }

    @Test
    void detectsSharedFromParent() {
        assertEquals(ComponentType.SHARED, ComponentType.fromParentArtifactId("maven-shared-components"));
    }

    @Test
    void detectsCoreFromParent() {
        assertEquals(ComponentType.CORE, ComponentType.fromParentArtifactId("maven"));
    }

    @Test
    void detectsParentPomFromParent() {
        assertEquals(ComponentType.PARENT_POM, ComponentType.fromParentArtifactId("maven-parent"));
        assertEquals(ComponentType.PARENT_POM, ComponentType.fromParentArtifactId("apache"));
    }

    @Test
    void detectsSkinFromParent() {
        assertEquals(ComponentType.SKIN, ComponentType.fromParentArtifactId("maven-skins"));
    }

    @Test
    void defaultsToPluginForUnknown() {
        assertEquals(ComponentType.PLUGIN, ComponentType.fromParentArtifactId("something-unknown"));
        assertEquals(ComponentType.PLUGIN, ComponentType.fromParentArtifactId(null));
    }
}
