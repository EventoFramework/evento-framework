package org.evento.parser.java;

import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.lang.java.ast.ASTClassOrInterfaceType;
import net.sourceforge.pmd.lang.java.ast.ASTMethodDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTTypeDeclaration;
import org.evento.parser.model.component.Projection;
import org.evento.parser.model.handler.QueryHandler;
import org.evento.parser.model.payload.MonoResultQueryReturnType;
import org.evento.parser.model.payload.MultipleResultQueryReturnType;
import org.evento.parser.model.payload.Query;
import org.jaxen.JaxenException;

import java.util.List;
import java.util.stream.Collectors;

public class JavaProjectionParser extends JavaComponentParser<Projection> {


	public JavaProjectionParser(Node node) {
		super(node);
	}

	private List<QueryHandler> findQueryHandlers() throws JaxenException {
		var query = getQueryForAnnotatedMethod("QueryHandler");
		return node.getFirstChildOfType(ASTTypeDeclaration.class).findChildNodesWithXPath(query).stream().map(
				n -> {
					var md = (ASTMethodDeclaration) n;
					var name = md.getFormalParameters().getFirstDescendantOfType(ASTClassOrInterfaceType.class).getImage();
					var msgRetType = md.getResultType();
					var resultTypeDefinitions = msgRetType.findDescendantsOfType(ASTClassOrInterfaceType.class);
					var isMultiple = !resultTypeDefinitions.get(0).getImage().equals("Single");
					var type = isMultiple ? new MultipleResultQueryReturnType(resultTypeDefinitions.get(1).getImage()) : new MonoResultQueryReturnType(resultTypeDefinitions.get(1).getImage());
					return new QueryHandler(new Query(name, type));
				}
		).collect(Collectors.toList());
	}


	public Projection parse() throws Exception {
		Projection projection = new Projection();
		projection.setComponentName(getDeclaredClassName());
		projection.setQueryHandlers(findQueryHandlers());
		return projection;
	}
}
