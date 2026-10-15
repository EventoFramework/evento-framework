package com.evento.lab.streaming;

import com.evento.common.modeling.annotations.component.Projection;
import com.evento.common.modeling.annotations.handler.QueryHandler;
import com.evento.common.modeling.messaging.query.Multiple;
import com.evento.common.modeling.messaging.query.Page;
import com.evento.common.modeling.messaging.query.Single;
import com.evento.common.modeling.messaging.query.Streamed;

import java.util.ArrayList;
import java.util.stream.IntStream;

@Projection
public class ExportProjection {

    @QueryHandler
    Multiple<ExportView> all(ExportAllQuery query) {
        var views = new ArrayList<ExportView>(query.getCount());
        for (int i = 0; i < query.getCount(); i++) views.add(new ExportView(i, "row-" + i));
        return Multiple.of(views);
    }

    @QueryHandler
    Streamed<ExportView> lazy(ExportLazyQuery query) {
        return Streamed.of(() -> {
            StreamingStore.opened.incrementAndGet();
            return IntStream.range(0, query.getCount())
                    .mapToObj(i -> {
                        if (i == query.getFailAt()) throw new IllegalStateException("source broke at " + i);
                        StreamingStore.produced.incrementAndGet();
                        return new ExportView(i, "row-" + i);
                    })
                    .onClose(StreamingStore.closed::incrementAndGet);
        });
    }

    @QueryHandler
    Page<ExportView> page(ExportPageQuery query) {
        var request = query.getPage();
        long from = request.offset();
        long to = Math.min(query.getTotal(), from + request.getSize());
        var views = new ArrayList<ExportView>();
        for (long i = from; i < to; i++) views.add(new ExportView((int) i, "row-" + i));
        return Page.of(views, request.getNumber(), request.getSize(), query.getTotal());
    }

    @QueryHandler
    Single<ExportView> rejected(RejectedQuery query) {
        throw new IllegalArgumentException("this query is refused");
    }
}
