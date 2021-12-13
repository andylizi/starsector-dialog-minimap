package net.andylizi.starsector.missionminimap.access;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;

public class InteractionDialogAccess {
    private final Class<? extends InteractionDialogAPI> interactionDialogType;
    private final MethodHandle f_pickerType_set;

    public InteractionDialogAccess(Class<? extends InteractionDialogAPI> interactionDialogType) throws ReflectiveOperationException {
        this.interactionDialogType = interactionDialogType;
        
        MethodHandle f_pickerType_set = null;
        for (Field f : interactionDialogType.getDeclaredFields()) {
            Class<?> type = f.getType();
            if (type.isEnum() && type.getEnclosingClass() == interactionDialogType) {
                f.setAccessible(true);
                f_pickerType_set = MethodHandles.publicLookup().unreflectSetter(f);
                break;
            }
        }
        
        if (f_pickerType_set == null) throw new NoSuchFieldException("pickerType in InteractionDialog " + interactionDialogType);
        this.f_pickerType_set = f_pickerType_set;
    }

    public Class<? extends InteractionDialogAPI> interactionDialogType() {
        return interactionDialogType;
    }
    
    public void setPickerType(InteractionDialogAPI dialog, Object type) {
        try {
            this.f_pickerType_set.invoke(dialog, type);
        } catch (RuntimeException | Error ex) {
            throw ex;
        } catch (Throwable t) {
            throw new AssertionError("unreachable", t);
        }
    }
}
