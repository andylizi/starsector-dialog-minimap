package net.andylizi.starsector.missionminimap.access;

import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import net.andylizi.starsector.missionminimap.ReflectionUtil;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;

public class MapParamsAccess {
    private final Class<?> mapParamsType;
    private final MethodHandle ctor;
    private final MethodHandle f_entity_set;
    private final MethodHandle f_filterData_set;
    private final MethodHandle f_location_set;

    @Nullable
    private final MethodHandle f_zoom_set;

    public MapParamsAccess(Class<?> mapParamsType, Class<?> mapFilterDataType) throws ReflectiveOperationException {
        this.mapParamsType = mapParamsType;

        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        this.ctor = lookup.findConstructor(mapParamsType, MethodType.methodType(void.class));

        Field field = ReflectionUtil.getFirstFieldByType(mapParamsType, SectorEntityToken.class);
        ReflectionUtil.trySetAccessible(field);
        this.f_entity_set = lookup.unreflectSetter(field);

        field = ReflectionUtil.getFirstFieldByType(mapParamsType, mapFilterDataType);
        ReflectionUtil.trySetAccessible(field);
        this.f_filterData_set = lookup.unreflectSetter(field);

        field = ReflectionUtil.getFirstFieldBySupertype(mapParamsType, LocationAPI.class);
        ReflectionUtil.trySetAccessible(field);
        this.f_location_set = lookup.unreflectSetter(field);

        // zoom is the only float defaulting to 0.0f
        Object tmp = newInstance();
        field = null;
        for (Field f : ReflectionUtil.getFieldsByType(mapParamsType, float.class)) {
            ReflectionUtil.trySetAccessible(f);
            if (f.getFloat(tmp) == 0.0f) {
                field = f;
                break;
            }
        }
        this.f_zoom_set = field == null ? null : lookup.unreflectSetter(field);
    }

    public Class<?> mapParamsType() {
        return mapParamsType;
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

    public void setEntity(Object mapParams, SectorEntityToken entity) {
        try {
            this.f_entity_set.invoke(mapParams, entity);
        } catch (RuntimeException | Error ex) {
            throw ex;
        } catch (Throwable t) {
            throw new AssertionError("unreachable", t);
        }
    }

    public void setFilterData(Object mapParams, Object filterData) {
        try {
            this.f_filterData_set.invoke(mapParams, filterData);
        } catch (RuntimeException | Error ex) {
            throw ex;
        } catch (Throwable t) {
            throw new AssertionError("unreachable", t);
        }
    }

    public void setLocation(Object mapParams, LocationAPI location) {
        try {
            this.f_location_set.invoke(mapParams, location);
        } catch (RuntimeException | Error ex) {
            throw ex;
        } catch (Throwable t) {
            throw new AssertionError("unreachable", t);
        }
    }

    public boolean trySetZoom(Object mapParams, float zoom) {
        if (this.f_zoom_set == null) return false;
        try {
            this.f_zoom_set.invoke(mapParams, zoom);
            return true;
        } catch (RuntimeException | Error ex) {
            throw ex;
        } catch (Throwable t) {
            throw new AssertionError("unreachable", t);
        }
    }
}
