/*
 * xnat-api: org.nrg.xnat.helpers.scanType.ImageScanTypeMapping
 *
 * Compile-time facade. Real implementation in apps/web. getMappedType
 * is typed against XnatImagescandataI to match the parent
 * AbstractScanTypeMapping erased descriptor and the apps/web runtime
 * impl. Excluded from the xnat-api JAR.
 */
package org.nrg.xnat.helpers.scanType;

import org.nrg.xdat.model.XnatImagescandataI;

import java.util.Hashtable;
import java.util.Map;

public class ImageScanTypeMapping extends AbstractScanTypeMapping<Object> implements ScanTypeMappingI {

    public ImageScanTypeMapping(String project, String dbName) {
        super(project, dbName, null);
    }

    @Override
    protected String getMappedType(XnatImagescandataI scan, Map<String, Object> histories) { return null; }

    @Override
    protected Object newScanHistory() { return new Object(); }

    @Override
    protected void update(Object h, Hashtable<?, ?> row) {}
}
