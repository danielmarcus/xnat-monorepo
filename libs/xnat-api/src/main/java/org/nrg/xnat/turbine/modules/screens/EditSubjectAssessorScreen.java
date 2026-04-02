/*
 * xnat-api: org.nrg.xnat.turbine.modules.screens.EditSubjectAssessorScreen
 * Compile-time facade. Real implementation in apps/web.
 */
package org.nrg.xnat.turbine.modules.screens;

import org.apache.turbine.util.RunData;
import org.apache.velocity.context.Context;
import org.nrg.xft.ItemI;

/** Facade for compile-time resolution. */
public class EditSubjectAssessorScreen {
    public String getElementName() { return null; }
    public ItemI getEmptyItem(RunData data) throws Exception { return null; }
    public void finalProcessing(RunData data, Context context) {}
}
