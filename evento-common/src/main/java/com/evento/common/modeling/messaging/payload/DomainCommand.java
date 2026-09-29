package com.evento.common.modeling.messaging.payload;

/**
 * The DomainCommand class is an abstract class that represents a command related to a domain object.
 * It extends the Command class.
 * Subclasses of DomainCommand must implement the getAggregateId() method to provide the ID of the aggregate the command is targeting.
 */
public abstract class DomainCommand extends Command {

	/**
	 * Whether the broker should drop its cached copy of the aggregate story before
	 * serving this command. Defaults to false.
	 * <p>
	 * Note on context: {@code Command} extends {@code TrackablePayload}, not a
	 * payload with context. Only {@link Event} carries a context
	 * ({@link Event#getContext()}); commands and queries carry none at the
	 * framework level, and the broker does not route or partition commands by
	 * context. An aggregate that must emit its events in a given context (a
	 * region, a tenant) has to receive it as an ordinary command field and copy
	 * it into the event it returns, e.g.
	 * {@code new OrderCreatedEvent(..., cmd.getContext())}, where
	 * {@code getContext()} is the command's own field.
	 */
	private boolean invalidateAggregateCaches = false;

	/**
	 * Retrieves whether the aggregate snapshot should be invalidated or not.
	 */
	private boolean invalidateAggregateSnapshot = false;


	@Override
	public String getLockId(){
		return getAggregateId();
	}

	/**
	 * Retrieves whether the caches associated with the command should be invalidated or not.
	 *
	 * @return true if the caches should be invalidated, false otherwise
	 */
	public boolean isInvalidateAggregateCaches() {
		return invalidateAggregateCaches;
	}

	/**
	 * Sets whether the caches associated with the command should be invalidated or not.
	 *
	 * @param invalidateAggregateCaches true if the caches should be invalidated, false otherwise
	 * @param <T>                       the type of the command
	 */
	public <T extends DomainCommand> void setInvalidateAggregateCaches(boolean invalidateAggregateCaches) {
		this.invalidateAggregateCaches = invalidateAggregateCaches;
	}

	/**
	 * Retrieves whether the aggregate snapshot associated with the command should be invalidated or not.
	 *
	 * @return true if the aggregate snapshot should be invalidated, false otherwise
	 */
	public boolean isInvalidateAggregateSnapshot() {
		return invalidateAggregateSnapshot;
	}

	/**
	 * Sets whether the aggregate snapshot associated with the command should be invalidated or not.
	 *
	 * @param invalidateAggregateSnapshot true if the aggregate snapshot should be invalidated, false otherwise
	 * @param <T>                         the type of the command
	 */
	public  <T extends DomainCommand> void setInvalidateAggregateSnapshot(boolean invalidateAggregateSnapshot) {
		this.invalidateAggregateSnapshot = invalidateAggregateSnapshot;
	}
}
