package codechicken.nei.recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraftforge.client.event.GuiScreenEvent;

import codechicken.lib.vec.Rectangle4i;
import codechicken.nei.GuiNEIButton;
import cpw.mods.fml.common.eventhandler.Cancelable;

public abstract class GuiRecipeButton extends GuiNEIButton {

    public static class UpdateRecipeButtonsEvent extends GuiScreenEvent {

        public List<GuiRecipeButton> buttonList;

        public UpdateRecipeButtonsEvent(GuiRecipe<?> gui, List<GuiRecipeButton> buttonList) {
            super(gui);
            this.buttonList = new ArrayList<>(buttonList);
        }

        @Cancelable
        public static class Pre extends UpdateRecipeButtonsEvent {

            public int xOffset;
            public int yOffset;
            public int height;
            public HandlerInfo handlerInfo;

            public Pre(GuiRecipe<?> gui, int xOffset, int yOffset, int height, HandlerInfo handlerInfo) {
                super(gui, new ArrayList<>());
                this.xOffset = xOffset;
                this.yOffset = yOffset;
                this.height = height;
                this.handlerInfo = handlerInfo;
            }
        }

        public static class Post extends UpdateRecipeButtonsEvent {

            public Post(GuiRecipe<?> gui, List<GuiRecipeButton> buttonList) {
                super(gui, buttonList);
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

    abstract List<String> handleTooltip(GuiRecipe<?> gui, List<String> currenttip);

    abstract Map<String, String> handleHotkeys(GuiContainer gui, int mousex, int mousey, Map<String, String> hotkeys);

    abstract void lastKeyTyped(GuiRecipe<?> gui, char keyChar, int keyID);

    abstract void drawItemOverlay();

    public boolean mouseScrolled(GuiRecipe<?> gui, int scroll) {
        return false;
    }

    public Rectangle4i bounds() {
        return new Rectangle4i(this.xPosition, this.yPosition, this.width, this.height);
    }

    public boolean contains(int mx, int my) {
        return bounds().contains(mx, my);
    }

}
