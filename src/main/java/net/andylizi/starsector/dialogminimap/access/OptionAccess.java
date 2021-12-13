package net.andylizi.starsector.dialogminimap.access;

import net.andylizi.starsector.dialogminimap.ReflectionUtil;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

public class OptionAccess {
    private final Class<?> optionType;
    private final MethodHandle m_getText;

    public OptionAccess(Class<?> optionType) throws ReflectiveOperationException {
        this.optionType = optionType;
        Method method = null;
        for (Method m : optionType.getDeclaredMethods()) {
            if (m.getReturnType() == String.class) {
                method = m;
                break;
            }
        }

        if (method == null) {
            throw new NoSuchMethodException("getText() in Option " + optionType);
        }
        ReflectionUtil.trySetAccessible(method);
        this.m_getText = MethodHandles.publicLookup().unreflect(method);
    }

    public Class<?> optionType() {
        return optionType;
    }

    public String getText(Object option) {
        try {
            return (String) this.m_getText.invoke(option);
        } catch (RuntimeException | Error ex) {
            throw ex;
        } catch (Throwable t) {
            throw new AssertionError("unreachable", t);
        }
    }
}
