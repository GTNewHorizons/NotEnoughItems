package codechicken.nei.recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import codechicken.core.TaskProfiler;
import codechicken.nei.ItemList;
import codechicken.nei.NEIClientConfig;

class RecipeHandlerQuery<T extends IRecipeHandler> {

    private final Function<T, T> recipeHandlerFunction;
    private final List<T> recipeHandlers;
    private final List<T> serialRecipeHandlers;
    private final String[] errorMessage;
    private boolean error = false;

    RecipeHandlerQuery(Function<T, T> recipeHandlerFunction, List<T> recipeHandlers, List<T> serialRecipeHandlers,
            String... errorMessage) {
        this.recipeHandlerFunction = recipeHandlerFunction;
        this.recipeHandlers = recipeHandlers;
        this.serialRecipeHandlers = serialRecipeHandlers;
        this.errorMessage = errorMessage;
    }

    ArrayList<T> runWithProfiling(String profilerSection) {
        TaskProfiler profiler = ProfilerRecipeHandler.getProfiler();
        TemplateRecipeHandler.disableCycledIngredients = true;
        profiler.start(profilerSection);
        try {
            ArrayList<T> handlers = getRecipeHandlersParallel();

            if (error) {
                displayRecipeLookupError();
            }
            return handlers;
        } catch (InterruptedException | ExecutionException e) {
            printLog(e);
            displayRecipeLookupError();
            return new ArrayList<>(0);
        } finally {
            TemplateRecipeHandler.disableCycledIngredients = false;
            profiler.end();
        }
    }

    private ArrayList<T> getRecipeHandlersParallel() throws InterruptedException, ExecutionException {
        // Pre-find the fuels so we're not fighting over it
        FuelRecipeHandler.findFuelsOnceParallel();
        ArrayList<T> handlers = getSerialHandlersWithRecipes();
        handlers.addAll(getHandlersWithRecipes());
        handlers.sort(NEIClientConfig.HANDLER_COMPARATOR);
        return handlers;
    }

    private ArrayList<T> getSerialHandlersWithRecipes() {

        return serialRecipeHandlers.stream().map(handler -> {
            try {
                return isHidden(handler) ? null : recipeHandlerFunction.apply(handler);
            } catch (Throwable t) {
                printLog(t);
                error = true;
                return null;
            }
        }).filter(h -> h != null && !h.isEmpty()).collect(Collectors.toCollection(ArrayList::new));
    }

    private ArrayList<T> getHandlersWithRecipes() throws InterruptedException, ExecutionException {

        return ItemList.forkJoinPool.submit(() -> recipeHandlers.parallelStream().map(handler -> {
            try {
                return isHidden(handler) ? null : recipeHandlerFunction.apply(handler);
            } catch (Throwable t) {
                printLog(t);
                error = true;
                return null;
            }
        }).filter(h -> h != null && !h.isEmpty()).collect(Collectors.toCollection(ArrayList::new))).get();
    }

    private boolean isHidden(T handler) {
        return NEIClientConfig.hiddenHandlerRegex.stream()
                .map(pattern -> pattern.matcher(GuiRecipeTab.getHandlerInfo(handler).getHandlerName()))
                .anyMatch(Matcher::matches);
    }

    private void printLog(Throwable t) {
        for (String message : errorMessage) {
            NEIClientConfig.logger.error(message);
        }
        t.printStackTrace();
    }

    private void displayRecipeLookupError() {
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (player != null) {
            IChatComponent chat = new ChatComponentTranslation("nei.chat.recipe.error");
            chat.getChatStyle().setColor(EnumChatFormatting.RED);
            player.addChatComponentMessage(chat);
        }
    }
}
