/*
 * xnat-api: org.nrg.xnat.utils.CatalogUtils
 *
 * Compile-time facade for the real CatalogUtils in apps/web. At runtime
 * apps/web's WEB-INF/classes version is the only one on the classpath
 * (xnat-api JAR excludes this class).
 *
 * Method, field, and constructor signatures called from xnat-data-models
 * MUST match the apps/web impl exactly — otherwise xnat-data-models
 * bakes mismatched bytecode descriptors that the runtime impl does not
 * declare, and the JVM throws NoSuchMethodError or NoSuchFieldError.
 *
 * Typed-stub interfaces / classes (XnatResourcecatalogI,
 * XnatImagescandataI, CatCatalogI, CatEntryI, CatCatalogBean,
 * XnatResourcecatalog) live in the xnat-api `stubs` sourceSet and are
 * NOT exposed to downstream consumers. The runtime versions come from
 * xnat-data-models codegen and apps/web. See ADR 0008.
 */
package org.nrg.xnat.utils;

import org.nrg.action.ServerException;
import org.nrg.xdat.bean.CatCatalogBean;
import org.nrg.xdat.model.CatCatalogI;
import org.nrg.xdat.model.CatEntryI;
import org.nrg.xdat.model.XnatResourcecatalogI;
import org.nrg.xdat.om.XnatResourcecatalog;
import org.nrg.xft.event.EventMetaI;
import org.nrg.xft.security.UserI;
import org.nrg.xnat.turbine.utils.ArchivableItem;

import javax.annotation.Nullable;
import java.io.File;

public class CatalogUtils {

    public static final String PROJECT_PATH  = "projectPath";
    public static final String ABSOLUTE_PATH = "absolutePath";
    public static final String LOCATOR       = "locator";
    public static final String URI           = "URI";

    // -------------------------------------------------------------------------
    // Inner classes
    // -------------------------------------------------------------------------

    public static class CatalogData {
        public File catFile;
        public String catFileChecksum = null;
        public String catPath;
        public CatCatalogBean catBean;
        public String project;

        public CatalogData(CatCatalogBean catBean, File catFile, String project, String catFileChecksum) {
            this.catBean = catBean;
            this.catFile = catFile;
            this.project = project;
            this.catFileChecksum = catFileChecksum;
            this.catPath = catFile != null ? catFile.getParent() : null;
        }

        public CatalogData(File catFile, String project) throws ServerException {
            this.catFile = catFile;
            this.project = project;
            this.catPath = catFile != null ? catFile.getParent() : null;
        }

        public CatalogData(File catFile, XnatResourcecatalog catRes, String project) throws ServerException {
            this.catFile = catFile;
            this.project = project;
            this.catPath = catFile != null ? catFile.getParent() : null;
        }

        public CatalogData(File catFile, XnatResourcecatalog catRes, String project, String catId) throws ServerException {
            this(catFile, catRes, project);
        }

        public CatalogData(File catFile, XnatResourcecatalog catRes, String project, String catId, boolean create) throws ServerException {
            this(catFile, catRes, project);
        }

        public static CatalogData getOrCreate(String rootPath, XnatResourcecatalogI resource, String project)
                throws ServerException { return null; }

        public static CatalogData getOrCreate(ArchivableItem item, XnatResourcecatalogI resource)
                throws ServerException { return null; }

        public static CatalogData getOrCreateAndClean(String rootPath, XnatResourcecatalogI resource, boolean includeFullPaths, String project)
                throws ServerException { return null; }

        public static CatalogData getOrCreateAndClean(String rootPath, XnatResourcecatalogI resource, boolean includeFullPaths,
                                                      String project, UserI user, EventMetaI c)
                throws ServerException { return null; }
    }

    public static class Stats {
        public int count;
        public long size;

        public Stats(CatCatalogI cat, String parentPath, String project) {
            count = 0;
            size = 0;
        }
    }

    // -------------------------------------------------------------------------
    // Static methods
    // -------------------------------------------------------------------------

    public static File getFile(CatEntryI entry, String catPath, String project) { return null; }

    public static File getFile(CatEntryI entry, String parentPath, @Nullable String project, @Nullable String destParentPath) { return null; }

    public static File getCatalogFile(String rootPath, XnatResourcecatalogI resource) { return null; }

    public static File getCatalogFile(String project, String rootPath, XnatResourcecatalogI resource) { return null; }

    public static CatCatalogBean getCatalog(String rootPath, XnatResourcecatalogI resource, String project) { return null; }

    public static CatCatalogBean getCatalog(File catalogFile, String project) { return null; }

    public static String getCatalogProject(CatCatalogBean bean) { return null; }

    public static boolean setCatalogProject(CatCatalogBean bean, String project) { return false; }

    public static CatCatalogBean getCleanCatalog(String project, String rootPath, XnatResourcecatalogI resource,
                                                 boolean includeFullPaths) { return null; }

    public static CatCatalogBean getCleanCatalog(String project, String rootPath, XnatResourcecatalogI resource,
                                                 boolean includeFullPaths, UserI user, EventMetaI c) { return null; }

    public static boolean formalizeCatalog(CatCatalogI cat, String catPath, String project,
                                           UserI user, EventMetaI now) { return false; }

    public static boolean formalizeCatalog(CatCatalogI cat, String catPath, String project,
                                           UserI user, EventMetaI now,
                                           boolean createChecksums, boolean removeMissingFiles) { return false; }

    public static void writeCatalogToFile(CatalogData catalogData) throws Exception {}

    public static Boolean maintainFileHistory() { return false; }

    public static Stats getFileStats(CatCatalogI cat, String parentPath, String project) { return null; }

    public static String formatSize(long size) { return null; }

    public static String formatFileStats(String label, long fileCount, Object rawSize) { return null; }

    public static File getFileOnLocalFileSystem(String fullPath) { return null; }

    public static File getFileOnLocalFileSystem(String uri, @Nullable String destPath, @Nullable String project) { return null; }

    public static boolean deleteRemoteFile(@Nullable String url, @Nullable String project) { return false; }
}
