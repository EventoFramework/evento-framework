package org.eventrails.shared.exceptions;

import java.lang.reflect.InvocationTargetException;

public class ThrowableWrapper {
	public String throwable;
	public String message;
	public StackTraceElement[] stackTrace;

	public ThrowableWrapper(Class<? extends Throwable> throwable, String message, StackTraceElement[] stackTrace) {
		this.throwable = throwable.getName();
		this.message = message;
		this.stackTrace = stackTrace;
	}

	public ThrowableWrapper() {
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

	public Throwable toThrowable() {
		try
		{
			Throwable ex  = (Throwable) ClassLoader.getSystemClassLoader().loadClass(throwable).getConstructor(String.class).newInstance(getMessage());
			ex.setStackTrace(stackTrace);
			return ex;
		} catch (ClassNotFoundException | InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e)
		{
			Throwable ex = new RuntimeException(throwable + ": " + getMessage());
			ex.setStackTrace(stackTrace);
			return ex;
		}
	}
}
