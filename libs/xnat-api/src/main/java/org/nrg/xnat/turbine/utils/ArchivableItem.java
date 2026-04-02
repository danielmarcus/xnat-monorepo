/*
 * xnat-api: org.nrg.xnat.turbine.utils.ArchivableItem
 * Compile-time facade. Real implementation in apps/web.
 */
package org.nrg.xnat.turbine.utils;

/**
 * Facade for compile-time resolution. The real interface throws
 * UnknownPrimaryProjectException from xnat-data-models; we use Exception here.
 */
public interface ArchivableItem {
    String getArchiveDirectoryName();
    String getXSIType();
    String getId();
    String getProject();
    String getArchiveRootPath() throws Exception;
}
