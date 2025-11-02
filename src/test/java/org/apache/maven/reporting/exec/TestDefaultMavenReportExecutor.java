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

import java.io.File;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.maven.DefaultMaven;
import org.apache.maven.Maven;
import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryFactory;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.cli.MavenCli;
import org.apache.maven.cli.configuration.SettingsXmlConfigurationProcessor;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.DefaultMavenExecutionResult;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequestPopulator;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginManagement;
import org.apache.maven.plugin.testing.stubs.MavenProjectStub;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.DefaultSettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuilder;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.apache.maven.settings.building.SettingsBuildingRequest;
import org.codehaus.plexus.ContainerConfiguration;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusTestCase;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.WorkspaceReader;
import org.eclipse.aether.repository.WorkspaceRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Olivier Lamy
 */
public class TestDefaultMavenReportExecutor extends PlexusTestCase {
    @Override
    protected void customizeContainerConfiguration(
            @SuppressWarnings("unused") final ContainerConfiguration configuration) {
        super.customizeContainerConfiguration(configuration);
        configuration.setAutoWiring(true).setClassPathScanning(PlexusConstants.SCANNING_CACHE);
    }

    MavenExecutionRequest request = null;

    ArtifactRepository localArtifactRepository;

    @Test
    public void testSimpleLookup() throws Exception {
        MavenReportExecutor mavenReportExecutor = lookup(MavenReportExecutor.class);
        assertNotNull(mavenReportExecutor);
    }

    @Test
    public void testSimpleBuildReports() throws Exception {
        ReportSet reportSet = new ReportSet();
        reportSet.getReports().add("test-javadoc");
        reportSet.getReports().add("javadoc");

        MavenProject mavenProject = getMavenProject();
        List<MavenReportExecution> mavenReportExecutions = buildReports(mavenProject, reportSet);

        assertNotNull(mavenReportExecutions);
        assertEquals(2, mavenReportExecutions.size());
        assertEquals(
                "testapidocs/index",
                mavenReportExecutions.get(0).getMavenReport().getOutputName());
        assertEquals(
                "apidocs/index", mavenReportExecutions.get(1).getMavenReport().getOutputName());
    }

    @Test
    public void testMultipleReportSets() throws Exception {
        ReportSet reportSet = new ReportSet();
        reportSet.getReports().add("javadoc");
        ReportSet reportSet2 = new ReportSet();
        reportSet2.getReports().add("test-javadoc");
        reportSet2.getReports().add("javadoc");

        MavenProject mavenProject = getMavenProject();
        List<MavenReportExecution> mavenReportExecutions = buildReports(mavenProject, reportSet, reportSet2);

        assertNotNull(mavenReportExecutions);
        assertEquals(3, mavenReportExecutions.size());
        assertEquals(
                "apidocs/index", mavenReportExecutions.get(0).getMavenReport().getOutputName());
        assertEquals(
                "testapidocs/index",
                mavenReportExecutions.get(1).getMavenReport().getOutputName());
        assertEquals(
                "apidocs/index", mavenReportExecutions.get(2).getMavenReport().getOutputName());
    }

    @Test
    public void testReportingPluginWithDependenciesInPluginManagement() throws Exception {
        ReportSet reportSet = new ReportSet();
        reportSet.getReports().add("javadoc");

        MavenProject mavenProject = getMavenProject();
        Plugin plugin = new Plugin();
        plugin.setGroupId("org.apache.maven.plugins");
        plugin.setArtifactId("maven-javadoc-plugin");
        plugin.setVersion("3.4.0");
        Dependency dependency = new Dependency();
        dependency.setGroupId("commons-lang");
        dependency.setArtifactId("commons-lang");
        dependency.setVersion("2.6");
        plugin.getDependencies().add(dependency);
        mavenProject.getBuild().setPluginManagement(new PluginManagement());
        mavenProject.getBuild().getPluginManagement().addPlugin(plugin);
        List<MavenReportExecution> mavenReportExecutions = buildReports(mavenProject, reportSet);

        assertNotNull(mavenReportExecutions);
        assertEquals(1, mavenReportExecutions.size());
        List<Dependency> dependencies = mavenReportExecutions.get(0).getPlugin().getDependencies();
        assertEquals(1, dependencies.size());
        assertEquals("commons-lang", dependencies.get(0).getGroupId());
        assertEquals("2.6", dependencies.get(0).getVersion());
    }

    private List<MavenReportExecution> buildReports(MavenProject mavenProject, ReportSet... javadocReportSets)
            throws Exception {
        ClassLoader orig = Thread.currentThread().getContextClassLoader();
        ClassRealm realm = getContainer().getContainerRealm();

        Thread.currentThread().setContextClassLoader(realm);
        try {
            MavenReportExecutorRequest mavenReportExecutorRequest = new MavenReportExecutorRequest();

            mavenReportExecutorRequest.setLocalRepository(getLocalRepo());

            mavenReportExecutorRequest.setProject(mavenProject);

            MavenSession mavenSession = getMavenSession(getLocalRepo(), mavenProject);
            mavenSession.setCurrentProject(mavenProject);
            mavenSession.setProjects(Arrays.asList(mavenProject));
            mavenReportExecutorRequest.setMavenSession(mavenSession);

            ReportPlugin reportPlugin = new ReportPlugin();
            reportPlugin.setGroupId("org.apache.maven.plugins");
            reportPlugin.setArtifactId("maven-javadoc-plugin");
            reportPlugin.setVersion("3.4.0");

            for (ReportSet reportSet : javadocReportSets) {
                reportPlugin.getReportSets().add(reportSet);
            }

            List<ReportPlugin> reportPlugins = Arrays.asList(reportPlugin);

            mavenReportExecutorRequest.setReportPlugins(reportPlugins.toArray(new ReportPlugin[1]));

            MavenReportExecutor mavenReportExecutor = lookup(MavenReportExecutor.class);

            return mavenReportExecutor.buildMavenReports(mavenReportExecutorRequest);
        } finally {
            Thread.currentThread().setContextClassLoader(orig);
        }
    }

    protected MavenSession getMavenSession(ArtifactRepository localRepository, final MavenProject mavenProject)
            throws Exception {
        request = new DefaultMavenExecutionRequest();
        request.setLocalRepository(localRepository);

        request.setWorkspaceReader(new WorkspaceReader() {
            @Override
            public WorkspaceRepository getRepository() {
                return new WorkspaceRepository();
            }

            @Override
            public File findArtifact(Artifact artifact) {
                return null;
            }

            @Override
            public List<String> findVersions(Artifact artifact) {
                return Collections.emptyList();
            }
        });
        final Settings settings = getSettings();

        getContainer().lookup(MavenExecutionRequestPopulator.class).populateFromSettings(request, settings);

        getContainer().lookup(MavenExecutionRequestPopulator.class).populateDefaults(request);

        request.setLocalRepository(getLocalRepo());
        request.setLocalRepositoryPath(getLocalRepo().getBasedir());
        request.setCacheNotFound(false);

        request.setSystemProperties(System.getProperties());

        MavenExecutionResult result = new DefaultMavenExecutionResult();

        RepositorySystemSession repositorySystemSession = buildRepositorySystemSession(request);

        return new MavenSession(getContainer(), repositorySystemSession, request, result) {
            @Override
            public MavenProject getTopLevelProject() {
                return mavenProject;
            }

            @Override
            public Settings getSettings() {
                return settings;
            }

            @Override
            public List<MavenProject> getProjects() {
                return Arrays.asList(mavenProject);
            }

            @Override
            public MavenProject getCurrentProject() {
                return mavenProject;
            }
        };
    }

    private ArtifactRepository getLocalRepo() throws Exception {
        ArtifactRepositoryFactory artifactRepositoryFactory = lookup(ArtifactRepositoryFactory.class);
        ArtifactRepositoryLayout defaultArtifactRepositoryLayout = lookup(ArtifactRepositoryLayout.class, "default");
        String updatePolicyFlag = ArtifactRepositoryPolicy.UPDATE_POLICY_ALWAYS;
        String checksumPolicyFlag = ArtifactRepositoryPolicy.CHECKSUM_POLICY_WARN;
        ArtifactRepositoryPolicy snapshotsPolicy =
                new ArtifactRepositoryPolicy(true, updatePolicyFlag, checksumPolicyFlag);
        ArtifactRepositoryPolicy releasesPolicy =
                new ArtifactRepositoryPolicy(true, updatePolicyFlag, checksumPolicyFlag);
        String localRepoPath =
                System.getProperty("localRepository", MavenCli.USER_MAVEN_CONFIGURATION_HOME.getPath() + "/repository");
        return artifactRepositoryFactory.createArtifactRepository(
                "local",
                Paths.get(localRepoPath).toUri().toASCIIString(),
                defaultArtifactRepositoryLayout,
                snapshotsPolicy,
                releasesPolicy);
    }

    public Settings getSettings() throws ComponentLookupException, SettingsBuildingException {

        SettingsBuildingRequest settingsBuildingRequest = new DefaultSettingsBuildingRequest();

        settingsBuildingRequest.setGlobalSettingsFile(SettingsXmlConfigurationProcessor.DEFAULT_GLOBAL_SETTINGS_FILE);

        settingsBuildingRequest.setUserSettingsFile(SettingsXmlConfigurationProcessor.DEFAULT_USER_SETTINGS_FILE);

        settingsBuildingRequest.getSystemProperties().putAll(System.getProperties());

        return getContainer()
                .lookup(SettingsBuilder.class)
                .build(settingsBuildingRequest)
                .getEffectiveSettings();
    }

    protected MavenProject getMavenProject() {
        MavenProjectStub mavenProjectStub = new MavenProjectStub() {
            @Override
            public List<RemoteRepository> getRemotePluginRepositories() {
                if (super.getRemotePluginRepositories() == null) {
                    return RepositoryUtils.toRepos(request.getRemoteRepositories());
                }
                return super.getRemotePluginRepositories();
            }

            @Override
            public List<ArtifactRepository> getRemoteArtifactRepositories() {
                if (super.getRemotePluginRepositories() == null) {
                    return request.getRemoteRepositories();
                }
                return super.getRemoteArtifactRepositories();
            }

            @Override
            public String getName() {
                return "foo";
            }

            @Override
            public String getVersion() {
                return "1.0-SNAPSHOT";
            }

            @Override
            public boolean isExecutionRoot() {
                return true;
            }

            @Override
            public List<String> getCompileSourceRoots() {
                return Arrays.asList("src/main/java");
            }

            @Override
            public List<String> getTestCompileSourceRoots() {
                return Arrays.asList("src/test/java");
            }
        };

        mavenProjectStub.setPackaging("jar");

        Build build = new Build();

        build.setOutputDirectory("target");

        build.setSourceDirectory("src/main/java");

        build.setTestSourceDirectory("src/test/java");

        mavenProjectStub.setBuild(build);

        return mavenProjectStub;
    }

    private RepositorySystemSession buildRepositorySystemSession(MavenExecutionRequest request)
            throws ComponentLookupException {
        DefaultMaven defaultMaven = (DefaultMaven) getContainer().lookup(Maven.class);

        return defaultMaven.newRepositorySession(request);
    }
}
