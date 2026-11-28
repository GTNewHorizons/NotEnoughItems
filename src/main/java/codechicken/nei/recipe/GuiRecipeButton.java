package codechicken.nei.recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.GuiScreenEvent;

import codechicken.lib.vec.Rectangle4i;
import codechicken.nei.GuiNEIButton;
import codechicken.nei.drawable.Drawable;
import codechicken.nei.theme.ButtonSkin;
import codechicken.nei.theme.UIWidgetTexture;
import cpw.mods.fml.common.eventhandler.Cancelable;

public abstract class GuiRecipeButton extends GuiNEIButton {

    public static class UpdateRecipeButtonsEvent extends GuiScreenEvent {

        public List<GuiRecipeButton> buttonList;
        public RecipeHandlerRef handlerRef;
        public int width;
        public int height;

        public UpdateRecipeButtonsEvent(GuiRecipe<?> gui, RecipeHandlerRef handlerRef, int width, int height,
                List<GuiRecipeButton> buttonList) {
            super(gui);
            this.handlerRef = handlerRef;
            this.width = width;
            this.height = height;
            this.buttonList = new ArrayList<>(buttonList);
        }

        @Cancelable
        public static class Pre extends UpdateRecipeButtonsEvent {

            public HandlerInfo handlerInfo;

            public Pre(GuiRecipe<?> gui, RecipeHandlerRef handlerRef, int width, int height, HandlerInfo handlerInfo) {
                super(gui, handlerRef, width, height, new ArrayList<>());
                this.handlerInfo = handlerInfo;
            }
        }

        public static class Post extends UpdateRecipeButtonsEvent {

            public Post(GuiRecipe<?> gui, RecipeHandlerRef handlerRef, int width, int height,
                    List<GuiRecipeButton> buttonList) {
                super(gui, handlerRef, width, height, buttonList);
            }
        }
    }

    public static final int BUTTON_WIDTH = 12;
    public static final int BUTTON_HEIGHT = 12;

    public final RecipeHandlerRef handlerRef;

    protected GuiRecipeButton(RecipeHandlerRef point, int x, int y, int buttonId, String label) {
        super(buttonId, x, y, BUTTON_WIDTH, BUTTON_HEIGHT, label);
        this.handlerRef = point;
    }

    protected Drawable getIcon(boolean mouseOver) {
        return null;
    }

    protected ButtonSkin getSkin() {
        return UIWidgetTexture.BUTTON;
    }

    @Override
    protected Drawable getBackground(int mouseX, int mouseY, boolean mouseOver) {
        return getSkin().background(this.enabled, false, mouseOver);
    }

    @Override
    protected void drawContent(Minecraft minecraft, int mouseX, int mouseY, boolean mouseOver) {
        final Drawable icon = getIcon(mouseOver);

        if (icon != null) {
            getSkin().drawIcon(
                    this.xPosition,
                    this.yPosition,
                    this.width,
                    this.height,
                    icon,
                    this.enabled,
                    false,
                    mouseOver);
        } else {
            super.drawContent(minecraft, mouseX, mouseY, mouseOver);
        }
    }

    public abstract List<String> handleTooltip(List<String> currenttip);

    public abstract Map<String, String> handleHotkeys(int mousex, int mousey, Map<String, String> hotkeys);

    public abstract void lastKeyTyped(char keyChar, int keyID);

    public boolean onHotkeyPressed() {
        return false;
    }

    public abstract void drawItemOverlay();

    public void update() {

    }

    public void onPermutationsChanged() {

    }

    public boolean mouseScrolled(int scroll) {
        return false;
    }

    public Rectangle4i bounds() {
        return new Rectangle4i(this.xPosition, this.yPosition, this.width, this.height);
    }

    public boolean contains(int mx, int my) {
        return bounds().contains(mx, my);
    }

}
