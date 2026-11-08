package codechicken.nei;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;

import codechicken.lib.math.MathHelper;

/**
 * The mob spawn modes of the blocks around the player, scanned a slice per tick into a back buffer. The buffers swap
 * when a pass is done. The modes come from {@link WorldOverlayRenderer#getSpawnMode}: 0 for no spawns, 1 for night only
 * and 2 for any time.
 */
public final class MobSpawnScan {

    public static final byte NIGHT_ONLY = 1;
    public static final int MIN_RADIUS = 8;
    public static final int MAX_RADIUS = 64;
    public static final int DEFAULT_RADIUS = 32;
    public static final int VERTICAL_RADIUS = 16;
    public static final int HEIGHT = 2 * VERTICAL_RADIUS + 1;
    private static final int REFRESH_TICKS = 10;

    private int radius;
    private int span;
    // front buffer: `modes[column * HEIGHT + k]`, valid for `k < columnEnds[column]`
    private byte[] modes = new byte[0];
    private int[] columnEnds = new int[0];
    private int minX;
    private int minY;
    private int minZ;
    // back buffer
    private byte[] nextModes = new byte[0];
    private int[] nextColumnEnds = new int[0];
    private int nextMinX;
    private int nextMinY;
    private int nextMinZ;
    private int nextColumn;
    private int passCount;
    private boolean hasModes;
    private long scannedTick;
    // world of the front buffer
    private World scannedWorld;

    /** Free the buffers. The next {@link #update} does a full scan. */
    public void clear() {
        radius = 0;
        span = 0;
        modes = new byte[0];
        columnEnds = new int[0];
        nextModes = new byte[0];
        nextColumnEnds = new int[0];
        hasModes = false;
        scannedWorld = null;
    }

    /**
     * Scan the next slice of columns, at most once per tick. After {@link #clear}, a radius change or a world change,
     * scan every column at once. {@code newRadius} is clamped between {@link #MIN_RADIUS} and {@link #MAX_RADIUS}.
     */
    public void update(Entity entity, int newRadius) {
        final World world = entity.worldObj;
        final int clampedRadius = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, newRadius));
        if (radius != clampedRadius) resize(clampedRadius);
        if (!(hasModes && world == scannedWorld)) {
            scanFullPass(entity);
            return;
        }

        final long worldTicks = world.getTotalWorldTime();
        if (worldTicks == scannedTick) return;

        scannedTick = worldTicks;
        final int columnCount = span * span;
        final int sliceEnd = Math.min(columnCount, nextColumn + (columnCount + REFRESH_TICKS - 1) / REFRESH_TICKS);
        for (; nextColumn < sliceEnd; nextColumn++) {
            scanColumn(world, nextColumn);
        }
        if (nextColumn == columnCount) {
            swapBuffers();
            startPass(entity);
        }
    }

    /** Changes each time the front buffer changes. */
    public int getPassCount() {
        return passCount;
    }

    public int getSpan() {
        return span;
    }

    public int getMinX() {
        return minX;
    }

    public int getMinY() {
        return minY;
    }

    public int getMinZ() {
        return minZ;
    }

    public int getColumnEnd(int column) {
        return columnEnds[column];
    }

    /** @return 0 at or above {@link #getColumnEnd} */
    public byte readSpawnMode(int column, int k) {
        return k < columnEnds[column] ? modes[column * HEIGHT + k] : 0;
    }

    private void resize(int newRadius) {
        radius = newRadius;
        span = 2 * newRadius + 1;
        modes = new byte[span * span * HEIGHT];
        columnEnds = new int[span * span];
        nextModes = new byte[span * span * HEIGHT];
        nextColumnEnds = new int[span * span];
        hasModes = false;
    }

    private void scanFullPass(Entity entity) {
        final long startNanos = System.nanoTime();
        startPass(entity);
        for (; nextColumn < span * span; nextColumn++) {
            scanColumn(entity.worldObj, nextColumn);
        }
        swapBuffers();
        hasModes = true;
        scannedWorld = entity.worldObj;
        scannedTick = entity.worldObj.getTotalWorldTime();
        NEIClientConfig.logger.info(
                "moboverlay.scan.full radius={} columns={} micros={}",
                radius,
                span * span,
                (System.nanoTime() - startNanos) / 1000);
        startPass(entity);
    }

    private void startPass(Entity entity) {
        final int centreY = (int) MathHelper
                .clip(entity.posY, VERTICAL_RADIUS, entity.worldObj.getHeight() - VERTICAL_RADIUS);
        nextMinX = (int) entity.posX - radius;
        nextMinY = centreY - VERTICAL_RADIUS;
        nextMinZ = (int) entity.posZ - radius;
        nextColumn = 0;
    }

    private void scanColumn(World world, int column) {
        final int x = nextMinX + column / span;
        final int z = nextMinZ + column % span;
        final BiomeGenBase biome = world.getBiomeGenForCoords(x, z);
        if (biome.getSpawnableList(EnumCreatureType.monster).isEmpty() || biome.getSpawningChance() <= 0) {
            nextColumnEnds[column] = 0;
            return;
        }

        final Chunk chunk = world.getChunkFromBlockCoords(x, z);
        final int topY = Math.min(world.getHeight() - 1, world.getHeightValue(x, z));
        int end = 0;
        while (end < HEIGHT && nextMinY + end <= topY) {
            nextModes[column * HEIGHT + end] = WorldOverlayRenderer.getSpawnMode(chunk, x, nextMinY + end, z);
            end++;
        }
        nextColumnEnds[column] = end;
    }

    private void swapBuffers() {
        final byte[] scannedModes = nextModes;
        nextModes = modes;
        modes = scannedModes;
        final int[] scannedEnds = nextColumnEnds;
        nextColumnEnds = columnEnds;
        columnEnds = scannedEnds;
        minX = nextMinX;
        minY = nextMinY;
        minZ = nextMinZ;
        passCount++;
    }
}
