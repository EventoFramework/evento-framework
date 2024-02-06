package com.evento.parser;

import com.evento.parser.model.BundleDescription;

import java.io.File;

/**
 * The BundleParser interface provides a method for parsing a directory and returning a BundleDescription object.
 */
public interface BundleParser {
	/**
	 * Parses a directory and returns a {@link BundleDescription} object.
	 *
	 * @param file           the directory to parse
	 * @param repositoryRoot the root URL of the repository
	 * @return the parsed {@link BundleDescription} object
	 * @throws Exception if an error occurs during parsing
	 */
	BundleDescription parseDirectory(File file, String repositoryRoot) throws Exception;
}
