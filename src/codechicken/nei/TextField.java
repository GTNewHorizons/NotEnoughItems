package codechicken.nei;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

public abstract class TextField extends Widget
{
    protected GuiTextField field;

    private static final int maxSearchLength = 256;

    public boolean centered;
    public String identifier;

    private boolean previousKeyboardRepeatEnabled;

    protected void initInternalTextField() {
        field = new GuiTextField(Minecraft.getMinecraft().fontRenderer, 0, 0, 0, 0);
        field.setMaxStringLength(maxSearchLength);
        field.setCursorPositionZero();
    }

    public TextField(String ident) {
        identifier = ident;
        initInternalTextField();
//        Keyboard.enableRepeatEvents(true);
    }

    public int getTextColour() {
        return focused() ? 0xFFE0E0E0 : 0xFF909090;
    }

    protected void setDimensionsAndColor() {
        field.xPosition = this.x + 2;
        field.yPosition = this.y + 2;
        field.width = this.w - 4;
        field.height = this.h - 4;
        field.setTextColor(getTextColour());
    }

    @Override
    public void draw(int mousex, int mousey) {
        try {
            setDimensionsAndColor();
            field.drawTextBox();
        } catch(NullPointerException npe) {
            // Hack to deal with a null font renderer... recreate the field and it should work
            GuiTextField oldField = field;

            initInternalTextField();
            setDimensionsAndColor();

            field.setText(oldField.getText());
            field.drawTextBox();
        }
    }

    @Override
    public void onGuiClick(int mousex, int mousey) {
        if (!contains(mousex, mousey))
            setFocus(false);
    }

    @Override
    public boolean handleClick(int mousex, int mousey, int button) {
        setFocus(true);

        if (button == 1)
            setText("");
        else
            field.mouseClicked(mousex, mousey, button);

        return true;
    }

    @Override
    public boolean handleKeyPress(int keyID, char keyChar) {
        if (!focused())
            return false;

        String oldText = text();
        boolean handled = field.textboxKeyTyped(keyChar, keyID);
        if(!handled) {
            if (keyID == Keyboard.KEY_RETURN || keyID == Keyboard.KEY_NUMPADENTER || keyID == Keyboard.KEY_ESCAPE) {
                setFocus(false);
                handled = true;
            }
        }

        if(handled) {
            onTextChange(oldText);
        }

        return handled;

    }

    public abstract void onTextChange(String oldText);

    @Override
    public void update() {
    }

    public void setText(String s) {
        String oldText = text();
        field.setText(s);
        field.setCursorPositionZero();
        onTextChange(oldText);
    }

    public String filterText(String s) {
        return s;
    }

    public void setFocus(boolean focus) {
        final boolean previousFocus = field.isFocused();
        field.setFocused(focus);

        if (previousFocus != focus) {
            if (focus) {
                previousKeyboardRepeatEnabled = Keyboard.areRepeatEventsEnabled();
                Keyboard.enableRepeatEvents(true);
            } else {
                Keyboard.enableRepeatEvents(previousKeyboardRepeatEnabled);
            }

        }
    }

    public boolean focused() {
        return field.isFocused();
    }

    public String text() {
        return field.getText();
    }

}
