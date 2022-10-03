package org.eventrails.parser;

import org.eventrails.parser.java.JavaRanchApplicationParser;

import java.io.File;

public class Parser {
	public static void parse(SourceType sourceType, File sourceDir, File outputFile) throws Exception {
		RanchApplicationParser ranchApplicationParser = new JavaRanchApplicationParser();
		var components = ranchApplicationParser.parseDirectory(sourceDir);

	}
}
