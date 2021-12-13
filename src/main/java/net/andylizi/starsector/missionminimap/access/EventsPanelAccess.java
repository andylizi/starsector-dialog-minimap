package net.andylizi.starsector.missionminimap.access;

import com.fs.starfarer.api.ui.UIPanelAPI;
import net.andylizi.starsector.missionminimap.ReflectionUtil;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class EventsPanelAccess {
    private final Class<? extends UIPanelAPI> eventsPanelType;
    private final MethodHandle ctor;
    private final MethodHandle f_sectorMap_get;
    private final MethodHandle m_sizeChanged;

    @SuppressWarnings("unchecked")
    public EventsPanelAccess() throws ReflectiveOperationException {
        this.eventsPanelType = (Class<? extends UIPanelAPI>) 
            Class.forName("com.fs.starfarer.campaign.comms.v2.EventsPanel");

        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        Constructor<?> ctor = ReflectionUtil.getFirstConstructorByParameterCount(eventsPanelType, 1);
        this.ctor = lookup.unreflectConstructor(ctor);

        Field sectorMapField = null;
        for (Field f : eventsPanelType.getDeclaredFields()) {
            Class<?> type = f.getType();
            if (!type.getName().startsWith("com.fs.starfarer.coreui.map")) continue;

            boolean hasGetMap = false, hasGetFilter = false;
            for (Method m : type.getDeclaredMethods()) {
                if ("getMap".equals(m.getName())) {
                    hasGetMap = true;
                    if (hasGetFilter) break;
                } else if ("getFilter".equals(m.getName())) {
                    hasGetFilter = true;
                    if (hasGetMap) break;
                }
            }

            if (hasGetMap && hasGetFilter) {
                sectorMapField = f;
                break;
            }
        }

        if (sectorMapField == null) throw new NoSuchFieldException("sectorMap in " + eventsPanelType);
        sectorMapField.setAccessible(true);
        this.f_sectorMap_get = lookup.unreflectGetter(sectorMapField);
        this.m_sizeChanged = lookup.findVirtual(eventsPanelType, "sizeChanged",
            MethodType.methodType(void.class, float.class, float.class));
    }

    public Class<? extends UIPanelAPI> eventsPanelType() {
        return eventsPanelType;
    }

    @SuppressWarnings("unchecked")
    public Class<? extends UIPanelAPI> sectorMapType() {
        return (Class<? extends UIPanelAPI>) this.f_sectorMap_get.type().returnType();
    }

    public Object newInstance(Object intel) {
        try {
            return this.ctor.invoke(intel);
        } catch (RuntimeException | Error ex) {
            throw ex;
        } catch (Throwable t) {
            throw new AssertionError("unreachable", t);
        }
    }

    public Object newInstanceDefault() throws ReflectiveOperationException {
        Object intel = ReflectionUtil.instantiateDefault(this.ctor.type().parameterType(0));
        return newInstance(intel);
    }

    public Object getSectorMap(Object eventsPanel) {
        try {
            return this.f_sectorMap_get.invoke(eventsPanel);
        } catch (RuntimeException | Error ex) {
            throw ex;
        } catch (Throwable t) {
            throw new AssertionError("unreachable", t);
        }
    }

    public void sizeChanged(Object eventsPanel, float width, float height) {
        try {
            this.m_sizeChanged.invoke(eventsPanel, width, height);
        } catch (RuntimeException | Error ex) {
            throw ex;
        } catch (Throwable t) {
            throw new AssertionError("unreachable", t);
        }
    }
}
