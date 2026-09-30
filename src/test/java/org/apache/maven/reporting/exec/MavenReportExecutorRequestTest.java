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

import java.util.ArrayList;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MavenReportExecutorRequestTest {

    @Test
    void setReportPluginsCopiesReportSetReports() {
        org.apache.maven.model.ReportSet modelReportSet = new org.apache.maven.model.ReportSet();
        modelReportSet.setId("default");
        modelReportSet.setReports(new ArrayList<>(Arrays.asList("index", "summary")));

        org.apache.maven.model.ReportPlugin modelPlugin = new org.apache.maven.model.ReportPlugin();
        modelPlugin.setGroupId("org.apache.maven.plugins");
        modelPlugin.setArtifactId("maven-project-info-reports-plugin");
        modelPlugin.addReportSet(modelReportSet);

        MavenReportExecutorRequest request = new MavenReportExecutorRequest();
        request.setReportPlugins(new org.apache.maven.model.ReportPlugin[] {modelPlugin});

        modelReportSet.getReports().add("dependencies");

        ReportSet copied = request.getReportPlugins()[0].getReportSets().get(0);
        assertEquals(Arrays.asList("index", "summary"), copied.getReports());
    }
}
