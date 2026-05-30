package org.example;

import java.io.Serializable;

/**
 * A {@link Serializable} type deliberately outside {@code com.evento.*} and the JDK value packages,
 * used by {@code PayloadTypeAllowlistTest} to stand in for an arbitrary application/gadget class that
 * the open default mapper accepts but a hardened package allowlist must refuse (unless its package
 * is explicitly configured).
 */
public class HostilePayload implements Serializable {
    public String name;

    public HostilePayload() {}

    public HostilePayload(String name) {
        this.name = name;
    }
}
