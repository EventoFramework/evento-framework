package com.evento.application.performance;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * The Track annotation is used to mark methods and classes for tracking and tracing purposes.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Track {
}
