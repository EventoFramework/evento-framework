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
				new File("D:\\Gabor\\DIDATTICA\\UNI\\TESI_MAGISTRALE\\event-rails\\event-rails-demo-command\\src\\main\\java\\org\\eventrails\\demo"));
		System.out.println(new JsonSerializer().serialize(components));

		components = applicationParser.parseDirectory(
				new File("D:\\Gabor\\DIDATTICA\\UNI\\TESI_MAGISTRALE\\event-rails\\event-rails-demo-query\\src\\main\\java\\org\\eventrails\\demo"));
		System.out.println(new JsonSerializer().serialize(components));

		components = applicationParser.parseDirectory(
				new File("D:\\Gabor\\DIDATTICA\\UNI\\TESI_MAGISTRALE\\event-rails\\event-rails-demo-saga\\src\\main\\java\\org\\eventrails\\demo"));
		System.out.println(new JsonSerializer().serialize(components));

	}
}