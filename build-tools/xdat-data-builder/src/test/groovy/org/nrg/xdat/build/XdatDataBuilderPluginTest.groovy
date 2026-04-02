/*
 * xdat-data-builder: org.nrg.xdat.build.XdatDataBuilderPluginTest
 * XNAT https://www.xnat.org
 * Copyright (c) 2020, Washington University School of Medicine
 * All Rights Reserved
 *
 * Released under the Simplified BSD.
 */

package org.nrg.xdat.build

import org.junit.Test
import org.nrg.xdat.build.gradle.XdatDataBuilderTest
import org.nrg.xdat.build.tasks.XdatDataBuilderGenerateSourceTask

import static org.junit.Assert.assertTrue

class XdatDataBuilderPluginTest extends XdatDataBuilderTest {
    XdatDataBuilderPluginTest() {
        super()
    }

    @Test
    void xdatPluginAddsXdatDataBuilderTaskToProject() {
        assertTrue(project.tasks.xdatDataBuilder instanceof XdatDataBuilderGenerateSourceTask)
    }
}
