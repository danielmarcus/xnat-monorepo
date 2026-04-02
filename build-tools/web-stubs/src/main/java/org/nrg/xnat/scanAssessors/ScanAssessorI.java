package org.nrg.xnat.scanAssessors;

public interface ScanAssessorI {
    ScanAssessorScanI getScanById(String id);
    String getHeader();
    int getPrecedence();
}
