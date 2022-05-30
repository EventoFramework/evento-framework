package org.eventrails.demo.parser;

/**
 *
 * @author Franck Arnulfo
 */
public class Callee {

  public String className;
  public String methodName;
  public String methodDesc;
  public String source;
  public int line;

  public Callee(String cName, String mName, String mDesc, String src, int ln) {
    className = cName;
    methodName = mName;
    methodDesc = mDesc;
    source = src;
    line = ln;
  }
}