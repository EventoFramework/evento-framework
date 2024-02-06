package com.evento.common.modeling.annotations.component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The Component annotation is a marker annotation that designates a class as a component.
 * Components can be annotated with other specific annotations such as {@link Service}, {@link Observer},
 * {@link Projection}, {@link Saga}, {@link Projector}, and {@link Invoker} to categorize and identify various roles or
 * characteristics of classes within a software system.
 */
@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Component {
}
