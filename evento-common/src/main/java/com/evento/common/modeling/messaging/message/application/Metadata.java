package com.evento.common.modeling.messaging.message.application;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.HashMap;

/**
 * The Metadata class represents a collection of key-value pairs that can be used to provide additional information
 * about a message, query, or command.
 * It extends the HashMap class, allowing for easy manipulation and retrieval of metadata values.
 */
public class Metadata extends HashMap<String, String> {

    /**
     * The FORCE_TELEMETRY variable is a key in the Metadata class that represents whether telemetry should be forced or not.
     * If the value of this variable is "true", it means that telemetry should be forced. Otherwise, if the value is "false",
     * telemetry should not be forced.
     *
     * This variable is used in conjunction with the Metadata class, which is a collection of key-value pairs used to provide
     * additional information about a message, query, or command.
     *
     * Example usage:
     *
     * Metadata metadata = new Metadata();
     * metadata.forceTelemetry(true);
     * boolean isTelemetryForced = metadata.isTelemetryForced(); // true
     * metadata.forceTelemetry(false);
     * isTelemetryForced = metadata.isTelemetryForced(); // false
     */
    public final static String FORCE_TELEMETRY = "force_telemetry";
    /**
     * The INVALIDATE_CACHE variable represents whether the cache should be invalidated or not.
     *
     * If the value of this variable is "true", it means that the cache should be invalidated. Otherwise, if the value is "false",
     * the cache should not be invalidated.
     *
     * This variable is used in conjunction with the Metadata class, which is a collection of key-value pairs used to provide
     * additional information about a message, query, or command.
     *
     * Example usage:
     *
     * Metadata metadata = new Metadata();
     * metadata.invalidateCache(true);
     * boolean isCacheInvalidated = metadata.isCacheInvalidated(); // true
     * metadata.invalidateCache(false);
     * isCacheInvalidated = metadata.isCacheInvalidated(); // false
     */
    public final static String INVALIDATE_CACHE = "invalidate_cache";

    /**
     * Sets the value of the FORCE_TELEMETRY key in the Metadata collection.
     *
     * If the value of the force parameter is true, telemetry will be forced. Otherwise, if the value is false,
     * telemetry will not be forced.
     *
     * @param force the boolean value indicating whether telemetry should be forced or not
     */
    public void forceTelemetry(boolean force){
        this.put(FORCE_TELEMETRY, String.valueOf(force));
    }

    @JsonIgnore
    public boolean isTelemetryForced(){
       return this.getOrDefault(FORCE_TELEMETRY, "false").equals("true");
    }

    /**
     * Invalidates the cache by setting the value of the "invalidate_cache" key in the Metadata collection.
     *
     * If the value of the force parameter is true, the cache will be invalidated. Otherwise, if the value is false,
     * the cache will not be invalidated.
     *
     * @param force the boolean value indicating whether the cache should be invalidated or not
     */
    public void invalidateCache(boolean force){
        this.put(INVALIDATE_CACHE, String.valueOf(force));
    }

    @JsonIgnore
    public boolean isCacheInvalidated(){
        return this.getOrDefault(INVALIDATE_CACHE, "false").equals("true");
    }
}
