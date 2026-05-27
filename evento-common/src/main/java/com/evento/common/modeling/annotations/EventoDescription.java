package com.evento.common.modeling.annotations;

import java.lang.annotation.*;

/**
 * Optional documentation annotation for Evento components, handlers, and payloads.
 *
 * <p>Apply to a component class or handler method to surface a human-readable
 * description in the Evento Server dashboard and graph explorer.
 *
 * <p>When absent, the dashboard falls back to the class simple name.
 *
 * <pre>{@code
 * @EventoDescription(
 *     value  = "Manages order lifecycle",
 *     detail = "Handles all commands that mutate order state. ..."
 * )
 * public class OrderAggregate extends Aggregate { ... }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface EventoDescription {
    /** Short one-liner shown in node labels and component lists. */
    String value() default "";
    /** Markdown long-form description shown in detail panels. */
    String detail() default "";
}
