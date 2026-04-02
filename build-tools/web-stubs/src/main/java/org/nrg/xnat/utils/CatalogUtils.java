package org.nrg.xnat.utils;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Compilation stub for the circular dependency between xnat-data-models and apps/web.
 * The real CatalogUtils lives in apps/web.
 *
 * Methods that return domain types (CatCatalogBean, etc.) use unchecked generic returns
 * because the concrete types live in xnat-data-models and cannot be referenced here.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class CatalogUtils {

    // -------------------------------------------------------------------------
    // Inner class stubs used by xnat-data-models
    // -------------------------------------------------------------------------

    /**
     * Stub for the real CatalogData inner class.
     *
     * In the real code, catBean is typed as CatCatalogBean and callers call
     * catBean.getEntries_entry(). Since we cannot reference CatCatalogBean here,
     * catBean is typed as CatBeanProxy which provides getEntries_entry() with a
     * generic return. This allows callers in xnat-data-models to iterate over
     * the result as CatEntryI (the compiler infers from assignment context).
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
         * At runtime the real CatCatalogBean instance is used (the field is reassigned).
         */
        public CatBeanProxy catBean;

        public CatalogData(Object catBean, File catFile, String project, String catFileChecksum) {
            this.catBean = new CatBeanProxy(catBean);
            this.catFile = catFile;
            this.project = project;
            this.catPath = catFile != null ? catFile.getParent() : null;
        }

        public CatalogData(File catFile, String project) throws org.nrg.action.ServerException {
            this.catFile = catFile;
            this.project = project;
            this.catPath = catFile != null ? catFile.getParent() : null;
        }

        public CatalogData(File catFile, Object catRes, String project) throws org.nrg.action.ServerException {
            this.catFile = catFile;
            this.project = project;
            this.catPath = catFile != null ? catFile.getParent() : null;
        }

        public CatalogData(File catFile, Object catRes, String project, String catId) throws org.nrg.action.ServerException {
            this(catFile, catRes, project);
        }

        public static CatalogData getOrCreate(String rootPath, Object resource, String project)
                throws org.nrg.action.ServerException { return null; }

        public static CatalogData getOrCreate(Object item, Object resource)
                throws org.nrg.action.ServerException { return null; }

        public static CatalogData getOrCreateAndClean(String rootPath, Object resource, boolean includeFullPaths, String project)
                throws org.nrg.action.ServerException { return null; }
    }

    /**
     * Proxy for the catalog bean that exposes getEntries_entry() without
     * referencing CatCatalogBean or CatCatalogI. Uses unchecked generics
     * so that callers can iterate as CatEntryI.
     */
    @SuppressWarnings("unchecked")
    public static class CatBeanProxy {
        private final Object delegate;

        public CatBeanProxy(Object delegate) {
            this.delegate = delegate;
        }

        /**
         * Delegates to CatCatalogBean.getEntries_entry() via reflection at runtime.
         * At compile time, the unchecked generic return lets callers assign to List&lt;CatEntryI&gt;.
         */
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
    // Static method stubs used by xnat-data-models
    // -------------------------------------------------------------------------

    public static File getFile(Object entry, String catPath, String project) { return null; }

    public static File getCatalogFile(String rootPath, Object resource) { return null; }

    /** Returns CatCatalogBean at runtime; uses unchecked generic to avoid circular dep. */
    @SuppressWarnings("unchecked")
    public static <T> T getCatalog(String rootPath, Object resource, String project) { return null; }

    public static String getCatalogProject(Object bean) { return null; }

    public static void setCatalogProject(Object bean, String project) {}

    @SuppressWarnings("unchecked")
    public static <T> T getCleanCatalog(String project, String rootPath, Object resource,
            boolean includeFullPaths, Object user, Object c) { return null; }

    public static boolean formalizeCatalog(Object cat, String catPath, String project,
            Object user, Object now) { return false; }

    public static void writeCatalogToFile(CatalogData catalogData) throws IOException {}

    public static boolean maintainFileHistory() { return false; }

    public static Stats getFileStats(Object cat, String parentPath, String project) { return null; }

    public static String formatSize(long size) { return null; }

    public static String formatFileStats(String label, long fileCount, Object rawSize) { return null; }

    public static File getFileOnLocalFileSystem(String fullPath) { return null; }

    public static Object getFileOnLocalFileSystem(Object entry, String catPath, String project) { return null; }

    public static boolean deleteRemoteFile(Object entry, String project) { return false; }
}
