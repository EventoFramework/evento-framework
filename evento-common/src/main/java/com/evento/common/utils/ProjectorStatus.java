package com.evento.common.utils;

/**
 * The ProjectorStatus class represents the status of a projector.
 * It provides a getter and setter for the headReached property.
 */
public class ProjectorStatus {

    private boolean headReached;

    /**
     * Returns the value of the `headReached` property.
     *
     * @return the value of the `headReached` property
     */
    public boolean isHeadReached() {
        return headReached;
    }

    /**
     * Sets the value of the `headReached` property.
     *
     * @param headReached the new value for the `headReached` property
     */
    public void setHeadReached(boolean headReached) {
        this.headReached = headReached;
    }
}
