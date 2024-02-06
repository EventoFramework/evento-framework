package com.evento.parser.java;

import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.lang.java.ast.ASTClassOrInterfaceType;
import net.sourceforge.pmd.lang.java.ast.ASTMethodDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTTypeDeclaration;
import com.evento.parser.model.component.Projection;
import com.evento.parser.model.handler.QueryHandler;
import com.evento.parser.model.payload.MonoResultQueryReturnType;
import com.evento.parser.model.payload.MultipleResultQueryReturnType;
import com.evento.parser.model.payload.Query;
import org.jaxen.JaxenException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * {@code JavaProjectionParser} is a class that parses a Java component and extracts information related to projections.
 * It extends the {@code JavaComponentParser} class.
 */
public class JavaProjectionParser extends JavaComponentParser<Projection> {


	/**
	 * The JavaProjectionParser class is responsible for parsing a Java component and extracting information related to projections.
	 * It extends the JavaComponentParser class.
     * @param node a node for a projection class
     */
	public JavaProjectionParser(Node node) {
		super(node);
	}

	/**
	 * Find query handlers by searching for methods annotated with "QueryHandler".
	 *
	 * @return a list of QueryHandler objects
	 * @throws JaxenException if an error occurs during XPath query execution
	 */
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
					return new QueryHandler(new Query(name, type), n.getBeginLine());
				}
		).collect(Collectors.toList());
	}


	public Projection parse() throws Exception {
		Projection projection = new Projection();
		projection.setComponentName(getDeclaredClassName());
		projection.setQueryHandlers(findQueryHandlers());
		findQueryInvocations(projection.getQueryHandlers());
		return projection;
	}
}
