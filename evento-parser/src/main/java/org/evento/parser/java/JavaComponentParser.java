package org.evento.parser.java;

import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.lang.java.ast.*;
import org.evento.parser.model.component.Component;
import org.evento.parser.model.handler.Handler;
import org.evento.parser.model.handler.HasCommandInvocations;
import org.evento.parser.model.handler.HasQueryInvocations;
import org.evento.parser.model.payload.Command;
import org.evento.parser.model.payload.Payload;
import org.evento.parser.model.payload.Query;
import org.jaxen.JaxenException;

import java.util.List;
import java.util.Objects;
import java.util.Stack;

/**
 * The JavaComponentParser class is an abstract class that provides basic functionality for parsing Java components.
 * It defines several static methods for checking the presence of specific annotations in a given ASTTypeDeclaration.
 * The abstract method parse() must be implemented in subclasses to perform the actual parsing logic.
 *
 * @param <T> The type of component to be parsed
 */
public abstract class JavaComponentParser<T extends Component> {


	protected final Node node;

	/**
	 * The JavaComponentParser class is responsible for parsing a Java component.
	 * It is a generic abstract class for parsing different types of components.
	 * The class provides methods for parsing different types of components and returning the parsed object.
     * @param node a node for a component class
     */
	public JavaComponentParser(Node node) {
		this.node = node;
	}

	/**
	 * Checks if a given ASTTypeDeclaration class is annotated with the annotation "Observer".
	 *
	 * @param classDef the ASTTypeDeclaration class to check for the "Observer" annotation
	 * @return true if the class is annotated with "Observer", false otherwise
	 * @throws JaxenException if there is an error in the XPath query
	 */
	public static boolean isObserver(ASTTypeDeclaration classDef) throws JaxenException {
		return !classDef.findChildNodesWithXPath("//Annotation//Name[@Image = \"Observer\"]").isEmpty();
	}

	/**
	 * Determines whether a given ASTTypeDeclaration class is annotated with the annotation "Saga".
	 *
	 * @param classDef the ASTTypeDeclaration class to check for the "Saga" annotation
	 * @return true if the class is annotated with "Saga", false otherwise
	 * @throws JaxenException  if there is an error in the XPath query
	 */
	public static boolean isSaga(ASTTypeDeclaration classDef) throws JaxenException {
		return !classDef.findChildNodesWithXPath("//Annotation//Name[@Image = \"Saga\"]").isEmpty();
	}

	/**
	 * Determines if a given ASTTypeDeclaration class is annotated with the annotation "Aggregate".
	 *
	 * @param classDef the ASTTypeDeclaration class to check for the "Aggregate" annotation
	 * @return true if the class is annotated with "Aggregate", false otherwise
	 * @throws JaxenException if there is an error in the XPath query
	 */
	public static boolean isAggregate(ASTTypeDeclaration classDef) throws JaxenException {
		return !classDef.findChildNodesWithXPath("//Name[@Image = \"Aggregate\"]").isEmpty();
	}

	/**
	 * Checks if a given {@link ASTTypeDeclaration} class is annotated with the annotation "Projection".
	 *
	 * @param classDef the {@link ASTTypeDeclaration} class to check for the "Projection" annotation
	 * @return true if the class is annotated with "Projection", false otherwise
	 * @throws JaxenException if there is an error in the XPath query
	 */
	public static boolean isProjection(ASTTypeDeclaration classDef) throws JaxenException {
		return !classDef.findChildNodesWithXPath("//Annotation//Name[@Image = \"Projection\"]").isEmpty();
	}

	/**
	 * Checks if a given ASTTypeDeclaration class is annotated with the annotation "Projector".
	 *
	 * @param classDef the ASTTypeDeclaration class to check for the "Projector" annotation
	 * @return true if the class is annotated with "Projector", false otherwise
	 * @throws JaxenException if there is an error in the XPath query
	 */
	public static boolean isProjector(ASTTypeDeclaration classDef) throws JaxenException {
		return !classDef.findChildNodesWithXPath("//Annotation//Name[@Image = \"Projector\"]").isEmpty();
	}

	/**
	 * Checks if a given ASTTypeDeclaration class is annotated with the annotation "Service".
	 *
	 * @param classDef the ASTTypeDeclaration class to check for the "Service" annotation
	 * @return true if the class is annotated with "Service", false otherwise
	 * @throws JaxenException if there is an error in the XPath query
	 */
	public static boolean isService(ASTTypeDeclaration classDef) throws JaxenException {
		return !classDef.findChildNodesWithXPath("//Annotation//Name[@Image = \"Service\"]").isEmpty();
	}

	/**
	 * Determines whether a given {@link ASTTypeDeclaration} class is annotated with the annotation "Invoker".
	 *
	 * @param classDef the {@link ASTTypeDeclaration} class to check for the "Invoker" annotation
	 * @return true if the class is annotated with "Invoker", false otherwise
	 * @throws JaxenException if there is an error in the XPath query
	 */
	public static boolean isInvoker(ASTTypeDeclaration classDef) throws JaxenException {
		return !classDef.findChildNodesWithXPath("//Annotation//Name[@Image = \"Invoker\"]").isEmpty();
	}

	/**
	 * Parses the given input and returns an object of type T.
	 *
	 * @return the parsed object
	 * @throws Exception if there is an error during parsing
	 */
	public abstract T parse() throws Exception;

	/**
	 * Retrieves the XPath query for finding a method annotated with the given annotation.
	 *
	 * @param annotation the annotation to search for
	 * @return the XPath query string
	 */
	protected String getQueryForAnnotatedMethod(String annotation) {
		return "//ClassOrInterfaceBodyDeclaration[Annotation//Name[@Image=\"" + annotation + "\"] and MethodDeclaration]//MethodDeclaration";
	}

	/**
	 * Retrieves the name of the class in which the method is declared.
	 *
	 * @return the name of the declaring class
	 */
	protected String getDeclaredClassName() {
		return node.getFirstDescendantOfType(ASTTypeDeclaration.class).getFirstDescendantOfType(ASTClassOrInterfaceDeclaration.class).getSimpleName();
	}

	/**
	 * Finds command invocations in a list of handler objects.
	 *
	 * @param ehs The list of handler objects.
	 * @throws JaxenException If there is an error in the XPath query.
	 */
	protected void findCommandInvocations(List<? extends Handler<?>> ehs) throws JaxenException {
		var query = "//PrimaryExpression[PrimaryPrefix/Name[ends-with(@Image,\"send\") or ends-with(@Image,\"sendAndWait\")]] | //PrimaryExpression[PrimarySuffix[ends-with(@Image,\"send\") or ends-with(@Image,\"sendAndWait\")]]";
		for (Node n : node.getFirstChildOfType(ASTTypeDeclaration.class).findChildNodesWithXPath(query))
		{
			var expr = ((ASTPrimaryExpression) n);
			var msgArg = expr.getFirstDescendantOfType(ASTClassOrInterfaceType.class);
			if (msgArg == null) return;
			var cmdName = msgArg.getImage();
			var cmd = new Command(cmdName);
			Node methodOrConstructor = expr.getFirstParentOfType(ASTMethodDeclaration.class);
			if (methodOrConstructor == null)
			{
				methodOrConstructor = expr.getFirstParentOfType(ASTConstructorDeclaration.class);
			}
			manageMessageInvocation(methodOrConstructor, cmd, ehs, stack(expr));
		}
	}

	/**
	 * Finds query invocations in a list of handler objects.
	 *
	 * @param ehs The list of handler objects.
	 * @throws JaxenException If there is an error in the XPath query.
	 */
	protected void findQueryInvocations(List<? extends Handler<?>> ehs) throws JaxenException {
		var query = "//PrimaryExpression[PrimaryPrefix/Name[ends-with(@Image,\"query\")]] | //PrimaryExpression[PrimarySuffix[ends-with(@Image,\"query\")]]";
		for (Node n : node.getFirstChildOfType(ASTTypeDeclaration.class).findChildNodesWithXPath(query))
		{
			var expr = ((ASTPrimaryExpression) n);
			var type = expr.getFirstDescendantOfType(ASTClassOrInterfaceType.class);
			if(type != null) {
				var qName = type.getImage();
				var q = new Query(qName, null);
				Node methodOrConstructor = expr.getFirstParentOfType(ASTMethodDeclaration.class);
				if (methodOrConstructor == null) {
					methodOrConstructor = expr.getFirstParentOfType(ASTConstructorDeclaration.class);
				}
				manageMessageInvocation(methodOrConstructor, q, ehs, stack(expr));
			}
		}
	}


	/**
	 * Creates a stack with the given primary expression as the initial element.
	 *
	 * @param expression the ASTPrimaryExpression to add to the stack
	 * @return a stack containing the given primary expression
	 */
	private Stack<ASTPrimaryExpression> stack(ASTPrimaryExpression expression) {
		var s = new Stack<ASTPrimaryExpression>();
		s.add(expression);
		return s;
	}


	/**
	 * Manages the invocation of a message based on the method or constructor, payload, handlers, and stack of primary expressions.
	 *
	 * @param methodOrConstructor The method or constructor node.
	 * @param m The payload.
	 * @param hs The list of handlers.
	 * @param expr The stack of primary expressions.
	 * @throws JaxenException If there is an error in the XPath query.
	 */
	private void manageMessageInvocation(Node methodOrConstructor, Payload m, List<? extends Handler<?>> hs,
										 Stack<ASTPrimaryExpression> expr
	) throws JaxenException {
		var annot = methodOrConstructor.getFirstParentOfType(ASTClassOrInterfaceBodyDeclaration.class).findChildNodesWithXPath("Annotation//Name[ends-with(@Image,\"Handler\") and not(starts-with(@Image,'Deadline'))]")
				.stream().map(Node::getImage).filter(Objects::nonNull).filter(n ->
						n.equals("AggregateCommandHandler") ||
								n.equals("CommandHandler") ||
								n.equals("EventHandler") ||
								n.equals("EventSourcingHandler") ||
								n.equals("InvocationHandler") ||
								n.equals("QueryHandler") ||
								n.equals("SagaEventHandler")
				).findFirst();
		if (annot.isPresent())
		{
			var name = annot.get().equals("InvocationHandler") ?
					methodOrConstructor.getFirstParentOfType(ASTClassOrInterfaceDeclaration.class).getSimpleName() + "::" + ((ASTMethodDeclaration) methodOrConstructor).getName() :
					methodOrConstructor.getFirstDescendantOfType(ASTFormalParameters.class).getFirstDescendantOfType(ASTClassOrInterfaceType.class).getImage();
			hs.stream().filter(eh -> eh.getPayload().getName().equals(name)).forEach(h -> {
				if (m instanceof Query q && h instanceof HasQueryInvocations qi)
				{
					qi.addQueryInvocation(q, expr.peek().getBeginLine());
				}
				if (m instanceof Command c && h instanceof HasCommandInvocations ci)
				{
					ci.addCommandInvocation(c, expr.peek().getBeginLine());
				}
			});
			return;
		}

		var mName = methodOrConstructor instanceof ASTMethodDeclaration ? ((ASTMethodDeclaration) methodOrConstructor).getName() : methodOrConstructor.getImage();
		var invocations = node.getFirstChildOfType(ASTTypeDeclaration.class).findChildNodesWithXPath("//PrimaryPrefix/Name[@Image = \"%s\"]".formatted(mName));
		for (Node i : invocations)
		{
			var mt = i.getFirstParentOfType(ASTMethodDeclaration.class);
			if (mt == null) return;
			var nExpr = new Stack<ASTPrimaryExpression>();
			nExpr.addAll(expr);
			nExpr.add(i.getFirstParentOfType(ASTPrimaryExpression.class));
			manageMessageInvocation(mt, m, hs, nExpr);
		}

	}


}
