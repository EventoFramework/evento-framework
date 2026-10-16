package com.evento.lab.streaming;

import com.evento.common.modeling.messaging.payload.Query;
import com.evento.common.modeling.messaging.query.Streamed;

/** Answered with a lazy Streamed: produced as the caller consumes. */
public class ExportLazyQuery extends Query<Streamed<ExportView>> {
    private int count;
    private int failAt = -1;

    public ExportLazyQuery() {}

    public ExportLazyQuery(int count) { this.count = count; }

    public ExportLazyQuery failingAt(int index) {
        this.failAt = index;
        return this;
    }

    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }
    public int getFailAt() { return failAt; }
    public void setFailAt(int failAt) { this.failAt = failAt; }
}
