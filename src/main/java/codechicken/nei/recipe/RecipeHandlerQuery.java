package codechicken.nei.recipe;

import codechicken.core.TaskProfiler;
import codechicken.nei.ItemList;
import codechicken.nei.NEIClientConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

class RecipeHandlerQuery<T extends IRecipeHandler> {
    private final Function<T, T> recipeHandlerFunction;
    private final List<T> recipeHandlers;
    private final List<T> serialRecipeHandlers;

    RecipeHandlerQuery(Function<T, T> recipeHandlerFunction, List<T> recipeHandlers, List<T> serialRecipeHandlers) {
        this.recipeHandlerFunction = recipeHandlerFunction;
        this.recipeHandlers = recipeHandlers;
        this.serialRecipeHandlers = serialRecipeHandlers;
    }

    ArrayList<T> run(String profilerSection) {
        TaskProfiler profiler = ProfilerRecipeHandler.getProfiler();
        profiler.start(profilerSection);
        try {
            return getRecipeHandlersParallel();
        } catch (InterruptedException | ExecutionException e) {
            displayRecipeLookupError(e);
            return new ArrayList<>(0);
        } finally {
            profiler.end();
        }
    }

    private ArrayList<T> getRecipeHandlersParallel() throws InterruptedException, ExecutionException {
        // Pre-find the fuels so we're not fighting over it
        FuelRecipeHandler.findFuelsOnceParallel();

        ArrayList<T> handlers = serialRecipeHandlers.stream()
                .map(recipeHandlerFunction)
                .filter(h -> h.numRecipes() > 0)
                .collect(Collectors.toCollection(ArrayList::new));

        handlers.addAll(ItemList.forkJoinPool
                .submit(() -> recipeHandlers.parallelStream()
                        .map(recipeHandlerFunction)
                        .filter(h -> h.numRecipes() > 0)
                        .collect(Collectors.toCollection(ArrayList::new)))
                .get());

        handlers.sort(NEIClientConfig.HANDLER_COMPARATOR);
        return handlers;
    }

    private static void displayRecipeLookupError(Exception e) {
        e.printStackTrace();
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (player != null) {
            IChatComponent chat = new ChatComponentTranslation("nei.chat.recipe.error");
            chat.getChatStyle().setColor(EnumChatFormatting.RED);
            player.addChatComponentMessage(chat);
        }
    }
}
