package net.andylizi.starsector.missionminimap.access;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.ui.UIPanelAPI;

import java.awt.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class MapDisplayAccess {
    private final Class<? extends UIPanelAPI> mapDisplayType;
    private final MethodHandle m_addPing;

    public MapDisplayAccess(Class<? extends UIPanelAPI> mapDisplayType) throws ReflectiveOperationException {
        this.mapDisplayType = mapDisplayType;
        this.m_addPing = MethodHandles.publicLookup()
            .findVirtual(mapDisplayType, "addPing", MethodType.methodType(void.class,
                SectorEntityToken.class, Color.class, float.class, int.class));
    }

    public Class<? extends UIPanelAPI> mapDisplayType() {
        return mapDisplayType;
    }

    public void addPing(Object mapDisplay, SectorEntityToken entity, Color color, float size, int count) {
        try {
            this.m_addPing.invoke(mapDisplay, entity, color, size, count);
        } catch (RuntimeException | Error ex) {
            throw ex;
        } catch (Throwable t) {
            throw new AssertionError("unreachable", t);
        }
    }
}
