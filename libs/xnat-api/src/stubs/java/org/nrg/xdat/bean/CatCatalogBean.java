/*
 * xnat-api: org.nrg.xdat.bean.CatCatalogBean
 *
 * Compile-time facade for the codegen class in
 * build-tools/xnat-data-models/build/xnat-generated/. Used as the field
 * type for CatalogData.catBean in the xnat-api CatalogUtils stub so the
 * runtime field-resolution against apps/web's CatCatalogBean field
 * succeeds (no NoSuchFieldError). NOT exposed to consumers — see ADR 0008.
 *
 * Method declarations match what xnat-data-models calls on catBean:
 * getEntries_entry, setId. Codegen has many more (it's the full XSD
 * binding); drift risk applies.
 */
package org.nrg.xdat.bean;

import org.nrg.xdat.model.CatEntryI;

import java.util.Collections;
import java.util.List;

public class CatCatalogBean {
    public <A extends CatEntryI> List<A> getEntries_entry() { return Collections.emptyList(); }
    public void setId(String id) {}
}
