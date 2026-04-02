/*
 * xnat-api: org.nrg.xnat.utils.WorkflowUtils
 * Compile-time facade. Real implementation in apps/web.
 */
package org.nrg.xnat.utils;

import org.nrg.xft.ItemI;
import org.nrg.xft.event.EventDetails;
import org.nrg.xft.event.EventMetaI;
import org.nrg.xft.event.persist.PersistentWorkflowI;
import org.nrg.xft.security.UserI;

/** Facade for compile-time resolution. */
public class WorkflowUtils {
    public static final String ADMIN = "admin";

    public static class EventRequirementAbsent extends Exception {
        public EventRequirementAbsent() {}
        public EventRequirementAbsent(String message) { super(message); }
    }

    public static PersistentWorkflowI buildOpenWorkflow(UserI user, String xsiType, String id, String project, EventDetails event)
            throws Exception { return null; }

    public static PersistentWorkflowI buildOpenWorkflow(UserI user, ItemI item, EventDetails event)
            throws Exception { return null; }

    public static void complete(PersistentWorkflowI workflow, EventMetaI event) throws Exception {}

    public static void fail(PersistentWorkflowI workflow, EventMetaI event) throws Exception {}

    public static void save(PersistentWorkflowI workflow, EventMetaI event) throws Exception {}

    public static PersistentWorkflowI getOrCreateWorkflowData(Object id, UserI user, String xsiType, String itemId,
            String externalId, EventDetails event) throws Exception { return null; }
}
