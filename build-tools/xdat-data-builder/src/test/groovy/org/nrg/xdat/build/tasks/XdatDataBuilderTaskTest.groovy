/*
 * xdat-data-builder: org.nrg.xdat.build.tasks.XdatDataBuilderTaskTest
 * XNAT https://www.xnat.org
 * Copyright (c) 2020, Washington University School of Medicine
 * All Rights Reserved
 *
 * Released under the Simplified BSD.
 */

package org.nrg.xdat.build.tasks

import org.junit.Test
import org.nrg.xdat.build.gradle.XdatDataBuilderTest

import static org.junit.Assert.assertTrue

class XdatDataBuilderTaskTest extends XdatDataBuilderTest {
    XdatDataBuilderTaskTest() {
        super(false)
    }

    @Test
    void canAddTaskToProject() {
        def task = project.task('xdatDataBuilder', type: XdatDataBuilderGenerateSourceTask)
        assertTrue(task instanceof XdatDataBuilderGenerateSourceTask)
    }
}
