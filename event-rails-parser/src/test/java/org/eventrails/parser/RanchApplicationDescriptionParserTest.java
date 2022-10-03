package org.eventrails.parser;

import org.eventrails.parser.java.JavaRanchApplicationParser;
import org.eventrails.parser.serializer.JsonSerializer;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

class RanchApplicationDescriptionParserTest {

	@Test
	void parseDirectory() throws IOException {
		JavaRanchApplicationParser applicationParser = new JavaRanchApplicationParser();
		var components = applicationParser.parseDirectory(
				new File("D:\\Gabor\\DIDATTICA\\UNI\\TESI_MAGISTRALE\\event-rails\\event-rails-demo\\event-rails-demo-command\\src\\main\\java\\org\\eventrails\\demo"));
		System.out.println(new JsonSerializer().serialize(components));

		components = applicationParser.parseDirectory(
				new File("D:\\Gabor\\DIDATTICA\\UNI\\TESI_MAGISTRALE\\event-rails\\event-rails-demo\\event-rails-demo-query\\src\\main\\java\\org\\eventrails\\demo"));
		System.out.println(new JsonSerializer().serialize(components));

		components = applicationParser.parseDirectory(
				new File("D:\\Gabor\\DIDATTICA\\UNI\\TESI_MAGISTRALE\\event-rails\\event-rails-demo\\event-rails-demo-saga\\src\\main\\java\\org\\eventrails\\demo"));
		System.out.println(new JsonSerializer().serialize(components));

	}
}