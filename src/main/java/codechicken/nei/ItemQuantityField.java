package codechicken.nei;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

import org.lwjgl.input.Keyboard;

import codechicken.nei.recipe.StackInfo;

public class ItemQuantityField extends TextField {

    public ItemQuantityField(String ident) {
        super(ident);
        centered = true;
        field.setDisabledTextColour(0xFF303030);
    }

    @Override
    protected void initInternalTextField() {
        field = new FormattedTextField(Minecraft.getMinecraft().fontRenderer, 0, 0, 0, 0) {

            {
                setPlaceholder(NEIClientUtils.translate("itempanel.quantity.default"));
            }

            @Override
            public String getText() {
                String text = super.getText();
                return !isFocused() && "0".equals(text) ? "" : text;
            }

            @Override
            protected boolean beforeWrite(String text) {
                if (text == null || text.isEmpty()) return true;
                try {
                    return Integer.parseInt(text) >= 0;
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
                    setText(Integer.toString(Math.max(0, getInteger() - 1)));
                    return true;
                } else if (keyID == Keyboard.KEY_UP) {
                    setText(Integer.toString(getInteger() + 1));
                    return true;
                }

                return false;
            }

            private int getInteger() {
                try {
                    return Math.max(0, Integer.parseInt(text()));
                } catch (NumberFormatException nfe) {
                    return 0;
                }
            }

        };
        field.setMaxStringLength(maxSearchLength);
        field.setCursorPositionZero();
    }

    private int getInteger() {
        try {
            return Math.max(0, Integer.parseInt(text()));
        } catch (NumberFormatException nfe) {
            return 0;
        }
    }

    public static ItemStack prepareStackWithQuantity(ItemStack stack, long amount) {
        if (stack == null) {
            return null;
        }

        if (amount == 0) {
            amount = NEIClientConfig.showItemQuantityWidget() ? NEIClientConfig.getItemQuantity() : 0;

            if (amount == 0) {
                amount = StackInfo.itemStackToNBT(stack).hasKey("gtFluidName") ? 144 : stack.getMaxStackSize();
            }
        }

        return StackInfo.withAmount(stack, amount);
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
        NEIClientUtils.setItemQuantity(getInteger());
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

        setText(Integer.toString(Math.max(0, getInteger() + i * multiplier)));
        return true;
    }

}
