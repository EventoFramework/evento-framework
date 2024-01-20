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

public abstract class JavaComponentParser<T extends Component> {


	protected final Node node;

	public JavaComponentParser(Node node) {
		this.node = node;
	}

	public static boolean isObserver(ASTTypeDeclaration classDef) throws JaxenException {
		return !classDef.findChildNodesWithXPath("//Annotation//Name[@Image = \"Observer\"]").isEmpty();
	}

	public static boolean isSaga(ASTTypeDeclaration classDef) throws JaxenException {
		return !classDef.findChildNodesWithXPath("//Annotation//Name[@Image = \"Saga\"]").isEmpty();
	}

	public static boolean isAggregate(ASTTypeDeclaration classDef) throws JaxenException {
		return !classDef.findChildNodesWithXPath("//Name[@Image = \"Aggregate\"]").isEmpty();
	}

	public static boolean isProjection(ASTTypeDeclaration classDef) throws JaxenException {
		return !classDef.findChildNodesWithXPath("//Annotation//Name[@Image = \"Projection\"]").isEmpty();
	}

	public static boolean isProjector(ASTTypeDeclaration classDef) throws JaxenException {
		return !classDef.findChildNodesWithXPath("//Annotation//Name[@Image = \"Projector\"]").isEmpty();
	}

	public static boolean isService(ASTTypeDeclaration classDef) throws JaxenException {
		return !classDef.findChildNodesWithXPath("//Annotation//Name[@Image = \"Service\"]").isEmpty();
	}

	public static boolean isInvoker(ASTTypeDeclaration classDef) throws JaxenException {
		return !classDef.findChildNodesWithXPath("//Annotation//Name[@Image = \"Invoker\"]").isEmpty();
	}

	public abstract T parse() throws Exception;

	protected String getQueryForAnnotatedMethod(String annotation) {
		return "//ClassOrInterfaceBodyDeclaration[Annotation//Name[@Image=\"" + annotation + "\"] and MethodDeclaration]//MethodDeclaration";
	}

	protected String getDeclaredClassName() {
		return node.getFirstDescendantOfType(ASTTypeDeclaration.class).getFirstDescendantOfType(ASTClassOrInterfaceDeclaration.class).getSimpleName();
	}

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


	private Stack<ASTPrimaryExpression> stack(ASTPrimaryExpression expression) {
		var s = new Stack<ASTPrimaryExpression>();
		s.add(expression);
		return s;
	}


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
			if (annot.get().equals("InvocationHandler"))
			{
				var name = methodOrConstructor.getFirstParentOfType(ASTClassOrInterfaceDeclaration.class).getSimpleName() + "::" + ((ASTMethodDeclaration) methodOrConstructor).getName();
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
			} else
			{
				var eName = methodOrConstructor.getFirstDescendantOfType(ASTFormalParameters.class).getFirstDescendantOfType(ASTClassOrInterfaceType.class).getImage();
				hs.stream().filter(eh -> eh.getPayload().getName().equals(eName)).forEach(h -> {
					if (m instanceof Query q && h instanceof HasQueryInvocations qi)
					{
						qi.addQueryInvocation(q, expr.peek().getBeginLine());
					}
					if (m instanceof Command c && h instanceof HasCommandInvocations ci)
					{
						ci.addCommandInvocation(c, expr.peek().getBeginLine());
					}
				});
			}
			return;
		}

		var mName = methodOrConstructor instanceof ASTMethodDeclaration ? ((ASTMethodDeclaration) methodOrConstructor).getName() : methodOrConstructor.getImage();
		var invoks = node.getFirstChildOfType(ASTTypeDeclaration.class).findChildNodesWithXPath("//PrimaryPrefix/Name[@Image = \"%s\"]".formatted(mName));
		for (Node i : invoks)
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
