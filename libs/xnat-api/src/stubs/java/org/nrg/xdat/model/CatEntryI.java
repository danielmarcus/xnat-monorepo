/*
 * xnat-api: org.nrg.xdat.model.CatEntryI
 *
 * Compile-time facade for the codegen-produced interface in
 * build-tools/xnat-data-models/build/xnat-generated/. Used by xnat-api's
 * CatalogUtils stub (getFile parameter type, getEntries_entry return
 * type on the CatCatalogBean stub). NOT exposed to consumers — see the
 * stubs sourceSet in libs/xnat-api/build.gradle.kts and ADR 0008.
 *
 * Method declarations match what xnat-data-models's BaseXnatResourcecatalog
 * (line 96) and BaseXnatImagescandata (loops over entries) call: getContent,
 * getUri. Codegen has many more — drift risk applies.
 */
package org.nrg.xdat.model;

public interface CatEntryI {
    String getContent();
    String getUri();
}
