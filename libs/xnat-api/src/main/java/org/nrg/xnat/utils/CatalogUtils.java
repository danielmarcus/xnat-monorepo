/*
 * xnat-api: org.nrg.xnat.utils.CatalogUtils
 * Compile-time facade for the real CatalogUtils in apps/web.
 * At runtime, apps/web's version (WEB-INF/classes) takes precedence.
 *
 * Uses Object/unchecked generics where the real types live in xnat-data-models.
 */
package org.nrg.xnat.utils;

import org.nrg.action.ServerException;
import org.nrg.xft.event.EventMetaI;
import org.nrg.xft.security.UserI;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Facade with correct method signatures for compile-time resolution.
 * The real implementation lives in apps/web and is used at runtime.
 *
 * Methods that return xnat-data-models types (CatCatalogBean, XnatResourcecatalog, etc.)
 * use unchecked generics to avoid the circular dependency.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class CatalogUtils {

    public static final String PROJECT_PATH  = "projectPath";
    public static final String ABSOLUTE_PATH = "absolutePath";
    public static final String LOCATOR       = "locator";
    public static final String URI           = "URI";

    // -------------------------------------------------------------------------
    // Inner classes
    // -------------------------------------------------------------------------

    /**
     * Stub for the real CatalogData inner class.
     * catBean is typed as CatBeanProxy to avoid referencing CatCatalogBean directly.
     */
    public static class CatalogData {
        /** The catalog XML file on disk. */
        public File catFile;
        /** Parent directory of catFile. */
        public String catPath;
        /** The project id associated with this catalog. */
        public String project;
        /**
         * The catalog bean. Typed as CatBeanProxy to provide getEntries_entry().
         * At runtime the real CatCatalogBean instance is used.
         */
        public CatBeanProxy catBean;

        public CatalogData(Object catBean, File catFile, String project, String catFileChecksum) {
            this.catBean = new CatBeanProxy(catBean);
            this.catFile = catFile;
            this.project = project;
            this.catPath = catFile != null ? catFile.getParent() : null;
        }

        public CatalogData(File catFile, String project) throws ServerException {
            this.catFile = catFile;
            this.project = project;
            this.catPath = catFile != null ? catFile.getParent() : null;
        }

        public CatalogData(File catFile, Object catRes, String project) throws ServerException {
            this.catFile = catFile;
            this.project = project;
            this.catPath = catFile != null ? catFile.getParent() : null;
        }

        public CatalogData(File catFile, Object catRes, String project, String catId) throws ServerException {
            this(catFile, catRes, project);
        }

        public CatalogData(File catFile, Object catRes, String project, String catId, boolean create) throws ServerException {
            this(catFile, catRes, project);
        }

        public static CatalogData getOrCreate(String rootPath, Object resource, String project)
                throws ServerException { return null; }

        public static CatalogData getOrCreate(Object item, Object resource)
                throws ServerException { return null; }

        public static CatalogData getOrCreateAndClean(String rootPath, Object resource, boolean includeFullPaths, String project)
                throws ServerException { return null; }

        public static CatalogData getOrCreateAndClean(String rootPath, Object resource, boolean includeFullPaths,
                                                      String project, UserI user, EventMetaI c)
                throws ServerException { return null; }
    }

    /**
     * Proxy for the catalog bean that exposes getEntries_entry() without
     * referencing CatCatalogBean or CatCatalogI.
     */
    @SuppressWarnings("unchecked")
    public static class CatBeanProxy {
        private final Object delegate;

        public CatBeanProxy(Object delegate) {
            this.delegate = delegate;
        }

        @SuppressWarnings("unchecked")
        public <A> List<A> getEntries_entry() {
            if (delegate == null) return Collections.emptyList();
            try {
                return (List<A>) delegate.getClass().getMethod("getEntries_entry").invoke(delegate);
            } catch (Exception e) {
                return Collections.emptyList();
            }
        }
    }

    public static class Stats {
        public int count;
        public long size;

        public Stats(Object cat, String parentPath, String project) {
            count = 0;
            size = 0;
        }
    }

    // -------------------------------------------------------------------------
    // Static methods
    // -------------------------------------------------------------------------

    public static File getFile(Object entry, String catPath, String project) { return null; }

    public static File getCatalogFile(String rootPath, Object resource) { return null; }

    public static File getCatalogFile(String project, String rootPath, Object resource) { return null; }

    /** Returns CatCatalogBean at runtime; uses unchecked generic to avoid circular dep. */
    @SuppressWarnings("unchecked")
    public static <T> T getCatalog(String rootPath, Object resource, String project) { return null; }

    @SuppressWarnings("unchecked")
    public static <T> T getCatalog(File catalogFile, String project) { return null; }

    public static String getCatalogProject(Object bean) { return null; }

    public static boolean setCatalogProject(Object bean, String project) { return false; }

    @SuppressWarnings("unchecked")
    public static <T> T getCleanCatalog(String project, String rootPath, Object resource,
                                        boolean includeFullPaths) { return null; }

    @SuppressWarnings("unchecked")
    public static <T> T getCleanCatalog(String project, String rootPath, Object resource,
                                        boolean includeFullPaths, Object user, Object c) { return null; }

    public static boolean formalizeCatalog(Object cat, String catPath, String project,
                                           Object user, Object now) { return false; }

    public static void writeCatalogToFile(CatalogData catalogData) throws Exception {}

    public static Boolean maintainFileHistory() { return false; }

    public static Stats getFileStats(Object cat, String parentPath, String project) { return null; }

    public static String formatSize(long size) { return null; }

    public static String formatFileStats(String label, long fileCount, Object rawSize) { return null; }

    public static File getFileOnLocalFileSystem(String fullPath) { return null; }

    public static File getFileOnLocalFileSystem(Object entry, String catPath, String project) { return null; }

    public static boolean deleteRemoteFile(Object entry, String project) { return false; }
}
