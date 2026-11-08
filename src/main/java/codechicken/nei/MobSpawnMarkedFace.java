package codechicken.nei;

import net.minecraft.client.renderer.Tessellator;

/** The vertices of the mob spawn overlay on the top face of one block. */
public final class MobSpawnMarkedFace {

    public static final byte OUTSIDE_SCAN = -2;

    private static final int NEIGHBOURHOOD_SIZE = 3;

    private final byte[] neighbourModes = new byte[NEIGHBOURHOOD_SIZE * NEIGHBOURHOOD_SIZE];
    private double x;
    private double y;
    private double z;

    /** {@code (x, y, z)} is the north-west corner of the block's top face. */
    public void moveTo(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /** An offset of -1 is west or north, and 1 is east or south. Offset {@code (0, 0)} is this block. */
    public void setNeighbourMode(int offsetX, int offsetZ, byte spawnMode) {
        neighbourModes[indexNeighbour(offsetX, offsetZ)] = spawnMode;
    }

    public void applyColor(MobSpawnOverlayStyle.Colouring colouring, float alpha) {
        final boolean isNightOnly = neighbourModes[indexNeighbour(0, 0)] == MobSpawnScan.NIGHT_ONLY;
        final int rgb = switch (colouring) {
            case OVERLAY -> isNightOnly ? 0xFFFF00 : 0xFF0000;
            case STICKER -> isNightOnly ? 0xFFFFFF : 0xFF7373;
        };
        Tessellator.instance.setColorRGBA_I(rgb, Math.round(alpha * 255));
    }

    /**
     * True when the neighbour has this block's spawn mode. A neighbour outside the scan counts as joined, so no band is
     * drawn along the edge of the scan.
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

    /** Add one cell of a 3 x 3 grid over the face. The outer rows and columns of the grid are {@code width} wide. */
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

    /** The texture is upright to a player facing south (+z). */
    public void addTexturedFace() {
        addTexturedVertex(0, 0);
        addTexturedVertex(0, 1);
        addTexturedVertex(1, 1);
        addTexturedVertex(1, 0);
    }

    /** @param offsetX 0 at the west edge, 1 at the east edge */
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
