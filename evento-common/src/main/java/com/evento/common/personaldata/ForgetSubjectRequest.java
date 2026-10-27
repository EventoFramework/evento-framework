package com.evento.common.personaldata;

import java.io.Serializable;

/**
 * A bundle asks the server to forget a data subject (3.0): delete its key. Answered by the server
 * itself with a {@link ForgetSubjectResponse}.
 */
public class ForgetSubjectRequest implements Serializable {

    private String subject;

    public ForgetSubjectRequest() {
    }

    public ForgetSubjectRequest(String subject) {
        this.subject = subject;
    }

    public String getSubject() {
        return subject;
    }
}
