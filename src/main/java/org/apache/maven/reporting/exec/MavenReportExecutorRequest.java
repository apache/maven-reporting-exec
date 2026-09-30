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

import org.apache.maven.api.Project;
import org.apache.maven.api.Session;
import org.apache.maven.api.xml.XmlNode;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.codehaus.plexus.util.xml.Xpp3Dom;

/**
 * Bean which contains necessary informations to build {@link MavenReportExecution} with {@link MavenReportExecutor}:
 * the intent is to store some informations regarding the current Maven execution.
 *
 * @author Olivier Lamy
 */
public class MavenReportExecutorRequest {

    private Session session;

    private String executionId;

    private Project project;

    private ReportPlugin[] reportPlugins;

    public Session getSession() {
        return session;
    }

    public void setSession(Session session) {
        this.session = session;
    }

    public String getExecutionId() {
        return executionId;
    }

    public void setExecutionId(String executionId) {
        this.executionId = executionId;
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public ReportPlugin[] getReportPlugins() {
        return reportPlugins;
    }

    public void setReportPlugins(ReportPlugin[] reportPlugins) {
        this.reportPlugins = reportPlugins;
    }

    /**
     * Set the report plugin directly from <code>${project.reporting.plugins}</code> parameter value.
     *
     * @param reportPlugins the report plugins from <code>&lt;reporting&gt;</code> section
     * @since 1.4
     */
    public void setReportPlugins(org.apache.maven.api.model.ReportPlugin[] reportPlugins) {
        setReportPlugins(new ReportPlugin[reportPlugins.length]);

        int i = 0;
        for (org.apache.maven.api.model.ReportPlugin r : reportPlugins) {
            ReportPlugin p = new ReportPlugin();
            p.setGroupId(r.getGroupId());
            p.setArtifactId(r.getArtifactId());
            p.setVersion(r.getVersion());
            if (r.getConfiguration() != null) {
                p.setConfiguration(new XmlPlexusConfiguration(toXpp3Dom(r.getConfiguration())));
            }

            List<ReportSet> prs = new ArrayList<>();
            for (org.apache.maven.api.model.ReportSet rs : r.getReportSets()) {
                ReportSet ps = new ReportSet();
                ps.setId(rs.getId());
                ps.setReports(new ArrayList<>(rs.getReports()));
                if (rs.getConfiguration() != null) {
                    ps.setConfiguration(new XmlPlexusConfiguration(toXpp3Dom(rs.getConfiguration())));
                }
                prs.add(ps);
            }
            p.setReportSets(prs);

            this.reportPlugins[i++] = p;
        }
    }

    private static Xpp3Dom toXpp3Dom(XmlNode node) {
        Xpp3Dom dom = new Xpp3Dom(node.name());
        dom.setValue(node.value());
        node.attributes().forEach(dom::setAttribute);
        node.children().forEach(child -> dom.addChild(toXpp3Dom(child)));
        return dom;
    }
}
