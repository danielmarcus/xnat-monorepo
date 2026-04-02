package org.nrg.action;

import org.restlet.data.Status;

/** Compilation stub for circular dependency resolution. */
public class ClientException extends Exception {
    public ClientException() {}
    public ClientException(String message) { super(message); }
    public ClientException(String message, Throwable cause) { super(message, cause); }
    public ClientException(Throwable cause) { super(cause); }
    public ClientException(Status status, String message) { super(message); }
    public ClientException(Status status, Throwable cause) { super(cause); }
    public ClientException(Status status, String message, Throwable cause) { super(message, cause); }
    public ClientException(Status status, Exception cause) { super(cause); }
}
