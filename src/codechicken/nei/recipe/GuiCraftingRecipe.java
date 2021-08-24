package codechicken.nei.recipe;

import codechicken.core.TaskProfiler;
import codechicken.nei.ItemList;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.NEIClientUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class GuiCraftingRecipe extends GuiRecipe
{
    public static boolean openRecipeGui(String outputId, Object... results) {
        Minecraft mc = NEIClientUtils.mc();
        GuiScreen prevscreen = mc.currentScreen;// instanceof GuiContainer ? (GuiContainer) mc.currentScreen : null;

        ArrayList<ICraftingHandler> handlers;
        TaskProfiler profiler = ProfilerRecipeHandler.getProfiler();
        profiler.start("recipe.concurrent.crafting");
       
        // Pre-find the fuels so we're not fighting over it
        FuelRecipeHandler.findFuelsOnceParallel();

        try {
            handlers = serialCraftingHandlers.stream().map(h -> h.getRecipeHandler(outputId, results))
                .filter(h -> h.numRecipes() > 0)
                .collect(Collectors.toCollection(ArrayList::new));

            handlers.addAll(ItemList.forkJoinPool.submit(() -> craftinghandlers.parallelStream()
                .map(h -> h.getRecipeHandler(outputId, results))
                .filter(h -> h.numRecipes() > 0)
                .collect(Collectors.toCollection(ArrayList::new))).get());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            return false;
        } finally {
            profiler.end();
        }

        if (handlers.isEmpty())
            return false;

        mc.displayGuiScreen(new GuiCraftingRecipe(prevscreen, handlers));
        return true;
    }

    private GuiCraftingRecipe(GuiScreen prevgui, ArrayList<ICraftingHandler> handlers) {
        super(prevgui);
        currenthandlers = handlers;
    }

    public static void registerRecipeHandler(ICraftingHandler handler) {
        final String handlerId = handler.getHandlerId();
        if(craftinghandlers.stream().anyMatch(h -> h.getHandlerId().equals(handlerId)) || serialCraftingHandlers.stream().anyMatch(h -> h.getHandlerId().equals(handlerId)))
            return;

        if(NEIClientConfig.serialHandlers.contains(handlerId))
            serialCraftingHandlers.add(handler);
        else
            craftinghandlers.add(handler);
    }

    public ArrayList<? extends IRecipeHandler> getCurrentRecipeHandlers() {
        return currenthandlers;
    }

    public ArrayList<ICraftingHandler> currenthandlers;

    public static ArrayList<ICraftingHandler> craftinghandlers = new ArrayList<>();
    public static ArrayList<ICraftingHandler> serialCraftingHandlers = new ArrayList<>();
}
