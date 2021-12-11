package net.andylizi.starsector.missionminimap;

import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

public class TypeCollector {
    static void scanTypes() throws TypeCollectorException {
        try (JarFile jar = new JarFile(getCoreJar())) {
            Enumeration<JarEntry> en = jar.entries();
            while (en.hasMoreElements()) {
                JarEntry entry = en.nextElement();
                if (entry.isDirectory())
                    continue;
                String entryName = entry.getName();
                if (entryName.startsWith("com/fs/starfarer/ui/newui/")) {
                    tryNewUI(entryName, readFully(jar.getInputStream(entry)));
                }
            }
        } catch (IOException ex) {
            throw new TypeCollectorException("Failed to read Starsector core JAR", ex);
        }
    }

    static void verify() throws TypeCollectorException {
        if (NAME_INTERACTION_DIALOG_IMPL == null) {
            throw new TypeCollectorException("Failed to find InteractionDialogAPI implementation");
        }
        if (NAME_OPTION_PANEL_IMPL == null) {
            throw new TypeCollectorException("Failed to find OptionPanelAPI implementation");
        }
        if (FIELD_OPTION_PANEL_OPTIONS == null) {
            throw new TypeCollectorException("Failed to find 'options' field in OptionPanelAPI implementation");
        }
        if (NAME_OPTION_PANEL_OPTION == null || FIELD_OPTION_PANEL_OPTION_TEXT == null) {
            throw new TypeCollectorException("Failed to find 'text' field in Option record");
        }
        if (NAME_OPTION_LISTENER == null || METHOD_OPTION_SELECTED == null) {
            throw new TypeCollectorException("Failed to find 'optionSelected' method");
        }
        if (METHOD_OPTION_SELECTED.type.getInternalName().equals(NAME_OPTION_PANEL_OPTION)) {
            throw new TypeCollectorException("Option type is inconsistent between two matches: "
                    + METHOD_OPTION_SELECTED.type.getInternalName() + " and " + NAME_OPTION_PANEL_OPTION);
        }
    }

    public static final String NAME_INTERACTION_DIALOG_API = "com/fs/starfarer/api/campaign/InteractionDialogAPI";

    private static final byte[] NEEDLE_INTERACTION_DIALOG_API = NAME_INTERACTION_DIALOG_API
            .getBytes(StandardCharsets.US_ASCII);

    public static String NAME_INTERACTION_DIALOG_IMPL;

    public static final String NAME_OPTION_PANEL_API = "com/fs/starfarer/api/campaign/OptionPanelAPI";

    private static final byte[] NEEDLE_OPTION_PANEL_API = NAME_OPTION_PANEL_API
            .getBytes(StandardCharsets.US_ASCII);

    public static String NAME_OPTION_PANEL_IMPL;

    public static NameAndType FIELD_OPTION_PANEL_OPTIONS;

    public static String NAME_OPTION_PANEL_OPTION;

    public static NameAndType FIELD_OPTION_PANEL_OPTION_TEXT;

    public static final String NAME_DIALOG_PLUGIN_API = "com/fs/starfarer/api/campaign/InteractionDialogPlugin";

    private static final byte[] NEEDLE_DIALOG_PLUGIN_API = NAME_DIALOG_PLUGIN_API.getBytes(StandardCharsets.UTF_8);

    public static String NAME_OPTION_LISTENER;

    public static NameAndType METHOD_OPTION_SELECTED;

    private static void tryNewUI(String entryName, byte[] classBuf) {
        if (indexOf(classBuf, NEEDLE_INTERACTION_DIALOG_API, 512) != -1) {
            ClassReader cr = new ClassReader(classBuf);
            if (arrayContains(cr.getInterfaces(), NAME_INTERACTION_DIALOG_API)) {
                checkDuplicate(NAME_INTERACTION_DIALOG_IMPL, "InteractionDialog");
                NAME_INTERACTION_DIALOG_IMPL = cr.getClassName();
                Agent.log("Found InteractionDialogAPI implementation: " + NAME_INTERACTION_DIALOG_IMPL);
            }
        } else if (indexOf(classBuf, NEEDLE_OPTION_PANEL_API, 512) != -1) {
            ClassReader cr = new ClassReader(classBuf);
            if (arrayContains(cr.getInterfaces(), NAME_OPTION_PANEL_API)) {
                checkDuplicate(NAME_OPTION_PANEL_IMPL, "OptionPanel");
                NAME_OPTION_PANEL_IMPL = cr.getClassName();
                Agent.log("Found OptionPanelAPI implementation: " + NAME_OPTION_PANEL_IMPL);

                if (NAME_OPTION_PANEL_OPTION != null && !NAME_OPTION_PANEL_OPTION.startsWith(NAME_OPTION_PANEL_IMPL)) {
                    NAME_OPTION_PANEL_OPTION = null;
                }

                // find 'options' field
                cr.accept(new ClassVisitor(Opcodes.ASM4) {
                    @Override
                    public MethodVisitor visitMethod(int access, String name, String desc, String signature,
                            String[] exceptions) {
                        if ("getSavedOptionList".equals(name)) {
                            return new MethodVisitor(Opcodes.ASM4) {
                                public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                                    // first GETFIELD in getSavedOptionList()
                                    if (opcode == Opcodes.GETFIELD && FIELD_OPTION_PANEL_OPTIONS == null) {
                                        FIELD_OPTION_PANEL_OPTIONS = new NameAndType(name, Type.getType(descriptor));
                                    }
                                }
                            };
                        }
                        return null;
                    }
                }, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);

                if (FIELD_OPTION_PANEL_OPTIONS != null)
                    Agent.log("'options' field is " + FIELD_OPTION_PANEL_OPTIONS);
            }
        } else if (FIELD_OPTION_PANEL_OPTION_TEXT == null &&
                ((NAME_OPTION_PANEL_IMPL != null && entryName.startsWith(NAME_OPTION_PANEL_IMPL))
                        || entryName.contains("$"))) {
            // find 'text' field
            ClassReader cr = new ClassReader(classBuf);
            cr.accept(new ClassVisitor(Opcodes.ASM4) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String desc, String signature,
                        String[] exceptions) {
                    Type[] args;
                    if ("<init>".equals(name) && (args = Type.getArgumentTypes(desc)).length >= 4 &&
                            "java/lang/String".equals(args[0].getInternalName()) &&
                            "java/lang/Object".equals(args[1].getInternalName()) &&
                            "java/awt/Color".equals(args[2].getInternalName()) &&
                            "java/lang/String".equals(args[3].getInternalName())) {
                        return new MethodVisitor(Opcodes.ASM4) {
                            public void visitFieldInsn(int opcode, String owner, String name, String desc) {
                                // first PUTFIELD to a String
                                if (opcode == Opcodes.PUTFIELD && "Ljava/lang/String;".equals(desc)
                                        && FIELD_OPTION_PANEL_OPTION_TEXT == null) {
                                    FIELD_OPTION_PANEL_OPTION_TEXT = new NameAndType(name, Type.getType(desc));
                                }
                            }
                        };
                    }
                    return null;
                }
            }, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);

            if (FIELD_OPTION_PANEL_OPTION_TEXT != null) {
                NAME_OPTION_PANEL_OPTION = cr.getClassName();
                Agent.log("'text' field is potentially " + NAME_OPTION_PANEL_OPTION + "."
                        + FIELD_OPTION_PANEL_OPTION_TEXT);
            }
        } else if (METHOD_OPTION_SELECTED == null && indexOf(classBuf, NEEDLE_DIALOG_PLUGIN_API, 512) != -1) {
            ClassReader cr = new ClassReader(classBuf);
            cr.accept(new ClassVisitor(Opcodes.ASM4) {
                @Override
                public MethodVisitor visitMethod(int access, final String methodName, final String methodDesc,
                        String signature,
                        String[] exceptions) {
                    final Type type = Type.getMethodType(methodDesc);
                    if (METHOD_OPTION_SELECTED == null && Type.VOID_TYPE.equals(type.getReturnType())
                            && type.getArgumentTypes().length == 1) {
                        return new MethodVisitor(Opcodes.ASM4) {
                            public void visitMethodInsn(int opcode, String owner, String callName, String callDesc,
                                    boolean isInterface) {
                                // only one INVOKEINTERFACE
                                if (opcode == Opcodes.INVOKEINTERFACE) {
                                    if (METHOD_OPTION_SELECTED == null && NAME_DIALOG_PLUGIN_API.equals(owner)
                                            && "optionSelected".equals(callName)) {
                                        METHOD_OPTION_SELECTED = new NameAndType(methodName, type);
                                    } else {
                                        METHOD_OPTION_SELECTED = null;
                                    }
                                }
                            };
                        };
                    }
                    return null;
                }
            }, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);

            if (METHOD_OPTION_SELECTED != null) {
                NAME_OPTION_LISTENER = cr.getClassName();
                Agent.log("'optionSelected' method is " + NAME_OPTION_LISTENER
                        + METHOD_OPTION_SELECTED.name + METHOD_OPTION_SELECTED.type);
            }
        }
    }

    private static File getCoreJar() {
        URI url;
        try {
            Class<?> cls = Class.forName("com.fs.starfarer.StarfarerLauncher", false,
                    ClassLoader.getSystemClassLoader());
            url = cls.getProtectionDomain().getCodeSource().getLocation().toURI();
        } catch (ClassNotFoundException ex) {
            throw new TypeCollectorException("Unable to find Starsector main class, probably incompatiable version",
                    ex);
        } catch (NullPointerException | SecurityException | URISyntaxException ex) {
            throw new TypeCollectorException("Unable to locate Starsector core jar", ex);
        }

        File file = new File(url.getPath());
        if (!file.exists()) {
            throw new TypeCollectorException("Unable to locate Starsector core jar: " + url);
        }
        if (!file.isFile() || !file.getName().toLowerCase(Locale.ROOT).endsWith(".jar")) {
            throw new TypeCollectorException("Starsector is not running from a JAR file? " + url);
        }
        return file;
    }

    private static byte[] readFully(InputStream in) throws IOException {
        try {
            List<byte[]> bufs = null;
            byte[] result = null;
            int total = 0;
            int n;
            do {
                byte[] buf = new byte[8192];
                int nread = 0;

                while ((n = in.read(buf, nread, buf.length - nread)) > 0) {
                    nread += n;
                }

                if (nread > 0) {
                    total += nread;
                    if (result == null) {
                        result = buf;
                    } else {
                        if (bufs == null) {
                            bufs = new ArrayList<>();
                            bufs.add(result);
                        }
                        bufs.add(buf);
                    }
                }
            } while (n >= 0);

            if (bufs == null) {
                if (result == null) {
                    return new byte[0];
                }
                return result.length == total ? result : Arrays.copyOf(result, total);
            }

            result = new byte[total];
            int offset = 0;
            for (byte[] b : bufs) {
                int count = Math.min(b.length, total);
                System.arraycopy(b, 0, result, offset, count);
                offset += count;
                total -= count;
            }

            return result;
        } finally {
            in.close();
        }
    }

    private static int indexOf(byte[] haystack, byte[] needle, int limit) {
        outer: for (int i = 0; i < Math.min(haystack.length, limit) - needle.length + 1; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private static <T> boolean arrayContains(final T[] array, final T v) {
        // assume v != null
        for (final T e : array)
            if (v.equals(e))
                return true;
        return false;
    }

    private static <T> void checkDuplicate(T old, String name) {
        if (old != null)
            throw new TypeCollectorException("Found multiple matches for " + name);
    }
}
