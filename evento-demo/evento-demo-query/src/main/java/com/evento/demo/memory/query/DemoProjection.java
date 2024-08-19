package com.evento.demo.memory.query;

import com.evento.common.messaging.gateway.QueryGateway;
import com.evento.common.modeling.annotations.component.Projection;
import com.evento.common.modeling.annotations.handler.QueryHandler;
import com.evento.common.modeling.messaging.message.application.Metadata;
import com.evento.common.modeling.messaging.message.application.QueryMessage;
import com.evento.common.modeling.messaging.query.Multiple;
import com.evento.common.modeling.messaging.query.Single;
import com.evento.demo.api.query.DemoRichViewFindAllQuery;
import com.evento.demo.api.query.DemoRichViewFindByIdQuery;
import com.evento.demo.api.query.DemoViewFindAllQuery;
import com.evento.demo.api.query.DemoViewFindByIdQuery;
import com.evento.demo.api.utils.Utils;
import com.evento.demo.api.view.DemoRichView;
import com.evento.demo.api.view.DemoView;
import com.evento.demo.memory.query.domain.Demo;
import com.evento.demo.memory.query.domain.DemoRepository;

import java.time.Instant;

@Projection
public class DemoProjection {

    private final DemoRepository repository;

    public DemoProjection(DemoRepository repository) {
        this.repository = repository;
    }

    @QueryHandler
    Single<DemoView> query(DemoViewFindByIdQuery query,
                           QueryMessage<DemoViewFindByIdQuery> queryMessage,
                           QueryGateway queryGateway,
                           Metadata metadata,
                           Instant instant) {
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
