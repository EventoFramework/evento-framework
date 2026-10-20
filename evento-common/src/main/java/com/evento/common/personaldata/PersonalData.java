package com.evento.common.personaldata;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A field of a payload (an event, a command, an aggregate state...) that holds personal data
 * (3.0, crypto-shredding). It is stored encrypted with a key of its own subject, the value of the
 * object's {@link DataSubject} field; forgetting the subject deletes that key
 * ({@link SubjectKeys#forget}), and every copy of the field, in the event store, in snapshots, in
 * backups, can no longer be read. Read after that, the field is {@code null} (or the default of a
 * primitive): the events stay, their personal data is gone.
 *
 * <pre>{@code
 * public class CustomerRegistered extends DomainEvent {
 *     @DataSubject String customerId;
 *     @PersonalData String name;
 *     @PersonalData Address address;
 *     int loyaltyPoints;
 * }
 * }</pre>
 *
 * <p>Applies to fields of the classes Evento serializes as payloads (fields, not getters or
 * record components). A {@code null} field is stored as {@code null}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface PersonalData {
}
