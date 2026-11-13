package codechicken.nei.moboverlay;

import net.minecraft.client.renderer.GLAllocation;

import org.lwjgl.opengl.GL11;

/** The mob spawn overlay quads, compiled into a GL display list. */
public final class MobOverlayDisplayList {

    private int listId = -1;
    private int modesVersion;
    private MobOverlayStyle style;
    private int minX;
    private int minY;
    private int minZ;
    private long compiledTicks;

    /**
     * Compiles after a {@link MobOverlayScan#getModesVersion} change are debounced to one per {@code MIN_COMPILE_TICKS}
     * ticks, because the version changes several times per second while the player moves fast, and a compile takes a
     * millisecond or more.
     */
    public boolean isCompileDue(int currentModesVersion, MobOverlayStyle currentStyle, long worldTicks) {
        final int MIN_COMPILE_TICKS = 5;
        if (listId < 0 || style != currentStyle) return true;

        // after a dimension change or a server switch, `worldTicks` can be lower than `compiledTicks`
        final boolean isWaitOver = worldTicks - compiledTicks >= MIN_COMPILE_TICKS || worldTicks < compiledTicks;
        return modesVersion != currentModesVersion && isWaitOver;
    }

    /**
     * Quad positions are relative to block ({@link MobOverlayScan#getMinX}, {@link MobOverlayScan#getMinY},
     * {@link MobOverlayScan#getMinZ}) of {@code scan}.
     */
    public void beginCompile(MobOverlayScan scan, MobOverlayStyle newStyle, long worldTicks) {
        if (listId < 0) listId = GLAllocation.generateDisplayLists(1);
        modesVersion = scan.getModesVersion();
        style = newStyle;
        minX = scan.getMinX();
        minY = scan.getMinY();
        minZ = scan.getMinZ();
        compiledTicks = worldTicks;
        GL11.glNewList(listId, GL11.GL_COMPILE);
    }

    public void endCompile() {
        GL11.glEndList();
    }

    /** The model view matrix is relative to block ({@code cameraX}, {@code cameraY}, {@code cameraZ}). */
    public void draw(int cameraX, int cameraY, int cameraZ) {
        GL11.glPushMatrix();
        GL11.glTranslated(minX - cameraX, minY - cameraY, minZ - cameraZ);
        GL11.glCallList(listId);
        GL11.glPopMatrix();
    }

    public void delete() {
        if (listId < 0) return;

        GLAllocation.deleteDisplayLists(listId);
        listId = -1;
    }
}
