package codechicken.nei.config;

import codechicken.lib.gui.GuiDraw;

public class OptionFloatField extends OptionTextField {

    public final float min;
    public final float max;

    public OptionFloatField(String name, float min, float max) {
        super(name);
        this.min = min;
        this.max = max;
    }

    @Override
    protected int getMaxInputWidth() {
        return GuiDraw.getStringWidth(Float.toString(max)) + 32;
    }

    @Override
    public boolean isValidInput(String text) {
        if (text.isEmpty() || text.equals(".")) {
            return true;
        }
        try {
            return Float.isFinite(Float.parseFloat(text));
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    @Override
    public boolean isValidValue(String text) {
        try {
            final float value = Float.parseFloat(text);
            return Float.isFinite(value) && value >= min && value <= max;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }
}
