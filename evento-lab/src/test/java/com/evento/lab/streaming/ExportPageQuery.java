package com.evento.lab.streaming;

import com.evento.common.modeling.messaging.payload.Query;
import com.evento.common.modeling.messaging.query.Page;
import com.evento.common.modeling.messaging.query.PageRequest;

public class ExportPageQuery extends Query<Page<ExportView>> {
    private int total;
    private PageRequest page;

    public ExportPageQuery() {}

    public ExportPageQuery(int total, PageRequest page) {
        this.total = total;
        this.page = page;
    }

    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }
    public PageRequest getPage() { return page; }
    public void setPage(PageRequest page) { this.page = page; }
}
