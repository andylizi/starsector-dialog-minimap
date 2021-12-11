package net.andylizi.starsector.missionminimap;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;

public final class Injector {
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
        OPTION_DELEGATE_SETTER = null;
        OPTION_DELEGATE_SETTER = null;
        OPTION_SELECTED_METHOD = null;
        OPTION_TYPE = null;
    }

    public static void injectDialog(InteractionDialogAPI dialog) {
        try {
            Class<?> dialogType = dialog.getClass();
            OptionPanelAPI panel = dialog.getOptionPanel();
            Class<?> panelType = panel.getClass();

            // There should only be one implementation, but just in case
            if ((DIALOG_IMPL_TYPE != null && !DIALOG_IMPL_TYPE.equals(dialogType)) ||
                    (OPTION_PANEL_IMPL_TYPE != null && !OPTION_PANEL_IMPL_TYPE.equals(panelType))) {
                clearCache();
            }

            DIALOG_IMPL_TYPE = dialogType;
            OPTION_PANEL_IMPL_TYPE = panelType;

            if (OPTION_DELEGATE_GETTER == null || OPTION_DELEGATE_SETTER == null) {
                Method getDelegate = ReflectionUtil.getDelcaringMethodByName(panelType, "getDelegate");
                Class<?> delegateInterface = getDelegate.getReturnType();

                if (!delegateInterface.isInterface()) {
                    throw new RuntimeException(
                            "OptionPanelAPI impl getDelegate() return type isn't a interface: "
                                    + delegateInterface);
                }

                Field delegateField = ReflectionUtil.getDeclaringFieldByType(panelType, delegateInterface);
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

            if (OPTION_SELECTED_METHOD == null || OPTION_TYPE == null) {
                OptionDelegateProbe probe = new OptionDelegateProbe();
                Field dialogPluginField = ReflectionUtil.getDeclaringFieldByType(dialogType,
                        InteractionDialogPlugin.class);
                dialogPluginField.setAccessible(true);
                Object originalPlugin = dialogPluginField.get(dialog);
                try {
                    dialogPluginField.set(dialog, probe);

                    for (Method m : delegateInterface.getMethods()) {
                        if (m.getParameterCount() != 1)
                            continue;
                        Class<?> paramType = m.getParameterTypes()[0];
                        Object dummyArg = ReflectionUtil.instantiateDefault(paramType);
                        m.invoke(originalDelegate, dummyArg);

                        if (probe.triggered) {
                            OPTION_SELECTED_METHOD = m;
                            OPTION_TYPE = paramType;
                            break;
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
                                String text = (String) optionTextGetter.invoke(args[0]);
                                System.out.println("Option clicked: " + text);
                            }
                            return result;
                        }
                    });
            OPTION_DELEGATE_SETTER.invoke(panel, newDelegate);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private static class OptionDelegateProbe implements InteractionDialogPlugin {
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

    private Injector() {
        throw new AssertionError();
    }
}
