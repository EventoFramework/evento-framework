package com.evento.common.modeling.messaging.message.application;

import com.evento.common.modeling.messaging.payload.Command;

/**
 * The CommandMessage class represents a message that carries a command payload.
 * It extends the Message class and is meant to be subclassed for specific types of commands.
 * <p>
 * CommandMessage objects can be used to send commands and invoke command handler methods.
 * They contain the command payload, metadata, timestamp, and other related information.
 * @param <T> the Command payload for this message
 */
public abstract class CommandMessage<T extends Command> extends Message<T> {

	/** Longest idempotency key accepted (the width of {@code es__events.idempotency_key}). */
	public static final int MAX_IDEMPOTENCY_KEY_LENGTH = 255;

	/** Prefix of the keys Evento derives for commands sent by consumer handlers; refused for caller-chosen keys. */
	public static final String RESERVED_IDEMPOTENCY_KEY_PREFIX = "evento:";

	private String aggregateId;
	private String lockId;
	/**
	 * Optional key that makes the command exactly-once across retries, reconnects and servers
	 * (CLUSTER-READINESS G5). Added in 2.5 by addition only: an older peer's message has no
	 * such field and decodes to {@code null} (no idempotency, the old behaviour), and an older
	 * peer receiving it skips it as an unknown property. Blank is read as absent.
	 */
	private String idempotencyKey;

	/**
	 * CommandMessage is a subclass of Message that represents a message carrying a command payload.
	 * It is meant to be subclassed for specific types of commands.
	 *
     * @param command The command payload of this message
     */
	public CommandMessage(T command) {
		super(command);
		this.aggregateId = command.getAggregateId();
		this.lockId = command.getLockId();
	}

	/**
	 * The CommandMessage class represents a message that carries a command payload.
	 * It extends the Message class and is meant to be subclassed for specific types of commands.
	 * <p>
	 * CommandMessage objects can be used to send commands and invoke command handler methods.
	 * They contain the command payload, metadata, timestamp, and other related information.
	 */
	public CommandMessage() {
	}

	/**
	 * Returns the name of the command.
	 *
	 * @return The name of the command.
	 */
	public String getCommandName() {
		return super.getPayloadName();
	}

	/**
	 * Retrieves the ID of the aggregate the command is targeting.
	 *
	 * @return The ID of the aggregate.
	 */
	public String getAggregateId() {
		return aggregateId;
	}

	/**
	 * Sets the ID of the aggregate that the command is targeting.
	 *
	 * @param aggregateId The ID of the aggregate as a string.
	 */
	public void setAggregateId(String aggregateId) {
		this.aggregateId = aggregateId;
	}


	/**
	 * Sets the lock ID for the ServiceCommandMessage.
	 *
	 * @param lockId The lock ID to be set.
	 */
	public void setLockId(String lockId) {
		this.lockId = lockId;
	}

	/**
	 * Retrieves the lock ID associated with the ServiceCommandMessage.
	 *
	 * @return The lock ID associated with the ServiceCommandMessage.
	 */
	public String getLockId() {
		return lockId;
	}


	/**
	 * The idempotency key of this command, or {@code null} when it has none. The broker stores
	 * it with the command's event; a later command carrying the same key, on any server, is
	 * answered with that stored event instead of being executed again.
	 *
	 * @return the key, {@code null} when absent or blank
	 */
	public String getIdempotencyKey() {
		return normalizeIdempotencyKey(idempotencyKey);
	}

	/**
	 * Sets the idempotency key. {@code null} or blank clears it.
	 *
	 * @param idempotencyKey the key, at most {@value #MAX_IDEMPOTENCY_KEY_LENGTH} characters
	 * @throws IllegalArgumentException if the key is longer than {@value #MAX_IDEMPOTENCY_KEY_LENGTH}
	 */
	public void setIdempotencyKey(String idempotencyKey) {
		var key = normalizeIdempotencyKey(idempotencyKey);
		if (key != null && key.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
			throw new IllegalArgumentException("idempotency key longer than " + MAX_IDEMPOTENCY_KEY_LENGTH
					+ " characters: " + key.length());
		}
		this.idempotencyKey = key;
	}

	/**
	 * The one reading of a key off the wire: {@code null} and blank both mean "no key". Used by
	 * the getter too, since the wire codec sets fields directly and bypasses the setter.
	 *
	 * @param key a key as received
	 * @return the key, or {@code null} when absent or blank
	 */
	public static String normalizeIdempotencyKey(String key) {
		return key == null || key.isBlank() ? null : key;
	}

	/**
	 * Checks a key chosen by application code ({@code CommandGateway.sendIdempotent}, or an
	 * explicit key in a consumer handler): at most {@value #MAX_IDEMPOTENCY_KEY_LENGTH}
	 * characters, and not in the {@value #RESERVED_IDEMPOTENCY_KEY_PREFIX} namespace, which
	 * belongs to the keys the framework derives for consumer handlers. A caller-chosen key there
	 * could collide with a derived one and be answered with another command's event.
	 *
	 * @param key the key as given; {@code null} or blank is "no key" and passes
	 * @throws IllegalArgumentException for a reserved or overlong key
	 */
	public static void checkCallerIdempotencyKey(String key) {
		var k = normalizeIdempotencyKey(key);
		if (k == null) return;
		if (k.startsWith(RESERVED_IDEMPOTENCY_KEY_PREFIX)) {
			throw new IllegalArgumentException("idempotency keys starting with '" + RESERVED_IDEMPOTENCY_KEY_PREFIX
					+ "' are reserved for the keys Evento derives in consumer handlers: " + k);
		}
		if (k.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
			throw new IllegalArgumentException("idempotency key longer than " + MAX_IDEMPOTENCY_KEY_LENGTH
					+ " characters: " + k.length());
		}
	}

	@Override
	public void setPayload(T payload) {
		super.setPayload(payload);
		this.lockId = payload.getLockId();
		this.aggregateId = payload.getAggregateId();
	}

	@Override
	public String toString() {
		return "CommandMessage{" +
				"aggregateId='" + aggregateId + '\'' +
				", lockId='" + lockId + '\'' +
				", idempotencyKey='" + idempotencyKey + '\'' +
				"} " + super.toString();
	}
}
