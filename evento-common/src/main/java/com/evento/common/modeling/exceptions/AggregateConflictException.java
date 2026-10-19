package com.evento.common.modeling.exceptions;

/**
 * The aggregate changed while the command ran: another command on the same aggregate stored its
 * event first, from the same state this command was decided on. The broker refused this event at
 * the database (the aggregate fence), so nothing was stored.
 *
 * <p>Definite, not indeterminate: sending the command again runs it on the new state. It happens
 * when commands on one aggregate are not queued by the aggregate lock: a {@code DomainCommand}
 * whose {@code getLockId()} returns {@code null}, or a server that lost the lock (a failover).
 *
 * <p>Travels as an {@link ExceptionWrapper}; a peer without this class sees a
 * {@code RuntimeException} naming it.
 */
public class AggregateConflictException extends RuntimeException {

    public AggregateConflictException(String message) {
        super(message);
    }

    public AggregateConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
