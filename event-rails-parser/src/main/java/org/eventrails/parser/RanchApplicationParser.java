package org.eventrails.parser;

import org.eventrails.parser.model.RanchApplicationDescription;

import java.io.File;

public interface RanchApplicationParser {
	public RanchApplicationDescription parseDirectory(File file) throws Exception;
}
