/*
 * xnat-api: org.nrg.xnat.turbine.utils.ArcSpecManager
 * Compile-time facade for the real ArcSpecManager in apps/web.
 * At runtime, apps/web's version (WEB-INF/classes) takes precedence.
 */
package org.nrg.xnat.turbine.utils;

import org.nrg.xft.event.EventDetails;
import org.nrg.xft.security.UserI;

import java.util.Calendar;
import java.util.Date;

/**
 * Facade that provides the method signatures called by xnat-data-models
 * Base* files. GetInstance() returns this class itself (self-type pattern)
 * so that methods like getArchivePathForProject() resolve at compile time.
 *
 * At runtime, apps/web contains the real ArcSpecManager whose GetInstance()
 * returns ArcArchivespecification. Since WEB-INF/classes takes classloader
 * precedence over WEB-INF/lib JARs, the real implementation is always used.
 */
@SuppressWarnings("unchecked")
public class ArcSpecManager {

    private static final ArcSpecManager INSTANCE = new ArcSpecManager();

    public static Date lastModified = null;
    public static Long lastChecked = Calendar.getInstance().getTimeInMillis();
    public static Long lastCheckedInterval = 60000L;

    public synchronized static ArcSpecManager GetFreshInstance() { return INSTANCE; }
    public synchronized static ArcSpecManager GetInstance() { return INSTANCE; }
    public synchronized static ArcSpecManager GetInstance(boolean dbInit) { return INSTANCE; }

    public static boolean isComplete() { return false; }
    public synchronized static void Reset() {}

    public synchronized static ArcSpecManager initialize(final UserI user) throws Exception {
        return INSTANCE;
    }

    public static boolean allowTransferEmail() { return false; }

    public static synchronized void save(Object arcSpec, EventDetails event) throws Exception {}
    public static synchronized void save(Object arcSpec, UserI user, EventDetails event) throws Exception {}

    // Methods called on GetInstance() result by xnat-data-models Base* files:
    public String getArchivePathForProject(String project) { return null; }
    public String getGlobalArchivePath() { return null; }
    public String getGlobalBuildPath() { return null; }
    public String getGlobalCachePath() { return null; }
    public String getGlobalPrearchivePath() { return null; }
    public Object getProjectArc(String project) { return null; }
    public Integer getArcArchivespecificationId() { return null; }
    public Integer getAutoQuarantineCodeForProject(String project) { return null; }
}
