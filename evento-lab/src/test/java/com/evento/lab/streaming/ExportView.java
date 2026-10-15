package com.evento.lab.streaming;

import com.evento.common.modeling.messaging.payload.View;

public class ExportView implements View {
    private int index;
    private String text;

    public ExportView() {}

    public ExportView(int index, String text) {
        this.index = index;
        this.text = text;
    }

    public int getIndex() { return index; }
    public void setIndex(int index) { this.index = index; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
}
