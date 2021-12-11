package net.andylizi.starsector.missionminimap;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class OptionPanelTransformer implements ClassFileTransformer {
    public static final NameAndType METHOD_HAS_OPTION_TEXT = new NameAndType("hasOptionText",
            Type.getMethodType(Type.BOOLEAN_TYPE, Type.getObjectType("java/lang/String")));

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        if (!TypeCollector.NAME_OPTION_PANEL_IMPL.equals(className))
            return null;

        try {
            ClassReader cr = new ClassReader(classfileBuffer);
            ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
            cr.accept(cw, 0);

            MethodVisitor mv = cw.visitMethod(Opcodes.ACC_SYNTHETIC, METHOD_HAS_OPTION_TEXT.name,
                    METHOD_HAS_OPTION_TEXT.type.getDescriptor(), null, null);
            // Iterator it = this.options.values().iterator();
            // while (it.hasNext()) if (text.equals(it.next().text)) return true;
            // return false;
            mv.visitCode();
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitFieldInsn(Opcodes.GETFIELD, TypeCollector.NAME_OPTION_PANEL_IMPL,
                    TypeCollector.FIELD_OPTION_PANEL_OPTIONS.name,
                    TypeCollector.FIELD_OPTION_PANEL_OPTIONS.type.getDescriptor());
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Map", "values", "()Ljava/util/Collection;", true);
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Collection", "iterator", "()Ljava/util/Iterator;",
                    true);
            mv.visitVarInsn(Opcodes.ASTORE, 2); // it
            Label loopStart = new Label();
            Label end = new Label();
            mv.visitLabel(loopStart);
            mv.visitVarInsn(Opcodes.ALOAD, 2); // it
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Iterator", "hasNext", "()Z", true);
            mv.visitJumpInsn(Opcodes.IFEQ, end);
            mv.visitVarInsn(Opcodes.ALOAD, 1); // text
            mv.visitVarInsn(Opcodes.ALOAD, 2); // it
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Iterator", "next", "()Ljava/lang/Object;", true);
            mv.visitTypeInsn(Opcodes.CHECKCAST, TypeCollector.NAME_OPTION_PANEL_OPTION);
            mv.visitFieldInsn(Opcodes.GETFIELD, TypeCollector.NAME_OPTION_PANEL_OPTION,
                    TypeCollector.FIELD_OPTION_PANEL_OPTION_TEXT.name,
                    TypeCollector.FIELD_OPTION_PANEL_OPTION_TEXT.type.getDescriptor());
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Object", "equals", "(Ljava/lang/Object;)Z", false);
            mv.visitJumpInsn(Opcodes.IFEQ, loopStart);
            mv.visitInsn(Opcodes.ICONST_1);
            mv.visitInsn(Opcodes.IRETURN);
            mv.visitLabel(end);
            mv.visitInsn(Opcodes.ICONST_0);
            mv.visitInsn(Opcodes.IRETURN);
            mv.visitMaxs(4, 3);
            mv.visitEnd();

            byte[] data = cw.toByteArray();
            if (Agent.DEBUG_DUMP) {
                Agent.dumpClassFile(className, data);
            }
            return data;
        } catch (Throwable t) {
            Agent.logError("Failed to transform " + className, t);
            System.exit(1);
            return null;
        }
    }
}
