/*
 * xdat-data-builder: org.nrg.xdat.build.XdatDataBuilderPlugin
 * XNAT https://www.xnat.org
 * Copyright (c) 2020, Washington University School of Medicine
 * All Rights Reserved
 *
 * Released under the Simplified BSD.
 */

package org.nrg.xdat.build

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.UnknownTaskException
import org.gradle.api.tasks.compile.JavaCompile
import org.nrg.xdat.build.tasks.XdatDataBuilderBeanJarTask
import org.nrg.xdat.build.tasks.XdatDataBuilderGenerateSourceTask

class XdatDataBuilderPlugin implements Plugin<Project> {
    void apply(Project project) {
        // Set the primary compileJava task to depend on generate source.
        try {
            (project.tasks.getByName("compileJava") as JavaCompile).with {
                dependsOn project.task("xdatDataBuilder", type: XdatDataBuilderGenerateSourceTask)
            }

            // Set the primary jar task to trigger bean jar task.
            project.task("xdatBeanJar", type: XdatDataBuilderBeanJarTask).dependsOn project.tasks.getByName("jar")
        } catch (UnknownTaskException ignored) {
            // Ignore this, just means there's no compilation task.
        }
    }
}
