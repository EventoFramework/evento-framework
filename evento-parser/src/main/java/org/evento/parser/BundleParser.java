package org.evento.parser;

import org.evento.parser.model.BundleDescription;

import java.io.File;

public interface BundleParser {
	BundleDescription parseDirectory(File file, String repositoryRoot) throws Exception;
}
