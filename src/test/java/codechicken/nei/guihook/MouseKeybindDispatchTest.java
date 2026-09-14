package codechicken.nei.guihook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Collections;

import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import codechicken.lib.asm.ASMReader;
import cpw.mods.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper;

public class MouseKeybindDispatchTest {

    @Test
    void boundInputIsHandledBeforeTheScreenCanConsumeIt() throws Exception {
        GuiContainerManager manager = mock(GuiContainerManager.class);
        ScreenStub screen = createScreen(manager);
        when(manager.handleMouseKeybind()).thenReturn(true);

        screen.getClass().getMethod("handleMouseInput").invoke(screen);

        verify(manager).handleMouseKeybind();
        verify(manager, never()).handleMouseWheel();
        assertEquals(0, screen.mouseInputs);
    }

    @Test
    void ordinaryInputStillReachesTheScreenAndWheelHandler() throws Exception {
        GuiContainerManager manager = mock(GuiContainerManager.class);
        ScreenStub screen = createScreen(manager);

        screen.getClass().getMethod("handleMouseInput").invoke(screen);

        verify(manager).handleMouseKeybind();
        verify(manager).handleMouseWheel();
        assertEquals(1, screen.mouseInputs);
    }

    // Execute the same ASM block installed into GuiContainer, using a headless screen as its superclass.
    private static ScreenStub createScreen(GuiContainerManager manager) throws Exception {
        String className = "codechicken/nei/guihook/MouseKeybindHarness";
        String superName = Type.getInternalName(ScreenStub.class);
        String managerDescriptor = Type.getDescriptor(GuiContainerManager.class);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, className, null, superName, null);
        writer.visitField(Opcodes.ACC_PUBLIC, "manager", managerDescriptor, null, null).visitEnd();

        MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, superName, "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();

        InsnList instructions = readMouseInputBlock();
        for (AbstractInsnNode instruction : instructions.toArray()) {
            if (instruction instanceof FieldInsnNode field
                    && field.owner.equals("net/minecraft/client/gui/inventory/GuiContainer")) {
                field.owner = className;
            } else if (instruction instanceof MethodInsnNode method
                    && method.owner.equals("net/minecraft/client/gui/GuiScreen")) {
                        method.owner = superName;
                    }
        }
        MethodNode input = new MethodNode(Opcodes.ACC_PUBLIC, "handleMouseInput", "()V", null, null);
        input.instructions = instructions;
        input.accept(writer);
        writer.visitEnd();

        Class<?> screenClass = new HarnessLoader().define(writer.toByteArray());
        ScreenStub screen = (ScreenStub) screenClass.getConstructor().newInstance();
        screenClass.getField("manager").set(screen, manager);
        return screen;
    }

    private static InsnList readMouseInputBlock() throws Exception {
        // This class runs in the dedicated mouseKeybindAsmTest JVM: ObfMapping caches this fake
        // environment permanently, even after the Forge fields below have been restored.
        Field fields = FMLDeobfuscatingRemapper.class.getDeclaredField("rawFieldMaps");
        Field methods = FMLDeobfuscatingRemapper.class.getDeclaredField("rawMethodMaps");
        fields.setAccessible(true);
        methods.setAccessible(true);
        Object previousFields = fields.get(FMLDeobfuscatingRemapper.INSTANCE);
        Object previousMethods = methods.get(FMLDeobfuscatingRemapper.INSTANCE);
        LaunchClassLoader previousLoader = Launch.classLoader;
        try {
            fields.set(FMLDeobfuscatingRemapper.INSTANCE, Collections.emptyMap());
            methods.set(FMLDeobfuscatingRemapper.INSTANCE, Collections.emptyMap());
            Launch.classLoader = mock(LaunchClassLoader.class);
            return ASMReader.loadResource("/assets/nei/asm/blocks.asm").get("m_handleMouseInput").rawListCopy();
        } finally {
            fields.set(FMLDeobfuscatingRemapper.INSTANCE, previousFields);
            methods.set(FMLDeobfuscatingRemapper.INSTANCE, previousMethods);
            Launch.classLoader = previousLoader;
        }
    }

    public static class ScreenStub {

        int mouseInputs;

        public void func_146274_d() {
            mouseInputs++;
        }
    }

    private static class HarnessLoader extends ClassLoader {

        HarnessLoader() {
            super(MouseKeybindDispatchTest.class.getClassLoader());
        }

        Class<?> define(byte[] bytecode) {
            return defineClass(null, bytecode, 0, bytecode.length);
        }
    }
}
