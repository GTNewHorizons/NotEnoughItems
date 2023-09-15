package codechicken.nei;

import static codechicken.nei.NEIClientUtils.translate;

public class ItemQuantityField extends TextField {

    public ItemQuantityField(String ident) {
        super(ident);
        centered = true;
        field.setDisabledTextColour(0xFF303030);
    }

    public int intValue() {
        return intValue(text());
    }

    public int intValue(String s) {
        try {
            return Math.max(0, Integer.parseInt(s));
        } catch (NumberFormatException nfe) {
            return 0;
        }
    }

    @Override
    public void gainFocus() {
        if (text().equals("0")) {
            setText("");
        }
    }

    @Override
    public void loseFocus() {
        setText(Integer.toString(NEIClientConfig.getItemQuantity()));
    }

    @Override
    public void onTextChange(String oldText) {
        NEIClientUtils.setItemQuantity(intValue());
    }

    @Override
    public void draw(int mousex, int mousey) {

        if (!focused() && intValue() == 0) {
            field.setText(translate("itempanel.quantity.default"));
            field.setEnabled(false);
            super.draw(mousex, mousey);
            field.setEnabled(true);
            field.setText("0");
        } else {
            super.draw(mousex, mousey);
        }
    }

    @Override
    public boolean onMouseWheel(int i, int mx, int my) {
        if (!contains(mx, my)) return false;
        int multiplier = 1;
        if (NEIClientUtils.shiftKey()) {
            multiplier = 10;
        } else if (NEIClientUtils.controlKey()) {
            multiplier = 64;
        }

        int quantity = intValue() + i * multiplier;
        setText(Integer.toString(quantity));
        return true;
    }

}
