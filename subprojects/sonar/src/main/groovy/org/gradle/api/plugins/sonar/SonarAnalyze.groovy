/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.plugins.sonar

import org.gradle.api.internal.ConventionTask
import org.gradle.api.internal.tasks.CommandLineOption
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.sonar.model.ModelToPropertiesConverter
import org.gradle.api.plugins.sonar.model.SonarModel
import org.gradle.api.plugins.sonar.model.SonarRootModel
import org.gradle.api.tasks.TaskAction
import org.gradle.util.GFileUtils
import org.sonar.runner.Runner

/**
 * Analyzes a project hierarchy and writes the results to the
 * Sonar database.
 */
class SonarAnalyze extends ConventionTask {
    private static final Logger LOGGER = Logging.getLogger(SonarAnalyze)

    /**
     * Entry point to Sonar configuration.
     */
    SonarRootModel rootModel

    @TaskAction
    void analyze() {
        if (skipped(rootModel)) { return }

        GFileUtils.mkdirs(rootModel.bootstrapDir)
        def properties = new Properties()
        extractProperties(rootModel, properties, null)
        if (LOGGER.infoEnabled) {
            LOGGER.info("Properties to be passed to Sonar runner:\n{}",
                    properties.keySet().sort().collect { it + ": " + properties[it] }.join("\n"))
        }
        def runner = Runner.create(properties, rootModel.bootstrapDir)
        runner.execute()
    }

    @CommandLineOption(options = "server.url", description = "The URL for the Sonar web server.")
    void setServerUrl(String url) {
        rootModel.server.url = url
    }

    @CommandLineOption(options = "database.url", description = "The JDBC URL for the Sonar database.")
    void setDbUrl(String url) {
        rootModel.database.url = url
    }

    @CommandLineOption(options = "database.driverClassName", description = "The JDBC driver class name for the Sonar database.")
    void setDbDriverClassName(String driverClassName) {
        rootModel.database.driverClassName = driverClassName
    }

    @CommandLineOption(options = "database.username", description = "The JDBC username for the Sonar database.")
    void setDbUsername(String username) {
        rootModel.database.username = username
    }

    @CommandLineOption(options = "database.password", description = "The JDBC password for the Sonar database.")
    void setDbPassword(String password) {
        rootModel.database.password = password
    }

    @CommandLineOption(options = "showSql", description = "Whether to show SQL statements sent to the Sonar database.")
    void setShowSql(boolean flag) {
        rootModel.showSql = flag
    }

    @CommandLineOption(options = "showSqlResults", description = "Whether to show results of SQL statements sent to the Sonar database.")
    void setShowSqlResults(boolean flag) {
        rootModel.showSqlResults = flag
    }

    @CommandLineOption(options = "verbose", description = "Whether to activate debug logging for Sonar analysis.")
    void setVerbose(boolean flag) {
        rootModel.verbose = flag
    }

    @CommandLineOption(options = "forceAnalysis", description = "Whether to force re-running an analysis that appears to be running already.")
    void setForceAnalysis(boolean flag) {
        rootModel.forceAnalysis = flag
    }

    private void extractProperties(SonarModel model, Properties properties, String prefix) {
        def converter = createConverter(model, prefix)
        properties.putAll(converter.convert())
        setMandatoryProperties(prefix, properties)
        extractSubProjectProperties(model, properties, prefix)
    }

    private void setMandatoryProperties(String prefix, Properties properties) {
        def sonarSourcesKey = join(prefix, "sonar.sources")
        if (!properties.containsKey(sonarSourcesKey)) {
            // for some reason, this must always be set
            properties[sonarSourcesKey] = ""
        }
    }

    private void extractSubProjectProperties(SonarModel model, Properties properties, String prefix) {
        def modules = []
        for (childModel in model.childModels) {
            if (skipped(childModel)) { continue }
            modules << childModel.project.name
            extractProperties(childModel, properties, join(prefix, childModel.project.name))
        }
        if (!modules.empty) {
            properties.put(join(prefix, "sonar.modules"), modules.join(","))
        }
    }

    private ModelToPropertiesConverter createConverter(SonarModel model, String prefix) {
        def converter = new ModelToPropertiesConverter(model, prefix)
        if (model instanceof SonarRootModel) {
            converter.propertyProcessors = model.propertyProcessors + model.project.propertyProcessors
        } else {
            converter.propertyProcessors = model.project.propertyProcessors
        }
        converter
    }

    private boolean skipped(SonarModel model) {
        if (model.project.skip) {
            LOGGER.info("Skipping Sonar analysis for project '{}' and its subprojects because 'sonar.project.skip' is 'true'", model.project.name)
            return true
        }
        false
    }

    private String join(String prefix, String key) {
        prefix ? prefix + "." + key : key
    }
}