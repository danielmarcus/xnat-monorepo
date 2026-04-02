package org.nrg.xnat.helpers.scanType;

import java.util.Hashtable;
import java.util.Map;

/**
 * Compilation stub for circular dependency resolution.
 *
 * The real AbstractScanTypeMapping has signature:
 *   AbstractScanTypeMapping&lt;HistoryType&gt; with getMappedType(XnatImagescandataI, Map)
 *
 * Since we can't reference XnatImagescandataI from web-stubs, we keep one type parameter
 * for HistoryType and use Object for the scan parameter. The subclass MRScanTypeMapping
 * will define its own getMappedType(XnatImagescandataI, ...) which overloads (not overrides)
 * the abstract method. To make this compile, we make this class non-abstract for the
 * getMappedType method and provide a default implementation.
 */
public abstract class AbstractScanTypeMapping<HistoryType> implements ScanTypeMappingI {

    public AbstractScanTypeMapping(String projectId, String dbName, String scanSelectSql) {}

    /**
     * Non-abstract stub method. Subclasses in xnat-data-models define a more specific
     * overload with XnatImagescandataI as the first parameter, which the real
     * AbstractScanTypeMapping's setType() dispatches to.
     */
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
