package org.evento.demo.api.utils;


public class Utils {
	public static void doWork(long millis) {
		System.out.println("working for " + millis + " milliseconds...");
		try
		{
			Thread.sleep(millis);
		} catch (InterruptedException e)
		{
			throw new RuntimeException(e);
		}
	}

	public static void logMethodFlow(Object component, String method, Object message, String action) {
		System.out.println(component.getClass() + ":" + method + "(" + message.getClass() + ") - " + action);
	}
}
