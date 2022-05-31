package org.eventrails.demo;

import org.eventrails.demo.parser.AppClassVisitor;
import org.eventrails.demo.parser.Callee;
import org.eventrails.modeling.annotations.component.*;
import org.eventrails.modeling.gateway.CommandGateway;
import org.eventrails.modeling.messaging.payload.Command;
import org.objectweb.asm.commons.Method;
import org.reflections.Reflections;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import org.objectweb.asm.ClassReader;

import static org.reflections.vfs.Vfs.DefaultUrlTypes.jarFile;

public class Parser {
	public static void main(String[] args) throws IOException, NoSuchMethodException {
		String pkg = "org.eventrails.demo";
		Reflections reflections = new Reflections(pkg);

		String targetClass = CommandGateway.class.getName().replace('.', '/');

		Method targetMethod = Method.getMethod(CommandGateway.class.getDeclaredMethod("send", Command.class));
		AppClassVisitor cv = new AppClassVisitor(targetClass, targetMethod);
		for (Class<?> aClass : reflections.getTypesAnnotatedWith(Saga.class))
		{
			try (InputStream stream = new BufferedInputStream(aClass.getClassLoader().getResourceAsStream(aClass.getName().replace('.', '/')+".class"), 1024)) {
				ClassReader reader = new ClassReader(stream);

				reader.accept(cv, 0);
			}
		}

		for (Callee c : cv.callees) {
			System.out.println(c.source + ":" + c.line + " " + c.className + " " + c.methodName + " " + c.methodDesc);
		}

	}


}
