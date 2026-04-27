/*
 * xnat-api: org.nrg.xdat.model.XnatResourcecatalogI
 *
 * Compile-time facade for the codegen-produced interface that lives in
 * build-tools/xnat-data-models/build/xnat-generated/. Exists so xnat-api
 * stub signatures (e.g. CatalogUtils.CatalogData.getOrCreate) can take a
 * typed parameter, producing a bytecode descriptor that matches the
 * runtime version. Excluded from the xnat-api JAR — at runtime, the
 * codegen interface in xnat-data-models is the only one on the classpath.
 *
 * Intentionally empty: descriptor-match is the only contract we need
 * here. Method-level alignment is the responsibility of the codegen
 * output and apps/web's CatalogUtils impl.
 */
package org.nrg.xdat.model;

public interface XnatResourcecatalogI {
}
