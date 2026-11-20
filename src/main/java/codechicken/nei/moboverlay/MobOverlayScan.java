package codechicken.nei.moboverlay;

import java.util.Arrays;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.IWorldAccess;
import net.minecraft.world.SpawnerAnimals;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;

import codechicken.lib.math.MathHelper;

/**
 * Spawn modes, and the tops of flat blocks such as carpets, in a box around the player. The spawn modes are from
 * {@link Column#getSpawnMode}: 0 for no spawns, 1 for night only and 2 for any time. The box moves with the player once
 * the player is a few blocks from the box's centre, and {@link #update} scans only the blocks that enter the box.
 * {@link #update} rescans the column of a block or light change on the next tick. On each tick, {@link #update} also
 * rescans a slice of the columns, for changes with no block update, such as a boat that moves into the box.
 */
public final class MobOverlayScan {

    public static final byte NIGHT_ONLY = 1;
    public static final int MIN_RADIUS = 8;
    public static final int MAX_RADIUS = 64;
    public static final int DEFAULT_RADIUS = 32;
    public static final int MIN_VERTICAL_RADIUS = 4;
    public static final int MAX_VERTICAL_RADIUS = 64;
    public static final int DEFAULT_VERTICAL_RADIUS = 16;
    public static final int CHUNK_SHIFT = 4;
    private static final int RECENTRE_DISTANCE = 4;
    private static final int VERTICAL_RECENTRE_DISTANCE = 2;
    private static final int RESCAN_TICKS = 40;
    private static final int SPAWN_MODE_MASK = 3;
    private static final int COVER_TOP_SHIFT = 2;
    private static final int COVER_TOP_STEPS = 32;

    private final IWorldAccess changeListener = new ChangeListener();
    private final Column column = new Column();
    private int radius;
    private int verticalRadius;
    private int span;
    private int height;
    // a ring buffer over the box, indexed by `findCell()`. A cell has the spawn mode in the bits of `SPAWN_MODE_MASK`,
    // and `Column.readCoverTop()` shifted left by `COVER_TOP_SHIFT`
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
    // chunk columns per side of the ring `chunkVersions`, enough for every chunk column that overlaps the box
    private int chunkSpan;
    // indexed by `findChunk()`
    private int[] chunkVersions = new int[0];
    private int chunkVersionCount;
    private boolean hasModes;
    private long scannedTick;
    // world of `modes`, with `changeListener` added
    private World scannedWorld;

    public void clear() {
        if (scannedWorld == null && span == 0) return;

        if (scannedWorld != null) scannedWorld.removeWorldAccess(changeListener);
        radius = 0;
        verticalRadius = 0;
        span = 0;
        height = 0;
        modes = new byte[0];
        dirty = new boolean[0];
        chunkSpan = 0;
        chunkVersions = new int[0];
        hasModes = false;
        scannedWorld = null;
        // `Chunk.worldObj` references the world
        column.chunk = null;
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

    /** The version changes when a spawn mode or the top of a flat block changes, or the box moves. */
    public int getModesVersion() {
        return modesVersion;
    }

    /** Chunk columns per side of the ring of {@link #readChunkVersion}. */
    public int getChunkSpan() {
        return chunkSpan;
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

    /**
     * The version changes when a cell of the chunk column or of a column beside the chunk column changes, and when the
     * box moves.
     */
    public int readChunkVersion(int chunkX, int chunkZ) {
        return chunkVersions[findChunk(chunkX, chunkZ)];
    }

    /** {@code i} and {@code j} are from 0 to {@link #getSpan}, and {@code k} is from 0 to {@link #getHeight}. */
    public byte readSpawnMode(int i, int k, int j) {
        return extractSpawnMode(modes[findCell(minX + i, minY + k, minZ + j)]);
    }

    /**
     * Copy the cells of column ({@code i}, {@code j}) to {@code columnCells}, from the bottom of the box to the top.
     * {@link #extractSpawnMode} and {@link #computeOverlayYOffset} read a cell. The indices are those of
     * {@link #readSpawnMode}.
     */
    public void readColumn(int i, int j, byte[] columnCells) {
        final int columnStart = findColumn(minX + i, minZ + j) * height;
        final int bottomCell = Math.floorMod(minY, height);
        System.arraycopy(modes, columnStart + bottomCell, columnCells, 0, height - bottomCell);
        System.arraycopy(modes, columnStart, columnCells, height - bottomCell, bottomCell);
    }

    public static byte extractSpawnMode(byte cell) {
        return (byte) (cell & SPAWN_MODE_MASK);
    }

    /**
     * Height of the overlay above the bottom of the block, so that a flat block such as a carpet does not cover the
     * overlay.
     */
    public static double computeOverlayYOffset(byte cell) {
        final double OVERLAY_GAP = 0.02;
        return (double) (cell >> COVER_TOP_SHIFT) / COVER_TOP_STEPS + OVERLAY_GAP;
    }

    private void resize(int newRadius, int newVerticalRadius) {
        radius = newRadius;
        verticalRadius = newVerticalRadius;
        span = 2 * newRadius + 1;
        height = 2 * newVerticalRadius + 1;
        modes = new byte[span * span * height];
        dirty = new boolean[span * span];
        chunkSpan = ((span - 1) >> CHUNK_SHIFT) + 2;
        chunkVersions = new int[chunkSpan * chunkSpan];
        hasModes = false;
        nextRescanColumn = 0;
    }

    private void scanFullBox(int centreX, int centreY, int centreZ) {
        minX = centreX - radius;
        minY = centreY - verticalRadius;
        minZ = centreZ - radius;
        for (int x = minX; x < minX + span; x++) {
            for (int z = minZ; z < minZ + span; z++) {
                scanColumn(x, z);
            }
        }
        markAllChunks();
        hasModes = true;
        modesVersion++;
        scannedTick = scannedWorld.getTotalWorldTime();
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
            markMovedEdgeChunks(minX, offsetX, true);
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
            markMovedEdgeChunks(minZ, offsetZ, false);
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
            markAllChunks();
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
        if (nextRescanColumn >= columnCount) nextRescanColumn = 0;
        return isChanged;
    }

    /** @return whether a spawn mode or the top of a flat block changed */
    private boolean scanColumn(int x, int z) {
        column.moveTo(x, z);
        dirty[findColumn(x, z)] = false;
        final boolean isChanged = column.scan(minY, minY + height);
        // the connected outline of a cell depends on the spawn modes of the cells beside the cell
        if (isChanged) {
            for (int offsetX = -1; offsetX <= 1; offsetX++) {
                for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                    markChunk((x + offsetX) >> CHUNK_SHIFT, (z + offsetZ) >> CHUNK_SHIFT);
                }
            }
        }
        return isChanged;
    }

    /**
     * Mark the chunk columns of the box's edge before and after a move of {@code offset} blocks along x, or along z
     * when {@code isAlongX} is false, and of the columns that enter or leave the box. No outline is drawn between an
     * edge cell of the box and a cell outside the box, so a move changes the outlines of the edge cells.
     */
    private void markMovedEdgeChunks(int oldMin, int offset, boolean isAlongX) {
        final int lowFrom = Math.min(oldMin, oldMin + offset);
        final int lowTo = Math.max(oldMin, oldMin + offset);
        final int highFrom = lowFrom + span - 1;
        final int highTo = lowTo + span - 1;
        final int acrossFrom = isAlongX ? minZ : minX;
        for (int across = acrossFrom >> CHUNK_SHIFT; across <= (acrossFrom + span - 1) >> CHUNK_SHIFT; across++) {
            for (int along = lowFrom >> CHUNK_SHIFT; along <= lowTo >> CHUNK_SHIFT; along++) {
                markChunk(isAlongX ? along : across, isAlongX ? across : along);
            }
            for (int along = highFrom >> CHUNK_SHIFT; along <= highTo >> CHUNK_SHIFT; along++) {
                markChunk(isAlongX ? along : across, isAlongX ? across : along);
            }
        }
    }

    private void markAllChunks() {
        Arrays.fill(chunkVersions, ++chunkVersionCount);
    }

    private void markChunk(int chunkX, int chunkZ) {
        chunkVersions[findChunk(chunkX, chunkZ)] = ++chunkVersionCount;
    }

    private int findChunk(int chunkX, int chunkZ) {
        return Math.floorMod(chunkX, chunkSpan) * chunkSpan + Math.floorMod(chunkZ, chunkSpan);
    }

    private int findColumn(int x, int z) {
        return Math.floorMod(x, span) * span + Math.floorMod(z, span);
    }

    private int findCell(int x, int y, int z) {
        return findColumn(x, z) * height + Math.floorMod(y, height);
    }

    /**
     * {@link #moveTo} reads a column's chunk, biome and highest non-air block in the box, and {@link #scan} reads the
     * column's blocks.
     */
    private final class Column {

        private final Entity dummyEntity = new EntityPig(null);
        private final AxisAlignedBB spawnBox = AxisAlignedBB.getBoundingBox(0, 0, 0, 0, 0, 0);
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
            // `getHeightValue()` is the height above the highest block with a light opacity above 0. A block with a
            // light opacity of 0 can be higher: Tinkers' Construct slime grass, a hopper or a snow layer. A mob can
            // spawn on top of slime grass, a hopper or a full snow block. Search the blocks for the highest non-air
            // block
            final int SECTION_HEIGHT = 16;
            int y = Math.min(minY + height - 1, chunk.getTopFilledSegment() + SECTION_HEIGHT - 1);
            while (y >= minY && chunk.getBlock(x & 15, y, z & 15).isAir(chunk.worldObj, x, y, z)) y--;
            topY = y + 1;
        }

        /** @return whether a spawn mode or the top of a flat block changed */
        private boolean scan(int fromY, int toY) {
            boolean isChanged = false;
            for (int y = fromY; y < toY; y++) {
                final int spawnMode = canBiomeSpawn && y <= topY ? getSpawnMode(y) : 0;
                final byte cellValue = (byte) (spawnMode == 0 ? 0 : spawnMode | (readCoverTop(y) << COVER_TOP_SHIFT));
                final int cell = findCell(x, y, z);
                isChanged |= modes[cell] != cellValue;
                modes[cell] = cellValue;
            }
            return isChanged;
        }

        /** The spawn mode of the block at height {@code y} in this column. */
        private byte getSpawnMode(int y) {
            if (y >= chunk.worldObj.getHeight()) {
                return 0;
            }

            // `SpawnerAnimals.canCreatureTypeSpawnAtLocation()` rejects a normal cube in the cell for a monster. Most
            // cells below the surface are a normal cube. Test the block here, with no light reads and no reads of the
            // blocks above and below the cell
            if (chunk.getBlock(x & 15, y, z & 15).isNormalCube()) return 0;

            if (chunk.getSavedLightValue(EnumSkyBlock.Block, x & 15, y, z & 15) >= 8 || !SpawnerAnimals
                    .canCreatureTypeSpawnAtLocation(EnumCreatureType.monster, chunk.worldObj, x, y, z)) {
                return 0;
            }

            // the size of a zombie, a creeper and a skeleton that is not a wither skeleton. A taller or wider monster,
            // such as an enderman or a spider, needs more room
            final double MOB_WIDTH = 0.6;
            final double MOB_HEIGHT = 1.8;
            spawnBox.minX = x + 0.5 - MOB_WIDTH / 2;
            spawnBox.maxX = x + 0.5 + MOB_WIDTH / 2;
            spawnBox.minY = y;
            spawnBox.maxY = y + MOB_HEIGHT;
            spawnBox.minZ = z + 0.5 - MOB_WIDTH / 2;
            spawnBox.maxZ = z + 0.5 + MOB_WIDTH / 2;
            if (!chunk.worldObj.getCollidingBoundingBoxes(dummyEntity, spawnBox).isEmpty()
                    || chunk.worldObj.isAnyLiquid(spawnBox)) {
                return 0;
            }

            return (byte) (chunk.getSavedLightValue(EnumSkyBlock.Sky, x & 15, y, z & 15) >= 8 ? 1 : 2);
        }

        /**
         * The top of the block at height {@code y}, in fractions of a block with {@link MobOverlayScan#COVER_TOP_STEPS}
         * as the denominator, or 0 for a tall block.
         */
        private int readCoverTop(int y) {
            final double MAX_COVER_TOP = 0.25;
            final Block block = chunk.getBlock(x & 15, y, z & 15);
            block.setBlockBoundsBasedOnState(chunk.worldObj, x, y, z);
            final double top = block.getBlockBoundsMaxY();
            // a mark on the bounds of a plant or a sloped rail would float above most of the block. Keep the mark at
            // the floor for a tall block
            return top > MAX_COVER_TOP ? 0 : (int) Math.ceil(top * COVER_TOP_STEPS);
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
