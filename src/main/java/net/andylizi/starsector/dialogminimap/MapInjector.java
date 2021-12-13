package net.andylizi.starsector.dialogminimap;

import java.awt.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.*;
import java.util.*;
import java.util.List;

import com.fs.graphics.util.Fader;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;

import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.procgen.Constellation;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import com.fs.starfarer.api.util.Misc;
import net.andylizi.starsector.dialogminimap.access.*;
import org.apache.log4j.Logger;

public final class MapInjector {
    private static final Logger logger = Logger.getLogger(MapInjector.class);

    private static OptionPanelAccess acc_OptionPanel;
    private static OptionAccess acc_Option;
    private static Method m_optionSelected;

    public static void injectDialog(final InteractionDialogAPI dialog) throws ReflectiveOperationException {
        final OptionPanelAPI panel = dialog.getOptionPanel();
        if (acc_OptionPanel == null) acc_OptionPanel = new OptionPanelAccess(panel.getClass());

        final Object originalDelegate = acc_OptionPanel.getDelegate(panel);
        Class<?> originalDelegateImplType = originalDelegate.getClass();
        if (Proxy.isProxyClass(originalDelegateImplType)) {
            return; // already injected
        }

        if (m_optionSelected == null || acc_Option == null) {
            /* We need to determine which method in the delegate interface is optionSelected()
             * rather than `optionMousedOver()` or `optionConfirmed()`.
             *
             * Their names are obfuscated and their signatures are the same. They only way they differ
             * is in their behaviour: `optionConfirmed()` will call the corresponding method
             * in the dialog's `InteractionDialogPlugin`. So we temporarily replace it
             * with a dummy one and trigger the candidates successively, checking which one of those
             * invokes the method we want.
             */

            class OptionDelegateProbe implements InteractionDialogPlugin {
                boolean triggered = false;

                @Override
                public void init(InteractionDialogAPI dialog) {}

                @Override
                public void optionSelected(String optionText, Object optionData) {
                    triggered = true;
                }

                @Override
                public void optionMousedOver(String optionText, Object optionData) {}

                @Override
                public void advance(float amount) {
                }

                @Override
                public void backFromEngagement(EngagementResultAPI battleResult) {}

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
            Field dialogPluginField = ReflectionUtil.getFirstFieldByType(
                dialog.getClass(), InteractionDialogPlugin.class);
            dialogPluginField.setAccessible(true);
            InteractionDialogPlugin originalPlugin = (InteractionDialogPlugin) dialogPluginField.get(dialog);
            try {
                dialogPluginField.set(dialog, probe);

                boolean isTheFirstOne = true;
                for (Method m : acc_OptionPanel.delegateType().getMethods()) {
                    Class<?>[] paramTypes = m.getParameterTypes();
                    if (paramTypes.length != 1) continue;
                    Class<?> paramType = paramTypes[0];
                    Object dummyArg = ReflectionUtil.instantiateDefault(paramType);
                    m.invoke(originalDelegate, dummyArg);

                    if (probe.triggered) {
                        m_optionSelected = m;
                        acc_Option = new OptionAccess(paramType);
                        break;
                    } else {
                        isTheFirstOne = false;
                    }
                }

                // We may have triggered showOptionConfirmDialog() while probing. Close that.
                // No need to do this if we succeed at first try.
                if (!isTheFirstOne) {
                    try {
                        removeOptionConfirmDialog(dialog);
                    } catch (Throwable t) {
                        // It's annoying having to close it manually, but ultimately not critical. Log and continue
                        logger.warn("Failed to close the confirm dialog", t);
                    }
                }

                if (m_optionSelected == null) {
                    throw new RuntimeException(
                        "Unable to determine which method in " + acc_OptionPanel.delegateType() +
                        "is optionSelected()");
                }
            } finally {
                dialogPluginField.set(dialog, originalPlugin);
            }
        }

        final OptionAccess access_Option = acc_Option;
        class DelegateProxyHandler implements InvocationHandler {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                Object result = method.invoke(originalDelegate, args);
                if (m_optionSelected.equals(method)) {
                    // The player just selected an option, and the list have been updated.
                    removeSectorMapFromDialog(dialog);
                    for (Object option : panel.getSavedOptionList()) {
                        String text = access_Option.getText(option);
                        // One of the options start with "Accept" — it's probably asking for a mission.
                        if (text.startsWith("Accept")) {
                            searchMissionSystem(dialog);
                            break;
                        }
                    }
                }
                return result;
            }
        }

        Object newDelegate = Proxy.newProxyInstance(originalDelegateImplType.getClassLoader(),
            new Class<?>[] { acc_OptionPanel.delegateType() }, new DelegateProxyHandler());
        acc_OptionPanel.setDelegate(panel, newDelegate);
    }

    private static TextPanelAccess acc_TextPanel;

    public static void searchMissionSystem(InteractionDialogAPI dialog) throws ReflectiveOperationException {
        // From BaseHubMission.updateInteractionData()
        Map<String, MemoryAPI> memory = dialog.getPlugin().getMemoryMap();
        MemoryAPI interactionMemory = memory.get(MemKeys.LOCAL);
        if (interactionMemory == null) {
            if (dialog.getInteractionTarget().getActivePerson() != null) {
                interactionMemory = dialog.getInteractionTarget().getActivePerson().getMemoryWithoutUpdate();
            } else {
                interactionMemory = dialog.getInteractionTarget().getMemoryWithoutUpdate();
            }
        }

        // Find all keys that look like $XX_systemName.
        // The naming is just a convention, but I don't know of any exceptions.
        List<String> systemNames = new ArrayList<>(1);
        for (String key : interactionMemory.getKeys())
            if (key.endsWith("_systemName"))
                systemNames.add(interactionMemory.getString(key));
        if (systemNames.isEmpty()) {
            logger.info("No systemName for the mission is detected");
            return;
        }

        // Find the longest match
        Collections.sort(systemNames, new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                return Integer.compare(o2.length(), o1.length());
            }
        });

        TextPanelAPI textPanel = dialog.getTextPanel();
        if (acc_TextPanel == null) acc_TextPanel = new TextPanelAccess(textPanel.getClass());
        List<LabelAPI> paragraphs = acc_TextPanel.getParagraphs(textPanel);

        // Use last three paragraphs (explanation + selected option + explanation).
        // This is because some missions put the target name before a "Continue"
        ListIterator<LabelAPI> it = paragraphs.listIterator(paragraphs.size() - 3);
        StringBuilder description = new StringBuilder();
        while (it.hasNext()) description.append(it.next().getText());

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
                showMinimap(dialog, target);
            }
        } else {
            StarSystemAPI system = getStarSystem(systemName);
            if (system != null) {
                logger.info("Mission target found: " + system.getNameWithLowercaseTypeShort());
                showMinimap(dialog, system.getCenter());
            } else {
                logger.warn("Unrecognized star system for the mission: " + systemName);
            }
        }
    }

    private static EventsPanelAccess acc_EventsPanel;
    private static SectorMapAccess acc_SectorMap;
    private static MapParamsAccess acc_MapParams;
    private static MapFilterDataAccess acc_MapFilterData;
    private static Map<MethodHandle, Boolean> mapParamsFlags;
    private static MapDisplayAccess acc_mapDisplay;

    public static void showMinimap(InteractionDialogAPI dialog, SectorEntityToken target)
        throws ReflectiveOperationException {
        if (acc_EventsPanel == null) acc_EventsPanel = new EventsPanelAccess();
        if (acc_SectorMap == null) acc_SectorMap = new SectorMapAccess(acc_EventsPanel.sectorMapType());
        if (acc_MapParams == null)
            acc_MapParams = new MapParamsAccess(acc_SectorMap.mapParamsType(), acc_SectorMap.mapFilterDataType());
        if (acc_MapFilterData == null) acc_MapFilterData = new MapFilterDataAccess(acc_SectorMap.mapFilterDataType());
        if (mapParamsFlags == null) mapParamsFlags = getIntelMapParams(acc_EventsPanel, acc_SectorMap);

        Object filterData = acc_MapFilterData.newInstance(true);
        acc_MapFilterData.setFactions(filterData, false); // Also show names for uninhabited stars

        Object mapParams = acc_MapParams.newInstance();
        acc_MapParams.setEntity(mapParams, target);
        acc_MapParams.setFilterData(mapParams, filterData);
        acc_MapParams.setBorderColor(mapParams, Misc.getDarkPlayerColor());
        acc_MapParams.setLocation(mapParams, Global.getSector().getHyperspace());
        acc_MapParams.trySetZoom(mapParams, 2.4f);

        try {
            for (Map.Entry<MethodHandle, Boolean> entry : mapParamsFlags.entrySet()) {
                // MethodHandle.invoke() is @PolymorphicSignature, so it must be unboxed first
                boolean flag = entry.getValue();
                entry.getKey().invoke(mapParams, flag);
            }
        } catch (RuntimeException | Error ex) {
            throw ex;
        } catch (Throwable t) {
            throw new AssertionError("unreachable", t);
        }

        float width = ((UIComponentAPI) dialog.getTextPanel()).getPosition().getWidth();
        SectorMapAPI sectorMap = acc_SectorMap.newInstance(mapParams, width, 300f);
        Object mapDisplay = acc_SectorMap.getMap(sectorMap);
        updateIntelToMap(sectorMap, mapDisplay, width * 0.6f);

        // Show the name of the target star system.
        StarSystemAPI system = target.getStarSystem();
        if (system != null) {
            Set<StarSystemAPI> starSystems = acc_MapParams.getStarSystems(mapParams);
            if (starSystems == null) acc_MapParams.setStarSystems(mapParams, starSystems = new HashSet<>());
            starSystems.add(system);
        }

        addSectorMapToDialog(dialog, (UIComponentAPI) sectorMap);
        acc_SectorMap.centerOn(sectorMap, target.getLocationInHyperspace());

        try {
            if (system == null) return;
            PlanetAPI star = system.getStar();
            Color pingColor = star == null ? Misc.getDarkPlayerColor() : star.getSpec().getIconColor();
            pingColor = new Color(pingColor.getRed(), pingColor.getGreen(), pingColor.getBlue(), 110);

            if (acc_mapDisplay == null) acc_mapDisplay = new MapDisplayAccess(((UIPanelAPI) mapDisplay).getClass());
            acc_mapDisplay.addPing(mapDisplay, system.getHyperspaceAnchor(), pingColor, 360f, 1);
        } catch (Throwable t) {
            logger.warn("Failed to ping the target on the map", t);
        }
    }

    private static IntelDataAccess acc_IntelData;

    // This method clears the existing intel data and the systems/constellations filter
    private static void updateIntelToMap(SectorMapAPI sectorMap, Object mapDisplay, float tooltipWidth)
        throws ReflectiveOperationException {
        if (acc_SectorMap == null) acc_SectorMap = new SectorMapAccess(sectorMap.getClass());
        if (acc_MapParams == null)
            acc_MapParams = new MapParamsAccess(acc_SectorMap.mapParamsType(), acc_SectorMap.mapFilterDataType());
        if (acc_mapDisplay == null) acc_mapDisplay = new MapDisplayAccess(((UIPanelAPI) mapDisplay).getClass());
        if (acc_IntelData == null) acc_IntelData = new IntelDataAccess(acc_mapDisplay.intelDataType());
        acc_mapDisplay.getIntelData(mapDisplay).clear();

        Object mapParams = acc_SectorMap.getParams(sectorMap);
        Set<StarSystemAPI> starSystems = new HashSet<>();
        Set<Constellation> constellations = new HashSet<>();
        acc_MapParams.setStarSystems(mapParams, starSystems);
        acc_MapParams.setConstellations(mapParams, constellations);

        for (IntelInfoPlugin plugin : Global.getSector().getIntelManager().getIntel()) {
            if (plugin.isHidden()) continue;

            // Only accepted missions
            Set<String> tags = plugin.getIntelTags(sectorMap);
            if (!tags.contains(Tags.INTEL_ACCEPTED)) continue;

            SectorEntityToken entity = plugin.getMapLocation(sectorMap);
            if (entity == null || (entity.getStarSystem() != null &&
                                   entity.getStarSystem().getDoNotShowIntelFromThisLocationOnMap() != null &&
                                   entity.getStarSystem().getDoNotShowIntelFromThisLocationOnMap())) continue;

            CommMessageAPI commMessage = Global.getFactory().createMessage();
            commMessage.setSmallIcon(plugin.getIcon());

            Object intelData = acc_IntelData.newInstance();
            acc_IntelData.setCommMessage(intelData, commMessage);
            acc_IntelData.setIntelPlugin(intelData, plugin);
            acc_IntelData.setEntity(intelData, entity);
            acc_IntelData.setTooltipWidth(intelData, tooltipWidth);
            acc_mapDisplay.addIntelData(mapDisplay, intelData);

            // Only show names for these stars
            if (Misc.isHyperspaceAnchor(entity)) {
                StarSystemAPI starSystem = Misc.getStarSystemForAnchor(entity);
                starSystems.add(starSystem);
            }
            if (entity.getStarSystem() != null) {
                starSystems.add(entity.getStarSystem());
            } else {
                Object constellation = entity.getCustomData().get("constellation");
                if (constellation instanceof Constellation) constellations.add((Constellation) constellation);
            }
        }
    }

    private static UIComponentAccess acc_UIComponent;

    private static Fader getFader(UIComponentAPI component) throws ReflectiveOperationException {
        if (acc_UIComponent == null) acc_UIComponent = new UIComponentAccess(component.getClass());
        return acc_UIComponent.getFader(component);
    }

    private static void addSectorMapToDialog(InteractionDialogAPI dialog, UIComponentAPI mapPanel) {
        ((UIPanelAPI) dialog).addComponent(mapPanel)
            .rightOfBottom((UIComponentAPI) dialog.getTextPanel(), 27.0f) // trying to align with the visual panel XD
            .setYAlignOffset(-150f);

        try {
            Fader fader = getFader(mapPanel);
            fader.setDuration(0.4f, 0.4f);
            fader.forceOut();
            fader.fadeIn();
        } catch (Throwable t) {
            // It's fine
            logger.warn("Failed to call Fader", t);
        }
    }

    private static UIPanelAccess acc_UIPanel;

    @SuppressWarnings({ "ObjectEquality", "EqualsBetweenInconvertibleTypes" })
    private static void removeSectorMapFromDialog(InteractionDialogAPI dialog) throws ReflectiveOperationException {
        UIPanelAPI dialogPanel = (UIPanelAPI) dialog;
        if (acc_UIPanel == null) acc_UIPanel = new UIPanelAccess(dialogPanel.getClass());
        if (acc_SectorMap == null) {
            if (acc_EventsPanel == null) acc_EventsPanel = new EventsPanelAccess();
            acc_SectorMap = new SectorMapAccess(acc_EventsPanel.sectorMapType());
        }

        List<UIComponentAPI> children = acc_UIPanel.getChildrenNonCopy(dialogPanel);
        List<UIComponentAPI> removeList = null;
        for (UIComponentAPI child : children) {
            if (child != null && child.getClass() == acc_SectorMap.sectorMapType()) {
                if (removeList == null) removeList = new ArrayList<>(1);
                removeList.add(child);
            }
        }

        if (removeList != null)
            for (UIComponentAPI toRemove : removeList)
                acc_UIPanel.remove(dialogPanel, toRemove);
    }

    private static BaseDialogAccess acc_BaseDialog;
    private static InteractionDialogAccess acc_InteractionDialog;

    private static void removeOptionConfirmDialog(InteractionDialogAPI dialog) throws Throwable {
        UIPanelAPI dialogPanel = (UIPanelAPI) dialog;
        if (acc_BaseDialog == null) acc_BaseDialog = new BaseDialogAccess(dialogPanel.getClass());
        if (acc_UIPanel == null) acc_UIPanel = new UIPanelAccess(dialogPanel.getClass());

        List<UIComponentAPI> children = acc_UIPanel.getChildrenNonCopy(dialogPanel);
        List<UIComponentAPI> subDialogs = new ArrayList<>(1);
        for (UIComponentAPI child : children)
            if (acc_BaseDialog.baseDialogType().isAssignableFrom(child.getClass())) subDialogs.add(child);

        if (!subDialogs.isEmpty()) {
            for (UIComponentAPI subDialog : subDialogs) {
                UIComponentAPI interceptor = acc_BaseDialog.getInterceptor((UIPanelAPI) subDialog);
                acc_UIPanel.remove(dialogPanel, subDialog);
                acc_UIPanel.remove(dialogPanel, interceptor);
            }

            if (acc_InteractionDialog == null)
                acc_InteractionDialog = new InteractionDialogAccess(dialog.getClass());
            acc_InteractionDialog.setPickerType(dialog, null);
        }
    }

    /* MapParams have a bunch of obfuscated boolean fields. Without other indicators, we
     * can't tell what each one of them means programmatically. Luckily, however, the map
     * configuration we want is the same as in the EventsPanel (the Intel screen in game).
     *
     * So, we construct one of those here, make it initialize the map panel,
     * then extract the flags it set for our own use.
     */
    private static Map<MethodHandle, Boolean> getIntelMapParams(
        EventsPanelAccess acc_EventsPanel,
        SectorMapAccess acc_SectorMap
    ) throws ReflectiveOperationException {
        Object eventsPanel = acc_EventsPanel.newInstanceDefault();
        acc_EventsPanel.sizeChanged(eventsPanel, 100f, 100f); // initialize the map panel

        SectorMapAPI sectorMap = acc_EventsPanel.getSectorMap(eventsPanel);
        Object mapParams = acc_SectorMap.getParams(sectorMap);

        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        Map<MethodHandle, Boolean> map = new LinkedHashMap<>();
        for (Field f : ReflectionUtil.getFieldsByType(acc_SectorMap.mapParamsType(), boolean.class)) {
            ReflectionUtil.trySetAccessible(f);
            boolean val = f.getBoolean(mapParams);
            map.put(lookup.unreflectSetter(f), val);
        }
        return Collections.unmodifiableMap(map);
    }

    private static StarSystemAPI getStarSystem(String systemName) {
        String lowercase = systemName.toLowerCase(Locale.ROOT);
        if (lowercase.endsWith(" star system")) {
            systemName = systemName.substring(0, systemName.length() - " star system".length());
        } else if (lowercase.endsWith(" system") || lowercase.endsWith(" nebula")) {
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
