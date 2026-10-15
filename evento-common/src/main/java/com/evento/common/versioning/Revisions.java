package com.evento.common.versioning;

/**
 * Reads the {@link Revision} of payload classes, and the payload name Evento routes by.
 */
public final class Revisions {

    private static final ClassValue<Integer> REVISIONS = new ClassValue<>() {
        @Override
        protected Integer computeValue(Class<?> type) {
            var annotation = type.getAnnotation(Revision.class);
            if (annotation == null) return 0;
            if (annotation.value() < 0) {
                throw new IllegalArgumentException("@Revision of " + type.getName() + " is negative: "
                        + annotation.value());
            }
            return annotation.value();
        }
    };

    private Revisions() {
    }

    /** @return the revision of {@code type}: its {@link Revision}, {@code 0} without one */
    public static int of(Class<?> type) {
        return type == null ? 0 : REVISIONS.get(type);
    }

    /**
     * The payload name of a class name, as Evento routes and dispatches by it: the last segment
     * of the binary name. Accepts {@code Class#getName()} and {@code Class#toString()} forms
     * ({@code "class com.app.OrderPlaced"}), the latter being what a serialized payload records.
     */
    public static String payloadName(String className) {
        if (className == null) return null;
        int dot = className.lastIndexOf('.');
        String name = dot < 0 ? className : className.substring(dot + 1);
        int space = name.lastIndexOf(' ');
        return space < 0 ? name : name.substring(space + 1);
    }

    /** @return the payload name of {@code type} (see {@link #payloadName(String)}) */
    public static String payloadName(Class<?> type) {
        return payloadName(type.getName());
    }
}
