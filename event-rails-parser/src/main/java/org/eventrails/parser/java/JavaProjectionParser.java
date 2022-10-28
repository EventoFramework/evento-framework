package org.eventrails.parser.java;

import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.lang.java.ast.*;
import org.eventrails.parser.model.component.Projection;
import org.eventrails.parser.model.handler.QueryHandler;
import org.eventrails.parser.model.payload.MultipleResultQueryReturnType;
import org.eventrails.parser.model.payload.MonoResultQueryReturnType;
import org.eventrails.parser.model.payload.Query;
import org.jaxen.JaxenException;

import java.util.List;
import java.util.stream.Collectors;

public class JavaProjectionParser extends JavaComponentParser<Projection> {


	public JavaProjectionParser(Node node) {
		super(node);
	}

	private List<QueryHandler> findQueryHandlers() throws JaxenException {
		var query =  getQueryForAnnotatedMethod("QueryHandler");
		return node.getFirstChildOfType(ASTTypeDeclaration.class).findChildNodesWithXPath(query).stream().map(
				n -> {
					var md = (ASTMethodDeclaration) n;
					var name = md.getFormalParameters().getFirstDescendantOfType(ASTClassOrInterfaceType.class).getImage();
					var msgRetType = md.getResultType();
					var resultTypeDefinitions =  msgRetType.findDescendantsOfType(ASTClassOrInterfaceType.class);
					var isMultiple = resultTypeDefinitions.size() > 1;
					var type = isMultiple ? new MultipleResultQueryReturnType(resultTypeDefinitions.get(1).getImage()) : new MonoResultQueryReturnType(resultTypeDefinitions.get(0).getImage());
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
