package com.evento.common.personaldata;

import java.io.Serializable;

/** The answer to a {@link SubjectKeyRequest}: the key, or {@code null} when the subject has none. */
public class SubjectKeyResponse implements Serializable {

    private byte[] key;

    public SubjectKeyResponse() {
    }

    public SubjectKeyResponse(byte[] key) {
        this.key = key;
    }

    public byte[] getKey() {
        return key;
    }
}
