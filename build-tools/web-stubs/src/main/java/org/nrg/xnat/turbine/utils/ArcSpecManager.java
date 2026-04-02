package org.nrg.xnat.turbine.utils;

/**
 * Compilation stub for the circular dependency between xnat-data-models and apps/web.
 * The real ArcSpecManager lives in apps/web and returns ArcArchivespecification.
 * Methods returning domain types use unchecked generics to avoid circular dependency.
 */
@SuppressWarnings("unchecked")
public class ArcSpecManager {

    private static final ArcSpecManager INSTANCE = new ArcSpecManager();

    public static ArcSpecManager GetInstance() { return INSTANCE; }
    public static ArcSpecManager GetInstance(boolean dbInit) { return INSTANCE; }
    public static ArcSpecManager GetFreshInstance() { return INSTANCE; }
    public static void Reset() {}
    public static boolean isComplete() { return false; }

    // Methods called on GetInstance() result in xnat-data-models
    public String getArchivePathForProject(String projectId) { return null; }

    /** Returns ArcProject at runtime; uses unchecked generic to avoid circular dep. */
    @SuppressWarnings("unchecked")
    public <T> T getProjectArc(String project) { return null; }

    public String getGlobalArchivePath() { return null; }
    public String getGlobalCachePath() { return null; }
    public String getGlobalPrearchivePath() { return null; }
    public String getGlobalBuildPath() { return null; }
    public Integer getAutoQuarantineCodeForProject(String projectId) { return null; }
    public Object getArcArchivespecificationId() { return null; }
}
