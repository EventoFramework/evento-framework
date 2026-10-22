package com.evento.common.versioning;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The revision of a payload's shape (3.0). Every serialized payload records the revision of its
 * class when it was written; an {@link Upcaster} turns a stored or received payload of an older
 * revision into the shape the current class expects, before it is deserialized.
 *
 * <p>A class without the annotation is revision {@code 0}, and so is every payload written before
 * revisions existed. Raise the revision when the JSON of the class changes in a way the class can
 * no longer read as it is: a field renamed, split, merged or moved, a value whose meaning or unit
 * changes. Changes Jackson already tolerates need no new revision: a field added (absent in old
 * payloads, it keeps its Java default) or removed (ignored when read).
 *
 * <pre>{@code
 * @Revision(1)
 * public class OrderPlaced extends DomainEvent {
 *     private String orderId;
 *     private String customerId;    // revision 0 called it "clientId"
 * }
 * }</pre>
 *
 * <p>The annotation is not inherited: each payload class has its own revision.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Revision {

    /** @return the revision of the class, {@code 0} or more */
    int value();
}
