package codechicken.nei.config;

import java.util.List;

import codechicken.lib.gui.GuiDraw;
import codechicken.lib.vec.Rectangle4i;
import codechicken.nei.TextField;

public class OptionTextField extends Option {

    protected final TextField textField = new TextField("option-text-field") {

        {
            this.h = 20;
        }

        @Override
        public void onTextChange(String oldText) {
            // don't override global if text hasn't changed
            if (focused() && isValidValue(text()) && (!defaulting() || !text().equals(getTag().getValue()))) {
                getTag().setValue(text());
                OptionTextField.this.onTextChange(text());
            }
        }

        @Override
        public void setFocus(boolean focus) {
            if (!focus && !isValidValue(text())) setText(renderTag().getValue());
            super.setFocus(focus);
        }

        @Override
        public void draw(int mousex, int mousey) {
            this.field.setEnabled(isEnabled());
            super.draw(mousex, mousey);
        }
    };

    public OptionTextField(String name) {
        super(name);
    }

    public void onTextChange(String text) {}

    @Override
    public void update() {
        textField.update();
        if (!textField.focused()) textField.setText(renderTag().getValue());
    }

    public String getPrefix() {
        return translateN(name);
    }

    protected int getMaxInputWidth() {
        return slot.slotWidth();
    }

    public boolean isEnabled() {
        return true;
    }

    @Override
    public void draw(int mousex, int mousey, float frame) {
        GuiDraw.drawString(getPrefix(), 10, 6, -1);

        textField.w = Math
                .max(60, Math.min(getMaxInputWidth(), slot.slotWidth() - GuiDraw.getStringWidth(getPrefix())) - 16);
        textField.x = slot.slotWidth() - textField.w;
        textField.draw(mousex, mousey);
    }

    @Override
    public void keyTyped(char c, int keycode) {
        if (isEnabled()) {
            textField.handleKeyPress(keycode, c);
        }
    }

    @Override
    public void mouseClicked(int mousex, int mousey, int button) {
        if (isEnabled() && textField.contains(mousex, mousey)) textField.handleClick(mousex, mousey, button);
    }

    @Override
    public void onMouseClicked(int mousex, int mousey, int button) {
        textField.onGuiClick(mousex, mousey);
    }

    @Override
    public List<String> handleTooltip(int mousex, int mousey, List<String> currenttip) {
        if (isEnabled() && new Rectangle4i(10, 0, textField.x - 10, 20).contains(mousex, mousey)) {
            String tip = translateN(name + ".tip");
            if (!tip.equals(namespaced(name + ".tip"))) currenttip.add(tip);
        }
        return currenttip;
    }

    public boolean isValidInput(String s) {
        return true;
    }

    public boolean isValidValue(String s) {
        return true;
    }
}
