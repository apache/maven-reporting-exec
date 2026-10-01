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
package org.apache.maven.reporting.exec;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MavenReportExecutorRequestTest {

    @Test
    void setReportPluginsCopiesReportSetReports() {
        org.apache.maven.api.model.ReportSet modelReportSet = org.apache.maven.api.model.ReportSet.newBuilder()
                .id("default")
                .reports(Arrays.asList("index", "summary"))
                .build();

        org.apache.maven.api.model.ReportPlugin modelPlugin = org.apache.maven.api.model.ReportPlugin.newBuilder()
                .groupId("org.apache.maven.plugins")
                .artifactId("maven-project-info-reports-plugin")
                .reportSets(Collections.singletonList(modelReportSet))
                .build();

        MavenReportExecutorRequest request = new MavenReportExecutorRequest();
        request.setReportPlugins(new org.apache.maven.api.model.ReportPlugin[] {modelPlugin});

        ReportSet copied = request.getReportPlugins()[0].getReportSets().get(0);
        copied.getReports().add("dependencies");

        assertEquals(Arrays.asList("index", "summary"), modelReportSet.getReports());
        assertEquals(Arrays.asList("index", "summary", "dependencies"), copied.getReports());
    }
}
