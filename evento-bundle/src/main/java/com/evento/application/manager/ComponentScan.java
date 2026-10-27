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
        var loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) loader = ComponentScan.class.getClassLoader();
        // ClassGraph 6 scans only the sources it is given: the bundle's class loader (and its
        // parents, as before) and the application modules.
        try (var result = new ClassGraph()
                .enableClassLoaders(loader)
                .enableNonSystemModules()
                .enableClassInfo()
                .enableAnnotationInfo()
                .ignoreClassVisibility()
                .acceptPackages(basePackage)
                .scan()) {
            var map = new LinkedHashMap<String, Scanned>();
            for (var info : result.getAllClasses()) {
                var annotations = new LinkedHashSet<String>();
                info.getDirectAnnotationInfo().forEach(a -> annotations.add(a.getName()));
                var subclasses = new ArrayList<String>();
                info.getAllSubclasses().forEach(s -> subclasses.add(s.getName()));
                map.put(info.getName(), new Scanned(load(info.getName(), loader), annotations, subclasses));
            }
            return new ComponentScan(map);
        }
    }

    // ClassGraph 6 only reads classfiles; loading them is the caller's job.
    private static Class<?> load(String name, ClassLoader loader) {
        try {
            return Class.forName(name, false, loader);
        } catch (ClassNotFoundException | LinkageError e) {
            throw new IllegalStateException("Cannot load scanned class " + name, e);
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
