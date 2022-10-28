package org.eventrails.parser;

import org.eventrails.parser.java.JavaBundleParser;

import java.io.File;

public class Parser {
	public static void parse(SourceType sourceType, File sourceDir, File outputFile) throws Exception {
		BundleParser bundleParser = new JavaBundleParser();
		var components = bundleParser.parseDirectory(sourceDir);

	}
}
