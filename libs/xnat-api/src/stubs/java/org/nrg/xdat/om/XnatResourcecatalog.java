/*
 * xnat-api: org.nrg.xdat.om.XnatResourcecatalog
 *
 * Compile-time facade for the codegen OM class in
 * build-tools/xnat-data-models/build/xnat-generated/. Used as the catRes
 * parameter type on CatalogData(File, XnatResourcecatalog, String, …)
 * constructors in the xnat-api CatalogUtils stub. Empty class —
 * descriptor-match is the only contract we need; the runtime version
 * comes from xnat-data-models codegen + apps/web. See ADR 0008.
 */
package org.nrg.xdat.om;

public class XnatResourcecatalog {
}
