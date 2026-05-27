package com.evento.application.scanner.fixtures;

import com.evento.common.modeling.annotations.EventoDescription;

@EventoDescription(value = "Test Component", detail = "Markdown detail for testing")
public class AnnotatedComponentFixture {
    public void handle(String msg) {}
}
