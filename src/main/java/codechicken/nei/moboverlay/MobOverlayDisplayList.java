package codechicken.nei.moboverlay;

import java.util.Arrays;

import net.minecraft.client.renderer.GLAllocation;

import org.lwjgl.opengl.GL11;

/**
 * The mob spawn overlay quads, compiled into one GL display list for each chunk column that overlaps the box of a
 * {@link MobOverlayScan}. A compile rebuilds only the lists of the chunk columns whose
 * {@link MobOverlayScan#readChunkVersion} changed. A parent list calls the chunk-column lists, so a draw is one
 * {@code glCallList()}.
 */
public final class MobOverlayDisplayList {

    private static final int NO_LIST = -1;

    // the first of `chunkSpan * chunkSpan` lists, in the order of the ring of `MobOverlayScan.readChunkVersion()`, then
    // the parent list
    private int firstListId = NO_LIST;
    private int parentListId = NO_LIST;
    private boolean hasParentList;
    private int anchorX;
    private int anchorY;
    private int anchorZ;
    private int chunkSpan;
    private boolean[] isCompiled = new boolean[0];
    private boolean[] hasFaces = new boolean[0];
    private int[] compiledChunkX = new int[0];
    private int[] compiledChunkZ = new int[0];
    private int[] compiledMinY = new int[0];
    private int[] compiledChunkVersion = new int[0];
    private int modesVersion;
    private MobOverlayStyle style;
    private long compiledTicks;
    private int compilingChunk;

    /**
     * Compiles after a {@link MobOverlayScan#getModesVersion} change are debounced to one per {@code MIN_COMPILE_TICKS}
     * ticks, because the version changes several times per second while the player moves fast, and a compile runs for a
     * millisecond or more.
     */
    public boolean isCompileDue(int currentModesVersion, MobOverlayStyle currentStyle, long worldTicks) {
        final int MIN_COMPILE_TICKS = 5;
        if (firstListId == NO_LIST || style != currentStyle) return true;

        // after a dimension change or a server switch, `worldTicks` can be lower than `compiledTicks`
        final boolean isWaitOver = worldTicks - compiledTicks >= MIN_COMPILE_TICKS || worldTicks < compiledTicks;
        return modesVersion != currentModesVersion && isWaitOver;
    }

    /**
     * Start a compile of {@code scan}. After this call, {@link #beginChunk} and {@link #endChunk} compile each chunk
     * column of the box, and {@link #endCompile} compiles the parent list. The lists of the chunk columns outside the
     * box are not drawn.
     */
    public void beginCompile(MobOverlayScan scan, MobOverlayStyle newStyle, long worldTicks) {
        if (chunkSpan != scan.getChunkSpan()) resize(scan.getChunkSpan());
        if (style != newStyle) Arrays.fill(isCompiled, false);
        modesVersion = scan.getModesVersion();
        style = newStyle;
        compiledTicks = worldTicks;
        anchorX = scan.getMinX();
        anchorY = scan.getMinY();
        anchorZ = scan.getMinZ();
        final int fromChunkX = scan.getMinX() >> MobOverlayScan.CHUNK_SHIFT;
        final int toChunkX = (scan.getMinX() + scan.getSpan() - 1) >> MobOverlayScan.CHUNK_SHIFT;
        final int fromChunkZ = scan.getMinZ() >> MobOverlayScan.CHUNK_SHIFT;
        final int toChunkZ = (scan.getMinZ() + scan.getSpan() - 1) >> MobOverlayScan.CHUNK_SHIFT;
        for (int chunk = 0; chunk < isCompiled.length; chunk++) {
            final boolean isInBox = compiledChunkX[chunk] >= fromChunkX && compiledChunkX[chunk] <= toChunkX
                    && compiledChunkZ[chunk] >= fromChunkZ
                    && compiledChunkZ[chunk] <= toChunkZ;
            isCompiled[chunk] &= isInBox;
        }
    }

    /**
     * Start the list of chunk column ({@code chunkX}, {@code chunkZ}), with quad positions relative to block ({@code
     * chunkX * 16}, {@link MobOverlayScan#getMinY}, {@code chunkZ * 16}) of {@code scan}.
     *
     * @return false, with no list started, when the list is compiled for the chunk column's version
     */
    public boolean beginChunk(MobOverlayScan scan, int chunkX, int chunkZ) {
        final int chunk = Math.floorMod(chunkX, chunkSpan) * chunkSpan + Math.floorMod(chunkZ, chunkSpan);
        final int chunkVersion = scan.readChunkVersion(chunkX, chunkZ);
        final boolean isCurrent = isCompiled[chunk] && compiledChunkX[chunk] == chunkX
                && compiledChunkZ[chunk] == chunkZ
                && compiledChunkVersion[chunk] == chunkVersion;
        if (isCurrent) return false;

        isCompiled[chunk] = true;
        compiledChunkX[chunk] = chunkX;
        compiledChunkZ[chunk] = chunkZ;
        compiledMinY[chunk] = scan.getMinY();
        compiledChunkVersion[chunk] = chunkVersion;
        compilingChunk = chunk;
        GL11.glNewList(firstListId + chunk, GL11.GL_COMPILE);
        return true;
    }

    public void endChunk(boolean hasChunkFaces) {
        GL11.glEndList();
        hasFaces[compilingChunk] = hasChunkFaces;
    }

    /** Compile the parent list, with positions relative to the box's minimum corner at {@link #beginCompile}. */
    public void endCompile() {
        GL11.glNewList(parentListId, GL11.GL_COMPILE);
        for (int chunk = 0; chunk < isCompiled.length; chunk++) {
            if (!(isCompiled[chunk] && hasFaces[chunk])) continue;

            GL11.glPushMatrix();
            GL11.glTranslated(
                    (compiledChunkX[chunk] << MobOverlayScan.CHUNK_SHIFT) - anchorX,
                    compiledMinY[chunk] - anchorY,
                    (compiledChunkZ[chunk] << MobOverlayScan.CHUNK_SHIFT) - anchorZ);
            GL11.glCallList(firstListId + chunk);
            GL11.glPopMatrix();
        }
        GL11.glEndList();
        hasParentList = true;
    }

    /** The model view matrix is relative to block ({@code cameraX}, {@code cameraY}, {@code cameraZ}). */
    public void draw(int cameraX, int cameraY, int cameraZ) {
        if (!hasParentList) return;

        GL11.glPushMatrix();
        GL11.glTranslated(anchorX - cameraX, anchorY - cameraY, anchorZ - cameraZ);
        GL11.glCallList(parentListId);
        GL11.glPopMatrix();
    }

    public void delete() {
        if (firstListId == NO_LIST) return;

        GLAllocation.deleteDisplayLists(firstListId);
        firstListId = NO_LIST;
        parentListId = NO_LIST;
        hasParentList = false;
        chunkSpan = 0;
        Arrays.fill(isCompiled, false);
    }

    private void resize(int newChunkSpan) {
        delete();
        chunkSpan = newChunkSpan;
        final int chunkCount = newChunkSpan * newChunkSpan;
        firstListId = GLAllocation.generateDisplayLists(chunkCount + 1);
        parentListId = firstListId + chunkCount;
        isCompiled = new boolean[chunkCount];
        hasFaces = new boolean[chunkCount];
        compiledChunkX = new int[chunkCount];
        compiledChunkZ = new int[chunkCount];
        compiledMinY = new int[chunkCount];
        compiledChunkVersion = new int[chunkCount];
    }
}
