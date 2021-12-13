package net.andylizi.starsector.dialogminimap.access;

import com.fs.graphics.util.Fader;
import com.fs.starfarer.api.ui.UIComponentAPI;
import net.andylizi.starsector.dialogminimap.ReflectionUtil;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

public class UIComponentAccess {
    private final Class<? extends UIComponentAPI> uiComponentType;
    private final MethodHandle m_getFader;
    
    @SuppressWarnings("unchecked")
    public UIComponentAccess(Class<? extends UIComponentAPI> subclass) throws ReflectiveOperationException {
        Method method = subclass.getMethod("getFader");
        ReflectionUtil.trySetAccessible(method);
        this.uiComponentType = (Class<? extends UIComponentAPI>) method.getDeclaringClass();
        this.m_getFader = MethodHandles.publicLookup().unreflect(method);
    }

    public Class<? extends UIComponentAPI> uiComponentType() {
        return uiComponentType;
    }
    
    public Fader getFader(UIComponentAPI component) {
        try {
            return (Fader) this.m_getFader.invoke(component);
        } catch (RuntimeException | Error ex) {
            throw ex;
        } catch (Throwable t) {
            throw new AssertionError("unreachable", t);
        }
    }
}
