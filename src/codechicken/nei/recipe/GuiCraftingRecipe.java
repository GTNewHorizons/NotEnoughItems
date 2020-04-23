package codechicken.nei.recipe;

import codechicken.core.TaskProfiler;
import codechicken.nei.ItemList;
import codechicken.nei.NEIClientUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;

import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class GuiCraftingRecipe extends GuiRecipe
{
    protected static ArrayList<ICraftingHandler> getCraftingHandlers(String outputId, Object... results) {
        return craftinghandlers.parallelStream()
            .map(h -> h.getRecipeHandler(outputId, results))
            .filter(h -> h.numRecipes() > 0)
            .collect(Collectors.toCollection(ArrayList::new));
    }

    public static boolean openRecipeGui(String outputId, Object... results) {
        Minecraft mc = NEIClientUtils.mc();
        GuiContainer prevscreen = mc.currentScreen instanceof GuiContainer ? (GuiContainer) mc.currentScreen : null;

        ArrayList<ICraftingHandler> handlers;
        TaskProfiler profiler = ProfilerRecipeHandler.getProfiler();
        try {
            profiler.start("recipe.concurrent.crafting");
            handlers = ItemList.forkJoinPool.submit(() -> getCraftingHandlers(outputId, results)).get();
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

    private GuiCraftingRecipe(GuiContainer prevgui, ArrayList<ICraftingHandler> handlers) {
        super(prevgui);
        currenthandlers = handlers;
    }

    public static void registerRecipeHandler(ICraftingHandler handler) {
        for (ICraftingHandler handler1 : craftinghandlers)
            if (handler1.getClass() == handler.getClass())
                return;

        craftinghandlers.add(handler);
    }

    public ArrayList<? extends IRecipeHandler> getCurrentRecipeHandlers() {
        return currenthandlers;
    }

    public ArrayList<ICraftingHandler> currenthandlers;

    public static ArrayList<ICraftingHandler> craftinghandlers = new ArrayList<>();
}
