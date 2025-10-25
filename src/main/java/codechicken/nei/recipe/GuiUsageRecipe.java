package codechicken.nei.recipe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

import codechicken.nei.ItemPanels;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.recipe.Recipe.RecipeId;

public class GuiUsageRecipe extends GuiRecipe<IUsageHandler> {

    public static ArrayList<IUsageHandler> usagehandlers = new ArrayList<>();
    public static ArrayList<IUsageHandler> serialUsageHandlers = new ArrayList<>();
    private static Set<String> existingHandlers = new HashSet<>();

    public static boolean openRecipeGui(String inputId, Object... ingredients) {

        for (int i = 0; i < ingredients.length; i++) {
            if (ingredients[i] instanceof ItemStack stack) {
                ingredients[i] = StackInfo.normalizeRecipeQueryStack(stack.copy());
            }
        }

        final ArrayList<IUsageHandler> handlers = getUsageHandlers(inputId, ingredients);

        if (!handlers.isEmpty()) {
            final Minecraft mc = NEIClientUtils.mc();
            final RecipeId recipeId = getCurrentRecipeId(mc.currentScreen);
            final GuiUsageRecipe gui = new GuiUsageRecipe(handlers);

            if (NEIClientConfig.showHistoryPanelWidget() && "item".equals(inputId)
                    && ingredients[0] instanceof ItemStack stack) {
                ItemPanels.itemPanel.historyPanel.addItem(stack);
            }

            mc.displayGuiScreen(gui);
            gui.openTargetRecipe(recipeId);
            return true;
        }

        return false;
    }

    private GuiUsageRecipe(ArrayList<IUsageHandler> handlers) {
        super(NEIClientUtils.mc().currentScreen);
        this.currenthandlers = handlers;
    }

    public static ArrayList<IUsageHandler> getUsageHandlers(String inputId, Object... ingredients) {

        final RecipeHandlerQuery<IUsageHandler> recipeQuery = new RecipeHandlerQuery<>(
                h -> getUsageOrCatalystHandler(h, inputId, ingredients),
                GuiUsageRecipe.usagehandlers,
                GuiUsageRecipe.serialUsageHandlers,
                "Error while looking up usage recipe",
                "inputId: " + inputId,
                "ingredients: " + Arrays.toString(ingredients));

        return recipeQuery.runWithProfiling(NEIClientUtils.translate("recipe.concurrent.usage"));
    }

    public static void registerUsageHandler(IUsageHandler handler) {
        final String handlerId = handler.getHandlerId();

        if (existingHandlers.contains(handlerId)) {
            return;
        }

        synchronized (existingHandlers) {
            existingHandlers.add(handlerId);
        }

        if (NEIClientConfig.serialHandlers.contains(handlerId)) {
            synchronized (serialUsageHandlers) {
                serialUsageHandlers.add(handler);
            }
        } else {
            synchronized (usagehandlers) {
                usagehandlers.add(handler);
            }
        }

    }

    private static IUsageHandler getUsageOrCatalystHandler(IUsageHandler handler, String inputId,
            Object... ingredients) {
        boolean skipCatalyst = NEIClientUtils.controlKey();
        if (NEIClientConfig.getJEIStyleRecipeCatalysts() != 0 && !skipCatalyst) {
            return handler.getUsageAndCatalystHandler(inputId, ingredients);
        } else {
            return handler.getUsageHandler(inputId, ingredients);
        }
    }

    @Override
    public ArrayList<IUsageHandler> getCurrentRecipeHandlers() {
        return currenthandlers;
    }
}
