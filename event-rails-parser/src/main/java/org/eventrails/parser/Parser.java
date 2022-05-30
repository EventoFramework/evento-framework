package org.eventrails.parser;

import org.eventrails.parser.java.JavaApplicationParser;

import java.io.File;

public class Parser {
	public static void parse(SourceType sourceType, File sourceDir, File outputFile) throws Exception {
		ApplicationParser applicationParser = new JavaApplicationParser();
		var components = applicationParser.parseDirectory(sourceDir);

	}
}
