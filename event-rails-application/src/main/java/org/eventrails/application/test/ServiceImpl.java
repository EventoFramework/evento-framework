package org.eventrails.application.test;

public class ServiceImpl implements Service{
	@Override
	public String concat(String s1, String s2) {
		System.out.println("#### concat invoked");
		return s1 + s2;
	}
}
