/*
 * xnat-api: org.nrg.xnat.helpers.scanType.ImageScanTypeMapping
 * Compile-time facade. Real implementation in apps/web.
 */
package org.nrg.xnat.helpers.scanType;

import java.util.Hashtable;
import java.util.Map;

/** Facade for compile-time resolution. */
public class ImageScanTypeMapping extends AbstractScanTypeMapping<Object> implements ScanTypeMappingI {

    public ImageScanTypeMapping(String project, String dbName) {
        super(project, dbName, null);
    }

    @Override
    protected String getMappedType(Object scan, Map<String, Object> histories) { return null; }

    @Override
    protected Object newScanHistory() { return new Object(); }

    @Override
    protected void update(Object h, Hashtable<?, ?> row) {}
}
