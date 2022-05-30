package org.eventrails.demo.parser;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.Method;

public class AppMethodVisitor extends MethodVisitor {

  private boolean callsTarget;
  private int line;
  private final String targetClass;
  private final Method targetMethod;
  private final AppClassVisitor appClassVisitor;

  public AppMethodVisitor(String targetClass, Method targetMethod, AppClassVisitor appClassVisitor) {
    super(Opcodes.ASM6);
    this.targetClass = targetClass;
    this.targetMethod = targetMethod;
    this.appClassVisitor = appClassVisitor;
  }

  @Override
  public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
    if (owner.equals(targetClass)
      && name.equals(targetMethod.getName())
      && desc.equals(targetMethod.getDescriptor())) {
      callsTarget = true;
    }
  }

  @Override
  public void visitCode() {
    callsTarget = false;
  }

  @Override
  public void visitLineNumber(int line, Label start) {
    this.line = line;
  }

  @Override
  public void visitEnd() {
    if (callsTarget) {
      appClassVisitor.callees.add(new Callee(appClassVisitor.className, appClassVisitor.methodName,
        appClassVisitor.methodDesc, appClassVisitor.source, line));
    }
  }

}