/*
 * xnat-api: org.nrg.xnat.helpers.prearchive.PrearcUtils
 * Compile-time facade for the real PrearcUtils in apps/web.
 * At runtime, apps/web's version (WEB-INF/classes) takes precedence.
 */
package org.nrg.xnat.helpers.prearchive;

/**
 * Facade with correct method signatures for compile-time resolution.
 * The real implementation lives in apps/web and is used at runtime.
 */
public class PrearcUtils {

    public static void deleteProject(String projectId) throws Exception {
        throw new UnsupportedOperationException("PrearcUtils facade: real implementation not on classpath");
    }
}
