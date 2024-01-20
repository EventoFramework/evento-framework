package org.evento.common.documentation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This is an annotation used to mark a class as a domain.
 * The @Domain annotation is used to define the name of the domain.
 * The name of the domain is specified by the 'name' attribute of the annotation.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Domain {
    /**
     * Returns the name of the domain as specified by the @Domain annotation.
     *
     * @return the name of the domain.
     */
    String name();
}
