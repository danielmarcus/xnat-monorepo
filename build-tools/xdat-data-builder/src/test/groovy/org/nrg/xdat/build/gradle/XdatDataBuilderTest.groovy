/*
 * xdat-data-builder: org.nrg.xdat.build.gradle.XdatDataBuilderTest
 * XNAT https://www.xnat.org
 * Copyright (c) 2020, Washington University School of Medicine
 * All Rights Reserved
 *
 * Released under the Simplified BSD.
 */

package org.nrg.xdat.build.gradle

import org.gradle.api.plugins.JavaPlugin
import org.gradle.testfixtures.ProjectBuilder
import org.nrg.xdat.build.XdatDataBuilderPlugin

class XdatDataBuilderTest {
    def project

    XdatDataBuilderTest(boolean includePlugin = true) {
        project = ProjectBuilder.builder().build()
        project.pluginManager.apply JavaPlugin
        if (includePlugin) {
            project.pluginManager.apply XdatDataBuilderPlugin
        }
    }
}
