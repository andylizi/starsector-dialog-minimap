package net.andylizi.starsector.missionminimap;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class CampaignEngineTransformer implements ClassFileTransformer {

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        if (!"com/fs/starfarer/campaign/CampaignEngine".equals(className)) return null;

        ClassReader cr = new ClassReader(classfileBuffer);
        ClassWriter cw = new ClassWriter(cr, 0);

        cr.accept(new ClassVisitor(Opcodes.ASM4, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                    String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                if ("reportShowInteractionDialog".equals(name)) {
                    return new MethodVisitor(Opcodes.ASM4, mv) {
                        public void visitCode() {
                            mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                            mv.visitVarInsn(Opcodes.ALOAD, 1);
                            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "com/fs/starfarer/api/campaign/InteractionDialogAPI", 
                                "getPlugin", "()Lcom/fs/starfarer/api/campaign/InteractionDialogPlugin;", true);
                            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/Object;)V", false);
                        }
                    };
                }
                return mv;
            }
        }, 0);
        
        byte[] data = cw.toByteArray();
        if (Agent.DEBUG_DUMP) {
            Agent.dumpClassFile(className, data);
        }
        return data;
    }
}
