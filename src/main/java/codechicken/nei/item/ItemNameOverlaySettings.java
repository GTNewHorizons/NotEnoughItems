package codechicken.nei.item;

import java.util.Objects;

public class ItemNameOverlaySettings {

    private static final ItemNameOverlaySettings DEFAULT = new ItemNameOverlaySettings(
            ItemNameOverlayMode.OFF,
            0.5f,
            0.5625f,
            0.3f,
            18,
            0,
            2,
            true);

    public final ItemNameOverlayMode mode;
    public final float fontScale;
    public final float unicodeFontScale;
    public final float minimumScale;
    public final int width;
    public final int maxCharacters;
    public final int maxLines;
    public final boolean outline;

    public ItemNameOverlaySettings(ItemNameOverlayMode mode, float fontScale, float unicodeFontScale,
            float minimumScale, int width, int maxCharacters, int maxLines, boolean outline) {
        this.mode = Objects.requireNonNull(mode);
        this.fontScale = normalizeScale(fontScale, 0.5f);
        this.unicodeFontScale = normalizeScale(unicodeFontScale, 0.5625f);
        this.minimumScale = normalizeScale(minimumScale, 0.3f);
        this.width = Math.max(4, Math.min(32, width));
        this.maxCharacters = Math.max(0, Math.min(256, maxCharacters));
        this.maxLines = Math.max(1, Math.min(4, maxLines));
        this.outline = outline;
    }

    public static ItemNameOverlaySettings defaults() {
        return DEFAULT;
    }

    private static float normalizeScale(float value, float fallback) {
        return Float.isFinite(value) ? Math.max(0.1f, Math.min(2f, value)) : fallback;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ItemNameOverlaySettings settings && getClass() == other.getClass()
                && mode == settings.mode
                && fontScale == settings.fontScale
                && unicodeFontScale == settings.unicodeFontScale
                && minimumScale == settings.minimumScale
                && width == settings.width
                && maxCharacters == settings.maxCharacters
                && maxLines == settings.maxLines
                && outline == settings.outline;
    }

    @Override
    public int hashCode() {
        int hash = mode.hashCode();
        hash = 31 * hash + Float.floatToIntBits(fontScale);
        hash = 31 * hash + Float.floatToIntBits(unicodeFontScale);
        hash = 31 * hash + Float.floatToIntBits(minimumScale);
        hash = 31 * hash + width;
        hash = 31 * hash + maxCharacters;
        hash = 31 * hash + maxLines;
        return 31 * hash + Boolean.hashCode(outline);
    }
}
