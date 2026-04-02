/*
 * xnat-data-builder: org.nrg.xnat.build.XnatDataBuilderTest
 * XNAT https://www.xnat.org
 * Copyright (c) 2021, Washington University School of Medicine
 * All Rights Reserved
 *
 * Released under the Simplified BSD.
 */

package org.nrg.xnat.build

import org.gradle.api.plugins.JavaPlugin
import org.gradle.testfixtures.ProjectBuilder

class XnatDataBuilderTest {
    def project

    XnatDataBuilderTest(boolean includePlugin = true) {
        project = ProjectBuilder.builder().build()
        project.pluginManager.apply JavaPlugin
        if (includePlugin) {
            project.pluginManager.apply XnatDataBuilderPlugin
        }
    }
}
