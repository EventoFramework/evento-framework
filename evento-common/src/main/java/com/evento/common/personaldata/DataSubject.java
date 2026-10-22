package com.evento.common.personaldata;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The field whose value identifies the person the {@link PersonalData} fields of the same object
 * belong to: their key is this subject's. Use a stable, pseudonymous id (a customer id), never
 * personal data itself: it is stored in clear, it is what erasure is asked for by. One per class
 * (it may be inherited); its value must not be null when the object carries personal data.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface DataSubject {
}
