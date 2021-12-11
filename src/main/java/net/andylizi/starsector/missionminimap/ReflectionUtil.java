package net.andylizi.starsector.missionminimap;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class ReflectionUtil {
    public static Method getDelcaringMethodByName(Class<?> owner, String name) throws NoSuchMethodException {
        for (Method m : owner.getDeclaredMethods()) {
            if (name.equals(m.getName()))
                return m;
        }
        throw new NoSuchMethodException(name + "() in " + owner);
    }

    public static Field getDeclaringFieldByType(Class<?> owner, Class<?> type) throws NoSuchFieldException {
        for (Field f : owner.getDeclaredFields()) {
            if (type == f.getType())
                return f;
        }
        throw new NoSuchFieldException("field with type " + type.getName() + " in " + owner);
    }

    @SuppressWarnings({ "unchecked", "deprecation" })
    public static <T> T instantiateDefault(Class<T> cls)
            throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        Constructor<?>[] ctors = cls.getConstructors();
        if (ctors.length == 0)
            return cls.newInstance();

        Constructor<T> ctor = (Constructor<T>) ctors[0];
        Class<?>[] paramTypes = ctor.getParameterTypes();
        if (paramTypes.length == 0)
            return cls.newInstance();

        Object[] args = new Object[paramTypes.length];
        for (int i = 0; i < args.length; i++) {
            Class<?> paramType = paramTypes[i];
            if (paramType.isPrimitive()) {
                if (paramType == Boolean.TYPE)
                    args[i] = Boolean.valueOf(false);
                else if (paramType == Character.TYPE)
                    args[i] = Character.valueOf('\0');
                else if (paramType == Byte.TYPE)
                    args[i] = Byte.valueOf((byte) 0);
                else if (paramType == Short.TYPE)
                    args[i] = Short.valueOf((short) 0);
                else if (paramType == Integer.TYPE)
                    args[i] = Integer.valueOf(0);
                else if (paramType == Float.TYPE)
                    args[i] = Float.valueOf(0);
                else if (paramType == Long.TYPE)
                    args[i] = Long.valueOf(0);
                else if (paramType == Double.TYPE)
                    args[i] = Double.valueOf(0);
                else
                    throw new AssertionError(paramType.toString());
            } else {
                args[i] = null;
            }
        }
        return ctor.newInstance(args);
    }

    private ReflectionUtil() {
        throw new AssertionError();
    }
}
