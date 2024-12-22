package codechicken.nei.config;

import codechicken.lib.gui.GuiDraw;

public class OptionIntegerField extends OptionTextField {

    public static final long UNSIGNED_INT_MAX = 0xFFFFFFFFL;

    public final long min;
    public final long max;

    public OptionIntegerField(String name, long min, long max) {
        super(name);
        this.min = min;
        this.max = max;
    }

    public OptionIntegerField(String name) {
        this(name, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    @Override
    protected int getMaxInputWidth() {

        if (renderTag().getValue().startsWith("0x")) {
            return GuiDraw.getStringWidth("0x" + Long.toHexString(this.max).toUpperCase()) + 32;
        }

        return GuiDraw.getStringWidth(String.valueOf(this.max)) + 32;
    }

    @Override
    public boolean isValidInput(String s) {
        if (s.isEmpty()) return true;

        try {
            if (s.startsWith("0x")) {
                Long.parseUnsignedLong(s.substring(2), 16);
            } else {
                Integer.parseInt(s);
            }
            return true;
        } catch (NumberFormatException nfe) {
            return false;
        }
    }

    @Override
    public boolean isValidValue(String s) {
        if (s.isEmpty() || !isValidInput(s)) return false;
        long i = 0;

        if (s.startsWith("0x")) {
            i = Long.parseUnsignedLong(s.substring(2), 16);
        } else {
            i = Integer.parseInt(s);
        }

        return i >= this.min && i <= this.max;
    }
}
