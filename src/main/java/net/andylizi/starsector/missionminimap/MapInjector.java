package net.andylizi.starsector.missionminimap;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.*;
import java.util.*;

import com.fs.graphics.util.Fader;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;

import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

@SuppressWarnings("unused")
public final class MapInjector {
    private static final Logger logger = Logger.getLogger(MapInjector.class);

    private static Class<?> DIALOG_IMPL_TYPE;
    private static Class<?> OPTION_PANEL_IMPL_TYPE;
    private static MethodHandle OPTION_DELEGATE_GETTER;
    private static MethodHandle OPTION_DELEGATE_SETTER;
    private static Method OPTION_SELECTED_METHOD;
    private static Class<?> OPTION_TYPE;
    private static MethodHandle OPTION_TEXT_GETTER;

    private static void clearCache() {
        DIALOG_IMPL_TYPE = null;
        OPTION_PANEL_IMPL_TYPE = null;
        OPTION_DELEGATE_GETTER = null;
        OPTION_DELEGATE_SETTER = null;
        OPTION_SELECTED_METHOD = null;
        OPTION_TYPE = null;
    }

    public static void injectDialog(final InteractionDialogAPI dialog) {
        try {
            Class<?> dialogType = dialog.getClass();
            final OptionPanelAPI panel = dialog.getOptionPanel();
            Class<?> panelType = panel.getClass();

            // There should only be one implementation, but just in case
            if ((DIALOG_IMPL_TYPE != null && !DIALOG_IMPL_TYPE.equals(dialogType)) ||
                (OPTION_PANEL_IMPL_TYPE != null && !OPTION_PANEL_IMPL_TYPE.equals(panelType))) {
                clearCache();
            }

            DIALOG_IMPL_TYPE = dialogType;
            OPTION_PANEL_IMPL_TYPE = panelType;

            if (OPTION_DELEGATE_GETTER == null) {
                Method getDelegate = ReflectionUtil.getFirstMethodByName(panelType, "getDelegate");
                Class<?> delegateInterface = getDelegate.getReturnType();

                if (!delegateInterface.isInterface()) {
                    throw new RuntimeException(
                        "OptionPanelAPI impl getDelegate() return type isn't a interface: "
                        + delegateInterface);
                }

                Field delegateField = ReflectionUtil.getFirstFieldByType(panelType, delegateInterface);
                delegateField.setAccessible(true);

                Lookup lookup = MethodHandles.publicLookup();
                OPTION_DELEGATE_GETTER = lookup.unreflectGetter(delegateField);
                OPTION_DELEGATE_SETTER = lookup.unreflectSetter(delegateField);
            }

            Class<?> delegateInterface = OPTION_DELEGATE_GETTER.type().returnType();
            final Object originalDelegate = OPTION_DELEGATE_GETTER.invoke(panel);
            Class<?> originalDelegateImplType = originalDelegate.getClass();
            if (Proxy.isProxyClass(originalDelegateImplType)) {
                return; // already injected
            }

            if (OPTION_SELECTED_METHOD == null) {
                class OptionDelegateProbe implements InteractionDialogPlugin {
                    boolean triggered = false;

                    @Override
                    public void init(InteractionDialogAPI dialog) {
                    }

                    @Override
                    public void optionSelected(String optionText, Object optionData) {
                        triggered = true;
                    }

                    @Override
                    public void optionMousedOver(String optionText, Object optionData) {
                    }

                    @Override
                    public void advance(float amount) {
                    }

                    @Override
                    public void backFromEngagement(EngagementResultAPI battleResult) {
                    }

                    @Override
                    public Object getContext() {
                        throw new UnsupportedOperationException("unimplemented");
                    }

                    @Override
                    public Map<String, MemoryAPI> getMemoryMap() {
                        throw new UnsupportedOperationException("unimplemented");
                    }
                }

                OptionDelegateProbe probe = new OptionDelegateProbe();
                Field dialogPluginField = ReflectionUtil.getFirstFieldByType(dialogType,
                                                                             InteractionDialogPlugin.class);
                dialogPluginField.setAccessible(true);
                Object originalPlugin = dialogPluginField.get(dialog);
                try {
                    dialogPluginField.set(dialog, probe);

                    boolean isTheFirstOne = true;
                    for (Method m : delegateInterface.getMethods()) {
                        Class<?>[] paramTypes = m.getParameterTypes();
                        if (paramTypes.length != 1)
                            continue;
                        Class<?> paramType = paramTypes[0];
                        Object dummyArg = ReflectionUtil.instantiateDefault(paramType);
                        m.invoke(originalDelegate, dummyArg);

                        if (probe.triggered) {
                            OPTION_SELECTED_METHOD = m;
                            OPTION_TYPE = paramType;
                            break;
                        } else {
                            isTheFirstOne = false;
                        }
                    }

                    if (!isTheFirstOne) {
                        // We may have triggered showOptionConfirmDialog() while probing. Close that.
                        try {
                            removeOptionConfirmDialog(dialog);
                        } catch (Throwable t) {
                            // It's annoying having to close it manually, but ultimately not critical. Log and continue
                            logger.warn("Failed to close the confirm dialog", t);
                        }
                    }

                    if (OPTION_SELECTED_METHOD == null) {
                        throw new RuntimeException(
                            "Unable to determine which method in " + delegateInterface + "is optionSelected()");
                    }
                } finally {
                    dialogPluginField.set(dialog, originalPlugin);
                }
            }

            if (OPTION_TEXT_GETTER == null) {
                Field textField = null;
                for (Field f : OPTION_TYPE.getFields()) {
                    if (String.class == f.getType()) {
                        textField = f;
                        break;
                    }
                }

                if (textField == null) {
                    throw new RuntimeException(
                        "'text' field not found in the supposedly Option class: " + OPTION_TYPE.getName());
                }
                textField.setAccessible(true); // is actually public, but whatever
                OPTION_TEXT_GETTER = MethodHandles.publicLookup().unreflectGetter(textField);
            }

            final Method optionSelectedMethod = OPTION_SELECTED_METHOD;
            final MethodHandle optionTextGetter = OPTION_TEXT_GETTER;
            Object newDelegate = Proxy.newProxyInstance(originalDelegateImplType.getClassLoader(),
                                                        new Class<?>[] { delegateInterface }, new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        Object result = method.invoke(originalDelegate, args);
                        if (optionSelectedMethod.equals(method)) {
                            removeMapPanelFromDialog(dialog);

                            String chosenOption = (String) optionTextGetter.invoke(args[0]);
                            for (Object option : panel.getSavedOptionList()) {
                                String text = (String) optionTextGetter.invoke(option);
                                if ("Accept".startsWith(text)) {
                                    searchMissionSystem(dialog, chosenOption);
                                    break;
                                }
                            }
                        }
                        return result;
                    }
                });
            OPTION_DELEGATE_SETTER.invoke(panel, newDelegate);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static void searchMissionSystem(InteractionDialogAPI dialog, String selectedOption) throws Throwable {
        Map<String, MemoryAPI> memory = dialog.getPlugin().getMemoryMap();
        MemoryAPI interactionMemory = memory.get(MemKeys.LOCAL);
        if (interactionMemory == null) {
            if (dialog.getInteractionTarget().getActivePerson() != null) {
                interactionMemory = dialog.getInteractionTarget().getActivePerson().getMemoryWithoutUpdate();
            } else {
                interactionMemory = dialog.getInteractionTarget().getMemoryWithoutUpdate();
            }
        }

        List<String> systemNames = new ArrayList<>(1);
        for (String key : interactionMemory.getKeys())
            if (key.endsWith("_systemName"))
                systemNames.add(interactionMemory.getString(key));
        if (systemNames.isEmpty()) {
            logger.info("No systemName for the mission is detected");
            return;
        }

        Collections.sort(systemNames, new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                return Integer.compare(o2.length(), o1.length());
            }
        });

        List<LabelAPI> paragraphs = getParagraphs(dialog.getTextPanel());
        // Use last three paragraphs (explanation + selected option + explanation).
        // This is because some missions put the target name before a "Continue"
        ListIterator<LabelAPI> it = paragraphs.listIterator(paragraphs.size() - 3);
        StringBuilder description = new StringBuilder();
        while (it.hasNext()) description.append(it.next().getText()).append('\n').append('\n');

        String systemName = null;
        int appearancePos = 0;
        for (String name : systemNames) {
            int pos = description.indexOf(name);
            // If multiple matches, we pick the last(latest) one
            if (pos != -1 && pos > appearancePos) {
                systemName = name;
                appearancePos = pos;
            }
        }

        if (systemName == null) {
            // Some missions only show market names...
            SectorEntityToken target = null;
            outer:
            for (String name : systemNames) {
                StarSystemAPI system = getStarSystem(name);
                if (system == null) continue;
                Set<String> seen = new HashSet<>();

                for (SectorEntityToken entity : system.getAllEntities()) {
                    String entityName = entity.getName();

                    // Who the hell decided that using "Null" for null is a good idea?!
                    if (entityName == null || "null".equalsIgnoreCase(entityName)) continue;

                    // We want unique entities, not a bunch of "Asteroid"s for example.
                    if (!seen.add(entityName)) continue;

                    if (description.indexOf(entityName) != -1) {
                        target = entity;
                        break outer;
                    }
                }
            }

            if (target != null) {
                logger.info("Mission target found: " + target.getName() + ", " +
                            target.getStarSystem().getNameWithLowercaseTypeShort());
                tryShowMinimap(dialog, target);
            }
        } else {
            StarSystemAPI system = getStarSystem(systemName);
            if (system != null) {
                logger.info("Mission target found: " + system.getNameWithLowercaseTypeShort());
                tryShowMinimap(dialog, system.getCenter());
            } else {
                logger.warn("Unrecognized star system for the mission: " + systemName);
            }
        }
    }

    private static Class<?> EVENTS_PANEL_TYPE;
    private static MethodHandle EVENTS_MAP_PANEL_GETTER;
    private static Class<?> MAP_PANEL_TYPE;
    private static MethodHandle MAP_PANEL_CTOR;
    private static MethodHandle MAP_PANEL_CENTER_ON_ENTITY;

    private static Class<?> MAP_FILTER_TYPE;
    private static Class<?> MAP_FILTER_DATA_TYPE;
    private static MethodHandle MAP_FILTER_DATA_CTOR;

    private static Class<?> MAP_DATA_TYPE;
    private static MethodHandle MAP_DATA_GETTER;
    private static MethodHandle MAP_DATA_CTOR;

    private static Field MAP_DATA_ENTITY;
    private static Field MAP_DATA_FILTER_DATA;
    private static Field MAP_DATA_TARGET_LOC;
    private static Field MAP_DATA_ZOOM;
    private static Map<Field, Boolean> MAP_DATA_FLAGS;

    public static void tryShowMinimap(InteractionDialogAPI dialog, SectorEntityToken target) throws Throwable {
        if (EVENTS_PANEL_TYPE == null) {
            EVENTS_PANEL_TYPE = Class.forName("com.fs.starfarer.campaign.comms.v2.EventsPanel");
        }

        if (MAP_PANEL_TYPE == null) {
            for (Field f : EVENTS_PANEL_TYPE.getDeclaredFields()) {
                Class<?> type = f.getType();
                if (!type.getName().startsWith("com.fs.starfarer.coreui.map")) continue;

                boolean hasGetMap = false, hasGetFilter = false;
                for (Method m : type.getDeclaredMethods()) {
                    if ("getMap".equals(m.getName())) {
                        hasGetMap = true;
                        if (hasGetFilter) break;
                    } else if ("getFilter".equals(m.getName())) {
                        hasGetFilter = true;
                        MAP_FILTER_TYPE = m.getReturnType();
                        if (hasGetMap) break;
                    }
                }

                if (hasGetMap && hasGetFilter) {
                    MAP_PANEL_TYPE = type;
                    f.setAccessible(true);
                    EVENTS_MAP_PANEL_GETTER = MethodHandles.publicLookup().unreflectGetter(f);
                    break;
                }
            }

            if (MAP_PANEL_TYPE == null)
                throw new ClassNotFoundException("Failed to find MapPanel from " + EVENTS_PANEL_TYPE);

            MAP_PANEL_CENTER_ON_ENTITY = MethodHandles.publicLookup()
                .unreflect(MAP_PANEL_TYPE.getMethod("centerOnEntity", SectorEntityToken.class));
        }

        if (MAP_FILTER_DATA_TYPE == null) {
            MAP_FILTER_DATA_TYPE = ReflectionUtil.getFirstMethodByName(MAP_FILTER_TYPE, "getData").getReturnType();
            MAP_FILTER_DATA_CTOR = MethodHandles.publicLookup()
                .findConstructor(MAP_FILTER_DATA_TYPE, MethodType.methodType(void.class, boolean.class));
        }

        if (MAP_DATA_TYPE == null) {
            for (Constructor<?> ctor : MAP_PANEL_TYPE.getDeclaredConstructors()) {
                Class<?>[] paramTypes = ctor.getParameterTypes();
                // MapData data, float width, float height
                if (paramTypes.length == 3 && paramTypes[1] == float.class && paramTypes[2] == float.class) {
                    ctor.setAccessible(true); // TODO trySetAccessible
                    MAP_DATA_TYPE = paramTypes[0];
                    MAP_PANEL_CTOR = MethodHandles.publicLookup().unreflectConstructor(ctor);
                }
            }

            if (MAP_DATA_TYPE == null)
                throw new ClassNotFoundException("Failed to find MapPanel.Data in " + MAP_PANEL_TYPE);

            MAP_DATA_GETTER = MethodHandles.publicLookup()
                .unreflectGetter(ReflectionUtil.getFirstFieldByType(MAP_PANEL_TYPE, MAP_DATA_TYPE));
            MAP_DATA_CTOR = MethodHandles.publicLookup()
                .findConstructor(MAP_DATA_TYPE, MethodType.methodType(void.class));

            MAP_DATA_ENTITY = ReflectionUtil.getFirstFieldByType(MAP_DATA_TYPE, SectorEntityToken.class);
            MAP_DATA_FILTER_DATA = ReflectionUtil.getFirstFieldByType(MAP_DATA_TYPE, MAP_FILTER_DATA_TYPE);
            MAP_DATA_TARGET_LOC = ReflectionUtil.getFirstFieldBySupertype(MAP_DATA_TYPE, LocationAPI.class);

            // zoom is the only float defaulting to 0.0f
            Object tmp = MAP_DATA_CTOR.invoke();
            for (Field f : ReflectionUtil.getFieldsByType(MAP_DATA_TYPE, float.class)) {
                f.setAccessible(true); // TODO trySetAccessible
                if (f.getFloat(tmp) == 0.0f) {
                    MAP_DATA_ZOOM = f;
                    break;
                }
            }

            MAP_DATA_FLAGS = getIntelMapSettings(EVENTS_PANEL_TYPE, EVENTS_MAP_PANEL_GETTER, MAP_DATA_GETTER);
        }

        Object filterData = MAP_FILTER_DATA_CTOR.invoke(false);
        Object mapData = MAP_DATA_CTOR.invoke();
        LocationAPI hyperspace = Global.getSector().getHyperspace();

        MAP_DATA_ENTITY.set(mapData, target);
        MAP_DATA_FILTER_DATA.set(mapData, filterData);
        MAP_DATA_TARGET_LOC.set(mapData, hyperspace);
        if (MAP_DATA_ZOOM != null) MAP_DATA_ZOOM.setFloat(mapData, 2.4f);
        for (Map.Entry<Field, Boolean> entry : MAP_DATA_FLAGS.entrySet()) {
            entry.getKey().set(mapData, entry.getValue());
        }

        float width = ((UIComponentAPI) dialog.getTextPanel()).getPosition().getWidth();
        UIComponentAPI mapPanel = (UIComponentAPI) MAP_PANEL_CTOR.invoke(mapData, width, 300f);
        addMapPanelToDialog(dialog, mapPanel);
        MAP_PANEL_CENTER_ON_ENTITY.invoke(mapPanel, hyperspace.createToken(target.getContainingLocation().getLocation()));
    }

    private static MethodHandle GET_FADER;

    private static Fader getFader(UIComponentAPI component) throws Throwable {
        if (GET_FADER == null) {
            Method m = component.getClass().getMethod("getFader");
            m.setAccessible(true); // TODO trySetAccessible
            GET_FADER = MethodHandles.publicLookup().unreflect(m);
        }
        return (Fader) GET_FADER.invoke(component);
    }

    private static void addMapPanelToDialog(InteractionDialogAPI dialog, UIComponentAPI mapPanel) {
        ((UIPanelAPI) dialog).addComponent(mapPanel)
            .rightOfBottom((UIComponentAPI) dialog.getTextPanel(), 28.0f) // trying to align with the visual XD
            .setYAlignOffset(-150f);

        try {
            Fader fader = getFader(mapPanel);
            fader.setDuration(0.4f, 0.4f);
            fader.forceOut();
            fader.fadeIn();
        } catch (Throwable t) {
            // It's fine
            logger.warn("Failed to use Fader", t);
        }
    }

    private static Class<?> UIPANEL_TYPE;
    private static MethodHandle UIPANEL_GET_CHILDREN_NONCOPY;
    private static MethodHandle UIPANEL_REMOVE;

    private static void removeMapPanelFromDialog(InteractionDialogAPI dialog) throws Throwable {
        if (UIPANEL_TYPE == null) {
            UIPANEL_TYPE = BASE_DIALOG_TYPE.getSuperclass();
            UIPANEL_GET_CHILDREN_NONCOPY = MethodHandles.publicLookup()
                .unreflect(ReflectionUtil.getFirstMethodByName(UIPANEL_TYPE, "getChildrenNonCopy"));

            for (Method m : UIPANEL_TYPE.getMethods()) {
                Class<?>[] paramTypes;
                if ("remove".equals(m.getName()) && !m.isVarArgs() &&
                    (paramTypes = m.getParameterTypes()).length == 1 &&
                    !paramTypes[0].isArray()) {
                    m.setAccessible(true);  // TODO trySetAccessible
                    UIPANEL_REMOVE = MethodHandles.publicLookup().unreflect(m);
                }
            }
        }

        if (MAP_PANEL_TYPE == null) return;

        @SuppressWarnings("unchecked")
        List<UIComponentAPI> children = (List<UIComponentAPI>) UIPANEL_GET_CHILDREN_NONCOPY.invoke(dialog);
        List<UIComponentAPI> removeList = null;
        for (UIComponentAPI child : children) {
            if (child != null && child.getClass() == MAP_PANEL_TYPE) {
                if (removeList == null) removeList = new ArrayList<>(1);
                removeList.add(child);
            }
        }

        if (removeList != null)
            for (UIComponentAPI toRemove : removeList) UIPANEL_REMOVE.invoke(dialog, toRemove);
    }

    private static Class<?> BASE_DIALOG_TYPE;
    private static MethodHandle BASE_DIALOG_GET_INTERCEPTOR;
    private static MethodHandle DIALOG_PICKERTYPE_SETTER;

    private static void removeOptionConfirmDialog(InteractionDialogAPI dialog) throws Throwable {
        if (BASE_DIALOG_TYPE == null) {
            Method m = dialog.getClass().getMethod("getInterceptor");
            m.setAccessible(true); // TODO trySetAccessible
            BASE_DIALOG_TYPE = m.getDeclaringClass();
            BASE_DIALOG_GET_INTERCEPTOR = MethodHandles.publicLookup().unreflect(m);
        }

        if (UIPANEL_GET_CHILDREN_NONCOPY == null) {
            removeMapPanelFromDialog(dialog); // initialize. TODO find a better way to structure this
        }

        @SuppressWarnings("unchecked")
        List<UIComponentAPI> children = (List<UIComponentAPI>) UIPANEL_GET_CHILDREN_NONCOPY.invoke(dialog);
        List<UIComponentAPI> subdialogs = new ArrayList<>(1);
        for (UIComponentAPI child : children)
            if (BASE_DIALOG_TYPE.isAssignableFrom(child.getClass())) subdialogs.add(child);

        if (!subdialogs.isEmpty()) {
            for (UIComponentAPI subdialog : subdialogs) {
                UIComponentAPI interceptor = (UIComponentAPI) BASE_DIALOG_GET_INTERCEPTOR.invoke(subdialog);
                UIPANEL_REMOVE.invoke(dialog, subdialog);
                UIPANEL_REMOVE.invoke(dialog, interceptor);
            }

            if (DIALOG_PICKERTYPE_SETTER == null) {
                Class<?> dialogType = dialog.getClass();
                for (Field f : dialogType.getDeclaredFields()) {
                    Class<?> type = f.getType();
                    if (type.isEnum() && type.getEnclosingClass() == dialogType) {
                        f.setAccessible(true);
                        DIALOG_PICKERTYPE_SETTER = MethodHandles.publicLookup().unreflectSetter(f);
                        break;
                    }
                }

                if (DIALOG_PICKERTYPE_SETTER == null)
                    throw new NoSuchFieldException("field 'pickerType' in " + dialogType);
            }

            // reset it to null
            DIALOG_PICKERTYPE_SETTER.invoke(dialog, null);
        }
    }

    private static Map<Field, Boolean> getIntelMapSettings(Class<?> eventsPanelType,
                                                           MethodHandle mapPanelGetter,
                                                           MethodHandle mapDataGetter) throws Throwable {
        Constructor<?> eventsPanelCtor = ReflectionUtil.getFirstConstructorByParameterCount(eventsPanelType, 1);
        Class<?> intelPanelType = eventsPanelCtor.getParameterTypes()[0];
        Object intelPanel = ReflectionUtil.instantiateDefault(intelPanelType);
        Object eventsPanel = eventsPanelCtor.newInstance(intelPanel);

        // initialize the map panel
        Method sizeChanged = eventsPanelType.getMethod("sizeChanged", float.class, float.class);
        sizeChanged.invoke(eventsPanel, 100f, 100f);

        Object mapPanel = mapPanelGetter.invoke(eventsPanel);
        Object mapData = mapDataGetter.invoke(mapPanel);

        Map<Field, Boolean> map = new LinkedHashMap<>();
        for (Field f : ReflectionUtil.getFieldsByType(MAP_DATA_TYPE, boolean.class)) {
            f.setAccessible(true);
            boolean val = f.getBoolean(mapData);
            map.put(f, val);
        }
        return Collections.unmodifiableMap(map);
    }

    private static MethodHandle PARAGRAPHS_GETTER;

    @SuppressWarnings("unchecked")
    private static List<LabelAPI> getParagraphs(TextPanelAPI textPanel) throws Throwable {
        if (PARAGRAPHS_GETTER == null) {
            Field f = ReflectionUtil.getFirstFieldBySupertype(textPanel.getClass(), List.class);
            PARAGRAPHS_GETTER = MethodHandles.publicLookup().unreflectGetter(f);
        }

        return (List<LabelAPI>) PARAGRAPHS_GETTER.invoke(textPanel);
    }

    private static StarSystemAPI getStarSystem(String systemName) {
        String lowercased = systemName.toLowerCase(Locale.ROOT);
        if (lowercased.endsWith(" star system")) {
            systemName = systemName.substring(0, systemName.length() - " star system".length());
        } else if (lowercased.endsWith(" system") || lowercased.endsWith(" nebula")) {
            // "system" and "nebula" happened to be the same length :D
            systemName = systemName.substring(0, systemName.length() - " system".length());
        }

        // This searches for the "baseName", which is the same as getNameWithNoType()
        return Global.getSector().getStarSystem(systemName);
    }

    private MapInjector() {
        throw new AssertionError();
    }
}
