package org.eventrails.parser;

import org.eventrails.parser.java.JavaApplicationParser;
import org.eventrails.parser.model.component.*;
import org.eventrails.parser.model.payload.Payload;
import org.eventrails.parser.serializer.JsonSerializer;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.stream.Collectors;

class ApplicationParserTest {

	@Test
	void parseDirectory() throws IOException {
		JavaApplicationParser applicationParser = new JavaApplicationParser();
		var components = applicationParser.parseDirectory(
				new File("D:\\Gabor\\DIDATTICA\\UNI\\TESI_MAGISTRALE\\event-rails\\event-rails-demo\\src\\main\\java\\org\\eventrails\\demo"));
		/*components.forEach(c -> {
			if(c instanceof Saga s){
				s.getSagaEventHandlers().forEach(h -> {
					System.out.printf("%30s | %30s | %30s | %30s | %s - %s \n", c.getClass().getSimpleName(), c.getComponentName(), h.getPayload().getName(), null, h.getQueryInvocations().stream().map(Object::toString).collect(Collectors.joining(",")),  h.getCommandInvocations().stream().map(Payload::getName).collect(Collectors.joining(",")));
				});
			}
			else if(c instanceof Aggregate a){
				a.getAggregateCommandHandlers().forEach(h -> {
					System.out.printf("%30s | %30s | %30s | %30s \n", c.getClass().getSimpleName(), c.getComponentName(), h.getPayload().getName(), h.getProducedEvent().getName());
				});
				a.getEventSourcingHandlers().forEach(h -> {
					System.out.printf("%30s | %30s | %30s | %30s \n", c.getClass().getSimpleName(), c.getComponentName(), h.getPayload().getName(), null);
				});
			}else if(c instanceof Projector p){
				p.getEventHandlers().forEach(h -> {
					System.out.printf("%30s | %30s | %30s | %30s | %s \n", c.getClass().getSimpleName(), c.getComponentName(), h.getPayload().getName(), null, h.getQueryInvocations().stream().map(Object::toString).collect(Collectors.joining(",")));
				});
			}else if(c instanceof Projection p){
				p.getQueryHandlers().forEach(h -> {
					System.out.printf("%30s | %30s | %30s | %30s \n", c.getClass().getSimpleName(), c.getComponentName(), h.getPayload().getName(), h.getPayload().getReturnType());
				});
			}else if(c instanceof Service s){
				s.getCommandHandlers().forEach(h -> {
					System.out.printf("%30s | %30s | %30s | %30s| %s - %s \n", c.getClass().getSimpleName(), c.getComponentName(), h.getPayload().getName(), h.getProducedEvent(), h.getQueryInvocations().stream().map(Object::toString).collect(Collectors.joining(",")),  h.getCommandInvocations().stream().map(Payload::getName).collect(Collectors.joining(",")));
				});
			}
		});*/
		System.out.println(new JsonSerializer().serialize(components));

	}
}