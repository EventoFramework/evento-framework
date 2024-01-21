package org.evento.common.modeling.exceptions;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;

/**
 *  The ExceptionWrapper class is a utility class that wraps a Throwable object and provides methods for accessing and manipulating its properties.
 */
public class ExceptionWrapper implements Serializable {
	/**
	 * Represents a Throwable object.
	 */
	public String throwable;
	/**
	 * The message associated with this ExceptionWrapper object.
	 */
	public String message;
	/**
	 *
	 */
	public StackTraceElement[] stackTrace;
	private ExceptionWrapper cause;

	/**
	 * The ExceptionWrapper class is a utility class that wraps a Throwable object and provides methods for accessing and manipulating its properties.
     * @param throwable The original throwable
     */
	public ExceptionWrapper(Throwable throwable) {
		this.throwable = throwable.getClass().getName();
		this.message = throwable.getMessage();
		this.stackTrace = throwable.getStackTrace();
		if(throwable.getCause() != null && throwable.getCause() != throwable){
			cause = new ExceptionWrapper(throwable.getCause());
		}

	}

	/**
	 * The ExceptionWrapper class is a utility class that wraps a Throwable object
	 * and provides methods for accessing and manipulating its properties.
	 */
	public ExceptionWrapper() {
	}

	/**
	 * Returns the Throwable associated with this ExceptionWrapper object.
	 *
	 * @return the Throwable
	 */
	public String getThrowable() {
		return throwable;
	}

	/**
	 * Sets the Throwable associated with this ExceptionWrapper object.
	 *
	 * @param throwable the Throwable to be set
	 */
	public void setThrowable(String throwable) {
		this.throwable = throwable;
	}

	/**
	 * Retrieves the message associated with this ExceptionWrapper object.
	 *
	 * @return the message as a string
	 */
	public String getMessage() {
		return message;
	}

	/**
	 * Sets the message associated with this ExceptionWrapper object.
	 *
	 * @param message the message to be set
	 */
	public void setMessage(String message) {
		this.message = message;
	}

	/**
	 * Retrieves the detailed message associated with this ExceptionWrapper object.
	 *
	 * @return the detailed message as a string
	 */
	public String getDetailMessage() {
		return message;
	}

	/**
	 * Sets the detailed message associated with this ExceptionWrapper object.
	 *
	 * @param message the detailed message to be set
	 */
	public void setDetailMessage(String message) {
		this.message = message;
	}

	/**
	 * Returns an array of StackTraceElement objects representing the stack trace recorded for this Throwable object.
	 *
	 * @return an array of StackTraceElement objects
	 */
	public StackTraceElement[] getStackTrace() {
		return stackTrace;
	}

	/**
	 * Sets the stack trace associated with this ExceptionWrapper object.
	 *
	 * @param stackTrace the array of StackTraceElement objects to be set
	 */
	public void setStackTrace(StackTraceElement[] stackTrace) {
		this.stackTrace = stackTrace;
	}

	/**
	 * Retrieves the cause of this ExceptionWrapper object.
	 *
	 * @return the cause as an ExceptionWrapper object
	 */
	public ExceptionWrapper getCause() {
		return cause;
	}

	/**
	 * Sets the cause of this ExceptionWrapper object.
	 *
	 * @param cause the cause to be set
	 * @return this ExceptionWrapper object
	 */
	public ExceptionWrapper setCause(ExceptionWrapper cause) {
		this.cause = cause;
		return this;
	}

	/**
	 * Converts the ExceptionWrapper object to an exception for the corresponding type.
	 *
	 * @return the converted Exception object
	 */
	public Exception toException() {
		try
		{
			if(cause != null){
				Exception ex = (Exception) ClassLoader.getSystemClassLoader().loadClass(throwable)
						.getConstructor(String.class, Throwable.class)
						.newInstance(getMessage(), cause.toException());
				ex.setStackTrace(stackTrace);
				return ex;
			}else{
				Exception ex = (Exception) ClassLoader.getSystemClassLoader().loadClass(throwable).getConstructor(String.class).newInstance(getMessage());
				ex.setStackTrace(stackTrace);
				return ex;
			}

		} catch (ClassNotFoundException | InstantiationException | IllegalAccessException | InvocationTargetException |
				 NoSuchMethodException e)
		{
			Exception ex = new RuntimeException(throwable + ": " + getMessage());
			ex.setStackTrace(stackTrace);
			return ex;
		}
	}
}
