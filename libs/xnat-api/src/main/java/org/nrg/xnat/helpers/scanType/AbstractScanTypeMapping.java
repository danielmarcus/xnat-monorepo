/*
 * xnat-api: org.nrg.xnat.helpers.scanType.AbstractScanTypeMapping
 *
 * Compile-time facade. Real implementation in apps/web. getMappedType
 * is typed against XnatImagescandataI so xnat-data-models's
 * BaseXnatMrscandata.MRScanTypeMapping.getMappedType properly overrides
 * the parent (same erased descriptor) — using Object here would make
 * MRScanTypeMapping's typed override merely an overload and break the
 * vtable contract at runtime against apps/web's typed abstract method.
 *
 * Excluded from the xnat-api JAR.
 */
package org.nrg.xnat.helpers.scanType;

import org.nrg.xdat.model.XnatImagescandataI;

import java.util.Hashtable;
import java.util.Map;

public abstract class AbstractScanTypeMapping<HistoryType> implements ScanTypeMappingI {

    public AbstractScanTypeMapping(String projectId, String dbName, String scanSelectSql) {}

    protected String getMappedType(XnatImagescandataI scan, Map<String, HistoryType> histories) { return null; }

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
