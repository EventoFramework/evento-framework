package org.evento.parser.model.handler;

import org.evento.parser.model.payload.Query;

import java.util.Collection;
import java.util.Map;

public interface HasQueryInvocations {

	void addQueryInvocation(Query query, int line);

	Map<Integer, Query> getQueryInvocations();
}
