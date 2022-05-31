package org.eventrails.demo.parser;

import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.Method;

/**
 *
 * @author Franck Arnulfo
 */
public class AppClassVisitor extends ClassVisitor {

  public String source;
  public String className;
  public String methodName;
  public String methodDesc;

  private final AppMethodVisitor mv;
  public final List<Callee> callees = new ArrayList<>();

  public AppClassVisitor(String targetClass, Method targetMethod) {
    super(Opcodes.ASM6);
//    this.source = source;
//    this.className = className;
//    this.methodName = methodName;
//    this.methodDesc = this.methodDesc;
    mv = new AppMethodVisitor(targetClass, targetMethod, this);
  }

  @Override
  public void visit(int version, int access, String name,
    String signature, String superName, String[] interfaces) {
    className = name;
  }

  @Override
  public void visitSource(String source, String debug) {
    this.source = source;
  }

  @Override
  public MethodVisitor visitMethod(int access, String name,
    String desc, String signature,
    String[] exceptions) {
    methodName = name;
    methodDesc = desc;

    return mv;
  }

}