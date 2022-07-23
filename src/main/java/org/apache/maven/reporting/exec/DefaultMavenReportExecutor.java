package org.apache.maven.reporting.exec;

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

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.apache.maven.lifecycle.LifecycleExecutor;
import org.apache.maven.model.Build;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.MavenPluginManager;
import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoNotFoundException;
import org.apache.maven.plugin.PluginConfigurationException;
import org.apache.maven.plugin.PluginContainerException;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugin.version.DefaultPluginVersionRequest;
import org.apache.maven.plugin.version.PluginVersionRequest;
import org.apache.maven.plugin.version.PluginVersionResolutionException;
import org.apache.maven.plugin.version.PluginVersionResolver;
import org.apache.maven.plugin.version.PluginVersionResult;
import org.apache.maven.project.MavenProject;
import org.apache.maven.reporting.MavenReport;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;

/**
 * <p>
 * This component will build some {@link MavenReportExecution} from {@link MavenReportExecutorRequest}. If a
 * {@link MavenReport} needs to fork a lifecycle, this fork is executed here. It will ask the core to get some
 * informations in order to correctly setup {@link MavenReport}.
 * </p>
 * <p>
 * <b>Note</b> if no version is defined in the report plugin, the version will be searched with
 * {@link #resolvePluginVersion(ReportPlugin, MavenReportExecutorRequest) resolvePluginVersion(...)} method:
 * </p>
 * <ol>
 * <li>use the one defined in the reportPlugin configuration,</li>
 * <li>search similar (same groupId and artifactId) plugin in the build/plugins section of the pom,</li>
 * <li>search similar (same groupId and artifactId) plugin in the build/pluginManagement section of the pom,</li>
 * <li>ask {@link PluginVersionResolver} to get a fallback version (display a warning as it's not a recommended use).
 * </li>
 * </ol>
 * <p>
 * Following steps are done:
 * </p>
 * <ul>
 * <li>get {@link PluginDescriptor} from the {@link MavenPluginManager} (through
 * {@link MavenPluginManagerHelper#getPluginDescriptor(Plugin, org.apache.maven.execution.MavenSession)
 * MavenPluginManagerHelper.getPluginDescriptor(...)} to protect from core API change)</li>
 * <li>setup a {@link ClassLoader}, with the Site plugin classloader as parent for the report execution. <br>
 * Notice that some classes are imported from the current Site plugin ClassRealm: see {@link #IMPORTS}. Corresponding
 * artifacts are excluded from the artifact resolution: <code>doxia-site-renderer</code>, <code>doxia-sink-api</code>
 *  and <code>maven-reporting-api</code>.<br>
 * Work is done using {@link MavenPluginManager} (through
 * {@link MavenPluginManagerHelper#setupPluginRealm(PluginDescriptor, MavenSession, ClassLoader, List, List)
 * MavenPluginManagerHelper.setupPluginRealm(...)} to protect from core API change)</li>
 * <li>setup the mojo using {@link MavenPluginManager#getConfiguredMojo(Class, MavenSession, MojoExecution)
 * MavenPluginManager.getConfiguredMojo(...)}</li>
 * <li>verify with {@link LifecycleExecutor#calculateForkedExecutions(MojoExecution, MavenSession)
 * LifecycleExecutor.calculateForkedExecutions(...)} if any forked execution is needed: if yes, execute the forked
 * execution here</li>
 * </ul>
 *
 * @author Olivier Lamy
 */
@Singleton
@Named
public class DefaultMavenReportExecutor
    implements MavenReportExecutor
{
    private static final Logger LOGGER = LoggerFactory.getLogger( DefaultMavenReportExecutor.class );

    private final MavenPluginManager mavenPluginManager;

    private final MavenPluginManagerHelper mavenPluginManagerHelper;

    private final LifecycleExecutor lifecycleExecutor;

    private final PluginVersionResolver pluginVersionResolver;

    private static final List<String> IMPORTS = Arrays.asList( "org.apache.maven.reporting.MavenReport",
                                                               "org.apache.maven.reporting.MavenMultiPageReport",
                                                               "org.apache.maven.doxia.siterenderer.Renderer",
                                                               "org.apache.maven.doxia.sink.SinkFactory",
                                                               // TODO Will be removed with Doxia 2.0.0
                                                               "org.codehaus.doxia.sink.Sink",
                                                               "org.apache.maven.doxia.sink.Sink",
                                                               "org.apache.maven.doxia.sink.SinkEventAttributes",
                                                               // TODO Will be removed with Doxia 2.0.0
                                                               "org.apache.maven.doxia.logging.LogEnabled",
                                                               // TODO Will be removed with Doxia 2.0.0
                                                               "org.apache.maven.doxia.logging.Log" );

    private static final List<String> EXCLUDES = Arrays.asList( "doxia-site-renderer", "doxia-sink-api",
                                                                "maven-reporting-api" );

    @Inject
    public DefaultMavenReportExecutor( MavenPluginManager mavenPluginManager,
                                       MavenPluginManagerHelper mavenPluginManagerHelper,
                                       LifecycleExecutor lifecycleExecutor,
                                       PluginVersionResolver pluginVersionResolver )
    {
        this.mavenPluginManager = requireNonNull( mavenPluginManager );
        this.mavenPluginManagerHelper = requireNonNull( mavenPluginManagerHelper );
        this.lifecycleExecutor = requireNonNull( lifecycleExecutor );
        this.pluginVersionResolver = requireNonNull( pluginVersionResolver );
    }

    @Override
    public List<MavenReportExecution> buildMavenReports( MavenReportExecutorRequest mavenReportExecutorRequest )
        throws MojoExecutionException
    {
        if ( mavenReportExecutorRequest.getReportPlugins() == null )
        {
            return Collections.emptyList();
        }

        Set<String> reportPluginKeys = new HashSet<>();
        List<MavenReportExecution> reportExecutions = new ArrayList<>();

        String pluginKey = "";
        try
        {
            for ( ReportPlugin reportPlugin : mavenReportExecutorRequest.getReportPlugins() )
            {
                pluginKey = reportPlugin.getGroupId() + ':' + reportPlugin.getArtifactId();

                if ( !reportPluginKeys.add( pluginKey ) )
                {
                    LOGGER.info( "Plugin {} will be executed more than one time", pluginKey );
                }

                reportExecutions.addAll( buildReportPlugin( mavenReportExecutorRequest, reportPlugin ) );
            }
        }
        catch ( Exception e )
        {
            throw new MojoExecutionException( "Failed to get report for " + pluginKey, e );
        }

        return reportExecutions;
    }

    protected List<MavenReportExecution> buildReportPlugin( MavenReportExecutorRequest mavenReportExecutorRequest,
                                                            ReportPlugin reportPlugin )
        throws Exception
    {
        // step 1: prepare the plugin
        Plugin plugin = new Plugin();
        plugin.setGroupId( reportPlugin.getGroupId() );
        plugin.setArtifactId( reportPlugin.getArtifactId() );
        plugin.setVersion( resolvePluginVersion( reportPlugin, mavenReportExecutorRequest ) );
        LOGGER.info( "Configuring report plugin {}", plugin.getId() );

        mergePluginToReportPlugin( mavenReportExecutorRequest, plugin, reportPlugin );

        PluginDescriptor pluginDescriptor =
            mavenPluginManagerHelper.getPluginDescriptor( plugin, mavenReportExecutorRequest.getMavenSession() );

        // step 2: prepare the goals
        List<GoalWithConf> goalsWithConfiguration = new ArrayList<>();
        boolean hasUserDefinedReports = prepareGoals( reportPlugin, pluginDescriptor, goalsWithConfiguration );

        // step 3: prepare the reports
        List<MavenReportExecution> reports = new ArrayList<>( goalsWithConfiguration.size() );
        for ( GoalWithConf report : goalsWithConfiguration )
        {
            MavenReportExecution mavenReportExecution =
                prepareReportExecution( mavenReportExecutorRequest, report, hasUserDefinedReports );

            if ( mavenReportExecution != null )
            {
                // ok, report is ready to generate
                reports.add( mavenReportExecution );
            }
        }

        if ( !reports.isEmpty() )
        {
            // log reports, either configured or detected
            StringBuilder buff = new StringBuilder();
            for ( MavenReportExecution mre : reports )
            {
                if ( buff.length() > 0 )
                {
                    buff.append( ", " );
                }
                buff.append( mre.getGoal() );
            }
            LOGGER.info( "{} report{} {} for {}:{}: {}", reports.size(), ( reports.size() > 1 ? "s" : "" ),
                ( hasUserDefinedReports ? "configured" : "detected" ), plugin.getArtifactId(),
                plugin.getVersion(), buff );
        }

        return reports;
    }

    private boolean prepareGoals( ReportPlugin reportPlugin, PluginDescriptor pluginDescriptor,
                               List<GoalWithConf> goalsWithConfiguration )
    {
        if ( reportPlugin.getReportSets().isEmpty() && reportPlugin.getReports().isEmpty() )
        {
            // by default, use every goal, which will be filtered later to only keep reporting goals
            List<MojoDescriptor> mojoDescriptors = pluginDescriptor.getMojos();
            for ( MojoDescriptor mojoDescriptor : mojoDescriptors )
            {
                goalsWithConfiguration.add( new GoalWithConf( reportPlugin, pluginDescriptor, mojoDescriptor.getGoal(),
                                                              mojoDescriptor.getConfiguration() ) );
            }

            return false;
        }

        Set<String> goals = new HashSet<>();
        for ( String report : reportPlugin.getReports() )
        {
            if ( goals.add( report ) )
            {
                goalsWithConfiguration.add( new GoalWithConf( reportPlugin, pluginDescriptor, report,
                                                              reportPlugin.getConfiguration() ) );
            }
            else
            {
                LOGGER.warn( "{} report is declared twice in default reports", report );
            }
        }

        for ( ReportSet reportSet : reportPlugin.getReportSets() )
        {
            goals = new HashSet<>();
            for ( String report : reportSet.getReports() )
            {
                if ( goals.add( report ) )
                {
                    goalsWithConfiguration.add( new GoalWithConf( reportPlugin, pluginDescriptor, report,
                                                                  reportSet.getConfiguration() ) );
                }
                else
                {
                    LOGGER.warn( "{} report is declared twice in {} reportSet", report, reportSet.getId() );
                }
            }
        }

        return true;
    }

    private MavenReportExecution prepareReportExecution( MavenReportExecutorRequest mavenReportExecutorRequest,
                                                         GoalWithConf report, boolean hasUserDefinedReports )
        throws Exception
    {
        ReportPlugin reportPlugin = report.getReportPlugin();
        PluginDescriptor pluginDescriptor = report.getPluginDescriptor();

        MojoDescriptor mojoDescriptor = pluginDescriptor.getMojo( report.getGoal() );
        if ( mojoDescriptor == null )
        {
            throw new MojoNotFoundException( report.getGoal(), pluginDescriptor );
        }

        MavenProject project = mavenReportExecutorRequest.getProject();
        if ( !hasUserDefinedReports && mojoDescriptor.isAggregator() && !canAggregate( project ) )
        {
            // aggregator mojos automatically added from plugin are only run at execution root
            return null;
        }

        MojoExecution mojoExecution = new MojoExecution( pluginDescriptor.getPlugin(), report.getGoal(), null );

        mojoExecution.setMojoDescriptor( mojoDescriptor );

        mavenPluginManagerHelper.setupPluginRealm( pluginDescriptor, mavenReportExecutorRequest.getMavenSession(),
                                                   Thread.currentThread().getContextClassLoader(), IMPORTS,
                                                   EXCLUDES );

        if ( !isMavenReport( mojoExecution, pluginDescriptor ) )
        {
            if ( hasUserDefinedReports )
            {
                // reports were explicitly written in the POM
                LOGGER.warn( "Ignoring {}:{}"
                    + " goal since it is not a report: should be removed from reporting configuration in POM",
                    mojoExecution.getPlugin().getId(), report.getGoal() );
            }
            return null;
        }

        Xpp3Dom pluginMgmtConfiguration = null;
        if ( project.getBuild() != null && project.getBuild().getPluginManagement() != null )
        {
            Plugin pluginMgmt = find( reportPlugin, project.getBuild().getPluginManagement().getPlugins() );

            if ( pluginMgmt != null )
            {
                pluginMgmtConfiguration = (Xpp3Dom) pluginMgmt.getConfiguration();
            }
        }

        mojoExecution.setConfiguration( mergeConfiguration( mojoDescriptor.getMojoConfiguration(),
                                                            pluginMgmtConfiguration,
                                                            reportPlugin.getConfiguration(),
                                                            report.getConfiguration(),
                                                            mojoDescriptor.getParameterMap().keySet() ) );

        MavenReport mavenReport =
            getConfiguredMavenReport( mojoExecution, pluginDescriptor, mavenReportExecutorRequest );

        MavenReportExecution mavenReportExecution =
            new MavenReportExecution( report.getGoal(), mojoExecution.getPlugin(), mavenReport,
                                      pluginDescriptor.getClassRealm() );

        lifecycleExecutor.calculateForkedExecutions( mojoExecution,
                                                     mavenReportExecutorRequest.getMavenSession() );

        if ( !mojoExecution.getForkedExecutions().isEmpty() )
        {
            String reportDescription = pluginDescriptor.getArtifactId() + ":" + report.getGoal() + " report";

            String execution;
            if ( StringUtils.isNotEmpty( mojoDescriptor.getExecutePhase() ) )
            {
                // forked phase
                execution = "'"
                    + ( StringUtils.isEmpty( mojoDescriptor.getExecuteLifecycle() ) ? ""
                                    : ( '[' + mojoDescriptor.getExecuteLifecycle() + ']' ) )
                    + mojoDescriptor.getExecutePhase() + "' forked phase execution";
            }
            else
            {
                // forked goal
                execution = "'" + mojoDescriptor.getExecuteGoal() + "' forked goal execution";
            }

            LOGGER.info( "Preparing {} requires {}", reportDescription, execution );

            lifecycleExecutor.executeForkedExecutions( mojoExecution,
                                                       mavenReportExecutorRequest.getMavenSession() );

            LOGGER.info( "{} for {} preparation done", execution, reportDescription );
        }

        return mavenReportExecution;
    }

    private boolean canAggregate( MavenProject project )
    {
        return project.isExecutionRoot() && "pom".equals( project.getPackaging() ) && ( project.getModules() != null )
            && !project.getModules().isEmpty();
    }

    private MavenReport getConfiguredMavenReport( MojoExecution mojoExecution, PluginDescriptor pluginDescriptor,
                                                  MavenReportExecutorRequest mavenReportExecutorRequest )
        throws PluginContainerException, PluginConfigurationException
    {
        try
        {
            Mojo mojo =
                mavenPluginManager.getConfiguredMojo( Mojo.class, mavenReportExecutorRequest.getMavenSession(),
                                                      mojoExecution );

            return (MavenReport) mojo;
        }
        catch ( ClassCastException e )
        {
            if ( LOGGER.isDebugEnabled() )
            {
                LOGGER.warn( "Skipping ClassCastException", e );
            }
            else
            {
                LOGGER.warn( "Skipping ClassCastException" );
            }
            return null;
        }
        catch ( PluginContainerException e )
        {
            /*
             * ignore old plugin which are using removed PluginRegistry [INFO] Caused by:
             * java.lang.NoClassDefFoundError: org/apache/maven/plugin/registry/PluginRegistry
             */
            if ( e.getCause() != null && e.getCause() instanceof NoClassDefFoundError
                && e.getMessage().contains( "PluginRegistry" ) )
            {
                if ( LOGGER.isDebugEnabled() )
                {
                    LOGGER.warn( "Skipping NoClassDefFoundError with PluginRegistry", e );
                }
                else
                {
                    LOGGER.warn( "Skipping NoClassDefFoundError with PluginRegistry" );
                }
                return null;
            }
            throw e;
        }
    }

    private boolean isMavenReport( MojoExecution mojoExecution, PluginDescriptor pluginDescriptor )
    {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();

        // get the plugin's goal Mojo class
        Class<?> mojoClass;
        try
        {
            Thread.currentThread().setContextClassLoader( mojoExecution.getMojoDescriptor().getRealm() );

            mojoClass =
                pluginDescriptor.getClassRealm().loadClass( mojoExecution.getMojoDescriptor().getImplementation() );
        }
        catch ( ClassNotFoundException e )
        {
            if ( LOGGER.isDebugEnabled() )
            {
                LOGGER.warn( "Skipping ClassNotFoundException mojoExecution.goal {}", mojoExecution.getGoal(), e );
            }
            else
            {
                LOGGER.warn( "Skipping ClassNotFoundException mojoExecution.goal {}", mojoExecution.getGoal() );
            }
            return false;
        }
        finally
        {
            Thread.currentThread().setContextClassLoader( originalClassLoader );
        }

        // check if it is a report
        try
        {
            Thread.currentThread().setContextClassLoader( mojoExecution.getMojoDescriptor().getRealm() );
            MojoDescriptor mojoDescriptor = pluginDescriptor.getMojo( mojoExecution.getGoal() );

            boolean isMavenReport = MavenReport.class.isAssignableFrom( mojoClass );

            if ( LOGGER.isDebugEnabled() )
            {
                if ( mojoDescriptor != null && mojoDescriptor.getImplementationClass() != null )
                {
                    LOGGER.debug( "Class {} is MavenReport: ",
                        mojoDescriptor.getImplementationClass().getName(), isMavenReport );
                }

                if ( !isMavenReport )
                {
                    LOGGER.debug( "Skipping non MavenReport {}", mojoExecution.getMojoDescriptor().getId() );
                }
            }

            return isMavenReport;
        }
        catch ( LinkageError e )
        {
            if ( LOGGER.isDebugEnabled() )
            {
                LOGGER.warn( "Skipping LinkageError mojoExecution.goal {}", mojoExecution.getGoal(), e );
            }
            else
            {
                LOGGER.warn( "Skipping LinkageError mojoExecution.goal {}", mojoExecution.getGoal() );
            }
            return false;
        }
        finally
        {
            Thread.currentThread().setContextClassLoader( originalClassLoader );
        }
    }

    /**
     * Merge plugin configuration and reportset configuration to mojo configuration to get effective
     * mojo configuration.
     *
     * @param mojoConf configuration done at mojo descriptor level
     * @param pluginMgmtConfig configuration done at build.pluginManagement level
     * @param pluginConf configuration done at reporting plugin level
     * @param reportSetConf configuration done at reportSet level
     * @param parameters set of supported parameters: any other parameter will be removed
     * @return the effective configuration to be used
     */
    private Xpp3Dom mergeConfiguration( PlexusConfiguration mojoConf, Xpp3Dom pluginMgmtConfig,
                                        PlexusConfiguration pluginConf, PlexusConfiguration reportSetConf,
                                        Set<String> parameters )
    {
        Xpp3Dom mojoConfig = ( mojoConf != null ) ? convert( mojoConf ) : new Xpp3Dom( "configuration" );

        if ( pluginMgmtConfig != null || pluginConf != null || reportSetConf != null )
        {
            Xpp3Dom pluginConfig = ( pluginConf == null ) ? new Xpp3Dom( "fake" ) : convert( pluginConf );

            // merge pluginConf into reportSetConf
            Xpp3Dom mergedConfig = Xpp3DomUtils.mergeXpp3Dom( convert( reportSetConf ), pluginConfig );
            // then merge pluginMgmtConfig
            mergedConfig = Xpp3DomUtils.mergeXpp3Dom( mergedConfig, pluginMgmtConfig );
            // then merge mojoConf
            mergedConfig = Xpp3DomUtils.mergeXpp3Dom( mergedConfig, mojoConfig );

            // clean result
            Xpp3Dom cleanedConfig = new Xpp3Dom( "configuration" );
            if ( mergedConfig.getChildren() != null )
            {
                for ( Xpp3Dom parameter : mergedConfig.getChildren() )
                {
                    if ( parameters.contains( parameter.getName() ) )
                    {
                        cleanedConfig.addChild( parameter );
                    }
                }
            }

            mojoConfig = cleanedConfig;
        }

        return mojoConfig;
    }

    private Xpp3Dom convert( PlexusConfiguration config )
    {
        if ( config == null )
        {
            return null;
        }

        Xpp3Dom dom = new Xpp3Dom( config.getName() );
        dom.setValue( config.getValue( null ) );

        for ( String attrib : config.getAttributeNames() )
        {
            dom.setAttribute( attrib, config.getAttribute( attrib, null ) );
        }

        for ( int n = config.getChildCount(), i = 0; i < n; i++ )
        {
            dom.addChild( convert( config.getChild( i ) ) );
        }

        return dom;
    }

    /**
     * Resolve report plugin version. Steps to find a plugin version stop after each step if a non <code>null</code>
     * value has been found:
     * <ol>
     * <li>use the one defined in the reportPlugin configuration,</li>
     * <li>search similar (same groupId and artifactId) mojo in the build/plugins section of the pom,</li>
     * <li>search similar (same groupId and artifactId) mojo in the build/pluginManagement section of the pom,</li>
     * <li>ask {@link PluginVersionResolver} to get a fallback version and display a warning as it's not a recommended
     * use.</li>
     * </ol>
     *
     * @param reportPlugin the report plugin to resolve the version
     * @param mavenReportExecutorRequest the current report execution context
     * @return the report plugin version
     * @throws PluginVersionResolutionException on plugin version resolution issue
     */
    protected String resolvePluginVersion( ReportPlugin reportPlugin,
                                           MavenReportExecutorRequest mavenReportExecutorRequest )
        throws PluginVersionResolutionException
    {
        String reportPluginKey = reportPlugin.getGroupId() + ':' + reportPlugin.getArtifactId();
        LOGGER.debug( "Resolving version for {}", reportPluginKey );

        // look for version defined in the reportPlugin configuration
        if ( reportPlugin.getVersion() != null )
        {
            LOGGER.debug( "Resolved {} version from the reporting.plugins section: {}",
                reportPluginKey, reportPlugin.getVersion() );
            return reportPlugin.getVersion();
        }

        MavenProject project = mavenReportExecutorRequest.getProject();

        // search in the build section
        if ( project.getBuild() != null )
        {
            Plugin plugin = find( reportPlugin, project.getBuild().getPlugins() );

            if ( plugin != null && plugin.getVersion() != null )
            {
                LOGGER.debug( "Resolved {} version from the build.plugins section: {}",
                    reportPluginKey, plugin.getVersion() );
                return plugin.getVersion();
            }
        }

        // search in pluginManagement section
        if ( project.getBuild() != null && project.getBuild().getPluginManagement() != null )
        {
            Plugin plugin = find( reportPlugin, project.getBuild().getPluginManagement().getPlugins() );

            if ( plugin != null && plugin.getVersion() != null )
            {
                LOGGER.debug( "Resolved {} version from the build.pluginManagement.plugins section: {}",
                    reportPluginKey, plugin.getVersion() );
                return plugin.getVersion();
            }
        }

        LOGGER.warn( "Report plugin {} has an empty version.", reportPluginKey );
        LOGGER.warn( "" );
        LOGGER.warn( "It is highly recommended to fix these problems"
            + " because they threaten the stability of your build." );
        LOGGER.warn( "" );
        LOGGER.warn( "For this reason, future Maven versions might no"
            + " longer support building such malformed projects." );

        Plugin plugin = new Plugin();
        plugin.setGroupId( reportPlugin.getGroupId() );
        plugin.setArtifactId( reportPlugin.getArtifactId() );

        PluginVersionRequest pluginVersionRequest =
            new DefaultPluginVersionRequest( plugin, mavenReportExecutorRequest.getMavenSession() );

        PluginVersionResult result = pluginVersionResolver.resolve( pluginVersionRequest );
        LOGGER.debug( "Resolved {} version from repository: {}", reportPluginKey, result.getVersion() );
        return result.getVersion();
    }

    /**
     * Search similar (same groupId and artifactId) plugin as a given report plugin.
     *
     * @param reportPlugin the report plugin to search for a similar plugin
     * @param plugins the candidate plugins
     * @return the first similar plugin
     */
    private Plugin find( ReportPlugin reportPlugin, List<Plugin> plugins )
    {
        if ( plugins == null )
        {
            return null;
        }
        for ( Plugin plugin : plugins )
        {
            if ( Objects.equals( plugin.getArtifactId(), reportPlugin.getArtifactId() )
                && Objects.equals( plugin.getGroupId(), reportPlugin.getGroupId() ) )
            {
                return plugin;
            }
        }
        return null;
    }

    /**
     * TODO other stuff to merge ?
     * <p>
     * this method will "merge" some part of the plugin declaration existing in the build section to the fake plugin
     * build for report execution:
     * <ul>
     * <li>dependencies</li>
     * </ul>
     * </p>
     * The plugin could only be present in the dependency management section.
     *
     * @param mavenReportExecutorRequest
     * @param buildPlugin
     * @param reportPlugin
     */
    private void mergePluginToReportPlugin( MavenReportExecutorRequest mavenReportExecutorRequest, Plugin buildPlugin,
                                            ReportPlugin reportPlugin )
    {
        Build build = mavenReportExecutorRequest.getProject().getBuild();
        Plugin configuredPlugin = find( reportPlugin, build.getPlugins() );
        if ( configuredPlugin == null && build.getPluginManagement() != null )
        {
            configuredPlugin = find( reportPlugin, build.getPluginManagement().getPlugins() );
        }
        if ( configuredPlugin != null )
        {
            if ( !configuredPlugin.getDependencies().isEmpty() )
            {
                buildPlugin.getDependencies().addAll( configuredPlugin.getDependencies() );
            }
        }
    }

    private static class GoalWithConf
    {
        private final String goal;

        private final PlexusConfiguration configuration;

        private final ReportPlugin reportPlugin;

        private final PluginDescriptor pluginDescriptor;

        GoalWithConf( ReportPlugin reportPlugin, PluginDescriptor pluginDescriptor, String goal,
                             PlexusConfiguration configuration )
        {
            this.reportPlugin = reportPlugin;
            this.pluginDescriptor = pluginDescriptor;
            this.goal = goal;
            this.configuration = configuration;
        }

        public ReportPlugin getReportPlugin()
        {
            return reportPlugin;
        }

        public PluginDescriptor getPluginDescriptor()
        {
            return pluginDescriptor;
        }

        public String getGoal()
        {
            return goal;
        }

        public PlexusConfiguration getConfiguration()
        {
            return configuration;
        }
    }
}
