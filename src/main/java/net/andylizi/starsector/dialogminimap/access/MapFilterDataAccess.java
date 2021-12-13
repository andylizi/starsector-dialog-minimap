package net.andylizi.starsector.dialogminimap.access;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class MapFilterDataAccess {
    private final Class<?> mapFilterDataType;
    private final MethodHandle ctor;
    private final MethodHandle f_factions_set;

    public MapFilterDataAccess(Class<?> mapFilterDataType) throws ReflectiveOperationException {
        this.mapFilterDataType = mapFilterDataType;

        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        this.ctor = lookup
            .findConstructor(mapFilterDataType, MethodType.methodType(void.class, boolean.class));
        this.f_factions_set = lookup.findSetter(mapFilterDataType, "factions", boolean.class);
    }

    public Class<?> mapFilterDataType() {
        return mapFilterDataType;
    }

    // def=true sets the following:
    //  - starscape = true
    //  - names = true
    //  - factions = true
    //  - missions = false (unused)
    //  - fuel = false
    //  - exploration = false
    //  - legend = true (unused)
    public Object newInstance(boolean def) {
        try {
            return this.ctor.invoke(def);
        } catch (RuntimeException | Error ex) {
            throw ex;
        } catch (Throwable t) {
            throw new AssertionError("unreachable", t);
        }
    }

    // This is actually "Inhabited" on the UI
    public void setFactions(Object filterData, boolean factions) {
        try {
            this.f_factions_set.invoke(filterData, factions);
        } catch (RuntimeException | Error ex) {
            throw ex;
        } catch (Throwable t) {
            throw new AssertionError("unreachable", t);
        }
    }
}
