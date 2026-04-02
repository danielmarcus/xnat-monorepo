/*
 * xnat-api: org.nrg.xnat.helpers.scanType.AbstractScanTypeMapping
 * Compile-time facade. Real implementation in apps/web.
 */
package org.nrg.xnat.helpers.scanType;

import java.util.Hashtable;
import java.util.Map;

/**
 * Facade for compile-time resolution. The real class uses XnatImagescandataI
 * which lives in xnat-data-models; we use Object here to avoid the circular dep.
 */
public abstract class AbstractScanTypeMapping<HistoryType> implements ScanTypeMappingI {

    public AbstractScanTypeMapping(String projectId, String dbName, String scanSelectSql) {}

    protected String getMappedType(Object scan, Map<String, HistoryType> histories) { return null; }

    protected HistoryType newScanHistory() { return null; }

    protected void update(HistoryType h, Hashtable<?, ?> row) {}

    public static String standardizeFormat(String originalString) {
        if (null == originalString) {
            return "";
        } else {
            return originalString.replaceAll("[ _\\-\\*]", "").toUpperCase();
        }
    }
}
