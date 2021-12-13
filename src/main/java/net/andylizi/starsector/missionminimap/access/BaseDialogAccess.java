package net.andylizi.starsector.missionminimap.access;

import com.fs.starfarer.api.ui.UIPanelAPI;
import net.andylizi.starsector.missionminimap.ReflectionUtil;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

public class BaseDialogAccess {
    private final Class<? extends UIPanelAPI> baseDialogType;
    private final MethodHandle m_getInterceptor;

    @SuppressWarnings("unchecked")
    public BaseDialogAccess(Class<? extends UIPanelAPI> subclass) throws ReflectiveOperationException {
        Method m = subclass.getMethod("getInterceptor");
        ReflectionUtil.trySetAccessible(m);
        this.baseDialogType = (Class<? extends UIPanelAPI>) m.getDeclaringClass();
        this.m_getInterceptor = MethodHandles.publicLookup().unreflect(m);
    }

    public Class<? extends UIPanelAPI> baseDialogType() {
        return baseDialogType;
    }

    public UIPanelAPI getInterceptor(UIPanelAPI dialog) {
        try {
            return (UIPanelAPI) this.m_getInterceptor.invoke(dialog);
        } catch (RuntimeException | Error ex) {
            throw ex;
        } catch (Throwable t) {
            throw new AssertionError("unreachable", t);
        }
    }
}
