package org.nrg.xnat.exceptions;

public class InvalidArchiveStructure extends Exception {
    public InvalidArchiveStructure() {}
    public InvalidArchiveStructure(String message) { super(message); }
    public InvalidArchiveStructure(Throwable cause) { super(cause); }
    public InvalidArchiveStructure(String message, Throwable cause) { super(message, cause); }
}
