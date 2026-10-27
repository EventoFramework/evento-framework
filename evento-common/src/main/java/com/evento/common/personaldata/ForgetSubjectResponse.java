package com.evento.common.personaldata;

import java.io.Serializable;

/** The answer to a {@link ForgetSubjectRequest}: whether the subject had a key. */
public class ForgetSubjectResponse implements Serializable {

    private boolean forgotten;

    public ForgetSubjectResponse() {
    }

    public ForgetSubjectResponse(boolean forgotten) {
        this.forgotten = forgotten;
    }

    public boolean isForgotten() {
        return forgotten;
    }
}
