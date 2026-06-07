package codechicken.nei.api;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.util.StatCollector;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A color constant supporting both ARGB and RGB formats, with resource pack override and per-instance caching.
 * <p>
 * Use {@link Factory} to avoid repeating the mod ID on every line:
 *
 * <pre>
 * <code>
 *  public static class ColorUtils {
 *      private static final ColorResource.Factory color = new ColorResource.Factory("mymod");
 *
 *      public static final ColorResource
 *      // spotless:off
 *          background      = color.rgb("background",       "0x202020"),
 *
 *          guiOverlayWhite = color.argb("guiOverlayWhite", "0x80FFFFFF"),
 *          text            = color.argb("text",            "0xFFFFFFFF");
 *      // spotless:on
 *  }
 * </code>
 * </pre>
 * <p>
 * Then use the color anywhere:
 * 
 * <pre>
 * <code>
 *  GuiDraw.drawRect(x, y, w, h, ColorUtils.background.getColor());
 * </code>
 * </pre>
 * <p>
 * Resource packs override colors via a lang file entry: {@code color.resource.mymod.background=80FF20AA}
 * <p>
 * ARGB colors use 8-char hex (AARRGGBB). RGB colors use 6-char hex (RRGGBB), alpha is always FF.
 * <p>
 * Colors are resolved on every resource reload (F3+T) via {@link CacheReloadListener}
 */
public class ColorResource {

    private static final Logger LOG = LogManager.getLogger(ColorResource.class);
    private static final Set<ColorResource> INSTANCES = Collections.newSetFromMap(new WeakHashMap<>());

    private final String langKey;
    private final int defaultColor;
    private final boolean argb;
    private volatile int cachedColor;

    /**
     * @param modId the mod ID used as the namespace in the lang key
     * @param name  the color name used in the lang key
     * @param hex   default color — AARRGGBB if {@code argb} is true, RRGGBB otherwise
     * @param argb  true to include the alpha channel, false to force alpha to FF
     */
    public ColorResource(String modId, String name, String hex, boolean argb) {
        this.langKey = "color.resource." + modId + "." + name;
        this.argb = argb;
        this.defaultColor = parseHex(hex, argb);
        this.cachedColor = resolveColor();
        synchronized (INSTANCES) {
            INSTANCES.add(this);
        }
    }

    private static String stripPrefix(String hex) {
        String s = hex.trim();
        if (s.startsWith("0x") || s.startsWith("0X")) return s.substring(2);
        if (s.startsWith("#")) return s.substring(1);
        return s;
    }

    private static int parseHex(String hex, boolean argb) {
        long value = Long.parseLong(stripPrefix(hex), 16);
        return argb ? (int) value : (int) (0xFF000000L | value);
    }

    /** Lang key used to look up a resource pack override. */
    public String getLangKey() {
        return langKey;
    }

    /**
     * Returns the resolved ARGB color value. Checks the resource pack lang file first; falls back to the default color.
     * Updated on every resource reload (F3+T).
     * <p>
     * Example usage:
     * 
     * <pre>
     * <code>
     *  GuiDraw.drawRect(x, y, w, h, ColorUtils.background.getColor());
     * </code>
     * </pre>
     */
    public int getColor() {
        return cachedColor;
    }

    private int resolveColor() {
        if (StatCollector.canTranslate(langKey)) {
            String value = stripPrefix(StatCollector.translateToLocal(langKey));
            try {
                if (!argb && value.length() > 6) {
                    LOG.warn(
                            "[ColorResource] Lang key '{}' received ARGB hex '{}' but this color is RGB-only — alpha will be ignored.",
                            langKey,
                            value);
                }
                long parsed = Long.parseLong(value, 16);
                return argb ? (int) parsed : (int) (0xFF000000L | parsed);
            } catch (NumberFormatException e) {
                LOG.warn("[ColorResource] Invalid hex '{}' for lang key '{}', using default.", value, langKey);
                return defaultColor;
            }
        }
        return defaultColor;
    }

    /**
     * Factory that holds a mod ID so it does not need to be repeated on every color declaration.
     * <p>
     * Example usage:
     *
     * <pre>
     * <code>
     *  private static final ColorResource.Factory colors = new ColorResource.Factory("mymod");
     *  public static final ColorResource
     *      background = colors.argb("background", "0xFF202020"),
     *      text       = colors.rgb("text",        "0xFFFFFF");
     * </code>
     * </pre>
     */
    public static class Factory {

        private final String modId;

        public Factory(String modId) {
            this.modId = modId;
        }

        /** Creates an ARGB color (AARRGGBB hex). */
        public ColorResource argb(String name, String hex) {
            return new ColorResource(modId, name, hex, true);
        }

        /** Creates an RGB color (RRGGBB hex), alpha is always FF. */
        public ColorResource rgb(String name, String hex) {
            return new ColorResource(modId, name, hex, false);
        }
    }

    /** Resolves all color values on resource reload (F3+T). */
    public static class CacheReloadListener implements IResourceManagerReloadListener {

        @Override
        public void onResourceManagerReload(IResourceManager resourceManager) {
            synchronized (INSTANCES) {
                for (ColorResource instance : INSTANCES) {
                    instance.cachedColor = instance.resolveColor();
                }
            }
        }
    }
}
