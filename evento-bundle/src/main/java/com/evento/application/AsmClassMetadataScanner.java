package com.evento.application;

import com.evento.common.modeling.annotations.EventoDescription;
import org.objectweb.asm.*;

import java.io.IOException;
import java.io.InputStream;

/**
 * Extracts source-navigation metadata from a class file using ASM.
 *
 * <p>Does NOT require sources on the classpath — all information is read from
 * bytecode debug attributes ({@code SourceFile}, {@code LineNumberTable}).
 *
 * <p>The {@code @EventoDescription} annotation is read via reflection
 * (RUNTIME retention), so no ASM annotation parsing is needed.
 */
final class AsmClassMetadataScanner {

    record ClassMetadata(
            String sourcePath,
            int declarationLine,
            String description,
            String detail
    ) {
        static final ClassMetadata EMPTY = new ClassMetadata("", 0, "", "");
    }

    private AsmClassMetadataScanner() {}

    /**
     * Scans {@code cls} and returns source-navigation metadata.
     *
     * <ul>
     *   <li>{@code sourcePath} — relative path assembled from the class package and
     *       the {@code SourceFile} class-file attribute, e.g.
     *       {@code com/example/order/OrderAggregate.java}.</li>
     *   <li>{@code declarationLine} — minimum line number found in any {@code <init>}
     *       constructor's {@code LineNumberTable}; approximates the class declaration.</li>
     *   <li>{@code description}/{@code detail} — from {@code @EventoDescription} if
     *       present; otherwise {@code description} falls back to the simple class name.</li>
     * </ul>
     */
    static ClassMetadata scan(Class<?> cls) {
        var ann = cls.getAnnotation(EventoDescription.class);
        String description = (ann != null && !ann.value().isEmpty())
                ? ann.value() : cls.getSimpleName();
        String detail = ann != null ? ann.detail() : "";

        ClassLoader cl = cls.getClassLoader();
        if (cl == null) cl = ClassLoader.getSystemClassLoader();
        String internalName = cls.getName().replace('.', '/');
        try (InputStream in = cl.getResourceAsStream(internalName + ".class")) {
            if (in == null) return new ClassMetadata("", 0, description, detail);

            String[] sourceFile = {null};
            int[] minLine = {Integer.MAX_VALUE};

            ClassReader reader = new ClassReader(in);
            reader.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public void visitSource(String source, String debug) {
                    sourceFile[0] = source;
                }

                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                  String signature, String[] exceptions) {
                    if (!"<init>".equals(name)) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitLineNumber(int line, Label start) {
                            if (line < minLine[0]) minLine[0] = line;
                        }
                    };
                }
            }, 0); // no SKIP_DEBUG — we need LineNumberTable and SourceFile

            String pkg = cls.getPackageName().replace('.', '/');
            String path = "";
            if (sourceFile[0] != null) {
                path = pkg.isEmpty() ? sourceFile[0] : pkg + "/" + sourceFile[0];
            }
            int line = minLine[0] == Integer.MAX_VALUE ? 0 : minLine[0];
            return new ClassMetadata(path, line, description, detail);
        } catch (IOException e) {
            return new ClassMetadata("", 0, description, detail);
        }
    }
}
