package org.evento.parser.java;

import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.lang.java.ast.ASTClassOrInterfaceDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTMethodDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTTypeDeclaration;
import org.evento.parser.model.component.Invoker;
import org.evento.parser.model.handler.InvocationHandler;
import org.evento.parser.model.payload.Invocation;
import org.jaxen.JaxenException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * The JavaInvokerParser class is responsible for parsing a Java source code file and extracting information about Invokers.
 * It extends the JavaComponentParser class and implements the parsing logic specific to Invokers.
 */
public class JavaInvokerParser extends JavaComponentParser<Invoker> {


	/**
	 * The {@code JavaInvokerParser} class is responsible for parsing a Java source code file and extracting information about Invokers.
	 * It extends the {@code JavaComponentParser} class and implements the parsing logic specific to Invokers.
     * @param node a node for an invoker class
     */
	public JavaInvokerParser(Node node) {
		super(node);
	}

	/**
	 * Finds all invocation handlers in the class.
	 *
	 * @return A List of InvocationHandler objects representing the invocation handlers.
	 * @throws JaxenException if an error occurs while executing the XPath query.
	 */
	private List<InvocationHandler> findInvocationHandlers() throws JaxenException {
		var query = getQueryForAnnotatedMethod("InvocationHandler");
		return node.getFirstChildOfType(ASTTypeDeclaration.class).findChildNodesWithXPath(query).stream().map(
				n -> {
					var name = n.getFirstParentOfType(ASTClassOrInterfaceDeclaration.class).getSimpleName() + "::" + ((ASTMethodDeclaration) n).getName();
					return new InvocationHandler(new Invocation(name), n.getBeginLine());
				}
		).collect(Collectors.toList());
	}

	public Invoker parse() throws Exception {
		Invoker invoker = new Invoker();
		invoker.setComponentName(getDeclaredClassName());
		invoker.setInvocationHandlers(findInvocationHandlers());
		findCommandInvocations(invoker.getInvocationHandlers());
		findQueryInvocations(invoker.getInvocationHandlers());
		return invoker;
	}
}
