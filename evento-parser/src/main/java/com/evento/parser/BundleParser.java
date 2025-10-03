package com.evento.parser;

import com.evento.parser.model.BundleDescription;

import java.io.File;

/**
 * The BundleParser interface provides a method for parsing a directory and returning a BundleDescription object.
 */
public interface BundleParser {
	/**
	 * Parses a given directory to create a BundleDescription object.
	 *
	 * @param file the directory to parse for bundle information
	 * @param repositoryRoot the root directory of the repository
	 * @param linePrefix the prefix for each line in the parsed output
	 * @param javaVersion the version of Java used to compile the bundle
	 * @return a BundleDescription object containing the parsed bundle information
	 * @throws Exception if an error occurs during parsing
	 */
	BundleDescription parseDirectory(File file, String repositoryRoot, String linePrefix, String  javaVersion) throws Exception;
}
