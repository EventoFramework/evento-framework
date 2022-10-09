package org.eventrails.modeling.ranch;

public interface TransactionalProjector {
	public void begin() throws Exception;
	public void commit() throws Exception;
	public void rollback() throws Exception;
}
