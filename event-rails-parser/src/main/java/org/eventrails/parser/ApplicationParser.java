package org.eventrails.parser;

import org.eventrails.parser.model.Application;
import org.eventrails.parser.model.component.Component;

import java.io.File;
import java.io.IOException;
import java.util.List;

public interface ApplicationParser {
	public Application parseDirectory(File file) throws Exception;
}
