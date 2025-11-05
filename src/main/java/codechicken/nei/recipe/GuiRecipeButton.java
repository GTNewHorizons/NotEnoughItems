package codechicken.nei.recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraftforge.client.event.GuiScreenEvent;

import codechicken.lib.vec.Rectangle4i;
import codechicken.nei.GuiNEIButton;
import cpw.mods.fml.common.eventhandler.Cancelable;

public abstract class GuiRecipeButton extends GuiNEIButton {

    public static class UpdateRecipeButtonsEvent extends GuiScreenEvent {

        public List<GuiRecipeButton> buttonList;
        public NEIRecipeWidget recipeWidget;

        public UpdateRecipeButtonsEvent(GuiRecipe<?> gui, NEIRecipeWidget recipeWidget,
                List<GuiRecipeButton> buttonList) {
            super(gui);
            this.recipeWidget = recipeWidget;
            this.buttonList = new ArrayList<>(buttonList);
        }

        @Cancelable
        public static class Pre extends UpdateRecipeButtonsEvent {

            public HandlerInfo handlerInfo;

            public Pre(GuiRecipe<?> gui, NEIRecipeWidget recipeWidget, HandlerInfo handlerInfo) {
                super(gui, recipeWidget, new ArrayList<>());
                this.handlerInfo = handlerInfo;
            }
        }

        public static class Post extends UpdateRecipeButtonsEvent {

            public Post(GuiRecipe<?> gui, NEIRecipeWidget recipeWidget, List<GuiRecipeButton> buttonList) {
                super(gui, recipeWidget, buttonList);
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

    public abstract List<String> handleTooltip(List<String> currenttip);

    public abstract Map<String, String> handleHotkeys(int mousex, int mousey, Map<String, String> hotkeys);

    public abstract void lastKeyTyped(char keyChar, int keyID);

    public abstract void drawItemOverlay();

    public void update() {

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
