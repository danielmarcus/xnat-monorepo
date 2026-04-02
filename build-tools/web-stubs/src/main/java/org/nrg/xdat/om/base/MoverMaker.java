package org.nrg.xdat.om.base;

import java.io.File;
import java.util.concurrent.Callable;
import org.nrg.xft.ItemI;
import org.nrg.xft.event.EventMetaI;
import org.nrg.xft.security.UserI;

/**
 * Compilation stub for circular dependency resolution.
 * Uses Object parameters where the real types (XnatAbstractresourceI, XnatProjectdata, etc.)
 * live in xnat-data-models and cannot be referenced from web-stubs.
 */
public class MoverMaker {

    public static boolean check(ItemI item, UserI user) throws Exception {
        return false;
    }

    public static void writeDB(MoveableI m, Object newProject, String newLabel, UserI u, EventMetaI c)
            throws Exception {}

    public static void setLocal(MoveableI m, Object newProject, String newLabel) {}

    public static File createPrimaryBackupDirectory(String cacheBKDirName, String project, String folderName) {
        return null;
    }

    public static Mover moveResource(Object resource, String currentLabel, MoveableI m, File newSessionDir,
                                     String existingRootPath, String destinationProject, UserI u, EventMetaI c)
            throws Exception {
        return null;
    }

    public static class Mover implements Callable<Void> {
        public Mover(File newSessionDir, String existingSessionDir, String existingRootPath,
                     String currentProject, String destinationProject, UserI u, EventMetaI c) {}

        public void setResource(Object r) {}

        @Override
        public Void call() throws Exception { return null; }
    }
}
