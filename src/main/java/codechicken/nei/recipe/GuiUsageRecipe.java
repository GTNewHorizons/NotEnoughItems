package codechicken.nei.recipe;

import codechicken.nei.NEIClientConfig;
import codechicken.nei.NEIClientUtils;
import java.util.ArrayList;

public class GuiUsageRecipe extends GuiRecipe {
    public static boolean openRecipeGui(String inputId, Object... ingredients) {
        RecipeHandlerQuery<IUsageHandler> recipeQuery = new RecipeHandlerQuery<>(
                h -> getUsageOrCatalystHandler(h, inputId, ingredients), usagehandlers, serialUsageHandlers);
        ArrayList<IUsageHandler> handlers = recipeQuery.runWithProfiling("recipe.concurrent.usage");
        if (handlers.isEmpty()) return false;

        BookmarkRecipeId recipeId = getCurrentRecipe();
        GuiUsageRecipe gui = new GuiUsageRecipe(handlers, recipeId);

        NEIClientUtils.mc().displayGuiScreen(gui);

        if (!NEIClientUtils.shiftKey()) {
            gui.openTargetRecipe(gui.recipeId);
        }

        return true;
    }

    private GuiUsageRecipe(ArrayList<IUsageHandler> handlers, BookmarkRecipeId recipeId) {
        super(NEIClientUtils.mc().currentScreen);
        this.currenthandlers = handlers;
        this.recipeId = recipeId;
    }

    public static void registerUsageHandler(IUsageHandler handler) {
        final String handlerId = handler.getHandlerId();
        if (usagehandlers.stream().anyMatch(h -> h.getHandlerId().equals(handlerId))
                || serialUsageHandlers.stream().anyMatch(h -> h.getHandlerId().equals(handlerId))) return;

        if (NEIClientConfig.serialHandlers.contains(handlerId)) serialUsageHandlers.add(handler);
        else usagehandlers.add(handler);
    }

    public ArrayList<? extends IRecipeHandler> getCurrentRecipeHandlers() {
        return currenthandlers;
    }

    private static IUsageHandler getUsageOrCatalystHandler(
            IUsageHandler handler, String inputId, Object... ingredients) {
        boolean skipCatalyst = NEIClientUtils.controlKey();
        if (NEIClientConfig.areJEIStyleRecipeCatalystsVisible() && !skipCatalyst) {
            return handler.getUsageAndCatalystHandler(inputId, ingredients);
        } else {
            return handler.getUsageHandler(inputId, ingredients);
        }
    }

    public ArrayList<IUsageHandler> currenthandlers;

    public static ArrayList<IUsageHandler> usagehandlers = new ArrayList<>();
    public static ArrayList<IUsageHandler> serialUsageHandlers = new ArrayList<>();
}
