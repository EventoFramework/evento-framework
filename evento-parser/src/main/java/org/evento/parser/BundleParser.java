package org.evento.parser;

import org.evento.parser.model.BundleDescription;

import java.io.File;

public interface BundleParser {
	public BundleDescription parseDirectory(File file) throws Exception;
}
