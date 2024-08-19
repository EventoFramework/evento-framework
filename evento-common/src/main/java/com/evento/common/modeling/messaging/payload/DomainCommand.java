package com.evento.common.modeling.messaging.payload;

/**
 * The DomainCommand class is an abstract class that represents a command related to a domain object.
 * It extends the Command class.
 * Subclasses of DomainCommand must implement the getAggregateId() method to provide the ID of the aggregate the command is targeting.
 */
public abstract class DomainCommand extends Command {

	/**
	 * The invalidateAggregateCaches variable is a boolean flag that determines whether the caches associated with a domain command should be invalidated or not.
	 * <p>
	 * By default, the invalidateAggregateCaches flag is set to false, indicating that the caches should not be invalidated.
	 * <p>
	 * To retrieve the value of the invalidateAggregateCaches flag, use the isInvalidateAggregateCaches() method. This method returns a boolean value: true if the caches should be invalidated
	 * , and false otherwise.
	 * <p>
	 * To set the value of the invalidateAggregateCaches flag, use the setInvalidateAggregateCaches(boolean invalidateAggregateCaches) method. Pass in a boolean value that indicates
	 *  whether the caches should be invalidated or not. The method returns the command object itself.
	 * <p>
	 * The invalidateAggregateCaches flag is a property of the DomainCommand class, which is an abstract class representing a command related to a domain object. The class extends the
	 *  Command class and must implement the getAggregateId() method to provide the ID of the aggregate the command is targeting.
	 * <p>
	 * The Command class is an abstract class that represents a command object that can be used in a software system. It extends the PayloadWithContext class, which is an abstract class
	 *  that represents a payload object with context information. The Command class also declares the getLockId() method, which retrieves the lock ID associated with the command.
	 * <p>
	 * The PayloadWithContext class extends the Payload interface and provides methods to get and set the context of the object. The class also declares the getAggregateId() method,
	 *  which retrieves the ID of the aggregate that the payload is targeting. The setContext(String context) method is used to set the context of the object. The context is a string
	 *  value representing the available context options for certain functionalities within a software system. The context can be accessed using the getContext() method.
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
