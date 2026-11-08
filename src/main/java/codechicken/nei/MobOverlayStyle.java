package codechicken.nei;

import java.util.Locale;
import java.util.StringJoiner;

import javax.annotation.Nullable;

import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

/** A mark of the mob spawn overlay. {@code world.overlays.mobOverlayStyle} is saved as {@link #formatName()}. */
public enum MobOverlayStyle {

    /** NEI's original cross from corner to corner. */
    X(GL11.GL_LINES) {

        @Override
        public void addVertices(MobOverlayFace face) {
            face.addVertex(0, 0);
            face.addVertex(1, 1);
            face.addVertex(1, 0);
            face.addVertex(0, 1);
        }
    },

    /**
     * A light tint, with a stronger band along the outline of each area of one spawn mode, like a connected texture.
     */
    CONNECTED(GL11.GL_QUADS) {

        @Override
        public void addVertices(MobOverlayFace face) {
            final double BAND_WIDTH = 1.0 / 16;
            face.addFullFace();
            face.applyColor(Colouring.OVERLAY, MARK_ALPHA);
            for (int offsetX = -1; offsetX <= 1; offsetX++) {
                for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                    // a corner cell is drawn when either edge next to the corner has a band, and on an inside corner
                    final boolean isBand = !(face.isJoined(offsetX, offsetZ) && face.isJoined(offsetX, 0)
                            && face.isJoined(0, offsetZ));
                    if (isBand) face.addCell(offsetX, offsetZ, BAND_WIDTH);
                }
            }
        }
    },

    /** A small square in the middle of the block, with no tint. */
    SQUARE(GL11.GL_QUADS) {

        @Override
        public void addVertices(MobOverlayFace face) {
            final double CENTRE = 0.5;
            final double HALF_SIDE = 7.0 / 64;
            face.addVertex(CENTRE - HALF_SIDE, CENTRE - HALF_SIDE);
            face.addVertex(CENTRE - HALF_SIDE, CENTRE + HALF_SIDE);
            face.addVertex(CENTRE + HALF_SIDE, CENTRE + HALF_SIDE);
            face.addVertex(CENTRE + HALF_SIDE, CENTRE - HALF_SIDE);
        }
    },

    /** The GTNH logo without the globe. */
    GTNH_LOGO("gtnh_logo", Colouring.OVERLAY) {

        @Override
        public void addVertices(MobOverlayFace face) {
            face.addTexturedFace();
        }
    },

    /** The pufferfish sticker. */
    PUFFERFISH("pufferfish", Colouring.STICKER) {

        @Override
        public void addVertices(MobOverlayFace face) {
            face.addTexturedFace();
        }
    };

    public static final MobOverlayStyle DEFAULT = CONNECTED;

    private static final float TINT_ALPHA = 0.15F;
    // at the tint's opacity, a small mark is too faint to see
    private static final float MARK_ALPHA = 0.45F;

    public final int glMode;
    @Nullable
    public final ResourceLocation texture;
    public final Colouring colouring;

    MobOverlayStyle(int glMode) {
        this.glMode = glMode;
        this.texture = null;
        this.colouring = Colouring.OVERLAY;
    }

    MobOverlayStyle(String textureName, Colouring colouring) {
        this.glMode = GL11.GL_QUADS;
        this.texture = new ResourceLocation("nei", "textures/overlay/" + textureName + ".png");
        this.colouring = colouring;
    }

    /** Case-insensitive. Returns {@link #DEFAULT} for an unknown name. */
    public static MobOverlayStyle parseStyle(String rawStyle) {
        for (MobOverlayStyle candidate : values()) {
            if (candidate.formatName().equalsIgnoreCase(rawStyle)) return candidate;
        }
        return DEFAULT;
    }

    /** @return the {@link #formatName} of every style, separated by commas */
    public static String formatAllNames() {
        final StringJoiner names = new StringJoiner(", ");
        for (MobOverlayStyle style : values()) {
            names.add(style.formatName());
        }
        return names.toString();
    }

    public String formatName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String getLangKey() {
        return switch (this) {
            case X -> "nei.options.world.overlays.mobOverlayStyle.x";
            case CONNECTED -> "nei.options.world.overlays.mobOverlayStyle.connected";
            case SQUARE -> "nei.options.world.overlays.mobOverlayStyle.square";
            case GTNH_LOGO -> "nei.options.world.overlays.mobOverlayStyle.gtnh_logo";
            case PUFFERFISH -> "nei.options.world.overlays.mobOverlayStyle.pufferfish";
        };
    }

    public boolean isFilled() {
        return glMode == GL11.GL_QUADS;
    }

    public float computeAlpha() {
        return switch (this) {
            case CONNECTED -> TINT_ALPHA;
            case SQUARE -> MARK_ALPHA;
            // the alpha is in the PNGs
            case X, GTNH_LOGO, PUFFERFISH -> 1.0F;
        };
    }

    public abstract void addVertices(MobOverlayFace face);

    public enum Colouring {
        /** Yellow where mobs spawn only at night, red where mobs spawn at any time. */
        OVERLAY,
        /** The texture's colours, tinted red where mobs spawn at any time. */
        STICKER
    }
}
