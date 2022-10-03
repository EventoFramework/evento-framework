package org.eventrails.parser.java;

import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.lang.java.ast.ASTClassOrInterfaceType;
import net.sourceforge.pmd.lang.java.ast.ASTMethodDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTTypeDeclaration;
import org.eventrails.parser.model.node.Aggregate;
import org.eventrails.parser.model.handler.AggregateCommandHandler;
import org.eventrails.parser.model.handler.EventSourcingHandler;
import org.eventrails.parser.model.payload.DomainCommand;
import org.eventrails.parser.model.payload.DomainEvent;
import org.jaxen.JaxenException;

import java.util.List;
import java.util.stream.Collectors;

public class JavaAggregateParser extends JavaComponentParser<Aggregate> {


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
					return new AggregateCommandHandler(new DomainCommand(commandName), new DomainEvent(eventName));
				}
		).collect(Collectors.toList());
	}
	private List<EventSourcingHandler> findEventSourcingHandlers() throws JaxenException {
		var query = getQueryForAnnotatedMethod("EventSourcingHandler");
		return node.getFirstChildOfType(ASTTypeDeclaration.class).findChildNodesWithXPath(query).stream().map(
				n -> {
					var md = (ASTMethodDeclaration) n;
					var name = md.getFormalParameters().getFirstDescendantOfType(ASTClassOrInterfaceType.class).getImage();
					return new EventSourcingHandler(new DomainEvent(name));
				}
		).collect(Collectors.toList());
	}

	@Override
	public Aggregate parse() throws Exception {
		Aggregate aggregate = new Aggregate();
		aggregate.setComponentName(getDeclaredClassName());
		aggregate.setAggregateCommandHandlers(findAggregateCommandHandler());
		aggregate.setEventSourcingHandlers(findEventSourcingHandlers());
		return aggregate;
	}





}
