package org.evento.parser.java;

import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.lang.java.ast.ASTClassOrInterfaceType;
import net.sourceforge.pmd.lang.java.ast.ASTMethodDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTTypeDeclaration;
import org.evento.parser.model.component.Aggregate;
import org.evento.parser.model.handler.AggregateCommandHandler;
import org.evento.parser.model.handler.EventSourcingHandler;
import org.evento.parser.model.payload.DomainCommand;
import org.evento.parser.model.payload.DomainEvent;
import org.jaxen.JaxenException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * The JavaAggregateParser class is responsible for parsing a Java class that represents an Aggregate.
 * It extends the JavaComponentParser class, which is a generic abstract class for parsing different types of components.
 * The class provides methods to find AggregateCommandHandlers and EventSourcingHandlers in the parsed class.
 * It also overrides the parse method to parse the aggregate and return an Aggregate object.
 */
public class JavaAggregateParser extends JavaComponentParser<Aggregate> {


	/**
	 * The JavaAggregateParser class is responsible for parsing a Java class that represents an Aggregate.
	 * It extends the JavaComponentParser class, which is a generic abstract class for parsing different types of components.
	 * The class provides methods to find AggregateCommandHandlers and EventSourcingHandlers in the parsed class.
	 * It also overrides the parse method to parse the aggregate and return an Aggregate object.
	 */
	public JavaAggregateParser(Node node) {
		super(node);
	}



	private List<AggregateCommandHandler> findAggregateCommandHandler() throws JaxenException {
		var query = getQueryForAnnotatedMethod("AggregateCommandHandler");
		return node.getFirstChildOfType(ASTTypeDeclaration.class).findChildNodesWithXPath(query).stream().map(
				n -> {
					var md = (ASTMethodDeclaration) n;
					var commandName = md.getFormalParameters().getFirstDescendantOfType(ASTClassOrInterfaceType.class).getImage();
					var eventName = md.getResultType().getFirstDescendantOfType(ASTClassOrInterfaceType.class).getImage();
					return new AggregateCommandHandler(new DomainCommand(commandName), new DomainEvent(eventName), n.getBeginLine());
				}
		).collect(Collectors.toList());
	}


	private List<EventSourcingHandler> findEventSourcingHandlers() throws JaxenException {
		var query = getQueryForAnnotatedMethod("EventSourcingHandler");
		return node.getFirstChildOfType(ASTTypeDeclaration.class).findChildNodesWithXPath(query).stream().map(
				n -> {
					var md = (ASTMethodDeclaration) n;
					var name = md.getFormalParameters().getFirstDescendantOfType(ASTClassOrInterfaceType.class).getImage();
					return new EventSourcingHandler(new DomainEvent(name), n.getBeginLine());
				}
		).collect(Collectors.toList());
	}


	@Override
	public Aggregate parse() throws Exception {
		Aggregate aggregate = new Aggregate();
		aggregate.setComponentName(getDeclaredClassName());
		aggregate.setAggregateCommandHandlers(findAggregateCommandHandler());
		aggregate.setEventSourcingHandlers(findEventSourcingHandlers());
		findCommandInvocations(aggregate.getAggregateCommandHandlers());
		findQueryInvocations(aggregate.getAggregateCommandHandlers());
		return aggregate;
	}


}
