package codechicken.nei;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;

import codechicken.lib.math.MathHelper;

/**
 * The spawn modes of the blocks around the render view entity, in a square that reaches the radius of {@link #update}
 * on each side of the entity, and {@link #VERTICAL_RADIUS} blocks above and below. Once per tick, {@link #update}
 * rescans {@code 1 / REFRESH_TICKS} of the columns into a second buffer, and after a full pass the second buffer
 * becomes the drawn buffer. A block's mode is 0 where mobs can't spawn, {@link #NIGHT_ONLY} where mobs spawn only at
 * night and 2 where mobs spawn at any time.
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
    // the drawn buffer: `modes[column * HEIGHT + k]`, valid for `k < columnEnds[column]`
    private byte[] modes = new byte[0];
    private int[] columnEnds = new int[0];
    private int minX;
    private int minY;
    private int minZ;
    // the buffer of the pass in progress
    private byte[] nextModes = new byte[0];
    private int[] nextColumnEnds = new int[0];
    private int nextMinX;
    private int nextMinY;
    private int nextMinZ;
    private int nextColumn;
    private boolean hasModes;
    private long scannedTick;
    // the world of the drawn modes
    private World scannedWorld;

    /** Drop the scanned modes and free the buffers, so that the next call of {@link #update} is a full scan. */
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
     * Scan the next slice of columns once per tick. On the first call after {@link #clear}, a change of
     * {@code newRadius} or a change of world, scan every column at once and log the time of the scan. {@code newRadius}
     * is clamped to {@link #MIN_RADIUS} and {@link #MAX_RADIUS}.
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

    /** @return the spawn mode of the block at height index {@code k} of {@code column}, 0 above the column's end */
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
    }
}
