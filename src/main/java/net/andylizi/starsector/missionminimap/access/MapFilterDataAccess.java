package net.andylizi.starsector.missionminimap.access;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class MapFilterDataAccess {
    private final Class<?> mapFilterDataType;
    private final MethodHandle ctor;

    public MapFilterDataAccess(Class<?> mapFilterDataType) throws ReflectiveOperationException {
        this.mapFilterDataType = mapFilterDataType;
        this.ctor = MethodHandles.publicLookup()
            .findConstructor(mapFilterDataType, MethodType.methodType(void.class, boolean.class));
    }

    public Class<?> mapFilterDataType() {
        return mapFilterDataType;
    }

    public Object newInstance(boolean init) {
        try {
            return this.ctor.invoke(init);
        } catch (RuntimeException | Error ex) {
            throw ex;
        } catch (Throwable t) {
            throw new AssertionError("unreachable", t);
        }
    }
}
