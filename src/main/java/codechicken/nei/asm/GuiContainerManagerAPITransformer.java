package codechicken.nei.asm;

import codechicken.lib.asm.ASMHelper;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;

import static org.objectweb.asm.Opcodes.POP;
import static org.objectweb.asm.Opcodes.PUTSTATIC;

public class GuiContainerManagerAPITransformer extends ClassVisitor {
    private final String tname;
    private boolean changed = false;

    public GuiContainerManagerAPITransformer(int api, ClassVisitor cv, String tname) {
        super(api, cv);
        this.tname = tname;
    }

    @Override
    public MethodVisitor visitMethod(int access, String mname, String mdesc, String signature, String[] exceptions) {
        return new MethodVisitor(api, super.visitMethod(access, mname, mdesc, signature, exceptions)) {
            @Override
            public void visitFieldInsn(int opcode, String owner, String name, String desc) {
                if (opcode == PUTSTATIC && "codechicken/nei/guihook/GuiContainerManager".equals(owner) && name.endsWith("Handlers") && desc.equals("Ljava/util/LinkedList;")) {
                    ASMHelper.logger.warn("Found suspicious PUTSTATIC {} in {}#{}{}, replacing with POP!", name, tname, mname, mdesc);
                    changed = true;
                    super.visitInsn(POP);
                } else {
                    super.visitFieldInsn(opcode, owner, name, desc);
                }
            }
        };
    }

    public boolean isChanged() {
        return changed;
    }
}
