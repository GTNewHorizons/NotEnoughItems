package codechicken.nei.recipe;

import java.util.List;
import java.util.function.BooleanSupplier;

import net.minecraft.client.gui.FontRenderer;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.Button;
import codechicken.nei.ColorUtils;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.Widget;
import codechicken.nei.drawable.DrawableResource;
import codechicken.nei.theme.UIRecipeTexture;
import codechicken.nei.theme.UIWidgetTexture;

class NavigationBar extends Widget {

    protected class NavButton extends Button {

        private final DrawableResource icon;
        private final Runnable action;
        private final BooleanSupplier isEnabled;

        protected NavButton(DrawableResource icon, BooleanSupplier isEnabled, Runnable action) {
            this.icon = icon;
            this.action = action;
            this.isEnabled = isEnabled;
            this.w = BUTTON_SIZE;
            this.h = BUTTON_SIZE;
        }

        @Override
        public void draw(int mx, int my) {
            final boolean enabled = this.isEnabled.getAsBoolean();
            final boolean active = (this.state & 0x3) == 2;

            UIWidgetTexture.BUTTON.draw(this.x, this.y, this.w, this.h, this.icon, enabled, active, contains(mx, my));
        }

        @Override
        public boolean onButtonPress(boolean rightclick) {
            if (rightclick) return false;
            this.action.run();
            return true;
        }
    }

    protected static final int BUTTON_SIZE = 12;
    protected static final int GAP = 2;

    private final NavButton prev;
    private final NavButton next;

    protected boolean enabled = true;

    protected String label = "";
    protected int color = 0xffffff;
    protected int hoverColor = 0xffffff;

    protected int insetLeft;
    protected Runnable labelAction;

    public String labelTooltip;
    private String croppedLabel;
    private int croppedLabelWidth;
    private String croppedLabelSource;
    private int croppedLabelMaxWidth;

    public NavigationBar(Runnable labelAction, Runnable onPrev, Runnable onNext) {
        this.prev = new NavButton(UIRecipeTexture.ICON_PREV, () -> NavigationBar.this.enabled, onPrev);
        this.next = new NavButton(UIRecipeTexture.ICON_NEXT, () -> NavigationBar.this.enabled, onNext);
        this.labelAction = labelAction;
        this.h = BUTTON_SIZE;
    }

    protected int plateX() {
        return this.x + this.h + GAP;
    }

    protected int plateWidth() {
        return this.w - (this.h + GAP) * 2;
    }

    public void place(int x, int y, int w) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.prev.x = x;
        this.prev.y = y;
        this.next.x = x + w - BUTTON_SIZE;
        this.next.y = y;
    }

    @Override
    public void draw(int mx, int my) {
        GuiDraw.drawRect(
                plateX() + this.insetLeft,
                this.y,
                plateWidth() - this.insetLeft,
                this.h,
                ColorUtils.recipeTitleBG.getColor());

        this.prev.draw(mx, my);
        this.next.draw(mx, my);

        if (!this.label.isEmpty()) {
            final FontRenderer font = NEIClientUtils.mc().fontRenderer;
            updateCroppedLabel(font);
            font.drawStringWithShadow(
                    this.croppedLabel,
                    labelX(),
                    labelY(font),
                    isLabelHovered(mx, my) ? this.hoverColor : this.color);
        }
    }

    private void updateCroppedLabel(FontRenderer font) {
        final int maxWidth = plateWidth() - this.insetLeft;

        if (!this.label.equals(this.croppedLabelSource) || maxWidth != this.croppedLabelMaxWidth) {
            this.croppedLabel = NEIClientUtils.cropText(font, this.label, maxWidth - 2);
            this.croppedLabelSource = this.label;
            this.croppedLabelMaxWidth = maxWidth;
            this.croppedLabelWidth = font.getStringWidth(this.croppedLabel);
        }
    }

    private int labelX() {
        return plateX() + this.insetLeft + (plateWidth() - this.insetLeft) / 2 - this.croppedLabelWidth / 2;
    }

    private int labelY(FontRenderer font) {
        return this.y + (this.h - font.FONT_HEIGHT) / 2 + 1;
    }

    private boolean isLabelHovered(int mx, int my) {
        if (this.labelAction == null || this.label.isEmpty()) return false;

        final FontRenderer font = NEIClientUtils.mc().fontRenderer;
        updateCroppedLabel(font);
        final int textX = labelX();
        final int textY = labelY(font);

        return mx >= textX && mx < textX + this.croppedLabelWidth && my >= textY && my < textY + font.FONT_HEIGHT;
    }

    @Override
    public List<String> handleTooltip(int mx, int my, List<String> tooltip) {
        if (tooltip.isEmpty() && this.labelTooltip != null && isLabelHovered(mx, my)) {
            tooltip.add(this.labelTooltip);
        }

        return tooltip;
    }

    @Override
    public boolean handleClick(int mx, int my, int button) {
        if (button == 0 && isLabelHovered(mx, my)) {
            this.labelAction.run();
            return true;
        }

        if (!this.enabled) return false;
        if (this.prev.contains(mx, my)) return this.prev.handleClick(mx, my, button);
        if (this.next.contains(mx, my)) return this.next.handleClick(mx, my, button);
        return false;
    }

}
