package codechicken.nei;

public class ItemQuantityField extends TextField
{
    public ItemQuantityField(String ident) {
        super(ident);
        centered = true;
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
}
