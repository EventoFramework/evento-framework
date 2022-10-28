package org.eventrails.parser;

import org.eventrails.parser.model.BundleDescription;

import java.io.File;

public interface BundleParser {
	public BundleDescription parseDirectory(File file) throws Exception;
}
