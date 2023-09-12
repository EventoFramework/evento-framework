package org.evento.common.modeling.annotations.component;

import org.evento.common.utils.Context;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Component
public @interface Projector {
	int version();
	String[] context() default {Context.ALL};
}
