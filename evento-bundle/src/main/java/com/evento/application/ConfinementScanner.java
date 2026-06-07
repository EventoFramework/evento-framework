package com.evento.application;

import org.objectweb.asm.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Registration-time confinement check: flags Command/Query gateway invocations
 * that live <em>outside</em> a RECQ component class.
 *
 * <p>The static extraction performed by {@link AsmInvocationScanner} follows a
 * handler's calls only within its declaring class (the <em>confinement
 * assumption</em>). A gateway call placed in a plain helper class — an injected
 * collaborator, a shared utility, an anonymous inner class — is therefore
 * invisible to extraction: the derived interaction graph silently
 * under-approximates the application's emit sets. This scanner closes that gap
 * at the same moment the rest of the model is checked: it sweeps every class in
 * the scanned packages that is <em>not</em> a registered component and reports
 * each gateway call site found there.
 *
 * <p>Detection reuses {@link AsmInvocationScanner#isCommandGatewayCall} /
 * {@link AsmInvocationScanner#isQueryGatewayCall}: a call named
 * {@code send} (resp. {@code query}) whose descriptor takes
 * the abstract {@code Command} (resp. {@code Query}) base as first argument is
 * a gateway invocation regardless of which interface or wrapper declares it.
 *
 * <p>What this does NOT see: classes outside the scanned packages (the
 * closed-world assumption already requires deployed code = scanned code) and
 * reflective dispatch. Violations are reported by {@code EventoBundle} as
 * warnings, or rejected at start-up when {@code strictConfinement} is set.
 */
final class ConfinementScanner {

    /**
     * One gateway call site found outside a component class.
     *
     * @param className  binary name of the offending class
     * @param methodName method containing the call
     * @param line       source line of the call (0 if no debug info)
     * @param kind       {@code "command"} or {@code "query"}
     */
    record Violation(String className, String methodName, int line, String kind) {}

    private ConfinementScanner() {}

    /**
     * Scans a single class's bytecode for gateway call sites.
     */
    static List<Violation> scan(Class<?> cls) throws IOException {
        String internalName = cls.getName().replace('.', '/');
        InputStream in = cls.getClassLoader().getResourceAsStream(internalName + ".class");
        if (in == null) return List.of();

        List<Violation> violations = new ArrayList<>();
        ClassReader reader = new ClassReader(in);
        // No SKIP_DEBUG: we want LineNumberTable for violation line numbers.
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    private int currentLine = 0;

                    @Override
                    public void visitLineNumber(int line, Label start) {
                        currentLine = line;
                    }

                    @Override
                    public void visitMethodInsn(int opcode, String callOwner, String calledName,
                                                String calledDesc, boolean isInterface) {
                        if (AsmInvocationScanner.isCommandGatewayCall(calledName, calledDesc)) {
                            violations.add(new Violation(cls.getName(), name, currentLine, "command"));
                        } else if (AsmInvocationScanner.isQueryGatewayCall(calledName, calledDesc)) {
                            violations.add(new Violation(cls.getName(), name, currentLine, "query"));
                        }
                    }
                };
            }
        }, 0);
        return violations;
    }

    /**
     * Sweeps every scanned class that is not a registered component and collects
     * the gateway call sites found there. Classes whose bytecode cannot be read
     * are skipped (they cannot host a compiled gateway call either).
     */
    static List<Violation> check(Collection<Class<?>> allClasses,
                                 Set<Class<?>> componentClasses) {
        List<Violation> all = new ArrayList<>();
        for (Class<?> cls : allClasses) {
            if (componentClasses.contains(cls)) continue;
            try {
                all.addAll(scan(cls));
            } catch (IOException ignored) {
                // unreadable bytecode — nothing compiled to scan
            }
        }
        all.sort(Comparator.comparing(Violation::className)
                .thenComparingInt(Violation::line));
        return all;
    }
}
