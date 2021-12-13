package net.andylizi.starsector.missionminimap;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.List;

public final class ReflectionUtil {
    private static final MethodHandle TRY_SET_ACCESSIBLE;

    static {
        MethodHandle trySetAccessible = null;
        try {
            //noinspection JavaLangInvokeHandleSignature
            trySetAccessible = MethodHandles.lookup()
                .findVirtual(AccessibleObject.class, "trySetAccessible", MethodType.methodType(boolean.class));
        } catch (NoSuchMethodException ignored) {
            // Below Java 9
        } catch (RuntimeException | Error ex) {
            throw ex;
        } catch (Throwable t) {
            throw new ExceptionInInitializerError(t);
        }
        TRY_SET_ACCESSIBLE = trySetAccessible;
    }

    public static boolean trySetAccessible(AccessibleObject object) {
        try {
            if (TRY_SET_ACCESSIBLE != null) {
                return (boolean) TRY_SET_ACCESSIBLE.invokeExact(object);
            } else {
                object.setAccessible(true);
                return true;
            }
        } catch (SecurityException ex) {
            return false;
        } catch (RuntimeException | Error ex) {
            throw ex;
        } catch (Throwable t) {
            throw new AssertionError(t);
        }
    }

    public static Method getFirstMethodByName(Class<?> owner, String name) throws NoSuchMethodException {
        for (Method m : owner.getDeclaredMethods()) {
            if (name.equals(m.getName())) {
                return m;
            }
        }
        throw new NoSuchMethodException(name + "() in " + owner);
    }

    public static Field getFirstFieldByType(Class<?> owner, Class<?> type) throws NoSuchFieldException {
        for (Field f : owner.getDeclaredFields()) {
            if (type == f.getType()) {
                return f;
            }
        }
        throw new NoSuchFieldException("field with type " + type.getName() + " in " + owner);
    }

    public static Field getFirstFieldBySupertype(Class<?> owner, Class<?> supertype) throws NoSuchFieldException {
        for (Field f : owner.getDeclaredFields()) {
            if (supertype.isAssignableFrom(f.getType())) {
                return f;
            }
        }
        throw new NoSuchFieldException("field with a type that is assignable to " +
                                       supertype.getName() + " in " + owner);
    }

    public static Field getFirstFieldByContainerType(Class<?> owner, Class<?> container, Class<?> element)
        throws NoSuchFieldException {
        for (Field f : owner.getDeclaredFields()) {
            Type type = f.getGenericType();
            if (type instanceof ParameterizedType) {
                ParameterizedType p = (ParameterizedType) type;
                if (p.getRawType() == container && p.getActualTypeArguments()[0] == element) {
                    return f;
                }
            }
        }
        throw new NoSuchFieldException("field with type " + container.getName() +
                                       "<" + element.getName() + "> in " + owner);
    }

    public static List<Field> getFieldsByType(Class<?> owner, Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Field f : owner.getDeclaredFields()) {
            if (type == f.getType()) fields.add(f);
        }
        return fields;
    }

    @SuppressWarnings("unchecked")
    public static <T> Constructor<T> getFirstConstructorByParameterCount(Class<T> type, int count) throws
        NoSuchMethodException {
        for (Constructor<?> ctor : type.getDeclaredConstructors()) {
            if (ctor.getParameterTypes().length == count)
                return (Constructor<T>) ctor;
        }
        throw new NoSuchMethodException("constructor with " + count + " parameters in " + type);
    }

    @SuppressWarnings("UnnecessaryBoxing")
    public static <T> T instantiateDefault(Class<T> cls)
        throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        Constructor<?>[] ctors = cls.getConstructors();
        if (ctors.length == 0)
            return cls.newInstance();

        @SuppressWarnings("unchecked")
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
