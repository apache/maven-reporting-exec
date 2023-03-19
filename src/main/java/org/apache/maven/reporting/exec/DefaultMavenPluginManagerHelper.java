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

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.util.List;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.InvalidPluginDescriptorException;
import org.apache.maven.plugin.MavenPluginManager;
import org.apache.maven.plugin.PluginContainerException;
import org.apache.maven.plugin.PluginDescriptorParsingException;
import org.apache.maven.plugin.PluginResolutionException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.filter.ExclusionsDependencyFilter;

import static java.util.Objects.requireNonNull;

/**
 * <p>DefaultMavenPluginManagerHelper class.</p>
 */
@Singleton
@Named
public class DefaultMavenPluginManagerHelper implements MavenPluginManagerHelper {
    private final MavenPluginManager mavenPluginManager;

    @Inject
    public DefaultMavenPluginManagerHelper(MavenPluginManager mavenPluginManager) {
        this.mavenPluginManager = requireNonNull(mavenPluginManager);
    }

    private DependencyFilter createExclusionsDependencyFilter(List<String> artifactIdsList) {
        return new ExclusionsDependencyFilter(artifactIdsList);
    }

    /** {@inheritDoc} */
    @Override
    public PluginDescriptor getPluginDescriptor(Plugin plugin, MavenSession session)
            throws PluginResolutionException, PluginDescriptorParsingException, InvalidPluginDescriptorException {
        RepositorySystemSession repositorySystemSession = session.getRepositorySession();
        List<RemoteRepository> remotePluginRepositories =
                session.getCurrentProject().getRemotePluginRepositories();

        return mavenPluginManager.getPluginDescriptor(plugin, remotePluginRepositories, repositorySystemSession);
    }

    /** {@inheritDoc} */
    @Override
    public void setupPluginRealm(
            PluginDescriptor pluginDescriptor,
            MavenSession session,
            ClassLoader parent,
            List<String> imports,
            List<String> excludeArtifactIds)
            throws PluginResolutionException, PluginContainerException {
        mavenPluginManager.setupPluginRealm(
                pluginDescriptor, session, parent, imports, createExclusionsDependencyFilter(excludeArtifactIds));
    }
}
