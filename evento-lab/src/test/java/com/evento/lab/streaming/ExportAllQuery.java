package com.evento.lab.streaming;

import com.evento.common.modeling.messaging.payload.Query;
import com.evento.common.modeling.messaging.query.Multiple;

/** Answered with a plain Multiple: streams through the broker, materialized on the handler. */
public class ExportAllQuery extends Query<Multiple<ExportView>> {
    private int count;

    public ExportAllQuery() {}

    public ExportAllQuery(int count) { this.count = count; }

    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }
}
