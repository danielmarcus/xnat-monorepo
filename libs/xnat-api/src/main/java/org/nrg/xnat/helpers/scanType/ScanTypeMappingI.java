/*
 * xnat-api: org.nrg.xnat.helpers.scanType.ScanTypeMappingI
 * Compile-time facade. Real implementation in apps/web.
 */
package org.nrg.xnat.helpers.scanType;

/** Facade for compile-time resolution. Uses Object for XnatImagescandataI. */
public interface ScanTypeMappingI {
    default void setType(Object scan) {}
}
