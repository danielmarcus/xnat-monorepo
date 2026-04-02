package org.nrg.xnat.helpers.scanType;

import java.util.Hashtable;
import java.util.Map;

/**
 * Compilation stub for circular dependency resolution.
 */
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
