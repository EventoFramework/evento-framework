package org.eventrails.parser.model.handler;

import org.eventrails.parser.model.payload.Query;

import java.util.Collection;

public interface HasQueryInvocations {

	void addQueryInvocation(Query query);

	Collection<Query> getQueryInvocations();
}
