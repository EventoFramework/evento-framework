package org.evento.parser.model.handler;

import org.evento.parser.model.payload.Query;

import java.util.Collection;

public interface HasQueryInvocations {

	void addQueryInvocation(Query query);

	Collection<Query> getQueryInvocations();
}
