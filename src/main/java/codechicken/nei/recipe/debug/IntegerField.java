package codechicken.nei.recipe.debug;

import java.util.function.BiConsumer;
import java.util.function.IntPredicate;

import net.minecraft.client.Minecraft;

import org.lwjgl.input.Keyboard;

import codechicken.nei.FormattedTextField;
import codechicken.nei.TextField;

public class IntegerField extends TextField implements IUpdatableWidget {

    protected int defaultValue = 0;
    protected BiConsumer<IntegerField, String> onChange;
    protected IntPredicate validator;

    public IntegerField(String ident, BiConsumer<IntegerField, String> onChange, IntPredicate validator,
            int defaultValue) {
        super(ident);
        this.onChange = onChange;
        this.validator = validator;
        this.defaultValue = defaultValue;
        this.h = 16;
        this.z = 2;
        ((FormattedTextField) field).setPlaceholder(Integer.toString(this.defaultValue));
    }

    @Override
    protected void initInternalTextField() {
        field = new FormattedTextField(Minecraft.getMinecraft().fontRenderer, 0, 0, 0, 0) {

            @Override
            public String getText() {
                final String text = super.getText();
                return !isFocused() && this.placeholder.equals(text) ? "" : text;
            }

            @Override
            protected boolean beforeWrite(String text) {
                if (text == null || text.isEmpty()) return true;
                try {
                    Integer.parseInt(text);
                    return true;
                } catch (NumberFormatException e) {
                    return false;
                }
            }

            @Override
            public boolean textboxKeyTyped(char keyChar, int keyID) {

                if (!isFocused()) return false;

                if (super.textboxKeyTyped(keyChar, keyID)) {
                    return true;
                } else if (keyID == Keyboard.KEY_DOWN) {
                    setText(Integer.toString(getInteger() - 1));
                    return true;
                } else if (keyID == Keyboard.KEY_UP) {
                    setText(Integer.toString(getInteger() + 1));
                    return true;
                }

                return false;
            }

        };
        field.setMaxStringLength(maxSearchLength);
        field.setCursorPositionZero();
    }

    protected int getInteger() {
        return getInteger(text());
    }

    protected int getInteger(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException nfe) {
            return this.defaultValue;
        }
    }

    @Override
    public boolean onMouseWheel(int i, int mx, int my) {
        if (!contains(mx, my)) return false;
        setText(Integer.toString(getInteger() - i));
        return true;
    }

    @Override
    public void setText(String newValue) {
        if (validator == null || validator.test(getInteger(newValue))) {
            super.setText(newValue);
        }
    }

    public void updateValue(String newValue) {
        setText(newValue);
    }

    @Override
    public void onTextChange(String oldText) {
        onChange.accept(this, oldText);
    }

}
