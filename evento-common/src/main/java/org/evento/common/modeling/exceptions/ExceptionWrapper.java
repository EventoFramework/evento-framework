package org.evento.common.modeling.exceptions;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;

public class ExceptionWrapper implements Serializable {
	public String throwable;
	public String message;
	public StackTraceElement[] stackTrace;
	private ExceptionWrapper cause;

	public ExceptionWrapper(Throwable throwable) {
		this.throwable = throwable.getClass().getName();
		this.message = throwable.getMessage();
		this.stackTrace = throwable.getStackTrace();
		if(throwable.getCause() != null && throwable.getCause() != throwable){
			cause = new ExceptionWrapper(throwable.getCause());
		}

	}

	public ExceptionWrapper() {
	}

	public String getThrowable() {
		return throwable;
	}

	public void setThrowable(String throwable) {
		this.throwable = throwable;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public String getDetailMessage() {
		return message;
	}

	public void setDetailMessage(String message) {
		this.message = message;
	}

	public StackTraceElement[] getStackTrace() {
		return stackTrace;
	}

	public void setStackTrace(StackTraceElement[] stackTrace) {
		this.stackTrace = stackTrace;
	}

	public ExceptionWrapper getCause() {
		return cause;
	}

	public ExceptionWrapper setCause(ExceptionWrapper cause) {
		this.cause = cause;
		return this;
	}

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
