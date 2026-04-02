/*
 * xnat-api: org.nrg.xnat.turbine.utils.XNATUtils
 * Compile-time facade for the real XNATUtils in apps/web.
 * At runtime, apps/web's version (WEB-INF/classes) takes precedence.
 *
 * Uses Object/unchecked generics where the real types live in xnat-data-models.
 */
package org.nrg.xnat.turbine.utils;

import org.nrg.xft.ItemI;
import org.nrg.xft.event.EventMetaI;
import org.nrg.xft.security.UserI;
import org.nrg.xnat.exceptions.InvalidArchiveStructure;

import java.io.File;
import java.util.Hashtable;
import java.util.Iterator;

/**
 * Facade with correct method signatures for compile-time resolution.
 * The real implementation lives in apps/web and is used at runtime.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class XNATUtils {

    public static String MAP_COLUMN_NAME = "map";
    public static String LAB_COLUMN_NAME = "lab_id";

    public static Hashtable getInvestigatorsForRead(String elementName, Object data) {
        throw new UnsupportedOperationException("XNATUtils facade");
    }

    public static Hashtable getInvestigatorsForRead(String elementName, UserI user) {
        throw new UnsupportedOperationException("XNATUtils facade");
    }

    public static Hashtable getInvestigatorsForCreate(String elementName, Object data) {
        throw new UnsupportedOperationException("XNATUtils facade");
    }

    public static Hashtable getInvestigatorsForCreate(String elementName, UserI user) {
        throw new UnsupportedOperationException("XNATUtils facade");
    }

    public static Hashtable getProjectsForCreate(String elementName, Object data) {
        throw new UnsupportedOperationException("XNATUtils facade");
    }

    public static Hashtable getProjectsForEdit(String elementName, Object data) {
        throw new UnsupportedOperationException("XNATUtils facade");
    }

    public static Hashtable getProjectsForCreate(String elementName, UserI user) {
        throw new UnsupportedOperationException("XNATUtils facade");
    }

    public static Hashtable getProjectsForAction(String elementName, UserI user, String action) {
        throw new UnsupportedOperationException("XNATUtils facade");
    }

    public static String getLastSessionIdForParticipant(String id, UserI user) {
        throw new UnsupportedOperationException("XNATUtils facade");
    }

    /** Returns XnatMrsessiondata at runtime; uses unchecked generic to avoid circular dep. */
    @SuppressWarnings("unchecked")
    public static <T> T getLastSessionForParticipant(String id, UserI user) {
        throw new UnsupportedOperationException("XNATUtils facade");
    }

    public static void removeScanDir(Object session, Object scan) throws Exception {
        throw new UnsupportedOperationException("XNATUtils facade");
    }

    public static void removeScanFromSessionAndDeleteFiles(Object session, Object scan,
                                                           UserI user, EventMetaI ci) throws Exception {
        throw new UnsupportedOperationException("XNATUtils facade");
    }

    public static void delete(ArchivableItem parent, ItemI item, EventMetaI ci, boolean removeFiles)
            throws Exception {
        throw new UnsupportedOperationException("XNATUtils facade");
    }

    public static void setArcProjectPaths(Object arcProject, Object preferences) throws Exception {
        // No-op in facade; real implementation in apps/web
    }

    public static void populateCatalogBean(Object cat, String header, File f) {
        throw new UnsupportedOperationException("XNATUtils facade");
    }

    public static CatalogSet getCatalogBean(Object data, ItemI input) {
        throw new UnsupportedOperationException("XNATUtils facade");
    }

    public static boolean isNull(String s) {
        return s == null;
    }

    public static boolean hasValue(String s) {
        return s != null && !s.isEmpty();
    }

    public static Object getFirstOf(final Iterator<?> i) {
        return i != null && i.hasNext() ? i.next() : null;
    }

    public static Object getFirstOf(final Object m, final Object key) {
        throw new UnsupportedOperationException("XNATUtils facade");
    }

    public static boolean isNullOrEmpty(final String s) {
        return s == null || s.isEmpty();
    }
}
