package org.nrg.xnat.exceptions;

public class PipelineNotFoundException extends Exception {
    public PipelineNotFoundException() {}
    public PipelineNotFoundException(String message) { super(message); }
    public PipelineNotFoundException(Throwable cause) { super(cause); }
    public PipelineNotFoundException(String message, Throwable cause) { super(message, cause); }
}
