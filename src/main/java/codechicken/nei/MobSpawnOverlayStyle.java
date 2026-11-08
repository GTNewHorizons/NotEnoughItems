package codechicken.nei;

import java.util.Locale;

import javax.annotation.Nullable;

import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

/**
 * The shape drawn on the top face of each block where mobs can spawn, by the mob spawn overlay. The setting
 * {@code world.overlays.mobSpawnStyle} has the lower-case name of the style.
 */
public enum MobSpawnOverlayStyle {

    /** A cross from corner to corner. */
    X(GL11.GL_LINES) {

        @Override
        public void addVertices(MobSpawnMarkedFace face) {
            face.addVertex(0, 0);
            face.addVertex(1, 1);
            face.addVertex(1, 0);
            face.addVertex(0, 1);
        }
    },

    /**
     * A translucent tint over the face, and a stronger band inside each edge to a block of another spawn mode. The band
     * follows the outline of each region of one spawn mode, like the border of a connected texture.
     */
    CONNECTED(GL11.GL_QUADS) {

        @Override
        public void addVertices(MobSpawnMarkedFace face) {
            final double BAND_WIDTH = 1.0 / 16;
            face.addFullFace();
            face.applyColor(Colouring.OVERLAY, MARK_ALPHA);
            for (int offsetX = -1; offsetX <= 1; offsetX++) {
                for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                    // a corner cell of the band is drawn when either side of the corner has a band, and on the inside
                    // corner of an L-shaped region
                    final boolean isBand = !(face.isJoined(offsetX, offsetZ) && face.isJoined(offsetX, 0)
                            && face.isJoined(0, offsetZ));
                    if (isBand) face.addCell(offsetX, offsetZ, BAND_WIDTH);
                }
            }
        }
    },

    /** A small translucent square in the middle of the face, with no tint. */
    SQUARE(GL11.GL_QUADS) {

        @Override
        public void addVertices(MobSpawnMarkedFace face) {
            final double CENTRE = 0.5;
            final double HALF_SIDE = 7.0 / 64;
            face.addVertex(CENTRE - HALF_SIDE, CENTRE - HALF_SIDE);
            face.addVertex(CENTRE - HALF_SIDE, CENTRE + HALF_SIDE);
            face.addVertex(CENTRE + HALF_SIDE, CENTRE + HALF_SIDE);
            face.addVertex(CENTRE + HALF_SIDE, CENTRE - HALF_SIDE);
        }
    },

    /** The gear and the letters of the GTNH logo, as a mask in the overlay colour. */
    GTNH_LOGO("gtnh_logo", Colouring.OVERLAY) {

        @Override
        public void addVertices(MobSpawnMarkedFace face) {
            face.addTexturedFace();
        }
    },

    /** A sticker of a yellow pufferfish, in the sticker's colours. */
    PUFFERFISH("pufferfish", Colouring.STICKER) {

        @Override
        public void addVertices(MobSpawnMarkedFace face) {
            face.addTexturedFace();
        }
    };

    public static final MobSpawnOverlayStyle DEFAULT = CONNECTED;

    private static final float TINT_ALPHA = 0.15F;
    // the band of `CONNECTED` and the mark of `SQUARE` cover a small part of the face. At `TINT_ALPHA`, the band and
    // the mark are too faint to see
    private static final float MARK_ALPHA = 0.45F;

    /** The draw mode for {@code Tessellator.startDrawing}. */
    public final int glMode;
    @Nullable
    public final ResourceLocation texture;
    public final Colouring colouring;

    MobSpawnOverlayStyle(int glMode) {
        this.glMode = glMode;
        this.texture = null;
        this.colouring = Colouring.OVERLAY;
    }

    /** A style of one textured quad over the face, from {@code textures/overlay/<textureName>.png} of NEI. */
    MobSpawnOverlayStyle(String textureName, Colouring colouring) {
        this.glMode = GL11.GL_QUADS;
        this.texture = new ResourceLocation("nei", "textures/overlay/" + textureName + ".png");
        this.colouring = colouring;
    }

    /** @return the style named {@code rawStyle} in any case, or {@link #DEFAULT} for an unknown name */
    public static MobSpawnOverlayStyle parseStyle(String rawStyle) {
        for (MobSpawnOverlayStyle candidate : values()) {
            if (candidate.formatName().equalsIgnoreCase(rawStyle)) return candidate;
        }
        return DEFAULT;
    }

    public String formatName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String getLangKey() {
        return switch (this) {
            case X -> "nei.options.world.overlays.mobSpawnStyle.x";
            case CONNECTED -> "nei.options.world.overlays.mobSpawnStyle.connected";
            case SQUARE -> "nei.options.world.overlays.mobSpawnStyle.square";
            case GTNH_LOGO -> "nei.options.world.overlays.mobSpawnStyle.gtnh_logo";
            case PUFFERFISH -> "nei.options.world.overlays.mobSpawnStyle.pufferfish";
        };
    }

    public boolean isFilled() {
        return glMode == GL11.GL_QUADS;
    }

    public float computeAlpha() {
        return switch (this) {
            case CONNECTED -> TINT_ALPHA;
            case SQUARE -> MARK_ALPHA;
            // the lines of `X` are opaque. The textures have the opacity
            case X, GTNH_LOGO, PUFFERFISH -> 1.0F;
        };
    }

    public abstract void addVertices(MobSpawnMarkedFace face);

    /** The colours of a style. */
    public enum Colouring {
        /**
         * Yellow where mobs spawn only at night, red where mobs spawn at any time, through the alpha of any texture.
         */
        OVERLAY,
        /** The colours of the texture, tinted red where mobs spawn at any time. */
        STICKER
    }
}
