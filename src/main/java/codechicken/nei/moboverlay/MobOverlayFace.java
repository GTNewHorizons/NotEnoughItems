package codechicken.nei.moboverlay;

import net.minecraft.client.renderer.Tessellator;

/** Vertex builder for one block's top face in the mob spawn overlay. */
public final class MobOverlayFace {

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

    public void setNeighbourMode(int offsetX, int offsetZ, byte spawnMode) {
        neighbourModes[indexNeighbour(offsetX, offsetZ)] = spawnMode;
    }

    public void applyColor(MobOverlayStyle.Colouring colouring, float alpha) {
        final boolean isNightOnly = neighbourModes[indexNeighbour(0, 0)] == MobOverlayScan.NIGHT_ONLY;
        final int rgb = switch (colouring) {
            case OVERLAY -> isNightOnly ? 0xFFFF00 : 0xFF0000;
            case STICKER -> isNightOnly ? 0xFFFFFF : 0xFF7373;
        };
        Tessellator.instance.setColorRGBA_I(rgb, Math.round(alpha * 255));
    }

    public boolean isJoined(int offsetX, int offsetZ) {
        final byte neighbourMode = neighbourModes[indexNeighbour(offsetX, offsetZ)];
        // no band along the edge of the scan
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

    /** The texture is upright to a player facing north (-z). */
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
        Tessellator.instance.setTextureUV(offsetX, offsetZ);
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
