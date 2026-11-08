package codechicken.nei.moboverlay;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.IWorldAccess;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;

import codechicken.lib.math.MathHelper;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.WorldOverlayRenderer;

/**
 * Spawn modes in a box around the player, from {@link WorldOverlayRenderer#getSpawnMode}: 0 for no spawns, 1 for night
 * only and 2 for any time. The box moves with the player once the player is a few blocks from the box's centre, and
 * {@link #update} scans only the blocks that enter the box. {@link #update} rescans the column of a block or light
 * change on the next tick. On each tick, {@link #update} also rescans a slice of the columns, for changes with no block
 * update, such as a boat that moves into the box.
 */
public final class MobOverlayScan {

    public static final byte NIGHT_ONLY = 1;
    public static final int MIN_RADIUS = 8;
    public static final int MAX_RADIUS = 64;
    public static final int DEFAULT_RADIUS = 32;
    public static final int MIN_VERTICAL_RADIUS = 4;
    public static final int MAX_VERTICAL_RADIUS = 64;
    public static final int DEFAULT_VERTICAL_RADIUS = 16;
    private static final int RECENTRE_DISTANCE = 4;
    private static final int VERTICAL_RECENTRE_DISTANCE = 2;
    private static final int RESCAN_TICKS = 40;

    private final IWorldAccess changeListener = new ChangeListener();
    private final Column column = new Column();
    private int radius;
    private int verticalRadius;
    private int span;
    private int height;
    // a ring buffer over the box, indexed by `findCell()`
    private byte[] modes = new byte[0];
    // indexed by `findColumn(x, z)`
    private boolean[] dirty = new boolean[0];
    private boolean hasDirtyColumns;
    private int minX;
    private int minY;
    private int minZ;
    // the slice rescan of `update()` continues from this column, numbered `(x - minX) * span + (z - minZ)`
    private int nextRescanColumn;
    private int modesVersion;
    private boolean hasModes;
    private long scannedTick;
    // world of `modes`, with `changeListener` added
    private World scannedWorld;

    public void clear() {
        if (scannedWorld != null) scannedWorld.removeWorldAccess(changeListener);
        radius = 0;
        verticalRadius = 0;
        span = 0;
        height = 0;
        modes = new byte[0];
        dirty = new boolean[0];
        hasModes = false;
        scannedWorld = null;
    }

    /**
     * Scan every column in this call after {@link #clear}, a change of either radius, a world change, or a move of
     * {@link #getSpan} blocks or more across or {@link #getHeight} blocks or more up or down. {@code newRadius} is
     * clamped between {@link #MIN_RADIUS} and {@link #MAX_RADIUS}, and {@code newVerticalRadius} between
     * {@link #MIN_VERTICAL_RADIUS} and {@link #MAX_VERTICAL_RADIUS}.
     */
    public void update(Entity entity, int newRadius, int newVerticalRadius) {
        final World world = entity.worldObj;
        final int clampedRadius = (int) MathHelper.clip(newRadius, MIN_RADIUS, MAX_RADIUS);
        final int clampedVerticalRadius = (int) MathHelper
                .clip(newVerticalRadius, MIN_VERTICAL_RADIUS, MAX_VERTICAL_RADIUS);
        if (radius != clampedRadius || verticalRadius != clampedVerticalRadius) {
            resize(clampedRadius, clampedVerticalRadius);
        }
        final int centreX = MathHelper.floor_double(entity.posX);
        final int centreY = (int) MathHelper.clip(entity.posY, verticalRadius, world.getHeight() - verticalRadius);
        final int centreZ = MathHelper.floor_double(entity.posZ);
        final boolean isNearBox = Math.abs(centreX - radius - minX) < span && Math.abs(centreZ - radius - minZ) < span
                && Math.abs(centreY - verticalRadius - minY) < height;
        if (!(hasModes && world == scannedWorld && isNearBox)) {
            if (world != scannedWorld) watchWorld(world);
            scanFullBox(centreX, centreY, centreZ);
            return;
        }

        final long worldTicks = world.getTotalWorldTime();
        if (worldTicks == scannedTick) return;

        scannedTick = worldTicks;
        boolean isChanged = moveBox(centreX, centreY, centreZ);
        if (hasDirtyColumns) isChanged |= rescanDirtyColumns();
        isChanged |= rescanSlice();
        if (isChanged) modesVersion++;
    }

    /** The version changes when a spawn mode changes or the box moves. */
    public int getModesVersion() {
        return modesVersion;
    }

    public int getSpan() {
        return span;
    }

    public int getHeight() {
        return height;
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

    /** {@code i} and {@code j} are from 0 to {@link #getSpan}, and {@code k} is from 0 to {@link #getHeight}. */
    public byte readSpawnMode(int i, int k, int j) {
        return modes[findCell(minX + i, minY + k, minZ + j)];
    }

    private void resize(int newRadius, int newVerticalRadius) {
        radius = newRadius;
        verticalRadius = newVerticalRadius;
        span = 2 * newRadius + 1;
        height = 2 * newVerticalRadius + 1;
        modes = new byte[span * span * height];
        dirty = new boolean[span * span];
        hasModes = false;
    }

    private void scanFullBox(int centreX, int centreY, int centreZ) {
        final long startNanos = System.nanoTime();
        minX = centreX - radius;
        minY = centreY - verticalRadius;
        minZ = centreZ - radius;
        for (int x = minX; x < minX + span; x++) {
            for (int z = minZ; z < minZ + span; z++) {
                scanColumn(x, z);
            }
        }
        hasModes = true;
        modesVersion++;
        scannedTick = scannedWorld.getTotalWorldTime();
        NEIClientConfig.logger.info(
                "moboverlay.scan.full radius={} verticalRadius={} columns={} micros={}",
                radius,
                verticalRadius,
                span * span,
                (System.nanoTime() - startNanos) / 1000);
    }

    private void watchWorld(World world) {
        if (scannedWorld != null) scannedWorld.removeWorldAccess(changeListener);
        world.addWorldAccess(changeListener);
        scannedWorld = world;
    }

    /** @return whether the box moved */
    private boolean moveBox(int centreX, int centreY, int centreZ) {
        boolean isMoved = false;
        final int offsetX = centreX - radius - minX;
        if (Math.abs(offsetX) > RECENTRE_DISTANCE) {
            final int enteringX = offsetX > 0 ? minX + span : minX + offsetX;
            minX += offsetX;
            for (int x = enteringX; x < enteringX + Math.abs(offsetX); x++) {
                for (int z = minZ; z < minZ + span; z++) {
                    scanColumn(x, z);
                }
            }
            isMoved = true;
        }

        final int offsetZ = centreZ - radius - minZ;
        if (Math.abs(offsetZ) > RECENTRE_DISTANCE) {
            final int enteringZ = offsetZ > 0 ? minZ + span : minZ + offsetZ;
            minZ += offsetZ;
            for (int x = minX; x < minX + span; x++) {
                for (int z = enteringZ; z < enteringZ + Math.abs(offsetZ); z++) {
                    scanColumn(x, z);
                }
            }
            isMoved = true;
        }

        final int offsetY = centreY - verticalRadius - minY;
        if (Math.abs(offsetY) > VERTICAL_RECENTRE_DISTANCE) {
            final int enteringY = offsetY > 0 ? minY + height : minY + offsetY;
            minY += offsetY;
            for (int x = minX; x < minX + span; x++) {
                for (int z = minZ; z < minZ + span; z++) {
                    column.moveTo(x, z);
                    column.scan(enteringY, enteringY + Math.abs(offsetY));
                }
            }
            isMoved = true;
        }
        return isMoved;
    }

    private boolean rescanDirtyColumns() {
        hasDirtyColumns = false;
        boolean isChanged = false;
        for (int x = minX; x < minX + span; x++) {
            for (int z = minZ; z < minZ + span; z++) {
                if (dirty[findColumn(x, z)]) isChanged |= scanColumn(x, z);
            }
        }
        return isChanged;
    }

    private boolean rescanSlice() {
        final int columnCount = span * span;
        final int sliceEnd = Math.min(columnCount, nextRescanColumn + (columnCount + RESCAN_TICKS - 1) / RESCAN_TICKS);
        boolean isChanged = false;
        for (; nextRescanColumn < sliceEnd; nextRescanColumn++) {
            final int x = minX + nextRescanColumn / span;
            final int z = minZ + nextRescanColumn % span;
            isChanged |= scanColumn(x, z);
        }
        if (nextRescanColumn == columnCount) nextRescanColumn = 0;
        return isChanged;
    }

    /** @return whether a spawn mode changed */
    private boolean scanColumn(int x, int z) {
        column.moveTo(x, z);
        dirty[findColumn(x, z)] = false;
        return column.scan(minY, minY + height);
    }

    private int findColumn(int x, int z) {
        return Math.floorMod(x, span) * span + Math.floorMod(z, span);
    }

    private int findCell(int x, int y, int z) {
        return findColumn(x, z) * height + Math.floorMod(y, height);
    }

    /**
     * {@link #moveTo} reads the chunk, the height map and the biome of a column, and {@link #scan} reads the column's
     * blocks.
     */
    private final class Column {

        private int x;
        private int z;
        private Chunk chunk;
        private int topY;
        private boolean canBiomeSpawn;

        private void moveTo(int newX, int newZ) {
            x = newX;
            z = newZ;
            final BiomeGenBase biome = scannedWorld.getBiomeGenForCoords(x, z);
            canBiomeSpawn = !biome.getSpawnableList(EnumCreatureType.monster).isEmpty()
                    && biome.getSpawningChance() > 0;
            chunk = scannedWorld.getChunkFromBlockCoords(x, z);
            topY = Math.min(scannedWorld.getHeight() - 1, scannedWorld.getHeightValue(x, z));
        }

        /** @return whether a spawn mode changed */
        private boolean scan(int fromY, int toY) {
            boolean isChanged = false;
            for (int y = fromY; y < toY; y++) {
                final byte spawnMode = canBiomeSpawn && y <= topY ? WorldOverlayRenderer.getSpawnMode(chunk, x, y, z)
                        : 0;
                final int cell = findCell(x, y, z);
                isChanged |= modes[cell] != spawnMode;
                modes[cell] = spawnMode;
            }
            return isChanged;
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
            for (int x = Math.max(fromX, minX); x <= Math.min(toX, minX + span - 1); x++) {
                for (int z = Math.max(fromZ, minZ); z <= Math.min(toZ, minZ + span - 1); z++) {
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
        public void spawnParticle(String particleName, double x, double y, double z, double velocityX, double velocityY,
                double velocityZ) {}

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

        private void markChanged(int x, int y, int z) {
            // spawn modes depend on the block below and the block above
            final boolean isInBox = x >= minX && x < minX + span && z >= minZ && z < minZ + span;
            if (isInBox && y >= minY - 1 && y <= minY + height) markColumn(x, z);
        }

        private void markColumn(int x, int z) {
            dirty[findColumn(x, z)] = true;
            hasDirtyColumns = true;
        }
    }
}
