package org.eventrails.modeling.bundle;

public interface TransactionalProjector {
	public void begin() throws Exception;
	public void commit() throws Exception;
	public void rollback() throws Exception;
}