package com.evento.application.manager;

import io.github.classgraph.ClassGraph;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The classes of a bundle's base package (sub-packages included), scanned once at start with
 * ClassGraph.
 *
 * <p>{@link #annotatedWith} returns the classes carrying the annotation and their subclasses in
 * the package, as the scanner it replaces (Reflections) did, so a component extending an annotated
 * class is still found. Classes outside the package are never scanned, so two bundles in one jar
 * stay isolated.
 */
public final class ComponentScan {

    private record Scanned(Class<?> type, Set<String> annotations, List<String> subclasses) {
    }

    private final Map<String, Scanned> classes;

    private ComponentScan(Map<String, Scanned> classes) {
        this.classes = classes;
    }

    /** Scans {@code basePackage} on the context class loader and loads its classes. */
    public static ComponentScan of(String basePackage) {
        try (var result = new ClassGraph()
                .enableClassInfo()
                .enableAnnotationInfo()
                .ignoreClassVisibility()
                .acceptPackages(basePackage)
                .scan()) {
            var map = new LinkedHashMap<String, Scanned>();
            for (var info : result.getAllClasses()) {
                var annotations = new LinkedHashSet<String>();
                info.getAnnotationInfo().forEach(a -> annotations.add(a.getName()));
                var subclasses = new ArrayList<String>();
                info.getSubclasses().forEach(s -> subclasses.add(s.getName()));
                map.put(info.getName(), new Scanned(info.loadClass(), annotations, subclasses));
            }
            return new ComponentScan(map);
        }
    }

    /** Classes annotated with {@code annotation}, and their subclasses. */
    public Set<Class<?>> annotatedWith(Class<? extends Annotation> annotation) {
        var out = new LinkedHashSet<Class<?>>();
        for (var scanned : classes.values()) {
            if (!scanned.annotations().contains(annotation.getName())) continue;
            out.add(scanned.type());
            for (var sub : scanned.subclasses()) {
                var s = classes.get(sub);
                if (s != null) out.add(s.type());
            }
        }
        return out;
    }

    /** Every class of the package. */
    public Set<Class<?>> allClasses() {
        var out = new LinkedHashSet<Class<?>>();
        classes.values().forEach(s -> out.add(s.type()));
        return out;
    }
}
