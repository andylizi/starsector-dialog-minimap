package net.andylizi.starsector.dialogminimap.access;

import com.fs.starfarer.api.impl.campaign.intel.bar.BarEventDialogPlugin;
import com.fs.starfarer.api.impl.campaign.intel.bar.PortsideBarEvent;
import net.andylizi.starsector.dialogminimap.ReflectionUtil;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;

public class BarEventDialogPluginAccess {
    private final MethodHandle f_event_get;

    public BarEventDialogPluginAccess() throws ReflectiveOperationException {
        Field f = ReflectionUtil.getFirstFieldByType(BarEventDialogPlugin.class, PortsideBarEvent.class);
        f.setAccessible(true);
        this.f_event_get = MethodHandles.publicLookup().unreflectGetter(f);
    }

    public PortsideBarEvent getEvent(BarEventDialogPlugin plugin) {
        try {
            return (PortsideBarEvent) this.f_event_get.invoke(plugin);
        } catch (RuntimeException | Error ex) {
            throw ex;
        } catch (Throwable t) {
            throw new AssertionError("unreachable", t);
        }
    }
}
