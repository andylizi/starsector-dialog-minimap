package net.andylizi.starsector.missionminimap;

import java.io.Serializable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URL;
import java.net.URLClassLoader;

import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;

public class DialogListener extends BaseCampaignEventListener implements Serializable {
    private static final MethodHandle injector;

    static {
        MethodHandle tmp = null;
        try {
            // Bypass reflection restriction
            ClassLoader cl = PluginMain.class.getClassLoader();
            while (cl != null && !(cl instanceof URLClassLoader)) cl = cl.getParent();
            if (cl == null) throw new RuntimeException("Unable to find URLClassLoader");
            URL[] urls = ((URLClassLoader) cl).getURLs();

            @SuppressWarnings("resource")
            Class<?> cls = new URLClassLoader(urls, ClassLoader.getSystemClassLoader())
                .loadClass(PluginMain.class.getPackage().getName() + ".Injector");

            tmp = MethodHandles.lookup().findStatic(cls, "injectDialog",
                    MethodType.methodType(void.class, InteractionDialogAPI.class));
        } catch (RuntimeException | Error ex) {
            throw ex;
        } catch (Throwable t) {
            throw new ExceptionInInitializerError(t);
        }
        injector = tmp;
    }

    DialogListener() {
        super(false);
    }

    @Override
    public void reportShownInteractionDialog(InteractionDialogAPI dialog) {
        if (injector != null)
            try {
                injector.invokeExact(dialog);
            } catch (RuntimeException | Error ex) {
                throw ex;
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
    }
}
