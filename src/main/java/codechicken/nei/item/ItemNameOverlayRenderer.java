package codechicken.nei.item;

import java.lang.reflect.Method;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import com.gtnewhorizon.gtnhlib.util.font.FontRendering;
import com.gtnewhorizon.gtnhlib.util.font.IFontParameters;

import codechicken.nei.LRUCache;
import codechicken.nei.recipe.StackInfo;
import cpw.mods.fml.common.Loader;

public class ItemNameOverlayRenderer {

    public static final ItemNameOverlayRenderer INSTANCE = new ItemNameOverlayRenderer();

    private static final int[] OUTLINE_STEPS = { -1, -1, 1, 0, 1, 0, 0, 1, 0, 1, -1, 0, -1, 0, 0, -1 };

    private final FloatBuffer matrixBuffer = BufferUtils.createFloatBuffer(16);
    private final LRUCache<LayoutKey, LabelLayout> layouts = new LRUCache<>(2048);
    private final LayoutKey lookupKey = new LayoutKey(null, "", 0, 0, false);
    private final GuiScaleCache guiScaleCache = new GuiScaleCache();
    private ItemNameOverlaySettings settings = ItemNameOverlaySettings.defaults();
    private Object fontState;
    private long cacheRevision;

    public void render(ItemNameOverlayMode mode, ItemStack stack, FontRenderer font, int x, int y,
            int availableHeight) {
        if (mode == ItemNameOverlayMode.OFF || stack == null || stack.getItem() == null) {
            return;
        }

        final boolean fluidDisplay = StackInfo.isFluidDisplayItem(stack);
        if (!mode.includes(fluidDisplay)) {
            return;
        }

        final String name;
        if (fluidDisplay) {
            final FluidStack fluid = StackInfo.getFluid(stack);
            name = fluid != null ? fluid.getLocalizedName() : stack.getDisplayName();
            // Fluid renderers own their amount overlay, so reserve its bottom row.
            availableHeight = Math.min(availableHeight, font.getUnicodeFlag() ? 8 : 11);
        } else {
            name = stack.getDisplayName();
        }

        final ItemNameOverlaySettings currentSettings = settings;
        drawLabel(
                font,
                name,
                x + (16 - currentSettings.width) / 2f,
                (float) y,
                currentSettings.width,
                availableHeight,
                currentSettings);
    }

    public void drawLabel(FontRenderer font, String text, int x, int y, int width, int height) {
        drawLabel(font, text, (float) x, (float) y, width, height, settings);
    }

    private void drawLabel(FontRenderer font, String text, float x, float y, int width, int height,
            ItemNameOverlaySettings currentSettings) {
        if (text == null || text.isEmpty() || width <= 0 || height <= 0) {
            return;
        }

        lookupKey.set(font, text, width, height, font.getUnicodeFlag());
        LabelLayout layout = layouts.get(lookupKey);
        if (layout == null) {
            // Stored keys are copies; the reusable lookup key never enters the cache.
            final LayoutKey key = new LayoutKey(lookupKey);
            layout = createLayout(key, currentSettings);
            layouts.put(key, layout);
        }
        if (layout.lines.length == 0) {
            return;
        }

        final int guiScale = readRenderTransform();
        final float scaleX = matrixBuffer.get(0);
        final float scaleY = matrixBuffer.get(5);
        final float translateX = matrixBuffer.get(12);
        final float translateY = matrixBuffer.get(13);
        final float pixelScaleX = Math.abs(scaleX) * guiScale;
        final float pixelScaleY = Math.abs(scaleY) * guiScale;
        if (pixelScaleX <= 0 || pixelScaleY <= 0) {
            return;
        }
        final float inverseScale = 1f / layout.scale;
        final float pixelWidth = inverseScale / pixelScaleX;
        final float pixelHeight = inverseScale / pixelScaleY;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glPushMatrix();
        try {
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glTranslatef(x, y, 0);
            GL11.glScalef(layout.scale, layout.scale, 1);

            for (LabelLine line : layout.lines) {
                final float alignedX = (alignToPixel(x + line.x, translateX, scaleX, guiScale) - x) * inverseScale;
                final float alignedY = (alignToPixel(y + line.y, translateY, scaleY, guiScale) - y) * inverseScale;
                GL11.glTranslatef(alignedX, alignedY, 0);
                if (currentSettings.outline) {
                    drawOutlinedLine(font, line.text, pixelWidth, pixelHeight);
                } else {
                    font.drawString(line.text, 0, 0, 0xFFFFFFFF);
                }
                GL11.glTranslatef(-alignedX, -alignedY, 0);
            }
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    public void setSettings(ItemNameOverlaySettings settings) {
        Objects.requireNonNull(settings);
        if (!this.settings.equals(settings)) {
            this.settings = settings;
            clearCache();
        }
    }

    public void refreshFontState() {
        final Object currentState = FontAccess.currentState();
        if (!Objects.equals(fontState, currentState)) {
            fontState = currentState;
            clearCache();
        }
    }

    public void clearCache() {
        layouts.clear();
        lookupKey.set(null, "", 0, 0, false);
        cacheRevision++;
    }

    public long getCacheRevision() {
        return cacheRevision;
    }

    private int readRenderTransform() {
        final Minecraft minecraft = Minecraft.getMinecraft();
        final int guiScale = guiScaleCache.getScaleFactor(minecraft);
        matrixBuffer.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, matrixBuffer);
        return guiScale;
    }

    private float alignToPixel(float coordinate, float translation, float scale, int guiScale) {
        final float screenPosition = (translation + coordinate * scale) * guiScale;
        return (Math.round(screenPosition) / (float) guiScale - translation) / scale;
    }

    private LabelLayout createLayout(LayoutKey key, ItemNameOverlaySettings settings) {
        final FontRenderer font = key.font;
        final String text = sanitizeText(key.text);
        final float minimumScale = settings.minimumScale;
        if (text.isEmpty()) {
            return new LabelLayout(font, key.width, Collections.emptyList(), minimumScale);
        }

        final boolean unicode = key.unicode || hasUnicodeCharacters(text);
        final float preferredScale = Math.max(minimumScale, unicode ? settings.unicodeFontScale : settings.fontScale);
        final float lineHeight = FontAccess.lineHeight(font);
        final float singleLineScale = Math.min(
                preferredScale,
                Math.min(key.width / (float) Math.max(1, getTextWidth(font, text)), key.height / (lineHeight + 1)));
        if (singleLineScale >= minimumScale && fitsCharacterLimit(text, settings.maxCharacters)) {
            return new LabelLayout(font, key.width, Collections.singletonList(text), singleLineScale);
        }

        int availableLines = 1;
        for (int lineCount = 2; lineCount <= settings.maxLines; lineCount++) {
            final float wrappedScale = Math.min(preferredScale, key.height / (lineHeight * lineCount + 1));
            if (wrappedScale < minimumScale) {
                break;
            }
            availableLines = lineCount;
            final int maximumWidth = (int) (key.width / minimumScale);
            List<String> completeLines = wrapText(font, text, maximumWidth, lineCount, settings.maxCharacters);
            if (fitsWidth(font, completeLines, maximumWidth, settings.maxCharacters)) {
                // Find the narrowest complete split before reducing the configured multi-line scale.
                int lowerWidth = 1;
                int upperWidth = maximumWidth;
                while (lowerWidth < upperWidth) {
                    final int candidateWidth = lowerWidth + (upperWidth - lowerWidth) / 2;
                    final List<String> candidateLines = wrapText(
                            font,
                            text,
                            candidateWidth,
                            lineCount,
                            settings.maxCharacters);
                    if (fitsWidth(font, candidateLines, candidateWidth, settings.maxCharacters)) {
                        upperWidth = candidateWidth;
                        completeLines = candidateLines;
                    } else {
                        lowerWidth = candidateWidth + 1;
                    }
                }

                int widestLine = 1;
                for (String line : completeLines) {
                    widestLine = Math.max(widestLine, getTextWidth(font, line));
                }
                final float completeScale = Math.min(wrappedScale, key.width / (float) widestLine);
                return new LabelLayout(font, key.width, completeLines, completeScale);
            }

        }

        final float croppedScale = Math.min(preferredScale, key.height / (lineHeight * availableLines + 1));
        if (croppedScale < minimumScale) {
            return new LabelLayout(font, key.width, Collections.emptyList(), croppedScale);
        }

        final int textWidth = (int) (key.width / croppedScale);
        final List<String> shortenedLines = new ArrayList<>(
                wrapText(font, text, textWidth, availableLines, settings.maxCharacters));
        if (shortenedLines.isEmpty()) {
            final String line = ellipsize(font, text, textWidth, settings.maxCharacters);
            if (!line.isEmpty()) {
                shortenedLines.add(line);
            }
        } else {
            final int lastLine = shortenedLines.size() - 1;
            shortenedLines
                    .set(lastLine, ellipsize(font, shortenedLines.get(lastLine), textWidth, settings.maxCharacters));
        }
        return new LabelLayout(font, key.width, shortenedLines, croppedScale);
    }

    private List<String> wrapText(FontRenderer font, String text, int width, int maxLines, int maxCharacters) {
        if (text.indexOf(' ') >= 0) {
            final List<String> wordLines = wrapText(font, text, width, maxLines, maxCharacters, true);
            if (fitsWidth(font, wordLines, width, maxCharacters)) {
                return wordLines;
            }
        }
        return wrapText(font, text, width, maxLines, maxCharacters, false);
    }

    private List<String> wrapText(FontRenderer font, String text, int width, int maxLines, int maxCharacters,
            boolean wordBreaks) {
        final List<String> lines = new ArrayList<>(maxLines);
        String remainder = text;
        while (!remainder.isEmpty()) {
            if (lines.size() == maxLines - 1) {
                lines.add(remainder);
                break;
            }
            String prefix = trimToWidth(font, remainder, width);
            if (maxCharacters > 0) {
                prefix = trimCharacters(prefix, maxCharacters);
            }
            if (prefix.isEmpty()) {
                return Collections.emptyList();
            }
            int split = prefix.length();
            if (wordBreaks && split < remainder.length()) {
                final int lastSpace = prefix.lastIndexOf(' ');
                if (lastSpace > 0) {
                    split = lastSpace;
                }
            }
            lines.add(remainder.substring(0, split).trim());
            remainder = remainder.substring(split).trim();
        }
        return lines;
    }

    private boolean fitsWidth(FontRenderer font, List<String> lines, int width, int maxCharacters) {
        if (lines.isEmpty()) {
            return false;
        }
        for (String line : lines) {
            if (getTextWidth(font, line) > width || !fitsCharacterLimit(line, maxCharacters)) {
                return false;
            }
        }
        return true;
    }

    private String sanitizeText(String text) {
        text = FontAccess.preprocess(text);
        if (text.indexOf('\u00a7') >= 0) {
            final StringBuilder plain = new StringBuilder(text.length());
            for (int index = 0; index < text.length(); index++) {
                if (text.charAt(index) == '\u00a7' && index + 1 < text.length()) {
                    index++;
                } else {
                    plain.append(text.charAt(index));
                }
            }
            text = plain.toString();
        }
        return text.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').trim();
    }

    private String ellipsize(FontRenderer font, String text, int width, int maxCharacters) {
        if (getTextWidth(font, text) <= width && fitsCharacterLimit(text, maxCharacters)) {
            return text;
        }

        String suffix = trimToWidth(font, "...", width);
        if (maxCharacters > 0) {
            suffix = trimCharacters(suffix, maxCharacters);
        }
        String prefix = trimToWidth(font, text, width - getTextWidth(font, suffix)).trim();
        if (maxCharacters > 0) {
            prefix = trimCharacters(prefix, maxCharacters - suffix.codePointCount(0, suffix.length()));
        }
        String shortened = prefix + suffix;
        // Custom glyph spacing at the join must be measured as part of the complete string.
        while (!prefix.isEmpty() && getTextWidth(font, shortened) > width) {
            prefix = prefix.substring(0, prefix.offsetByCodePoints(prefix.length(), -1)).trim();
            shortened = prefix + suffix;
        }
        return shortened;
    }

    private String trimToWidth(FontRenderer font, String text, int width) {
        if (width <= 0) {
            return "";
        }

        final String trimmed = FontAccess.trimToWidth(font, text, width);
        final int length = trimmed.length();
        return length > 0 && Character.isHighSurrogate(trimmed.charAt(length - 1)) ? trimmed.substring(0, length - 1)
                : trimmed;
    }

    private static int getTextWidth(FontRenderer font, String text) {
        return FontAccess.width(font, text);
    }

    private static boolean fitsCharacterLimit(String text, int limit) {
        return limit == 0 || text.codePointCount(0, text.length()) <= limit;
    }

    private static String trimCharacters(String text, int limit) {
        final int count = Math.min(limit, text.codePointCount(0, text.length()));
        return text.substring(0, text.offsetByCodePoints(0, count));
    }

    private boolean hasUnicodeCharacters(String text) {
        for (int index = 0; index < text.length(); index++) {
            if (text.charAt(index) > 255) {
                return true;
            }
        }
        return false;
    }

    private void drawOutlinedLine(FontRenderer font, String text, float pixelWidth, float pixelHeight) {
        // Visit each neighboring screen pixel once, then return to the center.
        for (int index = 0; index < OUTLINE_STEPS.length; index += 2) {
            GL11.glTranslatef(OUTLINE_STEPS[index] * pixelWidth, OUTLINE_STEPS[index + 1] * pixelHeight, 0);
            font.drawString(text, 0, 0, 0xFF000000);
        }
        GL11.glTranslatef(pixelWidth, 0, 0);
        font.drawString(text, 0, 0, 0xFFFFFFFF);
    }

    private static class LabelLayout {

        private final LabelLine[] lines;
        private final float scale;

        private LabelLayout(FontRenderer font, int width, List<String> texts, float scale) {
            this.lines = new LabelLine[texts.size()];
            this.scale = scale;
            final float lineHeight = FontAccess.lineHeight(font);
            final float anchorOffset = (lineHeight - font.FONT_HEIGHT) / 2f;
            for (int index = 0; index < texts.size(); index++) {
                final String text = texts.get(index);
                this.lines[index] = new LabelLine(
                        text,
                        (width - getTextWidth(font, text) * scale) / 2f,
                        (1 + anchorOffset + index * lineHeight) * scale);
            }
        }
    }

    private static class LabelLine {

        private final String text;
        private final float x;
        private final float y;

        private LabelLine(String text, float x, float y) {
            this.text = text;
            this.x = x;
            this.y = y;
        }
    }

    private static class FontAccess {

        private static final boolean GTNH_FONTS = hasFontAPI();
        private static Method settingsMethod = findSettingsMethod();

        private static boolean hasFontAPI() {
            if (!Loader.isModLoaded("gtnhlib")) {
                return false;
            }
            try {
                FontRendering.class.getMethod("preprocessText", String.class);
                IFontParameters.class.getMethod("getCharWidthFine", char.class);
                IFontParameters.class.getMethod("getGlyphScaleX");
                IFontParameters.class.getMethod("getGlyphScaleY");
                IFontParameters.class.getMethod("getGlyphSpacing");
                IFontParameters.class.getMethod("getWhitespaceScale");
                return true;
            } catch (ReflectiveOperationException | LinkageError ignored) {
                return false;
            }
        }

        private static Method findSettingsMethod() {
            if (!Loader.isModLoaded("angelica")) {
                return null;
            }
            try {
                return Class.forName(
                        "com.gtnewhorizons.angelica.client.font.GlyphSettings",
                        false,
                        ItemNameOverlayRenderer.class.getClassLoader()).getMethod("current");
            } catch (ReflectiveOperationException | LinkageError ignored) {
                return null;
            }
        }

        private static Object currentState() {
            if (settingsMethod != null) {
                try {
                    return settingsMethod.invoke(null, (Object[]) null);
                } catch (ReflectiveOperationException | LinkageError ignored) {
                    settingsMethod = null;
                }
            }
            return null;
        }

        private static String preprocess(String text) {
            return GTNH_FONTS ? FontRendering.preprocessText(text) : text;
        }

        private static int width(FontRenderer font, String text) {
            // Preserve custom FontRenderer subclasses' own measurement overrides.
            return GTNH_FONTS && font.getClass() == FontRenderer.class && font instanceof IFontParameters
                    ? FontRendering.getStringWidth(text, font)
                    : font.getStringWidth(text);
        }

        private static String trimToWidth(FontRenderer font, String text, int width) {
            return GTNH_FONTS && font.getClass() == FontRenderer.class && font instanceof IFontParameters
                    ? FontRendering.trimStringToWidth(text, width, false, font)
                    : font.trimStringToWidth(text, width);
        }

        private static float lineHeight(FontRenderer font) {
            if (GTNH_FONTS && font instanceof IFontParameters parameters) {
                final float scale = parameters.getGlyphScaleY();
                if (Float.isFinite(scale) && scale > 0) {
                    return font.FONT_HEIGHT * scale;
                }
            }
            return font.FONT_HEIGHT;
        }

        private static void applyMetrics(LayoutKey key, FontRenderer font) {
            if (GTNH_FONTS && font instanceof IFontParameters parameters) {
                key.glyphScaleX = parameters.getGlyphScaleX();
                key.glyphScaleY = parameters.getGlyphScaleY();
                key.glyphSpacing = parameters.getGlyphSpacing();
                key.whitespaceScale = parameters.getWhitespaceScale();
            } else {
                key.glyphScaleX = 1;
                key.glyphScaleY = 1;
                key.glyphSpacing = 0;
                key.whitespaceScale = 1;
            }
        }
    }

    private static class GuiScaleCache {

        private int displayWidth = -1;
        private int displayHeight = -1;
        private int setting;
        private boolean unicode;
        private int scaleFactor;

        private int getScaleFactor(Minecraft minecraft) {
            final boolean currentUnicode = minecraft.func_152349_b();
            if (displayWidth != minecraft.displayWidth || displayHeight != minecraft.displayHeight
                    || setting != minecraft.gameSettings.guiScale
                    || unicode != currentUnicode) {
                displayWidth = minecraft.displayWidth;
                displayHeight = minecraft.displayHeight;
                setting = minecraft.gameSettings.guiScale;
                unicode = currentUnicode;
                scaleFactor = new ScaledResolution(minecraft, displayWidth, displayHeight).getScaleFactor();
            }
            return scaleFactor;
        }
    }

    private static class LayoutKey {

        private FontRenderer font;
        private String text;
        private int width;
        private int height;
        private int fontHeight;
        private float glyphScaleX;
        private float glyphScaleY;
        private float glyphSpacing;
        private float whitespaceScale;
        private boolean unicode;
        private int hash;

        private LayoutKey(FontRenderer font, String text, int width, int height, boolean unicode) {
            set(font, text, width, height, unicode);
        }

        private LayoutKey(LayoutKey key) {
            this.font = key.font;
            this.text = key.text;
            this.width = key.width;
            this.height = key.height;
            this.fontHeight = key.fontHeight;
            this.glyphScaleX = key.glyphScaleX;
            this.glyphScaleY = key.glyphScaleY;
            this.glyphSpacing = key.glyphSpacing;
            this.whitespaceScale = key.whitespaceScale;
            this.unicode = key.unicode;
            this.hash = key.hash;
        }

        private void set(FontRenderer font, String text, int width, int height, boolean unicode) {
            this.font = font;
            this.text = text;
            this.width = width;
            this.height = height;
            this.fontHeight = font != null ? font.FONT_HEIGHT : 0;
            if (font != null) {
                FontAccess.applyMetrics(this, font);
            } else {
                this.glyphScaleX = 1;
                this.glyphScaleY = 1;
                this.glyphSpacing = 0;
                this.whitespaceScale = 1;
            }
            this.unicode = unicode;
            int hash = System.identityHashCode(font);
            hash = 31 * hash + text.hashCode();
            hash = 31 * hash + width;
            hash = 31 * hash + height;
            hash = 31 * hash + fontHeight;
            hash = 31 * hash + Float.floatToIntBits(glyphScaleX);
            hash = 31 * hash + Float.floatToIntBits(glyphScaleY);
            hash = 31 * hash + Float.floatToIntBits(glyphSpacing);
            hash = 31 * hash + Float.floatToIntBits(whitespaceScale);
            this.hash = 31 * hash + Boolean.hashCode(unicode);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof LayoutKey key && font == key.font
                    && text.equals(key.text)
                    && width == key.width
                    && height == key.height
                    && fontHeight == key.fontHeight
                    && Float.compare(glyphScaleX, key.glyphScaleX) == 0
                    && Float.compare(glyphScaleY, key.glyphScaleY) == 0
                    && Float.compare(glyphSpacing, key.glyphSpacing) == 0
                    && Float.compare(whitespaceScale, key.whitespaceScale) == 0
                    && unicode == key.unicode;
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}
