package org.nrg.xnat.scanAssessors;

import java.util.Comparator;

public class AssessorComparator implements Comparator<ScanAssessorI> {
    @Override
    public int compare(ScanAssessorI o1, ScanAssessorI o2) {
        return Integer.compare(o1.getPrecedence(), o2.getPrecedence());
    }
}
