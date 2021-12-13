package net.andylizi.starsector.missionminimap.access;

import com.fs.starfarer.api.ui.SectorMapAPI;
import net.andylizi.starsector.missionminimap.ReflectionUtil;
import org.lwjgl.util.vector.Vector2f;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public class SectorMapAccess {
    private final Class<? extends SectorMapAPI> sectorMapType;
    private final Class<?> mapParamsType;
    private final Class<?> mapFilterType;
    private final Class<?> mapFilterDataType;
    private final MethodHandle ctor;
    private final MethodHandle m_getParams;
    private final MethodHandle m_getMap;
    private final MethodHandle m_centerOn;

    public SectorMapAccess(Class<? extends SectorMapAPI> sectorMapType) throws ReflectiveOperationException {
        this.sectorMapType = sectorMapType;
        this.mapFilterType = sectorMapType.getDeclaredMethod("getFilter").getReturnType();
        this.mapFilterDataType = this.mapFilterType.getDeclaredMethod("getData").getReturnType();
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();

        MethodHandle ctor = null;
        for (Constructor<?> c : sectorMapType.getDeclaredConstructors()) {
            Class<?>[] paramTypes = c.getParameterTypes();
            // MapParams params, float width, float height
            if (paramTypes.length == 3 && paramTypes[1] == float.class && paramTypes[2] == float.class) {
                ReflectionUtil.trySetAccessible(c);
                ctor = lookup.unreflectConstructor(c);
            }
        }
        if (ctor == null) throw new NoSuchMethodException("<init>(MapParams, float, float) in SectorMap " + sectorMapType);
        this.ctor = ctor;

        Method method = sectorMapType.getMethod("getParams");
        ReflectionUtil.trySetAccessible(method);
        this.mapParamsType = method.getReturnType();
        this.m_getParams = lookup.unreflect(method);

        method = sectorMapType.getMethod("getMap");
        ReflectionUtil.trySetAccessible(method);
        this.m_getMap = lookup.unreflect(method);

        method = sectorMapType.getMethod("centerOn", Vector2f.class);
        ReflectionUtil.trySetAccessible(method);
        this.m_centerOn = lookup.unreflect(method);
    }

    public Class<? extends SectorMapAPI> sectorMapType() {
        return this.sectorMapType;
    }

    public Class<?> mapParamsType() {
        return this.mapParamsType;
    }

    public Class<?> mapFilterType() {
        return this.mapFilterType;
    }

    public Class<?> mapFilterDataType() {
        return this.mapFilterDataType;
    }

    public SectorMapAPI newInstance(Object mapParams, float width, float height) {
        try {
            return (SectorMapAPI) this.ctor.invoke(mapParams, width, height);
        } catch (RuntimeException | Error ex) {
            throw ex;
        } catch (Throwable t) {
            throw new AssertionError("unreachable", t);
        }
    }

    public Object getParams(SectorMapAPI sectorMap) {
        try {
            return this.m_getParams.invoke(sectorMap);
        } catch (RuntimeException | Error ex) {
            throw ex;
        } catch (Throwable t) {
            throw new AssertionError("unreachable", t);
        }
    }

    public Object getMap(SectorMapAPI sectorMap) {
        try {
            return this.m_getMap.invoke(sectorMap);
        } catch (RuntimeException | Error ex) {
            throw ex;
        } catch (Throwable t) {
            throw new AssertionError("unreachable", t);
        }
    }

    public void centerOn(SectorMapAPI sectorMap, Vector2f pos) {
        try {
            this.m_centerOn.invoke(sectorMap, pos);
        } catch (RuntimeException | Error ex) {
            throw ex;
        } catch (Throwable t) {
            throw new AssertionError("unreachable", t);
        }
    }
}
