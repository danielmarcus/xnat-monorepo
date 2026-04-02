package org.nrg.xnat.turbine.utils;

public class CatalogSet {
    public Object catalog = null;
    public java.util.Hashtable<String, Object> hash = null;

    public CatalogSet(Object c, java.util.Hashtable<String, Object> h) {
        catalog = c;
        hash = h;
    }
}
