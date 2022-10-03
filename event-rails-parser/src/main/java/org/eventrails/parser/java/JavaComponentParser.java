package org.eventrails.parser.java;

import net.sourceforge.pmd.lang.ast.AbstractNode;
import net.sourceforge.pmd.lang.java.ast.*;
import org.eventrails.parser.model.node.Node;
import org.eventrails.parser.model.handler.Handler;
import org.eventrails.parser.model.handler.HasCommandInvocations;
import org.eventrails.parser.model.handler.HasQueryInvocations;
import org.eventrails.parser.model.payload.*;
import org.jaxen.JaxenException;

import java.util.List;
import java.util.Stack;

public abstract class JavaComponentParser<T extends Node> {


	protected net.sourceforge.pmd.lang.ast.Node node;

	public JavaComponentParser(net.sourceforge.pmd.lang.ast.Node node) {
		this.node = node;
	}

	public abstract  T parse() throws Exception;


	public static boolean isSaga(ASTTypeDeclaration classDef) throws JaxenException {
		return !classDef.findChildNodesWithXPath("//MarkerAnnotation/Name[@Image = \"Saga\"]").isEmpty();
	}

	public static boolean isAggregate(ASTTypeDeclaration classDef) throws JaxenException {
		return !classDef.findChildNodesWithXPath("//Name[@Image = \"Aggregate\"]").isEmpty();
	}

	public static boolean isProjection(ASTTypeDeclaration classDef) throws JaxenException {
		return !classDef.findChildNodesWithXPath("//MarkerAnnotation/Name[@Image = \"Projection\"]").isEmpty();
	}

	public static boolean isProjector(ASTTypeDeclaration classDef) throws JaxenException {
		return !classDef.findChildNodesWithXPath("//MarkerAnnotation/Name[@Image = \"Projector\"]").isEmpty();
	}

	public static boolean isService(ASTTypeDeclaration classDef) throws JaxenException {
		return !classDef.findChildNodesWithXPath("//MarkerAnnotation/Name[@Image = \"Service\"]").isEmpty();
	}

	protected String getQueryForAnnotatedMethod(String annotation) {
		return "//ClassOrInterfaceBodyDeclaration[Annotation//Name[@Image=\""+annotation+"\"] and MethodDeclaration]//MethodDeclaration";
	}

	protected String getDeclaredClassName(){
		return node.getFirstDescendantOfType(ASTTypeDeclaration.class).getFirstDescendantOfType(ASTClassOrInterfaceDeclaration.class).getSimpleName();
	}

	protected void findCommandInvocations(List<? extends Handler<?>> ehs) throws JaxenException {
		var query = "//PrimaryExpression[PrimaryPrefix/Name[ends-with(@Image,\"send\") or ends-with(@Image,\"sendAndWait\")]]";
		for (net.sourceforge.pmd.lang.ast.Node n : node.getFirstChildOfType(ASTTypeDeclaration.class).findChildNodesWithXPath(query))
		{
			var expr = ((ASTPrimaryExpression) n);
			var msgArg = expr.getFirstDescendantOfType(ASTClassOrInterfaceType.class);
			if (msgArg == null) return;
			var cmdName = msgArg.getImage();
			var cmd = new Command(cmdName);
			net.sourceforge.pmd.lang.ast.Node methodOrConstructor = expr.getFirstParentOfType(ASTMethodDeclaration.class);
			if (methodOrConstructor == null)
			{
				methodOrConstructor = expr.getFirstParentOfType(ASTConstructorDeclaration.class);
			}
			manageMessageInvocation(methodOrConstructor, cmd, ehs, stack(expr));
		}
	}

	protected void findQueryInvocations(List<? extends Handler<?>> ehs) throws JaxenException {
		var query = "//PrimaryExpression[PrimaryPrefix/Name[ends-with(@Image,\"query\")]]";
		for (net.sourceforge.pmd.lang.ast.Node n : node.getFirstChildOfType(ASTTypeDeclaration.class).findChildNodesWithXPath(query))
		{
			var expr = ((ASTPrimaryExpression) n);
			var types = expr.findDescendantsOfType(ASTClassOrInterfaceType.class);
			if (types.size() < 2) return;
			var msgArg = types.get(0);
			var msgRetType = types.get(types.size() - 1);
			var isMultiple = expr.findDescendantsOfType(ASTName.class).stream().map(AbstractNode::getImage).anyMatch(i -> i.equals("multipleInstancesOf"));
			var type = isMultiple ? new MultipleResultQueryReturnType(msgRetType.getImage()) : new MonoResultQueryReturnType(msgRetType.getImage());
			var qName = msgArg.getImage();
			var q = new Query(qName, type);
			net.sourceforge.pmd.lang.ast.Node methodOrConstructor = expr.getFirstParentOfType(ASTMethodDeclaration.class);
			if (methodOrConstructor == null)
			{
				methodOrConstructor = expr.getFirstParentOfType(ASTConstructorDeclaration.class);
			}
			manageMessageInvocation(methodOrConstructor, q, ehs, stack(expr));
		}
	}


	private Stack<ASTPrimaryExpression> stack(ASTPrimaryExpression expression) {
		var s = new Stack<ASTPrimaryExpression>();
		s.add(expression);
		return s;
	}


	private void manageMessageInvocation(net.sourceforge.pmd.lang.ast.Node methodOrConstructor, Payload m, List<? extends Handler<?>> ehs,
										 Stack<ASTPrimaryExpression> expr
	) throws JaxenException {
		var annot = methodOrConstructor.getFirstParentOfType(ASTClassOrInterfaceBodyDeclaration.class).findChildNodesWithXPath("Annotation//Name[ends-with(@Image,\"Handler\") and not(starts-with(@Image,'Deadline'))]");
		if (!annot.isEmpty())
		{
			var eName = methodOrConstructor.getFirstDescendantOfType(ASTFormalParameters.class).getFirstDescendantOfType(ASTClassOrInterfaceType.class).getImage();
			ehs.stream().filter(eh -> eh.getPayload().getName().equals(eName)).forEach(eh -> {
				if (m instanceof Query q && eh instanceof HasQueryInvocations qi)
				{
					qi.addQueryInvocation(q);
				}
				if (m instanceof Command c && eh instanceof HasCommandInvocations ci)
				{
					ci.addCommandInvocation(c);
				}
			});
			return;
		}

		var mName = methodOrConstructor instanceof ASTMethodDeclaration ? ((ASTMethodDeclaration) methodOrConstructor).getName() : methodOrConstructor.getImage();
		var invoks = node.getFirstChildOfType(ASTTypeDeclaration.class).findChildNodesWithXPath("//PrimaryPrefix/Name[@Image = \"%s\"]".formatted(mName));
		for (net.sourceforge.pmd.lang.ast.Node i : invoks)
		{
			var mt = i.getFirstParentOfType(ASTMethodDeclaration.class);
			if (mt == null) return;
			var nExpr = new Stack<ASTPrimaryExpression>();
			nExpr.addAll(expr);
			nExpr.add(i.getFirstParentOfType(ASTPrimaryExpression.class));
			manageMessageInvocation(mt, m, ehs, nExpr);
		}

	}




}
