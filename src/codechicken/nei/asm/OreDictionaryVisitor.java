package codechicken.nei.asm;

import static org.objectweb.asm.Opcodes.*;

import codechicken.nei.NEIClientConfig;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

/**
 * Code without this patch
 * <pre>
 public class OreDictionary {
	 public static int getOreID(String name) {
		 Integer val = nameToId.get(name);
		 if (val == null)
		 {
			 idToName.add(name);
			 val = idToName.size() - 1; //0 indexed
			 nameToId.put(name, val);
			 idToStack.add(new ArrayList<ItemStack>());
			 idToStackUn.add(new UnmodifiableArrayList(idToStack.get(val)));
		 }
		 return val;
	 }
 }
 </pre>
 Code with this patch
 <pre>
 public class OreDictionary {
	 private static final Object emptyEntryCreationLock = new Object();
	 public static int getOreID(String name) {
		 Integer val = nameToId.get(name);
		 if (val == null)
		 {
			 synchronized (emptyEntryCreationLock) {
 				 if ((val = nameToId.get(name)) != null)
 					 return val;
				 idToName.add(name);
				 val = idToName.size() - 1; //0 indexed
				 nameToId.put(name, val);
				 idToStack.add(new ArrayList<ItemStack>());
				 idToStackUn.add(new UnmodifiableArrayList(idToStack.get(val)));
			 }
		 }
		 return val;
	 }
 }
 * </pre>
 */
public class OreDictionaryVisitor extends ClassVisitor {
	private static class GetOreIDVisitor extends MethodVisitor {
		Label jumpEnd;
		Label catchStart = new Label();
		Label exitStart = new Label();
		public GetOreIDVisitor(int api, MethodVisitor mv) {
			super(api, mv);
		}

		@Override
		public void visitJumpInsn(int opcode, Label label) {
			super.visitJumpInsn(opcode, label);
			NEIClientConfig.logger.trace("Adding getOreID MONITERENTER");
			jumpEnd = label;
			mv.visitFieldInsn(GETSTATIC, "net/minecraftforge/oredict/OreDictionary", "emptyEntryCreationLock", "Ljava/lang/Object;");
			mv.visitInsn(MONITORENTER);
			mv.visitLabel(catchStart);
			mv.visitFieldInsn(GETSTATIC, "net/minecraftforge/oredict/OreDictionary", "nameToId", "Ljava/util/Map;");
			mv.visitVarInsn(ALOAD, 0);
			mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "get", "(Ljava/lang/Object;)Ljava/lang/Object;", true);
			mv.visitTypeInsn(CHECKCAST, "java/lang/Integer");
			mv.visitVarInsn(ASTORE, 1);
			mv.visitVarInsn(ALOAD, 1);
			mv.visitJumpInsn(IFNONNULL, exitStart);
		}

		@Override
		public void visitLabel(Label label) {
			if (label.equals(jumpEnd)) {
				NEIClientConfig.logger.trace("Adding getOreID MONITEREXIT");
				// normal exit
				mv.visitLabel(exitStart);
				mv.visitFieldInsn(GETSTATIC, "net/minecraftforge/oredict/OreDictionary", "emptyEntryCreationLock", "Ljava/lang/Object;");
				mv.visitInsn(MONITOREXIT);
				// not normal
				mv.visitJumpInsn(GOTO, jumpEnd);
				Label catchBegin = new Label();
				mv.visitLabel(catchBegin);
				mv.visitFieldInsn(GETSTATIC, "net/minecraftforge/oredict/OreDictionary", "emptyEntryCreationLock", "Ljava/lang/Object;");
				mv.visitInsn(MONITOREXIT);
				mv.visitInsn(ATHROW);
				// try-catch block metadata
				mv.visitTryCatchBlock(catchStart, catchBegin, catchBegin, null);
			}
			super.visitLabel(label);
		}

		@Override
		public void visitEnd() {
			super.visitEnd();
		}
	}

	private static class ClassInitializerVisitor extends MethodVisitor{
		public ClassInitializerVisitor(int api, MethodVisitor mv) {
			super(api, mv);
		}

		@Override
		public void visitCode() {
			super.visitCode();
			NEIClientConfig.logger.trace("Adding emptyEntryCreationLock initializer");
			mv.visitTypeInsn(NEW, "java/lang/Object");
			mv.visitInsn(DUP);
			mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
			mv.visitFieldInsn(PUTSTATIC, "net/minecraftforge/oredict/OreDictionary","emptyEntryCreationLock", "Ljava/lang/Object;");
		}
		@Override
		public void visitEnd() {
			super.visitEnd();
		}
	}

	public OreDictionaryVisitor(int api, ClassVisitor cv) {
		super(api, cv);
	}

	@Override
	public void visitEnd() {
		NEIClientConfig.logger.debug("Adding emptyEntryCreationLock");
		cv.visitField(ACC_PRIVATE | ACC_STATIC | ACC_FINAL, "emptyEntryCreationLock", "Ljava/lang/Object;", null, null);
		super.visitEnd();
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
		if (name.equals("getOreID") && desc.equals("(Ljava/lang/String;)I")) {
			NEIClientConfig.logger.debug("Patching getOreID");
			return new GetOreIDVisitor(api, super.visitMethod(access, name, desc, signature, exceptions));
		} else if (name.equals("<clinit>")) {
			// some classes don't have clinit. not this one.
			NEIClientConfig.logger.debug("Patching clinit");
			return new ClassInitializerVisitor(api, super.visitMethod(access, name, desc, signature, exceptions));
		}
		return super.visitMethod(access, name, desc, signature, exceptions);
	}
}
