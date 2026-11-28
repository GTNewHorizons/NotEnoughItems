package codechicken.nei.moboverlay;

import java.util.Arrays;

import net.minecraft.client.renderer.GLAllocation;

import org.lwjgl.opengl.GL11;

/**
 * {@code MobOverlayDisplayList} compiles the overlay quads of each chunk column of a {@link MobOverlayScan} box into
 * one GL display list. A compile rebuilds only the lists whose {@link MobOverlayScan#readChunkVersion} changed.
 */
public final class MobOverlayDisplayList {

    private static final int NO_LIST = -1;

    private int firstListId = NO_LIST;
    private int parentListId = NO_LIST;
    private int anchorX;
    private int anchorY;
    private int anchorZ;
    private int chunkSpan;
    private boolean[] isCompiled = new boolean[0];
    private boolean[] hasFaces = new boolean[0];
    private int[] compiledChunkVersion = new int[0];
    private int modesVersion;
    private MobOverlayStyle style;
    private long compiledTicks;
    private int compilingChunk;

    public boolean isCompileDue(int currentModesVersion, MobOverlayStyle currentStyle, long worldTicks) {
        final int MIN_COMPILE_TICKS = 5;
        if (firstListId == NO_LIST || style != currentStyle) return true;

        // after a dimension change or a server switch, `worldTicks` can be lower than `compiledTicks`
        final boolean isWaitOver = worldTicks - compiledTicks >= MIN_COMPILE_TICKS || worldTicks < compiledTicks;
        return modesVersion != currentModesVersion && isWaitOver;
    }

    public void beginCompile(MobOverlayScan scan, MobOverlayStyle newStyle, long worldTicks) {
        if (chunkSpan != scan.getChunkSpan()) resize(scan.getChunkSpan());
        if (style != newStyle) Arrays.fill(isCompiled, false);
        modesVersion = scan.getModesVersion();
        style = newStyle;
        compiledTicks = worldTicks;
        anchorX = scan.getMinX();
        anchorY = scan.getMinY();
        anchorZ = scan.getMinZ();
    }

    public boolean beginChunk(MobOverlayScan scan, int chunkX, int chunkZ) {
        final int chunk = scan.findChunk(chunkX, chunkZ);
        final int chunkVersion = scan.readChunkVersion(chunkX, chunkZ);
        if (isCompiled[chunk] && compiledChunkVersion[chunk] == chunkVersion) return false;

        isCompiled[chunk] = true;
        compiledChunkVersion[chunk] = chunkVersion;
        compilingChunk = chunk;
        GL11.glNewList(firstListId + chunk, GL11.GL_COMPILE);
        return true;
    }

    public void endChunk(boolean hasChunkFaces) {
        GL11.glEndList();
        hasFaces[compilingChunk] = hasChunkFaces;
    }

    public void endCompile(MobOverlayScan scan) {
        GL11.glNewList(parentListId, GL11.GL_COMPILE);
        for (int chunkX = scan.getMinChunkX(); chunkX <= scan.getMaxChunkX(); chunkX++) {
            for (int chunkZ = scan.getMinChunkZ(); chunkZ <= scan.getMaxChunkZ(); chunkZ++) {
                final int chunk = scan.findChunk(chunkX, chunkZ);
                if (!hasFaces[chunk]) continue;

                GL11.glPushMatrix();
                GL11.glTranslated(
                        (chunkX << MobOverlayScan.CHUNK_SHIFT) - anchorX,
                        0,
                        (chunkZ << MobOverlayScan.CHUNK_SHIFT) - anchorZ);
                GL11.glCallList(firstListId + chunk);
                GL11.glPopMatrix();
            }
        }
        GL11.glEndList();
    }

    /** The model view matrix is relative to block ({@code cameraX}, {@code cameraY}, {@code cameraZ}). */
    public void draw(int cameraX, int cameraY, int cameraZ) {
        GL11.glPushMatrix();
        GL11.glTranslated(anchorX - cameraX, anchorY - cameraY, anchorZ - cameraZ);
        GL11.glCallList(parentListId);
        GL11.glPopMatrix();
    }

    private void resize(int newChunkSpan) {
        final int chunkCount = newChunkSpan * newChunkSpan;
        delete();

        chunkSpan = newChunkSpan;
        firstListId = GLAllocation.generateDisplayLists(chunkCount + 1);
        parentListId = firstListId + chunkCount;

        isCompiled = new boolean[chunkCount];
        hasFaces = new boolean[chunkCount];
        compiledChunkVersion = new int[chunkCount];
    }

    public void delete() {
        if (firstListId == NO_LIST) return;

        GLAllocation.deleteDisplayLists(firstListId);
        firstListId = NO_LIST;
        parentListId = NO_LIST;
        chunkSpan = 0;
        Arrays.fill(isCompiled, false);
    }
}
