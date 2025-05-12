package codechicken.nei;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ChatAllowedCharacters;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.opengl.GL11;

public class FormattedTextField extends GuiTextField {

    public static interface TextFormatter {

        TextFormatter DEFAULT = (text) -> text;

        public String format(String text);
    }

    protected FontRenderer fontRenderer;
    protected String rawText = "";
    protected String formattedText = "";
    protected String placeholder = "";
    protected boolean editable = true;
    protected int editableColor = 14737632;
    protected int notEditableColor = 7368816;
    protected TextFormatter formatter = TextFormatter.DEFAULT;
    protected int lineScrollOffset = 0;
    protected int frame = 0;

    public FormattedTextField(FontRenderer fontRenderer, int xPosition, int yPosition, int width, int height) {
        super(fontRenderer, xPosition, yPosition, width, height);
        this.fontRenderer = fontRenderer;
    }

    public void setFormatter(TextFormatter formatter) {
        this.formatter = formatter;
    }

    public void setPlaceholder(String placeholder) {
        this.placeholder = placeholder;
    }

    @Override
    public void updateCursorCounter() {
        super.updateCursorCounter();
        ++this.frame;
    }

    @Override
    public void setFocused(boolean focus) {
        if (focus && !this.isFocused()) {
            this.frame = 0;
        }

        super.setFocused(focus);
    }

    @Override
    public void setSelectionPos(int p_146199_1_) {
        String text = this.getText();
        int j = text.length();

        super.setSelectionPos(p_146199_1_);

        p_146199_1_ = Math.max(0, Math.min(p_146199_1_, j));

        if (this.fontRenderer != null) {

            if (this.lineScrollOffset > j) {
                this.lineScrollOffset = j;
            }

            int k = this.getWidth();
            String s = this.fontRenderer.trimStringToWidth(text.substring(this.lineScrollOffset), k);
            int l = s.length() + this.lineScrollOffset;

            if (p_146199_1_ == this.lineScrollOffset) {
                this.lineScrollOffset -= this.fontRenderer.trimStringToWidth(text, k, true).length();
            }

            if (p_146199_1_ > l) {
                this.lineScrollOffset += p_146199_1_ - l;
            } else if (p_146199_1_ <= this.lineScrollOffset) {
                this.lineScrollOffset -= this.lineScrollOffset - p_146199_1_;
            }

            this.lineScrollOffset = Math.max(0, Math.min(this.lineScrollOffset, j));
        }
    }

    @Override
    public void setTextColor(int p_146193_1_) {
        super.setTextColor(p_146193_1_);
        this.editableColor = p_146193_1_;
    }

    @Override
    public void setDisabledTextColour(int p_146204_1_) {
        super.setDisabledTextColour(p_146204_1_);
        this.notEditableColor = p_146204_1_;
    }

    @Override
    public void setEnabled(boolean p_146184_1_) {
        super.setEnabled(p_146184_1_);
        this.editable = p_146184_1_;
    }

    protected boolean beforeWrite(String text) {
        return true;
    }

    @Override
    public void writeText(String textPart) {
        String newText = "";
        String substr = ChatAllowedCharacters.filerAllowedCharacters(textPart);
        int cursorPosition = getCursorPosition();
        int maxStringLength = getMaxStringLength();
        String text = getText();

        if (text.isEmpty()) {
            newText = substr.substring(0, Math.min(maxStringLength, substr.length()));
            cursorPosition = newText.length();
        } else {
            int selectionEnd = getSelectionEnd();
            String textStart = text.substring(0, Math.min(cursorPosition, selectionEnd));
            String textEnd = text.substring(Math.max(cursorPosition, selectionEnd));
            newText = textStart + substr + textEnd;

            if (newText.length() > maxStringLength) {
                newText = newText.substring(0, maxStringLength);
            }

            cursorPosition = Math.min(textStart.length() + substr.length(), newText.length());
        }

        if (beforeWrite(newText)) {
            setText(newText);
            setCursorPosition(cursorPosition);
        }
    }

    @Override
    public int getNthWordFromPos(int p_146183_1_, int p_146183_2_) {
        return this.func_146197_a(p_146183_1_, p_146183_2_, true);
    }

    @Override
    public void drawTextBox() {

        if (!this.getVisible()) {
            return;
        }

        if (this.getEnableBackgroundDrawing()) {
            drawRect(
                    this.xPosition - 1,
                    this.yPosition - 1,
                    this.xPosition + this.width + 1,
                    this.yPosition + this.height + 1,
                    -6250336);
            drawRect(
                    this.xPosition,
                    this.yPosition,
                    this.xPosition + this.width,
                    this.yPosition + this.height,
                    -16777216);
        }

        if (!this.rawText.equals(getText())) {
            this.rawText = getText();
            this.formattedText = this.formatter.format(this.rawText);
            if (!EnumChatFormatting.getTextWithoutFormattingCodes(this.formattedText).toLowerCase()
                    .equals(this.rawText.toLowerCase())) {
                this.formattedText = this.rawText;
            }
        }

        if (!isFocused() && this.formattedText.isEmpty()) {
            int x = getEnableBackgroundDrawing() ? this.xPosition + 4 : this.xPosition;
            int y = getEnableBackgroundDrawing() ? this.yPosition + (this.height - 8) / 2 : this.yPosition;
            this.fontRenderer.drawStringWithShadow(
                    this.fontRenderer.trimStringToWidth(this.placeholder, this.getWidth()),
                    x,
                    y,
                    this.notEditableColor);
            return;
        }

        int firstCharacterIndex = getFormattedTextShift(this.lineScrollOffset);
        String rawTextClipped = this.fontRenderer
                .trimStringToWidth(this.rawText.substring(this.lineScrollOffset), this.getWidth());
        String textClipped = this.formattedText
                .substring(firstCharacterIndex, getFormattedTextShift(this.lineScrollOffset + rawTextClipped.length()));
        int cursorPosition = getFormattedTextShift(getCursorPosition());
        int selectionEnd = getFormattedTextShift(getSelectionEnd());

        int color = this.editable ? this.editableColor : this.notEditableColor;
        int cursorA = cursorPosition - firstCharacterIndex;
        int cursorB = selectionEnd - firstCharacterIndex;
        boolean flag = cursorA >= 0 && cursorA <= textClipped.length();
        boolean flag1 = isFocused() && this.frame / 6 % 2 == 0 && flag;
        boolean flag2 = getCursorPosition() < this.rawText.length() || this.rawText.length() >= getMaxStringLength();
        int x = getEnableBackgroundDrawing() ? this.xPosition + 4 : this.xPosition;
        int y = getEnableBackgroundDrawing() ? this.yPosition + (this.height - 8) / 2 : this.yPosition;
        int x2 = x;

        if (cursorB > textClipped.length()) {
            cursorB = textClipped.length();
        }

        if (textClipped.length() > 0) {
            String s1 = flag ? textClipped.substring(0, cursorA) : textClipped;
            String colorA = getPreviousColor(firstCharacterIndex);
            x2 = this.fontRenderer.drawStringWithShadow(colorA + s1, x, y, color);
        }

        int k1 = x2;

        if (!flag) {
            k1 = cursorA > 0 ? x + this.width : x;
        } else if (flag2) {
            k1 = x2 - 1;
            --x2;
        }

        if (textClipped.length() > 0 && flag && cursorA < textClipped.length()) {
            String colorB = getPreviousColor(firstCharacterIndex + cursorA);
            this.fontRenderer.drawStringWithShadow(colorB + textClipped.substring(cursorA), x2, y, color);
        }

        if (flag1) {
            if (flag2) {
                Gui.drawRect(k1, y - 1, k1 + 1, y + 1 + this.fontRenderer.FONT_HEIGHT, -3092272);
            } else {
                this.fontRenderer.drawStringWithShadow("_", k1, y, color);
            }
        }

        if (cursorB != cursorA) {
            int l1 = x + this.fontRenderer.getStringWidth(textClipped.substring(0, cursorB));
            drawCursorVertical(k1, y - 1, l1 - 1, y + 1 + this.fontRenderer.FONT_HEIGHT);
        }
    }

    private void drawCursorVertical(int x1, int y1, int x2, int y2) {
        int i1;

        if (x1 < x2) {
            i1 = x1;
            x1 = x2;
            x2 = i1;
        }

        if (y1 < y2) {
            i1 = y1;
            y1 = y2;
            y2 = i1;
        }

        if (x2 > this.xPosition + this.width) {
            x2 = this.xPosition + this.width;
        }

        if (x1 > this.xPosition + this.width) {
            x1 = this.xPosition + this.width;
        }

        Tessellator tessellator = Tessellator.instance;
        GL11.glColor4f(0.0F, 0.0F, 255.0F, 255.0F);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_COLOR_LOGIC_OP);
        GL11.glLogicOp(GL11.GL_OR_REVERSE);
        tessellator.startDrawingQuads();
        tessellator.addVertex((double) x1, (double) y2, 0.0D);
        tessellator.addVertex((double) x2, (double) y2, 0.0D);
        tessellator.addVertex((double) x2, (double) y1, 0.0D);
        tessellator.addVertex((double) x1, (double) y1, 0.0D);
        tessellator.draw();
        GL11.glDisable(GL11.GL_COLOR_LOGIC_OP);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    private int getFormattedTextShift(int position) {
        int shift = 0;

        for (int i = 0; i < position; i++) {
            while (this.formattedText.length() > i + shift && this.formattedText.charAt(i + shift) == '\u00a7') {
                shift += 2;
            }
        }

        return position + shift;
    }

    private String getPreviousColor(int position) {
        while (position >= 0) {
            if (this.formattedText.charAt(position) == '\u00a7') {
                return this.formattedText.substring(position, position + 2);
            }
            position--;
        }
        return "";
    }

}
