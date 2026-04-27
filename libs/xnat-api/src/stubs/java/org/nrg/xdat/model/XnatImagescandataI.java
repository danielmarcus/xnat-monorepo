/*
 * xnat-api: org.nrg.xdat.model.XnatImagescandataI
 *
 * Compile-time facade for the codegen-produced interface in
 * build-tools/xnat-data-models/build/xnat-generated/. Exists so xnat-api
 * scan-type stub signatures (ScanTypeMappingI.setType,
 * AbstractScanTypeMapping.getMappedType, ImageScanTypeMapping.getMappedType)
 * can take a typed parameter and produce a bytecode descriptor matching
 * the runtime apps/web versions. Excluded from the xnat-api JAR — at
 * runtime the codegen interface from xnat-data-models is authoritative.
 *
 * Intentionally empty: descriptor-match is the contract.
 */
package org.nrg.xdat.model;

public interface XnatImagescandataI {
}
