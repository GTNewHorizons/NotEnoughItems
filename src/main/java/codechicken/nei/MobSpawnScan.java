package codechicken.nei;

import java.util.Arrays;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.IWorldAccess;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;

import codechicken.lib.math.MathHelper;

/**
 * The mob spawn modes of the blocks around the player, scanned a slice per tick into a back buffer. The buffers swap
 * when a pass is done. The column of a block or light change is rescanned on the next tick. The modes come from
 * {@link WorldOverlayRenderer#getSpawnMode}: 0 for no spawns, 1 for night only and 2 for any time.
 */
public final class MobSpawnScan {

    public static final byte NIGHT_ONLY = 1;
    public static final int MIN_RADIUS = 8;
    public static final int MAX_RADIUS = 64;
    public static final int DEFAULT_RADIUS = 32;
    public static final int VERTICAL_RADIUS = 16;
    public static final int HEIGHT = 2 * VERTICAL_RADIUS + 1;
    private static final int REFRESH_TICKS = 10;

    private final IWorldAccess changeListener = new ChangeListener();
    private int radius;
    private int span;
    private Buffer front = new Buffer();
    private Buffer back = new Buffer();
    // columns of `back` below this index are scanned in the pass in progress
    private int nextColumn;
    private boolean hasDirtyColumns;
    private int changeCount;
    private boolean hasModes;
    private long scannedTick;
    // world of `front`, with `changeListener` added
    private World scannedWorld;

    /** Free the buffers. The next {@link #update} does a full scan. */
    public void clear() {
        if (scannedWorld != null) scannedWorld.removeWorldAccess(changeListener);
        radius = 0;
        span = 0;
        front = new Buffer();
        back = new Buffer();
        hasModes = false;
        scannedWorld = null;
    }

    /**
     * Rescan the columns of changed blocks, and scan the next slice of columns, at most once per tick. After
     * {@link #clear}, a radius change or a world change, scan every column at once. {@code newRadius} is clamped
     * between {@link #MIN_RADIUS} and {@link #MAX_RADIUS}.
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
        if (hasDirtyColumns) rescanDirtyColumns(world);
        final int columnCount = span * span;
        final int sliceEnd = Math.min(columnCount, nextColumn + (columnCount + REFRESH_TICKS - 1) / REFRESH_TICKS);
        for (; nextColumn < sliceEnd; nextColumn++) {
            back.scanColumn(world, nextColumn, span);
        }
        if (nextColumn == columnCount) {
            swapBuffers();
            startPass(entity);
        }
    }

    /** Changes each time the modes of {@link #readSpawnMode} change. */
    public int getChangeCount() {
        return changeCount;
    }

    public int getSpan() {
        return span;
    }

    public int getMinX() {
        return front.minX;
    }

    public int getMinY() {
        return front.minY;
    }

    public int getMinZ() {
        return front.minZ;
    }

    public int getColumnEnd(int column) {
        return front.columnEnds[column];
    }

    /** @return 0 at or above {@link #getColumnEnd} */
    public byte readSpawnMode(int column, int k) {
        return k < front.columnEnds[column] ? front.modes[column * HEIGHT + k] : 0;
    }

    private void resize(int newRadius) {
        radius = newRadius;
        span = 2 * newRadius + 1;
        front.allocate(span * span);
        back.allocate(span * span);
        hasModes = false;
    }

    private void scanFullPass(Entity entity) {
        final long startNanos = System.nanoTime();
        final World world = entity.worldObj;
        if (world != scannedWorld) {
            if (scannedWorld != null) scannedWorld.removeWorldAccess(changeListener);
            world.addWorldAccess(changeListener);
            scannedWorld = world;
        }

        startPass(entity);
        for (; nextColumn < span * span; nextColumn++) {
            back.scanColumn(world, nextColumn, span);
        }
        swapBuffers();
        hasModes = true;
        scannedTick = world.getTotalWorldTime();
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
        back.minX = (int) entity.posX - radius;
        back.minY = centreY - VERTICAL_RADIUS;
        back.minZ = (int) entity.posZ - radius;
        Arrays.fill(back.dirty, false);
        nextColumn = 0;
    }

    private void rescanDirtyColumns(World world) {
        hasDirtyColumns = false;
        for (int column = 0; column < span * span; column++) {
            if (front.dirty[column]) {
                front.scanColumn(world, column, span);
                front.dirty[column] = false;
                changeCount++;
            }
            if (back.dirty[column]) {
                back.scanColumn(world, column, span);
                back.dirty[column] = false;
            }
        }
    }

    private void swapBuffers() {
        final Buffer scanned = back;
        back = front;
        front = scanned;
        changeCount++;
    }

    /**
     * Mark the column of a changed block. A block's spawn mode depends on the block below and the block above, so a
     * change 1 block below or above the scanned height counts too.
     */
    private void markChanged(int x, int y, int z) {
        final int lowestY = Math.min(front.minY, back.minY) - 1;
        final int highestY = Math.max(front.minY, back.minY) + HEIGHT;
        if (y >= lowestY && y <= highestY) markColumn(x, z);
    }

    private void markColumn(int x, int z) {
        final int frontColumn = front.findColumn(x, z, span);
        if (frontColumn >= 0) {
            front.dirty[frontColumn] = true;
            hasDirtyColumns = true;
        }
        // a column at or above `nextColumn` is scanned later in this pass
        final int backColumn = back.findColumn(x, z, span);
        if (backColumn >= 0 && backColumn < nextColumn) {
            back.dirty[backColumn] = true;
            hasDirtyColumns = true;
        }
    }

    /** The spawn modes of one square of columns. */
    private static final class Buffer {

        // `modes[column * HEIGHT + k]`, valid for `k < columnEnds[column]`
        private byte[] modes = new byte[0];
        private int[] columnEnds = new int[0];
        private boolean[] dirty = new boolean[0];
        private int minX;
        private int minY;
        private int minZ;

        private void allocate(int columnCount) {
            modes = new byte[columnCount * HEIGHT];
            columnEnds = new int[columnCount];
            dirty = new boolean[columnCount];
        }

        /** @return -1 outside this buffer's square */
        private int findColumn(int x, int z, int span) {
            final int i = x - minX;
            final int j = z - minZ;
            return i >= 0 && i < span && j >= 0 && j < span ? i * span + j : -1;
        }

        private void scanColumn(World world, int column, int span) {
            final int x = minX + column / span;
            final int z = minZ + column % span;
            final BiomeGenBase biome = world.getBiomeGenForCoords(x, z);
            if (biome.getSpawnableList(EnumCreatureType.monster).isEmpty() || biome.getSpawningChance() <= 0) {
                columnEnds[column] = 0;
                return;
            }

            final Chunk chunk = world.getChunkFromBlockCoords(x, z);
            final int topY = Math.min(world.getHeight() - 1, world.getHeightValue(x, z));
            int end = 0;
            while (end < HEIGHT && minY + end <= topY) {
                modes[column * HEIGHT + end] = WorldOverlayRenderer.getSpawnMode(chunk, x, minY + end, z);
                end++;
            }
            columnEnds[column] = end;
        }
    }

    /** Marks the columns of the block and light changes in {@link #scannedWorld}. */
    private final class ChangeListener implements IWorldAccess {

        @Override
        public void markBlockForUpdate(int x, int y, int z) {
            markChanged(x, y, z);
        }

        @Override
        public void markBlockForRenderUpdate(int x, int y, int z) {
            markChanged(x, y, z);
        }

        @Override
        public void markBlockRangeForRenderUpdate(int fromX, int fromY, int fromZ, int toX, int toY, int toZ) {
            final int startX = Math.max(fromX, Math.min(front.minX, back.minX));
            final int endX = Math.min(toX, Math.max(front.minX, back.minX) + span - 1);
            final int startZ = Math.max(fromZ, Math.min(front.minZ, back.minZ));
            final int endZ = Math.min(toZ, Math.max(front.minZ, back.minZ) + span - 1);
            for (int x = startX; x <= endX; x++) {
                for (int z = startZ; z <= endZ; z++) {
                    markColumn(x, z);
                }
            }
        }

        @Override
        public void playSound(String soundName, double x, double y, double z, float volume, float pitch) {}

        @Override
        public void playSoundToNearExcept(EntityPlayer player, String soundName, double x, double y, double z,
                float volume, float pitch) {}

        @Override
        public void spawnParticle(String particleName, double x, double y, double z, double velocityX,
                double velocityY, double velocityZ) {}

        @Override
        public void onEntityCreate(Entity entity) {}

        @Override
        public void onEntityDestroy(Entity entity) {}

        @Override
        public void playRecord(String recordName, int x, int y, int z) {}

        @Override
        public void broadcastSound(int soundId, int x, int y, int z, int data) {}

        @Override
        public void playAuxSFX(EntityPlayer player, int sfxType, int x, int y, int z, int data) {}

        @Override
        public void destroyBlockPartially(int breakerId, int x, int y, int z, int progress) {}

        @Override
        public void onStaticEntitiesChanged() {}
    }
}
