package net.andylizi.starsector.dialogminimap.access;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import net.andylizi.starsector.dialogminimap.ReflectionUtil;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;

public class IntelDataAccess {
    private final Class<?> intelDataType;
    private final MethodHandle ctor;
    private final MethodHandle f_commMessage_set;
    private final MethodHandle f_plugin_set;
    private final MethodHandle f_tooltipWidth_set;
    private final MethodHandle f_entity_set;

    public IntelDataAccess(Class<?> intelDataType) throws ReflectiveOperationException {
        this.intelDataType = intelDataType;

        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        this.ctor = lookup.findConstructor(intelDataType, MethodType.methodType(void.class));

        Field f = ReflectionUtil.getFirstFieldBySupertype(intelDataType, CommMessageAPI.class);
        ReflectionUtil.trySetAccessible(f);
        this.f_commMessage_set = lookup.unreflectSetter(f);

        f = ReflectionUtil.getFirstFieldByType(intelDataType, IntelInfoPlugin.class);
        ReflectionUtil.trySetAccessible(f);
        this.f_plugin_set = lookup.unreflectSetter(f);

        f = ReflectionUtil.getFirstFieldByType(intelDataType, float.class);
        ReflectionUtil.trySetAccessible(f);
        this.f_tooltipWidth_set = lookup.unreflectSetter(f);

        f = ReflectionUtil.getFirstFieldByType(intelDataType, SectorEntityToken.class);
        ReflectionUtil.trySetAccessible(f);
        this.f_entity_set = lookup.unreflectSetter(f);
    }

    public Class<?> intelDataType() {
        return intelDataType;
    }

    public Object newInstance() {
        try {
            return this.ctor.invoke();
        } catch (RuntimeException | Error ex) {
            throw ex;
        } catch (Throwable t) {
            throw new AssertionError("unreachable", t);
        }
    }

    public void setCommMessage(Object intelData, CommMessageAPI commMessage) {
        try {
            this.f_commMessage_set.invoke(intelData, commMessage);
        } catch (RuntimeException | Error ex) {
            throw ex;
        } catch (Throwable t) {
            throw new AssertionError("unreachable", t);
        }
    }

    public void setIntelPlugin(Object intelData, IntelInfoPlugin plugin) {
        try {
            this.f_plugin_set.invoke(intelData, plugin);
        } catch (RuntimeException | Error ex) {
            throw ex;
        } catch (Throwable t) {
            throw new AssertionError("unreachable", t);
        }
    }

    public void setTooltipWidth(Object intelData, float tooltipWidth) {
        try {
            this.f_tooltipWidth_set.invoke(intelData, tooltipWidth);
        } catch (RuntimeException | Error ex) {
            throw ex;
        } catch (Throwable t) {
            throw new AssertionError("unreachable", t);
        }
    }

    public void setEntity(Object intelData, SectorEntityToken entity) {
        try {
            this.f_entity_set.invoke(intelData, entity);
        } catch (RuntimeException | Error ex) {
            throw ex;
        } catch (Throwable t) {
            throw new AssertionError("unreachable", t);
        }
    }
}
