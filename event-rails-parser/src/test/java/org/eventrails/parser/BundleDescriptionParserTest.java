package org.eventrails.parser;

import org.eventrails.parser.java.JavaBundleParser;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

class BundleDescriptionParserTest {

   @Test
    public void test() throws Exception {
       JavaBundleParser applicationParser = new JavaBundleParser();
       var components = applicationParser.parseDirectory(
               new File("../event-rails-demo/event-rails-demo-saga"));
    }
}