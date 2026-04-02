/*
 * xnat-api: org.nrg.xdat.om.base.MoveableI
 * Compile-time facade. Real implementation in apps/web.
 */
package org.nrg.xdat.om.base;

import org.nrg.xft.security.UserI;
import org.nrg.xft.event.EventMetaI;

/** Facade for compile-time resolution. Uses Object for xnat-data-models types. */
public interface MoveableI {
    default String getXSIType() { return null; }
    default String getId() { return null; }
    default String getProject() { return null; }
    default String getLabel() { return null; }
    default void setId(String id) {}
    default void setProject(String project) {}
    default void setLabel(String label) {}
    default Object getItem() { return null; }
    default void moveToProject(Object newProject, String newLabel, UserI user, EventMetaI ci) throws Exception {}
}
