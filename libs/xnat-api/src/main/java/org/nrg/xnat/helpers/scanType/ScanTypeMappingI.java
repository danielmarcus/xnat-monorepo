/*
 * xnat-api: org.nrg.xnat.helpers.scanType.ScanTypeMappingI
 *
 * Compile-time facade. Real implementation in apps/web. The setType
 * parameter MUST be XnatImagescandataI (not Object) — xnat-data-models
 * compiles BaseXnatImagesessiondata against this stub and bakes the
 * descriptor into bytecode; the runtime apps/web ScanTypeMappingI
 * declares only the typed overload, so a (Object) descriptor would
 * fail JVM method lookup at invokeinterface time.
 *
 * Excluded from the xnat-api JAR; only the apps/web version is on the
 * runtime classpath.
 */
package org.nrg.xnat.helpers.scanType;

import org.nrg.xdat.model.XnatImagescandataI;

public interface ScanTypeMappingI {
    default void setType(XnatImagescandataI scan) {}
}
