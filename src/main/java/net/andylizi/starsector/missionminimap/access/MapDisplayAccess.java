package net.andylizi.starsector.missionminimap.access;

import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.ui.UIPanelAPI;
import net.andylizi.starsector.missionminimap.ReflectionUtil;

import java.awt.Color;
import java.lang.reflect.Method;
import java.util.List;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class MapDisplayAccess {
    private final Class<? extends UIPanelAPI> mapDisplayType;
    private final MethodHandle m_addPing;
    private final MethodHandle m_getIntelData;
    private final MethodHandle m_addIntelData;

    public MapDisplayAccess(Class<? extends UIPanelAPI> mapDisplayType) throws ReflectiveOperationException {
        this.mapDisplayType = mapDisplayType;

        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        this.m_addPing = lookup
            .findVirtual(mapDisplayType, "addPing", MethodType.methodType(void.class,
                SectorEntityToken.class, Color.class, float.class, int.class));
        this.m_getIntelData = lookup
            .findVirtual(mapDisplayType, "getIntelData", MethodType.methodType(List.class));

        Method m = ReflectionUtil.getFirstMethodByName(mapDisplayType, "addIntelData");
        ReflectionUtil.trySetAccessible(m);
        this.m_addIntelData = lookup.unreflect(m);
    }

    public Class<? extends UIPanelAPI> mapDisplayType() {
        return mapDisplayType;
    }

    public Class<?> intelDataType() {
        return this.m_addIntelData.type().parameterType(1);  // 0 is `this`
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

    @SuppressWarnings("unchecked")
    public List<CustomCampaignEntityAPI> getIntelData(Object mapDisplay) {
        try {
            return (List<CustomCampaignEntityAPI>) this.m_getIntelData.invoke(mapDisplay);
        } catch (RuntimeException | Error ex) {
            throw ex;
        } catch (Throwable t) {
            throw new AssertionError("unreachable", t);
        }
    }

    public void addIntelData(Object mapDisplay, Object intelData) {
        try {
            this.m_addIntelData.invoke(mapDisplay, intelData);
        } catch (RuntimeException | Error ex) {
            throw ex;
        } catch (Throwable t) {
            throw new AssertionError("unreachable", t);
        }
    }
}
