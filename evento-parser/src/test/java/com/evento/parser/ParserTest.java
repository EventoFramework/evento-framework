package com.evento.parser;

import com.evento.parser.java.JavaBundleParser;
import org.junit.jupiter.api.Test;

import java.io.File;

import static com.evento.common.serialization.ObjectMapperUtils.getPayloadObjectMapper;

public class ParserTest {

    @Test
    public void test() throws Exception {
        JavaBundleParser applicationParser = new JavaBundleParser();
        var components = applicationParser.parseDirectory(
                new File("C:\\Users\\ggalazzo\\workspace\\personal\\eventrails\\evento-demo\\evento-demo-api"),
                "https://github.com/EventoFramework/evento-framework/blob/main/evento-demo/evento-demo-agent",
                "L",
                "20-preview");
        var jsonDescription = getPayloadObjectMapper().writeValueAsString(components);

        System.out.println(components);
        System.out.println(jsonDescription);
    }
}
