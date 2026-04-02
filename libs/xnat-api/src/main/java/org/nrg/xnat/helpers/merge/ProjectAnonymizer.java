/*
 * xnat-api: org.nrg.xnat.helpers.merge.ProjectAnonymizer
 * Compile-time facade for the real ProjectAnonymizer in apps/web.
 * At runtime, apps/web's version (WEB-INF/classes) takes precedence.
 *
 * Uses Object/unchecked generics where the real types live in xnat-data-models.
 */
package org.nrg.xnat.helpers.merge;

import java.util.concurrent.Callable;

/**
 * Facade with correct method signatures for compile-time resolution.
 * Uses raw Callable to avoid dependency on dicom-mizer AnonymizationResult.
 * Constructor uses Object for XnatImagesessiondataI to avoid xnat-data-models dep.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class ProjectAnonymizer implements Callable {

    public ProjectAnonymizer() {}
    public ProjectAnonymizer(Object session, String projectId, String archivePath, boolean overwrite) {}

    @Override
    public Object call() throws Exception {
        throw new UnsupportedOperationException("ProjectAnonymizer facade");
    }
}
