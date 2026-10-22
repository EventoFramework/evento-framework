package com.evento.common.personaldata;

import java.io.Serializable;

/**
 * A bundle asks the server for a data subject's key (3.0): answered by the server itself with a
 * {@link SubjectKeyResponse}, never forwarded to a bundle.
 */
public class SubjectKeyRequest implements Serializable {

    private String subject;
    private boolean create;

    public SubjectKeyRequest() {
    }

    public SubjectKeyRequest(String subject, boolean create) {
        this.subject = subject;
        this.create = create;
    }

    public String getSubject() {
        return subject;
    }

    /** Whether to create the key when the subject has none. */
    public boolean isCreate() {
        return create;
    }
}
