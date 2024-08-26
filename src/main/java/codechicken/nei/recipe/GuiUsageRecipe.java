package codechicken.nei.recipe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

import codechicken.nei.NEIClientConfig;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.recipe.stackinfo.GTFluidStackStringifyHandler;

public class GuiUsageRecipe extends GuiRecipe<IUsageHandler> {

    public static ArrayList<IUsageHandler> usagehandlers = new ArrayList<>();
    public static ArrayList<IUsageHandler> serialUsageHandlers = new ArrayList<>();
    private static Set<String> existingHandlers = new HashSet<>();

    public static boolean openRecipeGui(String inputId, Object... ingredients) {

        if ("item".equals(inputId)) {
            for (int i = 0; i < ingredients.length; i++) {
                ingredients[i] = normalizeItemStack((ItemStack) ingredients[i]);
            }
        }

        final ArrayList<IUsageHandler> handlers = getUsageHandlers(inputId, ingredients);

        if (!handlers.isEmpty()) {
            final Minecraft mc = NEIClientUtils.mc();
            final BookmarkRecipeId recipeId = getCurrentRecipeId(mc.currentScreen);
            final GuiUsageRecipe gui = new GuiUsageRecipe(handlers);

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
                usagehandlers,
                serialUsageHandlers,
                "Error while looking up usage recipe",
                "inputId: " + inputId,
                "ingredients: " + Arrays.toString(ingredients));

        return recipeQuery.runWithProfiling(NEIClientUtils.translate("recipe.concurrent.usage"));
    }

    private static ItemStack normalizeItemStack(ItemStack stack) {
        GTFluidStackStringifyHandler.replaceAE2FCFluidDrop = true;
        stack = StackInfo.loadFromNBT(StackInfo.itemStackToNBT(stack));
        GTFluidStackStringifyHandler.replaceAE2FCFluidDrop = false;
        return stack;
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
        if (NEIClientConfig.areJEIStyleRecipeCatalystsVisible() && !skipCatalyst) {
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
