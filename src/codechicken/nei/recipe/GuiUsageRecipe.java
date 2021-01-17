package codechicken.nei.recipe;

import codechicken.core.TaskProfiler;
import codechicken.nei.ItemList;
import codechicken.nei.NEIClientUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;

import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class GuiUsageRecipe extends GuiRecipe
{
     public static boolean openRecipeGui(String inputId, Object... ingredients) {
        Minecraft mc = NEIClientUtils.mc();
        GuiContainer prevscreen = mc.currentScreen instanceof GuiContainer ? (GuiContainer) mc.currentScreen : null;

        ArrayList<IUsageHandler> handlers;
        TaskProfiler profiler = ProfilerRecipeHandler.getProfiler();

        profiler.start("recipe.concurrent.usage");
        try {
            handlers = ItemList.forkJoinPool.submit(() -> usagehandlers.parallelStream()
                .map(h -> h.getUsageHandler(inputId, ingredients))
                .filter(h -> h.numRecipes() > 0)
                .collect(Collectors.toCollection(ArrayList::new))).get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            return false;
        } finally {
            profiler.end();
        }

        if (handlers.isEmpty())
            return false;

        mc.displayGuiScreen(new GuiUsageRecipe(prevscreen, handlers));
        return true;
    }

    private GuiUsageRecipe(GuiContainer prevgui, ArrayList<IUsageHandler> handlers) {
        super(prevgui);
        currenthandlers = handlers;
    }

    public static void registerUsageHandler(IUsageHandler handler) {
        for (IUsageHandler handler1 : usagehandlers) {
            if (handler1.getClass() == handler.getClass())
                return;
        }

        usagehandlers.add(handler);
    }

    public ArrayList<? extends IRecipeHandler> getCurrentRecipeHandlers() {
        return currenthandlers;
    }

    public ArrayList<IUsageHandler> currenthandlers;

    public static ArrayList<IUsageHandler> usagehandlers = new ArrayList<>();
}
