package codechicken.nei.item;

import java.lang.reflect.Method;
import java.nio.FloatBuffer;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.resources.Language;
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

    public static final int[] OUTLINE_STEPS = { -1, -1, 1, 0, 1, 0, 0, 1, 0, 1, -1, 0, -1, 0, 0, -1 };

    public final FloatBuffer matrixBuffer = BufferUtils.createFloatBuffer(16);
    public final LRUCache<LayoutKey, LabelLayout> layouts = new LRUCache<>(2048);
    public final LayoutKey lookupKey = new LayoutKey(null, "", 0, 0, false);
    public final GuiScaleCache guiScaleCache = new GuiScaleCache();
    public ItemNameOverlaySettings settings = ItemNameOverlaySettings.defaults();
    public Object fontState;
    public String languageCode;
    public BreakIterator lineBreaker;
    public long cacheRevision;

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

    public void drawLabel(FontRenderer font, String text, float x, float y, int width, int height,
            ItemNameOverlaySettings currentSettings) {
        if (text == null || text.isEmpty() || width <= 0 || height <= 0) {
            return;
        }

        refreshLineBreaker();
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
        refreshLineBreaker();
        final Object currentState = FontAccess.currentState();
        if (!Objects.equals(fontState, currentState)) {
            fontState = currentState;
            clearCache();
        }
    }

    public void clearCache() {
        layouts.clear();
        lookupKey.set(null, "", 0, 0, false);
        if (lineBreaker != null) {
            lineBreaker.setText("");
        }
        cacheRevision++;
    }

    public void refreshLineBreaker() {
        final Language language = Minecraft.getMinecraft().getLanguageManager().getCurrentLanguage();
        String currentCode = language != null ? language.getLanguageCode() : "en_US";
        if (currentCode == null || currentCode.isEmpty()) {
            currentCode = "en_US";
        }
        if (lineBreaker == null || !currentCode.equals(languageCode)) {
            Locale locale = Locale.forLanguageTag(currentCode.replace('_', '-'));
            if (locale.getLanguage().isEmpty()) {
                locale = Locale.ENGLISH;
            }
            languageCode = currentCode;
            lineBreaker = BreakIterator.getLineInstance(locale);
            clearCache();
        }
    }

    public long getCacheRevision() {
        return cacheRevision;
    }

    public int readRenderTransform() {
        final Minecraft minecraft = Minecraft.getMinecraft();
        final int guiScale = guiScaleCache.getScaleFactor(minecraft);
        matrixBuffer.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, matrixBuffer);
        return guiScale;
    }

    public float alignToPixel(float coordinate, float translation, float scale, int guiScale) {
        final float screenPosition = (translation + coordinate * scale) * guiScale;
        return (Math.round(screenPosition) / (float) guiScale - translation) / scale;
    }

    public LabelLayout createLayout(LayoutKey key, ItemNameOverlaySettings settings) {
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

        final int maximumWidth = (int) Math.nextUp(key.width / minimumScale);
        final LineBreaks breaks = createLineBreaks(font, text, maximumWidth, settings.maxCharacters);
        int availableLines = 1;
        for (int lineCount = 2; lineCount <= settings.maxLines; lineCount++) {
            final float wrappedScale = Math.min(preferredScale, key.height / (lineHeight * lineCount + 1));
            if (wrappedScale < minimumScale) {
                break;
            }
            availableLines = lineCount;
            List<String> completeLines = wrapText(
                    font,
                    text,
                    maximumWidth,
                    lineCount,
                    settings.maxCharacters,
                    breaks,
                    false);
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
                            settings.maxCharacters,
                            breaks,
                            false);
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
                wrapText(font, text, textWidth, availableLines, settings.maxCharacters, breaks, true));
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

    public LineBreaks createLineBreaks(FontRenderer font, String text, int maximumWidth, int maxCharacters) {
        lineBreaker.setText(text);
        final int[] boundaries = new int[text.length() + 1];
        int count = 0;
        for (int boundary = lineBreaker.first(); boundary != BreakIterator.DONE; boundary = lineBreaker.next()) {
            boundaries[count++] = boundary;
        }
        final boolean[] splittable = new boolean[count - 1];
        for (int index = 0; index < splittable.length; index++) {
            final String token = text.substring(boundaries[index], boundaries[index + 1]).trim();
            splittable[index] = getTextWidth(font, token) > maximumWidth || !fitsCharacterLimit(token, maxCharacters);
        }
        return new LineBreaks(boundaries, count, splittable);
    }

    public List<String> wrapText(FontRenderer font, String text, int width, int maxLines, int maxCharacters,
            LineBreaks breaks, boolean forceFit) {
        final List<String> lines = new ArrayList<>(maxLines);
        int start = 0;
        while (start < text.length()) {
            final String remainder = text.substring(start);
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
            int fittedEnd = start + prefix.length();
            // Trailing spaces do not consume visible line width or the character limit.
            while (fittedEnd < text.length() && text.charAt(fittedEnd) <= ' ') {
                fittedEnd++;
            }
            final int split = breaks.select(start, fittedEnd, forceFit);
            lines.add(text.substring(start, split).trim());
            start = split;
            while (start < text.length() && text.charAt(start) <= ' ') {
                start++;
            }
        }
        return lines;
    }

    public boolean fitsWidth(FontRenderer font, List<String> lines, int width, int maxCharacters) {
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

    public String sanitizeText(String text) {
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

    public String ellipsize(FontRenderer font, String text, int width, int maxCharacters) {
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

    public String trimToWidth(FontRenderer font, String text, int width) {
        if (width <= 0) {
            return "";
        }

        final String trimmed = FontAccess.trimToWidth(font, text, width);
        final int length = trimmed.length();
        return length > 0 && Character.isHighSurrogate(trimmed.charAt(length - 1)) ? trimmed.substring(0, length - 1)
                : trimmed;
    }

    public static int getTextWidth(FontRenderer font, String text) {
        return FontAccess.width(font, text);
    }

    public static boolean fitsCharacterLimit(String text, int limit) {
        return limit == 0 || text.codePointCount(0, text.length()) <= limit;
    }

    public static String trimCharacters(String text, int limit) {
        final int count = Math.min(limit, text.codePointCount(0, text.length()));
        return text.substring(0, text.offsetByCodePoints(0, count));
    }

    public boolean hasUnicodeCharacters(String text) {
        for (int index = 0; index < text.length(); index++) {
            if (text.charAt(index) > 255) {
                return true;
            }
        }
        return false;
    }

    public void drawOutlinedLine(FontRenderer font, String text, float pixelWidth, float pixelHeight) {
        // Visit each neighboring screen pixel once, then return to the center.
        for (int index = 0; index < OUTLINE_STEPS.length; index += 2) {
            GL11.glTranslatef(OUTLINE_STEPS[index] * pixelWidth, OUTLINE_STEPS[index + 1] * pixelHeight, 0);
            font.drawString(text, 0, 0, 0xFF000000);
        }
        GL11.glTranslatef(pixelWidth, 0, 0);
        font.drawString(text, 0, 0, 0xFFFFFFFF);
    }

    public static class LineBreaks {

        public final int[] boundaries;
        public final int count;
        public final boolean[] splittable;

        public LineBreaks(int[] boundaries, int count, boolean[] splittable) {
            this.boundaries = boundaries;
            this.count = count;
            this.splittable = splittable;
        }

        public int select(int start, int fittedEnd, boolean forceFit) {
            final int boundary = boundaries[atOrBefore(fittedEnd)];
            if (boundary > start) {
                return boundary;
            }
            final int token = atOrBefore(start);
            // Keep normal tokens whole during scale selection; split only when constraints require it.
            return forceFit || splittable[token] ? fittedEnd : boundaries[token + 1];
        }

        public int atOrBefore(int offset) {
            final int index = Arrays.binarySearch(boundaries, 0, count, offset);
            return index >= 0 ? index : -index - 2;
        }
    }

    public static class LabelLayout {

        public final LabelLine[] lines;
        public final float scale;

        public LabelLayout(FontRenderer font, int width, List<String> texts, float scale) {
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

    public static class LabelLine {

        public final String text;
        public final float x;
        public final float y;

        public LabelLine(String text, float x, float y) {
            this.text = text;
            this.x = x;
            this.y = y;
        }
    }

    public static class FontAccess {

        public static final boolean GTNH_FONTS = hasFontAPI();
        public static Method settingsMethod = findSettingsMethod();

        public static boolean hasFontAPI() {
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

        public static Method findSettingsMethod() {
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

        public static Object currentState() {
            if (settingsMethod != null) {
                try {
                    return settingsMethod.invoke(null, (Object[]) null);
                } catch (ReflectiveOperationException | LinkageError ignored) {
                    settingsMethod = null;
                }
            }
            return null;
        }

        public static String preprocess(String text) {
            return GTNH_FONTS ? FontRendering.preprocessText(text) : text;
        }

        public static int width(FontRenderer font, String text) {
            // Preserve custom FontRenderer subclasses' own measurement overrides.
            return GTNH_FONTS && font.getClass() == FontRenderer.class && font instanceof IFontParameters
                    ? FontRendering.getStringWidth(text, font)
                    : font.getStringWidth(text);
        }

        public static String trimToWidth(FontRenderer font, String text, int width) {
            return GTNH_FONTS && font.getClass() == FontRenderer.class && font instanceof IFontParameters
                    ? FontRendering.trimStringToWidth(text, width, false, font)
                    : font.trimStringToWidth(text, width);
        }

        public static float lineHeight(FontRenderer font) {
            if (GTNH_FONTS && font instanceof IFontParameters parameters) {
                final float scale = parameters.getGlyphScaleY();
                if (Float.isFinite(scale) && scale > 0) {
                    return font.FONT_HEIGHT * scale;
                }
            }
            return font.FONT_HEIGHT;
        }

        public static void applyMetrics(LayoutKey key, FontRenderer font) {
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

    public static class GuiScaleCache {

        public int displayWidth = -1;
        public int displayHeight = -1;
        public int setting;
        public boolean unicode;
        public int scaleFactor;

        public int getScaleFactor(Minecraft minecraft) {
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

    public static class LayoutKey {

        public FontRenderer font;
        public String text;
        public int width;
        public int height;
        public int fontHeight;
        public float glyphScaleX;
        public float glyphScaleY;
        public float glyphSpacing;
        public float whitespaceScale;
        public boolean unicode;
        public int hash;

        public LayoutKey(FontRenderer font, String text, int width, int height, boolean unicode) {
            set(font, text, width, height, unicode);
        }

        public LayoutKey(LayoutKey key) {
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

        public void set(FontRenderer font, String text, int width, int height, boolean unicode) {
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
