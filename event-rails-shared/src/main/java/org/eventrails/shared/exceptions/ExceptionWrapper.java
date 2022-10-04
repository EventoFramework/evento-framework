package org.eventrails.shared.exceptions;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

public class ExceptionWrapper{
	public String exception;
	public String message;
	public StackTraceElement[] stackTrace;

	public ExceptionWrapper(Class<? extends Throwable> exception, String message, StackTraceElement[] stackTrace) {
		this.exception = exception.getName();
		this.message = message;
		this.stackTrace = stackTrace;
	}

	public ExceptionWrapper() {
	}

	public String getException() {
		return exception;
	}

	public void setException(String exception) {
		this.exception = exception;
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
			Throwable ex  = (Throwable) ClassLoader.getSystemClassLoader().loadClass(exception).getConstructor(String.class).newInstance(getMessage());
			ex.setStackTrace(stackTrace);
			return ex;
		} catch (ClassNotFoundException | InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e)
		{
			Throwable ex = new RuntimeException(exception + ": " + getMessage());
			ex.setStackTrace(stackTrace);
			return ex;
		}
	}
}
