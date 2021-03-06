/*
 * Copyright 2018 Red Hat, Inc, and individual contributors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.wildfly.swarm.plugin.gradle;

import javax.inject.Inject;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.tooling.provider.model.ToolingModelBuilder;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

import static org.wildfly.swarm.plugin.gradle.GradleDependencyResolutionHelper.determinePluginVersion;

/**
 * The base plugin that initializes the tooling model.
 */
@SuppressWarnings("UnstableApiUsage")
public class AbstractThorntailPlugin implements Plugin<Project> {

    /**
     * The name of the Gradle configuration.
     */
    public static final String THORNTAIL_EXTENSION = "thorntail";

    private final ToolingModelBuilderRegistry registry;
    private Project project;

    /**
     * Constructs a new instance of {@code AbstractThorntailPlugin}, which is initialized with the Gradle tooling model builder registry.
     *
     * @param registry the Gradle project's {@code ToolingModelBuilderRegistry}.
     */
    @Inject
    public AbstractThorntailPlugin(ToolingModelBuilderRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void apply(Project project) {
        this.project = project;
        // Skip if the plugin has already been applied.
        if (project.getExtensions().findByType(ThorntailExtension.class) == null) {
            ThorntailExtension extension = new ThorntailExtension(project);
            project.getExtensions().add(THORNTAIL_EXTENSION, extension);

            // Register a model builder as well.
            registry.register(new ThorntailToolingModelBuilder());

            //noinspection Convert2Lambda
            project.afterEvaluate(new Action<Project>() {
                @Override
                public void execute(Project p) {
                    addDependency("runtimeOnly", "io.thorntail:bootstrap:" + determinePluginVersion());
                }
            });
        }
    }

    /**
     * Convenience method for adding a dependency identified by the specified GAV coordinates to the given configuration.
     *
     * @param configuration the name of the configuration.
     * @param gav           the GAV coordinates.
     */
    protected void addDependency(String configuration, String gav) {
        final ConfigurationContainer cfgContainer = project.getConfigurations();
        DependencyHandler handler = project.getDependencies();
        // Add the bootstrap library to the runtime classpath.
        if (cfgContainer.findByName(configuration) != null) {
            handler.add(configuration, gav);
        } else {
            System.err.printf("Unable to lookup configuration by name: %s, Thorntail integration might not work.%n",
                    configuration);
        }
    }

    /**
     * The extension of Gradle's ToolingModelBuilder. This class is responsible for exporting the {@link ThorntailConfiguration}
     * model to external tools, e.g., the Arquillian adapter for Gradle projects.
     */
    static class ThorntailToolingModelBuilder implements ToolingModelBuilder {

        /**
         * Return true if the requested {@code model name} is of {@link ThorntailConfiguration} type.
         *
         * @param modelName the name of the requested model.
         * @return true if the requested {@code model name} is of {@link ThorntailConfiguration} type.
         */
        @Override
        public boolean canBuild(String modelName) {
            return ThorntailConfiguration.class.getName().equals(modelName);
        }

        /**
         * Build and return the {@link ThorntailConfiguration} model for the requested project.
         *
         * @param modelName the name of the requested model.
         * @param project   the Gradle project reference.
         * @return the fully built {@link ThorntailConfiguration} model.
         */
        @Override
        public Object buildAll(String modelName, Project project) {
            if (!canBuild(modelName)) {
                throw new IllegalArgumentException("Unsupported model requested: " + modelName);
            }
            ThorntailConfiguration configuration = project.getExtensions().findByType(ThorntailExtension.class);
            if (configuration != null) {
                // Load the dependencies before sending them over the wire.
                configuration.getDependencies();
                configuration.getTestDependencies();
            }
            return configuration;
        }
    }
}
