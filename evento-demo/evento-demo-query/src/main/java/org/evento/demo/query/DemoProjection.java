package org.evento.demo.query;

import org.evento.common.modeling.annotations.component.Projection;
import org.evento.common.modeling.annotations.handler.QueryHandler;
import org.evento.common.modeling.messaging.message.application.QueryMessage;
import org.evento.common.modeling.messaging.query.Multiple;
import org.evento.common.modeling.messaging.query.Single;
import org.evento.demo.api.query.DemoRichViewFindAllQuery;
import org.evento.demo.api.query.DemoRichViewFindByIdQuery;
import org.evento.demo.api.query.DemoViewFindAllQuery;
import org.evento.demo.api.query.DemoViewFindByIdQuery;
import org.evento.demo.api.utils.Utils;
import org.evento.demo.api.view.DemoRichView;
import org.evento.demo.api.view.DemoView;
import org.evento.demo.query.domain.Demo;
import org.evento.demo.query.domain.DemoRepository;

@Projection
public class DemoProjection {

    private final DemoRepository repository;

    public DemoProjection(DemoRepository repository) {
        this.repository = repository;
    }

    @QueryHandler
    Single<DemoView> query(DemoViewFindByIdQuery query, QueryMessage<DemoViewFindByIdQuery> queryMessage) {
        Utils.logMethodFlow(this, "query", query, "BEGIN");
        var result = repository.findById(query.getDemoId())
                .filter(d -> d.getDeletedAt() == null)
                .map(Demo::toDemoView).orElseThrow();
        result.setDemoId(query.getDemoId());
        Utils.logMethodFlow(this, "query", query, "END");
        return Single.of(result);
    }

    @QueryHandler
    Multiple<DemoView> query(DemoViewFindAllQuery query) {
        Utils.logMethodFlow(this, "query", query, "BEGIN");
        var result = repository.findAll().stream()
                .filter(d -> d.getDeletedAt() == null)
                .map(Demo::toDemoView).toList();
        Utils.logMethodFlow(this, "query", query, "END");
        return Multiple.of(result);
    }

    @QueryHandler
    Single<DemoRichView> queryRich(DemoRichViewFindByIdQuery query) {
        Utils.logMethodFlow(this, "query", query, "BEGIN");
        var result = repository.findById(query.getDemoId())
                .map(Demo::toDemoRichView).orElseThrow();
        Utils.logMethodFlow(this, "query", query, "END");
        return Single.of(result);
    }


    @QueryHandler
    Multiple<DemoRichView> queryRich(DemoRichViewFindAllQuery query) {
        Utils.logMethodFlow(this, "query", query, "BEGIN");
        var result = repository.findAll().stream().map(Demo::toDemoRichView).toList();
        Utils.logMethodFlow(this, "query", query, "END");
        return Multiple.of(result);
    }
}
