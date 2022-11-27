package org.evento.application.utils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class ReflectionUtils {

	private static final Map<Class<?>, Class<?>> WRAPPER_TYPE_MAP;
	static {
		WRAPPER_TYPE_MAP = new HashMap<Class<?>, Class<?>>(16);
		WRAPPER_TYPE_MAP.put(Integer.class, int.class);
		WRAPPER_TYPE_MAP.put(Byte.class, byte.class);
		WRAPPER_TYPE_MAP.put(Character.class, char.class);
		WRAPPER_TYPE_MAP.put(Boolean.class, boolean.class);
		WRAPPER_TYPE_MAP.put(Double.class, double.class);
		WRAPPER_TYPE_MAP.put(Float.class, float.class);
		WRAPPER_TYPE_MAP.put(Long.class, long.class);
		WRAPPER_TYPE_MAP.put(Short.class, short.class);
		WRAPPER_TYPE_MAP.put(Void.class, void.class);
	}
	public static Object[] buildParameters(Method method, Object... availableParams){
		Object[] args = new Object[method.getParameterCount()];
		for (int i = 0; i < method.getParameterCount(); i++)
		{
			var param = method.getParameters()[i];
			for(var input : availableParams){
				if(input == null) continue;
				if (param.getType().isPrimitive() && param.getType() == WRAPPER_TYPE_MAP.get(input.getClass())){
					args[i] = input;
				}else if(param.getType().isAssignableFrom(input.getClass())){
					args[i] = input;
					break;
				}
			}
		}
		return args;
	}

	public static Object invoke(Object object, Method method, Object... params) throws InvocationTargetException, IllegalAccessException {
		var old = method.canAccess(object);
		try{
			method.setAccessible(true);
			return method.invoke(object, buildParameters(method, params));
		}finally
		{
			method.setAccessible(old);
		}
	}
}
