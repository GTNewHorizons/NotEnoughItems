package codechicken.nei;

import net.minecraft.client.renderer.Tessellator;

/**
 * The top face of one block where mobs can spawn, in the coordinates of the overlay draw, with the spawn modes of the 8
 * blocks around the block at the same height. One instance is moved from block to block, so the draw allocates nothing.
 * An offset of -1 is west or north, and 1 is east or south.
 */
public final class MobSpawnMarkedFace {

    /** The spawn mode of a neighbour outside {@link MobSpawnScan}. */
    public static final byte OUTSIDE_SCAN = -2;

    private static final int NEIGHBOURHOOD_SIZE = 3;

    private final byte[] neighbourModes = new byte[NEIGHBOURHOOD_SIZE * NEIGHBOURHOOD_SIZE];
    private double x;
    private double y;
    private double z;

    /** Move to the block whose north-west top corner is at {@code (x, y, z)}. */
    public void moveTo(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /** The mode at offset {@code (0, 0)} is the spawn mode of this face. */
    public void setNeighbourMode(int offsetX, int offsetZ, byte spawnMode) {
        neighbourModes[indexNeighbour(offsetX, offsetZ)] = spawnMode;
    }

    /** Set the colour of the next vertices, for the spawn mode of this face. */
    public void applyColor(MobSpawnOverlayStyle.Colouring colouring, float alpha) {
        final boolean isNightOnly = neighbourModes[indexNeighbour(0, 0)] == MobSpawnScan.NIGHT_ONLY;
        final int rgb = switch (colouring) {
            case OVERLAY -> isNightOnly ? 0xFFFF00 : 0xFF0000;
            case STICKER -> isNightOnly ? 0xFFFFFF : 0xFF7373;
        };
        Tessellator.instance.setColorRGBA_I(rgb, Math.round(alpha * 255));
    }

    /**
     * Whether the neighbour has the spawn mode of this face. A neighbour outside {@link MobSpawnScan} counts as joined:
     * the edge of the scan is no edge between spawn modes.
     */
    public boolean isJoined(int offsetX, int offsetZ) {
        final byte neighbourMode = neighbourModes[indexNeighbour(offsetX, offsetZ)];
        return neighbourMode == neighbourModes[indexNeighbour(0, 0)] || neighbourMode == OUTSIDE_SCAN;
    }

    public void addFullFace() {
        addVertex(0, 0);
        addVertex(0, 1);
        addVertex(1, 1);
        addVertex(1, 0);
    }

    /**
     * Add a quad on one cell of a 3 x 3 grid over the face. The cells along the edges are {@code width} wide, and the
     * cell at offset {@code (0, 0)} fills the rest.
     */
    public void addCell(int offsetX, int offsetZ, double width) {
        final double west = computeCellStart(offsetX, width);
        final double east = 1 - computeCellStart(-offsetX, width);
        final double north = computeCellStart(offsetZ, width);
        final double south = 1 - computeCellStart(-offsetZ, width);
        addVertex(west, north);
        addVertex(west, south);
        addVertex(east, south);
        addVertex(east, north);
    }

    /** Add the face as one quad with the whole texture, upright to a player facing south (+z). */
    public void addTexturedFace() {
        addTexturedVertex(0, 0);
        addTexturedVertex(0, 1);
        addTexturedVertex(1, 1);
        addTexturedVertex(1, 0);
    }

    /** @param offsetX from 0 at the west edge to 1 at the east edge */
    public void addVertex(double offsetX, double offsetZ) {
        Tessellator.instance.addVertex(x + offsetX, y, z + offsetZ);
    }

    private void addTexturedVertex(double offsetX, double offsetZ) {
        Tessellator.instance.setTextureUV(1 - offsetX, 1 - offsetZ);
        addVertex(offsetX, offsetZ);
    }

    private static int indexNeighbour(int offsetX, int offsetZ) {
        return (offsetX + 1) * NEIGHBOURHOOD_SIZE + offsetZ + 1;
    }

    private static double computeCellStart(int offset, double width) {
        if (offset < 0) return 0;
        return offset == 0 ? width : 1 - width;
    }
}
