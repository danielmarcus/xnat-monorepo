/*
 * xnat-api: org.nrg.xnat.turbine.utils.ArcSpecBridge
 *
 * Reflection-based bridge to the real ArcSpecManager in apps/web.
 *
 * Background: The real ArcSpecManager.GetInstance() returns ArcArchivespecification,
 * but xnat-data-models is compiled without a dependency on apps/web. A compile-time
 * facade with GetInstance() returning Object would produce bytecode with descriptor
 * ()Ljava/lang/Object;, which the JVM cannot match to the real ()LArcArchivespecification;
 * descriptor (static methods are not subject to covariant bridge-method generation).
 *
 * Solution: use reflection to invoke GetInstance() so that no descriptor is baked into
 * the bytecode of the callers. The returned Object is then cast to ArcArchivespecification
 * at the call site, which is safe because at runtime apps/web provides an
 * ArcArchivespecification instance.
 */
package org.nrg.xnat.turbine.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ArcSpecBridge {

    private static final Logger log = LoggerFactory.getLogger(ArcSpecBridge.class);

    private ArcSpecBridge() {}

    /**
     * Returns the singleton ArcArchivespecification instance by reflectively
     * calling {@code ArcSpecManager.GetInstance()} on the real runtime class.
     *
     * @return the ArcArchivespecification singleton, or {@code null} if
     *         ArcSpecManager is not available on the classpath.
     */
    public static Object getInstance() {
        try {
            final Class<?> clazz = Class.forName("org.nrg.xnat.turbine.utils.ArcSpecManager");
            return clazz.getMethod("GetInstance").invoke(null);
        } catch (Exception e) {
            log.error("Failed to obtain ArcSpecManager instance via reflection", e);
            return null;
        }
    }

    /**
     * Reflectively calls {@code ArcSpecManager.Reset()} on the real runtime class.
     */
    public static void reset() {
        try {
            final Class<?> clazz = Class.forName("org.nrg.xnat.turbine.utils.ArcSpecManager");
            clazz.getMethod("Reset").invoke(null);
        } catch (Exception e) {
            log.error("Failed to call ArcSpecManager.Reset() via reflection", e);
        }
    }
}
