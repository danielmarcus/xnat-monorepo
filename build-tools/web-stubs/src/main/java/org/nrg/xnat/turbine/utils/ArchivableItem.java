package org.nrg.xnat.turbine.utils;

/**
 * Compilation stub for circular dependency resolution.
 * The real ArchivableItem declares getArchiveRootPath() throws UnknownPrimaryProjectException.
 * Since that exception class is in xnat-data-models, we declare throws Exception instead.
 */
public interface ArchivableItem {
    String getArchiveDirectoryName();
    String getXSIType();
    String getId();
    String getProject();
    String getArchiveRootPath() throws Exception;
}
