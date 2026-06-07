package com.evento.application;

import com.evento.common.modeling.messaging.payload.Command;
import com.evento.common.modeling.messaging.payload.Query;
import org.objectweb.asm.*;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Statically analyses a handler method's bytecode to discover which Command and Query
 * subtypes are actually passed to {@code CommandGateway.send} or
 * {@code QueryGateway.query} within its transitive intra-class call closure.
 *
 * <h3>Detection strategy</h3>
 * <ol>
 *   <li>Build an intra-class call graph and per-method operand-stack snapshot in a
 *       single ASM pass over the declaring class.</li>
 *   <li>The operand-stack model tracks the concrete reference type for every slot
 *       using:
 *       <ul>
 *         <li>{@code NEW T} → push T</li>
 *         <li>{@code ASTORE n} / {@code ALOAD n} → local-variable type map</li>
 *         <li>{@code GETFIELD}/{@code GETSTATIC} → field descriptor</li>
 *         <li>method return type → descriptor (e.g. a factory method returning
 *             {@code FooCmd} is tracked as {@code FooCmd})</li>
 *         <li>lambda captures via {@code INVOKEDYNAMIC} + LambdaMetafactory bootstrap</li>
 *       </ul>
 *   </li>
 *   <li>A gateway call is identified by:
 *       <ul>
 *         <li>method name {@code send}, first descriptor arg is
 *             {@code Command} → CommandGateway call</li>
 *         <li>method name {@code query}, first descriptor arg is {@code Query} →
 *             QueryGateway call</li>
 *       </ul>
 *   </li>
 *   <li>At each gateway call, the abstract stack is scanned top-down for the first
 *       entry assignable to {@code Command} or {@code Query} (the receiver and
 *       non-payload arguments — {@code Metadata}, {@code Message}, {@code long},
 *       {@code TimeUnit} — are never Command/Query, so no false positives across
 *       overloads). The current source line is recorded as the invocation key.</li>
 *   <li>BFS from the target handler through the intra-class call graph collects
 *       all transitively reachable method findings (jump detection guarantee).</li>
 * </ol>
 *
 * <h3>What this does NOT capture</h3>
 * <ul>
 *   <li>Commands/queries passed as {@code Command} (abstract base) rather than a
 *       concrete subtype — the concrete type is unresolvable. These are reported
 *       as {@link Result#unresolved()} so the confinement check can surface them
 *       instead of failing silently.</li>
 *   <li>Commands constructed but <em>never</em> sent — correctly excluded.</li>
 *   <li>Cross-class helpers (injected collaborators) — only intra-class calls.
 *       {@link ConfinementScanner} flags gateway calls living outside component
 *       classes at registration time.</li>
 * </ul>
 */
final class AsmInvocationScanner {

    /**
     * @param commands source-line → Command simple name for each gateway invocation
     * @param queries  source-line → Query simple name for each gateway invocation
     * @param unresolved source-line → gateway kind ({@code "command"}/{@code "query"})
     *                   for invocations whose concrete payload type could not be
     *                   resolved statically (e.g. sends typed as the abstract base)
     * @param handlerLine first source line of the handler method body (0 if unknown)
     */
    record Result(Map<Integer, String> commands, Map<Integer, String> queries,
                  Map<Integer, String> unresolved, int handlerLine) {
        static final Result EMPTY = new Result(Map.of(), Map.of(), Map.of(), 0);
    }

    /** Sentinel pushed for primitive / non-reference stack slots. */
    private static final String PRIM = "#";

    private static final String COMMAND_INTERNAL =
            "com/evento/common/modeling/messaging/payload/Command";
    private static final String QUERY_INTERNAL =
            "com/evento/common/modeling/messaging/payload/Query";

    private AsmInvocationScanner() {}

    // ── gateway-call predicates (shared with ConfinementScanner) ──────────────

    static boolean isCommandGatewayCall(String name, String desc) {
        if (!name.equals("send")) return false;
        Type[] args = Type.getArgumentTypes(desc);
        return args.length > 0 && COMMAND_INTERNAL.equals(args[0].getInternalName());
    }

    static boolean isQueryGatewayCall(String name, String desc) {
        if (!name.equals("query")) return false;
        Type[] args = Type.getArgumentTypes(desc);
        return args.length > 0 && QUERY_INTERNAL.equals(args[0].getInternalName());
    }

    static Result scan(Method handlerMethod) throws IOException {
        Class<?> owner = handlerMethod.getDeclaringClass();
        String internalName = owner.getName().replace('.', '/');
        String startKey = handlerMethod.getName() + Type.getMethodDescriptor(handlerMethod);

        InputStream in = owner.getClassLoader()
                .getResourceAsStream(internalName + ".class");
        if (in == null) return Result.EMPTY;

        Map<String, Map<Integer, String>> directCommands   = new HashMap<>();
        Map<String, Map<Integer, String>> directQueries    = new HashMap<>();
        Map<String, Map<Integer, String>> directUnresolved = new HashMap<>();
        Map<String, Set<String>>          callGraph        = new HashMap<>();
        int[] handlerLineRef = {0};

        ClassReader reader = new ClassReader(in);
        // No SKIP_DEBUG: we need LineNumberTable for invocation line numbers and handler line.
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                String methodKey = name + descriptor;
                boolean isHandler = methodKey.equals(startKey);
                return new MethodScanner(
                        methodKey, access, descriptor,
                        internalName, owner,
                        directCommands, directQueries, directUnresolved, callGraph,
                        isHandler ? handlerLineRef : null);
            }
        }, 0);

        // BFS from the handler method through intra-class call graph
        Set<String> visited = new HashSet<>();
        Deque<String> worklist = new ArrayDeque<>();
        worklist.add(startKey);

        Map<Integer, String> allCmds   = new LinkedHashMap<>();
        Map<Integer, String> allQrys   = new LinkedHashMap<>();
        Map<Integer, String> allUnres  = new LinkedHashMap<>();

        while (!worklist.isEmpty()) {
            String current = worklist.poll();
            if (!visited.add(current)) continue;
            if (directCommands.containsKey(current))   allCmds.putAll(directCommands.get(current));
            if (directQueries.containsKey(current))    allQrys.putAll(directQueries.get(current));
            if (directUnresolved.containsKey(current)) allUnres.putAll(directUnresolved.get(current));
            callGraph.getOrDefault(current, Set.of()).stream()
                    .filter(c -> !visited.contains(c))
                    .forEach(worklist::add);
        }

        return new Result(Collections.unmodifiableMap(allCmds),
                          Collections.unmodifiableMap(allQrys),
                          Collections.unmodifiableMap(allUnres),
                          handlerLineRef[0]);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Per-method abstract-stack visitor
    // ─────────────────────────────────────────────────────────────────────────

    private static final class MethodScanner extends MethodVisitor {

        private final String key;
        private final String ownerInternal;
        private final Class<?> ownerClass;
        private final Map<String, Map<Integer, String>> directCommands;
        private final Map<String, Map<Integer, String>> directQueries;
        private final Map<String, Map<Integer, String>> directUnresolved;
        private final Map<String, Set<String>> callGraph;
        /** Non-null only for the root handler method — stores its first line. */
        private final int[] handlerLineRef;

        /** Abstract operand stack: internal type name, PRIM, or null (unknown ref). */
        private final ArrayDeque<String> stack = new ArrayDeque<>();
        /** Local variable type map: slot → internal type name / PRIM / null. */
        private final HashMap<Integer, String> locals = new HashMap<>();

        private final Map<Integer, String> cmds  = new LinkedHashMap<>();
        private final Map<Integer, String> qrys  = new LinkedHashMap<>();
        private final Map<Integer, String> unres = new LinkedHashMap<>();

        /** Source line of the current instruction (updated by visitLineNumber). */
        private int currentLine = 0;

        MethodScanner(String key, int access, String descriptor,
                      String ownerInternal, Class<?> ownerClass,
                      Map<String, Map<Integer, String>> directCommands,
                      Map<String, Map<Integer, String>> directQueries,
                      Map<String, Map<Integer, String>> directUnresolved,
                      Map<String, Set<String>> callGraph,
                      int[] handlerLineRef) {
            super(Opcodes.ASM9);
            this.key              = key;
            this.ownerInternal    = ownerInternal;
            this.ownerClass       = ownerClass;
            this.directCommands   = directCommands;
            this.directQueries    = directQueries;
            this.directUnresolved = directUnresolved;
            this.callGraph        = callGraph;
            this.handlerLineRef   = handlerLineRef;
            initLocals(access, descriptor);
        }

        /** Pre-populate locals from method parameters (covers lambda captures too). */
        private void initLocals(int access, String descriptor) {
            int slot = 0;
            if ((access & Opcodes.ACC_STATIC) == 0) {
                locals.put(slot++, ownerInternal);  // 'this'
            }
            for (Type t : Type.getArgumentTypes(descriptor)) {
                locals.put(slot, t.getSort() == Type.OBJECT ? t.getInternalName() : PRIM);
                slot += t.getSize();
            }
        }

        // ── line tracking ───────────────────────────────────────────────────

        @Override
        public void visitLineNumber(int line, Label start) {
            // Capture the handler method's own first source line
            if (handlerLineRef != null && handlerLineRef[0] == 0) {
                handlerLineRef[0] = line;
            }
            currentLine = line;
        }

        // ── stack helpers ──────────────────────────────────────────────────

        private void push(String t)  { stack.push(t); }
        private void pushPrim()       { stack.push(PRIM); }
        private String pop()          { return stack.isEmpty() ? null : stack.pop(); }
        private String peek()         { return stack.isEmpty() ? null : stack.peek(); }

        // ── visitor overrides ──────────────────────────────────────────────

        @Override
        public void visitInsn(int opcode) {
            switch (opcode) {
                case Opcodes.ACONST_NULL  -> push(null);
                case Opcodes.DUP          -> push(peek());
                case Opcodes.DUP_X1       -> { var t = pop(); var s = pop(); push(t); push(s); push(t); }
                case Opcodes.DUP_X2       -> { var t = pop(); var s = pop(); var u = pop(); push(t); push(u); push(s); push(t); }
                case Opcodes.DUP2         -> { var t = pop(); var s = pop(); push(s); push(t); push(s); push(t); }
                case Opcodes.DUP2_X1      -> { var t = pop(); var s = pop(); var u = pop(); push(s); push(t); push(u); push(s); push(t); }
                case Opcodes.DUP2_X2      -> { var t = pop(); var s = pop(); var u = pop(); var v = pop(); push(s); push(t); push(v); push(u); push(s); push(t); }
                case Opcodes.POP          -> pop();
                case Opcodes.POP2         -> { pop(); pop(); }
                case Opcodes.SWAP         -> { var t = pop(); var s = pop(); push(t); push(s); }
                case Opcodes.IADD, Opcodes.ISUB, Opcodes.IMUL, Opcodes.IDIV, Opcodes.IREM,
                     Opcodes.FADD, Opcodes.FSUB, Opcodes.FMUL, Opcodes.FDIV, Opcodes.FREM,
                     Opcodes.IAND, Opcodes.IOR,  Opcodes.IXOR,
                     Opcodes.ISHL, Opcodes.ISHR,  Opcodes.IUSHR,
                     Opcodes.FCMPL, Opcodes.FCMPG -> { pop(); pop(); pushPrim(); }
                case Opcodes.LADD, Opcodes.LSUB, Opcodes.LMUL, Opcodes.LDIV, Opcodes.LREM,
                     Opcodes.DADD, Opcodes.DSUB, Opcodes.DMUL, Opcodes.DDIV, Opcodes.DREM,
                     Opcodes.LAND, Opcodes.LOR,  Opcodes.LXOR -> { pop(); pop(); pop(); pop(); pushPrim(); pushPrim(); }
                case Opcodes.LSHL, Opcodes.LSHR, Opcodes.LUSHR -> { pop(); pop(); pop(); pushPrim(); pushPrim(); }
                case Opcodes.LCMP, Opcodes.DCMPL, Opcodes.DCMPG -> { pop(); pop(); pop(); pop(); pushPrim(); }
                case Opcodes.INEG, Opcodes.FNEG -> { pop(); pushPrim(); }
                case Opcodes.LNEG, Opcodes.DNEG -> { pop(); pop(); pushPrim(); pushPrim(); }
                case Opcodes.I2F, Opcodes.I2B, Opcodes.I2C, Opcodes.I2S, Opcodes.F2I -> { pop(); pushPrim(); }
                case Opcodes.I2L, Opcodes.I2D, Opcodes.F2L, Opcodes.F2D -> { pop(); pushPrim(); pushPrim(); }
                case Opcodes.L2I, Opcodes.L2F, Opcodes.D2I, Opcodes.D2F -> { pop(); pop(); pushPrim(); }
                case Opcodes.L2D, Opcodes.D2L -> { pop(); pop(); pushPrim(); pushPrim(); }
                case Opcodes.ARRAYLENGTH -> { pop(); pushPrim(); }
                case Opcodes.AALOAD      -> { pop(); pop(); push(null); }
                case Opcodes.IALOAD, Opcodes.FALOAD, Opcodes.SALOAD, Opcodes.CALOAD, Opcodes.BALOAD -> { pop(); pop(); pushPrim(); }
                case Opcodes.LALOAD, Opcodes.DALOAD -> { pop(); pop(); pushPrim(); pushPrim(); }
                case Opcodes.AASTORE, Opcodes.IASTORE, Opcodes.FASTORE,
                     Opcodes.SASTORE, Opcodes.CASTORE, Opcodes.BASTORE -> { pop(); pop(); pop(); }
                case Opcodes.LASTORE, Opcodes.DASTORE -> { pop(); pop(); pop(); pop(); }
                case Opcodes.ATHROW        -> pop();
                case Opcodes.MONITORENTER, Opcodes.MONITOREXIT -> pop();
                case Opcodes.INSTANCEOF    -> { pop(); pushPrim(); }
                case Opcodes.ARETURN, Opcodes.IRETURN, Opcodes.FRETURN -> pop();
                case Opcodes.LRETURN, Opcodes.DRETURN -> { pop(); pop(); }
                case Opcodes.RETURN -> {}
            }
        }

        @Override
        public void visitVarInsn(int opcode, int var) {
            switch (opcode) {
                case Opcodes.ALOAD  -> push(locals.get(var));
                case Opcodes.ASTORE -> locals.put(var, pop());
                case Opcodes.ILOAD, Opcodes.FLOAD -> pushPrim();
                case Opcodes.LLOAD, Opcodes.DLOAD -> { pushPrim(); pushPrim(); }
                case Opcodes.ISTORE, Opcodes.FSTORE -> pop();
                case Opcodes.LSTORE, Opcodes.DSTORE -> { pop(); pop(); }
            }
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            switch (opcode) {
                case Opcodes.NEW        -> push(type);
                case Opcodes.ANEWARRAY  -> { pop(); push(null); }
                case Opcodes.CHECKCAST  -> {}
                case Opcodes.INSTANCEOF -> { pop(); pushPrim(); }
            }
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String desc) {
            switch (opcode) {
                case Opcodes.GETFIELD  -> { pop(); push(descRef(desc)); }
                case Opcodes.GETSTATIC -> push(descRef(desc));
                case Opcodes.PUTFIELD  -> { pop(); pop(); }
                case Opcodes.PUTSTATIC -> pop();
            }
        }

        @Override
        public void visitIntInsn(int opcode, int operand) {
            switch (opcode) {
                case Opcodes.BIPUSH, Opcodes.SIPUSH -> pushPrim();
                case Opcodes.NEWARRAY -> { pop(); push(null); }
            }
        }

        @Override
        public void visitLdcInsn(Object value) {
            if (value instanceof Long || value instanceof Double) { pushPrim(); pushPrim(); }
            else if (value instanceof String)   push("java/lang/String");
            else if (value instanceof org.objectweb.asm.Type) push("java/lang/Class");
            else pushPrim();
        }

        @Override
        public void visitJumpInsn(int opcode, Label label) {
            switch (opcode) {
                case Opcodes.IFEQ, Opcodes.IFNE, Opcodes.IFLT, Opcodes.IFGE,
                     Opcodes.IFGT, Opcodes.IFLE, Opcodes.IFNULL, Opcodes.IFNONNULL -> pop();
                case Opcodes.IF_ICMPEQ, Opcodes.IF_ICMPNE, Opcodes.IF_ICMPLT,
                     Opcodes.IF_ICMPGE, Opcodes.IF_ICMPGT, Opcodes.IF_ICMPLE,
                     Opcodes.IF_ACMPEQ, Opcodes.IF_ACMPNE -> { pop(); pop(); }
            }
        }

        @Override
        public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) { pop(); }

        @Override
        public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) { pop(); }

        @Override
        public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
            for (int i = 0; i < numDimensions; i++) pop();
            push(null);
        }

        @Override
        public void visitMethodInsn(int opcode, String callOwner, String calledName,
                                    String calledDesc, boolean isInterface) {
            if (isCommandGatewayCall(calledName, calledDesc)) {
                captureGatewayArg(true);
            } else if (isQueryGatewayCall(calledName, calledDesc)) {
                captureGatewayArg(false);
            }

            Type[] argTypes = Type.getArgumentTypes(calledDesc);
            for (int i = argTypes.length - 1; i >= 0; i--) {
                for (int s = 0; s < argTypes[i].getSize(); s++) pop();
            }
            if (opcode != Opcodes.INVOKESTATIC) pop();

            Type ret = Type.getReturnType(calledDesc);
            switch (ret.getSort()) {
                case Type.VOID -> {}
                case Type.OBJECT -> push(ret.getInternalName());
                case Type.ARRAY  -> push(null);
                case Type.LONG, Type.DOUBLE -> { pushPrim(); pushPrim(); }
                default -> pushPrim();
            }

            if (callOwner.equals(ownerInternal)) {
                callGraph.computeIfAbsent(key, k -> new HashSet<>())
                        .add(calledName + calledDesc);
            }
        }

        @Override
        public void visitInvokeDynamicInsn(String name, String descriptor,
                                           Handle bsm, Object... bsmArgs) {
            if ("java/lang/invoke/LambdaMetafactory".equals(bsm.getOwner())) {
                for (Object arg : bsmArgs) {
                    if (arg instanceof Handle h && h.getOwner().equals(ownerInternal)) {
                        callGraph.computeIfAbsent(key, k -> new HashSet<>())
                                .add(h.getName() + h.getDesc());
                    }
                }
            }
            for (Type t : Type.getArgumentTypes(descriptor)) {
                for (int s = 0; s < t.getSize(); s++) pop();
            }
            Type ret = Type.getReturnType(descriptor);
            push(ret.getSort() == Type.OBJECT ? ret.getInternalName() : null);
        }

        @Override
        public void visitEnd() {
            directCommands.put(key, Map.copyOf(cmds));
            directQueries.put(key, Map.copyOf(qrys));
            directUnresolved.put(key, Map.copyOf(unres));
        }

        // ── gateway detection ──────────────────────────────────────────────

        /**
         * Scan the abstract stack top-down for the first entry assignable to
         * {@link Command} (isCommand=true) or {@link Query} (isCommand=false),
         * then record (currentLine → simpleClassName) in the appropriate map.
         * A gateway call whose payload cannot be resolved to a concrete subtype
         * (e.g. a value typed as the abstract base) is recorded in {@code unres}
         * so callers can warn instead of under-approximating silently.
         */
        private void captureGatewayArg(boolean isCommand) {
            for (String type : stack) {
                if (type == null || type.equals(PRIM)) continue;
                String className = type.replace('/', '.');
                try {
                    Class<?> cls = Class.forName(className, false, ownerClass.getClassLoader());
                    if (isCommand && Command.class.isAssignableFrom(cls) && cls != Command.class) {
                        cmds.put(currentLine, cls.getSimpleName());
                        return;
                    }
                    if (!isCommand && Query.class.isAssignableFrom(cls) && cls != Query.class) {
                        qrys.put(currentLine, cls.getSimpleName());
                        return;
                    }
                } catch (ClassNotFoundException ignored) {}
            }
            unres.put(currentLine, isCommand ? "command" : "query");
        }

        private static String descRef(String desc) {
            if (desc.startsWith("L") && desc.endsWith(";"))
                return desc.substring(1, desc.length() - 1);
            return PRIM;
        }
    }
}
