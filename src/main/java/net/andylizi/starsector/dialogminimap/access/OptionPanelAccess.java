package net.andylizi.starsector.dialogminimap.access;

import com.fs.starfarer.api.campaign.OptionPanelAPI;
import net.andylizi.starsector.dialogminimap.ReflectionUtil;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.WrongMethodTypeException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class OptionPanelAccess {
    private final Class<? extends OptionPanelAPI> panelType;
    private final Class<?> delegateType;
    private final MethodHandle m_getDelegate;
    private final MethodHandle f_delegate_set;

    public OptionPanelAccess(Class<? extends OptionPanelAPI> panelType) throws ReflectiveOperationException {
        this.panelType = panelType;

        Method getDelegate = ReflectionUtil.getFirstMethodByName(panelType, "getDelegate");
        ReflectionUtil.trySetAccessible(getDelegate);
        
        this.delegateType = getDelegate.getReturnType();
        if (!delegateType.isInterface()) {
            throw new NoSuchMethodException(
                "OptionPanel.getDelegate() return type isn't an interface: "
                + delegateType);
        }

        Field delegateField = ReflectionUtil.getFirstFieldByType(panelType, delegateType);
        delegateField.setAccessible(true);

        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        this.m_getDelegate = lookup.unreflect(getDelegate);
        this.f_delegate_set = lookup.unreflectSetter(delegateField);
    }

    public Class<? extends OptionPanelAPI> optionPanelType() {
        return panelType;
    }

    public Class<?> delegateType() {
        return delegateType;
    }
    
    public Object getDelegate(OptionPanelAPI optionPanel) throws WrongMethodTypeException, ClassCastException {
        try {
            return this.m_getDelegate.invoke(optionPanel);
        } catch (RuntimeException | Error ex) {
            throw ex;
        } catch (Throwable t) {
            throw new AssertionError("unreachable", t);
        }
    }
    
    public void setDelegate(OptionPanelAPI optionPanel, Object delegate) throws WrongMethodTypeException, ClassCastException {
        try {
            this.f_delegate_set.invoke(optionPanel, delegate);
        } catch (RuntimeException | Error ex) {
            throw ex;
        } catch (Throwable t) {
            throw new AssertionError("unreachable", t);
        }
    }
}
