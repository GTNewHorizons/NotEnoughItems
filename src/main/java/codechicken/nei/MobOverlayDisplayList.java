package codechicken.nei;

import net.minecraft.client.renderer.GLAllocation;

import org.lwjgl.opengl.GL11;

/** The mob spawn overlay quads, compiled into a GL display list. */
public final class MobOverlayDisplayList {

    private int listId = -1;
    private int modesVersion;
    private MobOverlayStyle style;

    public boolean isCompiled(int currentModesVersion, MobOverlayStyle currentStyle) {
        return listId >= 0 && modesVersion == currentModesVersion && style == currentStyle;
    }

    public void beginCompile(int newModesVersion, MobOverlayStyle newStyle) {
        if (listId < 0) listId = GLAllocation.generateDisplayLists(1);
        modesVersion = newModesVersion;
        style = newStyle;
        GL11.glNewList(listId, GL11.GL_COMPILE);
    }

    public void endCompile() {
        GL11.glEndList();
    }

    public void draw() {
        GL11.glCallList(listId);
    }

    public void delete() {
        if (listId < 0) return;

        GLAllocation.deleteDisplayLists(listId);
        listId = -1;
    }
}
