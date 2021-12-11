package net.andylizi.starsector.missionminimap;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.instrument.Instrumentation;

public final class Agent {
    public static String LOG_PREFIX = "[Mission Minimap] ";

    public static boolean DEBUG_DUMP = Boolean.getBoolean("net.andylizi.starsector.missionminimap.dump");

    public static void premain(String agentArgs, Instrumentation inst) {
        // https://fractalsoftworks.com/forum/index.php?topic=19149.msg313709#msg313709
        System.setProperty("java.util.Arrays.useLegacyMergeSort", "true");

        try {
            TypeCollector.scanTypes();
            TypeCollector.verify();

            inst.addTransformer(new CampaignEngineTransformer());
            inst.addTransformer(new OptionPanelTransformer());

            Class.forName(TypeCollector.NAME_OPTION_PANEL_IMPL.replace('/', '.'));
        } catch (Throwable ex) {
            logError("", ex);
            System.exit(1);
        }
    }

    public static void log(String msg) {
        System.out.println(LOG_PREFIX.concat(msg));
    }

    public static void logError(String msg, Throwable t) {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        try (PrintWriter writer = new PrintWriter(bout, false)) {
            t.printStackTrace(writer);
        }
        System.err.print(LOG_PREFIX + msg + ": " + new String(bout.toByteArray()));
    }

    public static void dumpClassFile(String name, byte[] data) {
        try {
            name = name.replace('/', '.');
            File file = new File("dump", name.concat(".class"));
            // Windows MAX_PATH is 260 characters
            if (file.getAbsolutePath().length() > 250) {
                name = Integer.toHexString(name.hashCode()).concat(".class");
                log("Transform " + name + " (file name too long, saved to " + name + ")");
                file = new File("dump", name);
            } else {
                log("Transform " + name);
            }

            file.getParentFile().mkdirs();
            try (FileOutputStream fout = new FileOutputStream(file)) {
                fout.write(data);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Agent() {
        throw new AssertionError();
    }
}
