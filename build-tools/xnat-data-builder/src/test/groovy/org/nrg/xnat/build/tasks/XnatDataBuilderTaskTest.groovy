/*
 * xnat-data-builder: org.nrg.xnat.build.tasks.XnatDataBuilderTaskTest
 * XNAT https://www.xnat.org
 * Copyright (c) 2021, Washington University School of Medicine
 * All Rights Reserved
 *
 * Released under the Simplified BSD.
 */

package org.nrg.xnat.build.tasks

import org.junit.Test
import org.nrg.xdat.build.tasks.XdatDataBuilderGenerateSourceTask
import org.nrg.xnat.build.XnatDataBuilderTest

import static org.junit.Assert.assertTrue

class XnatDataBuilderTaskTest extends XnatDataBuilderTest {
    XnatDataBuilderTaskTest() {
        super(false)
    }

    @Test
    public void canAddTaskToProject() {
        def task = project.task('xnatDataBuilder', type: XdatDataBuilderGenerateSourceTask)
        assertTrue(task instanceof XdatDataBuilderGenerateSourceTask)
    }
}
