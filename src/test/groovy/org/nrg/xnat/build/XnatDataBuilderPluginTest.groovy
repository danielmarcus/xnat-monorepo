/*
 * xnat-data-builder: org.nrg.xnat.build.XnatDataBuilderPluginTest
 * XNAT https://www.xnat.org
 * Copyright (c) 2021, Washington University School of Medicine
 * All Rights Reserved
 *
 * Released under the Simplified BSD.
 */

package org.nrg.xnat.build

import org.junit.Test
import org.nrg.xdat.build.tasks.XdatDataBuilderGenerateSourceTask
import org.nrg.xnat.build.XnatDataBuilderTest

import static org.junit.Assert.assertTrue

class XnatDataBuilderPluginTest extends XnatDataBuilderTest {
    @Test
    public void xnatDataBuilderPluginAddsXnatDataBuilderTaskToProject() {
        assertTrue(project.tasks.xnatDataBuilder instanceof XdatDataBuilderGenerateSourceTask)
    }
}
