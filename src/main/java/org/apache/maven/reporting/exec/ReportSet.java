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
import java.util.List;

import org.codehaus.plexus.configuration.PlexusConfiguration;

/**
 * Represents a set of reports and a configuration to be used to generate them.
 * @see org.apache.maven.model.ReportSet
 */
class ReportSet {

    private String id = "default";

    private PlexusConfiguration configuration;

    private List<String> reports;

    public String getId() {
        return this.id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public PlexusConfiguration getConfiguration() {
        return this.configuration;
    }

    public void setConfiguration(PlexusConfiguration configuration) {
        this.configuration = configuration;
    }

    public List<String> getReports() {
        if (this.reports == null) {
            this.reports = new ArrayList<>();
        }

        return this.reports;
    }

    public void setReports(List<String> reports) {
        this.reports = reports;
    }

    @Override
    public String toString() {
        return "ReportSet{id='" + getId() + "', reports=" + reports + "}";
    }
}
