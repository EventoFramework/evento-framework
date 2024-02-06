package com.evento.application.utils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * The `ReflectionUtils` class provides utility methods for working with reflection.
 */
public class ReflectionUtils {

    // Mapping between wrapper types and primitive types
    private static final Map<Class<?>, Class<?>> WRAPPER_TYPE_MAP;

    static {
        WRAPPER_TYPE_MAP = new HashMap<>(16);
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

    /**
     * Builds an array of parameters to be used when invoking a method.
     *
     * @param method          The method to be invoked.
     * @param availableParams The available parameters.
     * @return An array of parameters suitable for the given method.
     */
    public static Object[] buildParameters(Method method, Object... availableParams) {
        Object[] args = new Object[method.getParameterCount()];
        for (int i = 0; i < method.getParameterCount(); i++) {
            var param = method.getParameters()[i];
            for (var input : availableParams) {
                if (input == null) continue;
                if (param.getType().isPrimitive() && param.getType() == WRAPPER_TYPE_MAP.get(input.getClass())) {
                    args[i] = input;
                } else if (param.getType().isAssignableFrom(input.getClass())) {
                    args[i] = input;
                    break;
                }
            }
        }
        return args;
    }

    /**
     * Invokes a method on a given object with the specified parameters.
     *
     * @param object The object on which the method is to be invoked.
     * @param method The method to be invoked.
     * @param params The parameters to be passed to the method.
     * @return The result of the method invocation.
     * @throws IllegalAccessException If the method cannot be accessed.
     */
    public static Object invoke(Object object, Method method, Object... params) throws IllegalAccessException {
        var old = method.canAccess(object);
        try {
            method.setAccessible(true);
            return method.invoke(object, buildParameters(method, params));
        } catch (InvocationTargetException e) {
            if (e.getTargetException() instanceof RuntimeException re)
                throw re;
            else throw new RuntimeException(e.getTargetException());
        } finally {
            method.setAccessible(old);
        }
    }
}
