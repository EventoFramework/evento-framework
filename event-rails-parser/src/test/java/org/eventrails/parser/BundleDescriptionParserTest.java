package org.eventrails.parser;

import org.eventrails.parser.java.JavaBundleParser;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

class BundleDescriptionParserTest {

	@Test
	void parseDirectory() throws IOException {
		JavaBundleParser applicationParser = new JavaBundleParser();
		var components = applicationParser.parseDirectory(
				new File("..\\..\\event-rails\\event-rails-demo\\event-rails-demo-command\\src\\main\\java\\org\\eventrails\\demo"));

		System.out.println(components);

		components = applicationParser.parseDirectory(
				new File("..\\..\\event-rails\\event-rails-demo\\event-rails-demo-query\\src\\main\\java\\org\\eventrails\\demo"));

		System.out.println(components);
		components = applicationParser.parseDirectory(
				new File("..\\..\\event-rails\\event-rails-demo\\event-rails-demo-saga\\src\\main\\java\\org\\eventrails\\demo"));
		System.out.println(components);

	}

	@Test
	void parseWeb() throws IOException {
		JavaBundleParser applicationParser = new JavaBundleParser();
		var components = applicationParser.parseDirectory(
				new File("..\\..\\event-rails\\event-rails-demo\\event-rails-demo-web-domain\\src\\main\\java\\org\\eventrails\\demo"));
		System.out.println(components);
	}
}