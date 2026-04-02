/*
 * xnat-api: org.nrg.xnat.turbine.utils.CatalogSet
 * Compile-time facade. Real implementation in apps/web.
 */
package org.nrg.xnat.turbine.utils;

/** Facade for compile-time resolution. Uses Object for CatCatalogBean. */
public class CatalogSet {
    public Object catalog = null;
    public java.util.Hashtable<String, Object> hash = null;

    public CatalogSet(Object c, java.util.Hashtable<String, Object> h) {
        catalog = c;
        hash = h;
    }
}
