package org.evento.parser;

import org.evento.parser.java.JavaBundleParser;
import org.junit.jupiter.api.Test;

import java.io.File;

class BundleDescriptionParserTest {

   @Test
    public void test() throws Exception {
       JavaBundleParser applicationParser = new JavaBundleParser();
       var components = applicationParser.parseDirectory(
               new File("../evento-demo/evento-demo-saga"));
    }
}