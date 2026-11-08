package codechicken.nei;

import net.minecraft.client.renderer.GLAllocation;

import org.lwjgl.opengl.GL11;

/**
 * The display list of the mob spawn overlay quads, and the scan version and the style that the list was compiled from.
 */
public final class MobSpawnDisplayList {

    private int listId = -1;
    private int modesVersion;
    private MobSpawnOverlayStyle style;

    public boolean isCompiled(int currentModesVersion, MobSpawnOverlayStyle currentStyle) {
        return listId >= 0 && modesVersion == currentModesVersion && style == currentStyle;
    }

    public void beginCompile(int newModesVersion, MobSpawnOverlayStyle newStyle) {
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
