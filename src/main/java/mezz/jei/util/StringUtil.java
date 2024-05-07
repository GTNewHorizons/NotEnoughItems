package mezz.jei.util;

import java.text.Normalizer;
import java.util.regex.Pattern;

import net.minecraft.client.gui.FontRenderer;

public final class StringUtil {

    private static final Pattern COMBINING_DIACRITICAL_MARKS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    private StringUtil() {

    }

    public static String truncateStringToWidth(String string, int width, FontRenderer fontRenderer) {
        return fontRenderer.trimStringToWidth(string, width - fontRenderer.getStringWidth("...")) + "...";
    }

    public static String stripAccents(String input) {
        final StringBuilder decomposed = new StringBuilder(Normalizer.normalize(input, Normalizer.Form.NFD));
        for (int i = 0; i < decomposed.length(); i++) {
            switch (decomposed.charAt(i)) {
                case '\u0141':
                    decomposed.setCharAt(i, 'L');
                    break;
                case '\u0142':
                    decomposed.setCharAt(i, 'l');
                    break;
                case '\u00D8':
                    decomposed.setCharAt(i, 'O');
                    break;
                case '\u00F8':
                    decomposed.setCharAt(i, 'o');
            }
        }
        return COMBINING_DIACRITICAL_MARKS.matcher(decomposed).replaceAll("");
    }

}
