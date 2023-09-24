package org.evento.parser;

import org.evento.parser.java.JavaBundleParser;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.evento.common.serialization.ObjectMapperUtils.getPayloadObjectMapper;

class BundleDescriptionParserTest {

	@Test
	public void test() throws Exception {
		JavaBundleParser applicationParser = new JavaBundleParser();
		var components = applicationParser.parseDirectory(
				new File("C:\\Users\\ggalazzo\\workspace\\iris_5\\iris-server\\iris-common\\src"), "https://github.com/EventoFramework/evento-framework/blob/main/evento-demo/evento-demo-agent");
		var jsonDescription = getPayloadObjectMapper().writeValueAsString(components);

		System.out.println(components);
	}
}