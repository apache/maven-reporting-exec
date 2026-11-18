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
package org.codehaus.doxia.sink;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps the legacy {@link Sink} in place: report plugins such as jacoco-maven-plugin 0.8.16 still declare
 * {@code generate(org.codehaus.doxia.sink.Sink, Locale)} and fail to load during {@code mvn site} without it.
 */
@SuppressWarnings("deprecation")
class LegacySinkTest {

    @Test
    void legacySinkStillExtendsTheDoxiaSink() {
        assertTrue(org.apache.maven.doxia.sink.Sink.class.isAssignableFrom(Sink.class));
    }
}
