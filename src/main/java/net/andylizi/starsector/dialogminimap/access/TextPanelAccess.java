package net.andylizi.starsector.dialogminimap.access;

import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import net.andylizi.starsector.dialogminimap.ReflectionUtil;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.util.List;

public class TextPanelAccess {
    private final Class<? extends TextPanelAPI> textPanelType;
    private final MethodHandle f_paragraphs_get;

    public TextPanelAccess(Class<? extends TextPanelAPI> textPanelType) throws ReflectiveOperationException {
        this.textPanelType = textPanelType;
        Field f = ReflectionUtil.getFirstFieldBySupertype(textPanelType, List.class);
        ReflectionUtil.trySetAccessible(f);
        this.f_paragraphs_get = MethodHandles.publicLookup().unreflectGetter(f);
    }

    public Class<? extends TextPanelAPI> textPanelType() {
        return textPanelType;
    }
    
    @SuppressWarnings("unchecked")
    public List<LabelAPI> getParagraphs(TextPanelAPI textPanel) {
        try {
            return (List<LabelAPI>) this.f_paragraphs_get.invoke(textPanel);
        } catch (RuntimeException | Error ex) {
            throw ex;
        } catch (Throwable t) {
            throw new AssertionError("unreachable", t);
        }
    }
}
