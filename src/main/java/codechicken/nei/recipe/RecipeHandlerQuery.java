package codechicken.nei.recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import codechicken.core.TaskProfiler;
import codechicken.nei.ItemList;
import codechicken.nei.NEIClientConfig;

class RecipeHandlerQuery<T extends IRecipeHandler> {

    private final Function<T, T> recipeHandlerFunction;
    private final List<T> recipeHandlers;
    private final List<T> serialRecipeHandlers;
    private final String[] errorMessage;

    RecipeHandlerQuery(Function<T, T> recipeHandlerFunction, List<T> recipeHandlers, List<T> serialRecipeHandlers,
            String... errorMessage) {
        this.recipeHandlerFunction = recipeHandlerFunction;
        this.recipeHandlers = recipeHandlers;
        this.serialRecipeHandlers = serialRecipeHandlers;
        this.errorMessage = errorMessage;
    }

    ArrayList<T> runWithProfiling(String profilerSection) {
        TaskProfiler profiler = ProfilerRecipeHandler.getProfiler();
        profiler.start(profilerSection);
        try {
            Pair<Boolean, ArrayList<T>> handlersResult = getRecipeHandlersParallel();
            if (handlersResult.getLeft()) {
                displayRecipeLookupError();
            }
            return handlersResult.getRight();
        } catch (InterruptedException | ExecutionException e) {
            printLog(e);
            displayRecipeLookupError();
            return new ArrayList<>(0);
        } finally {
            profiler.end();
        }
    }

    private Pair<Boolean, ArrayList<T>> getRecipeHandlersParallel() throws InterruptedException, ExecutionException {
        // Pre-find the fuels so we're not fighting over it
        FuelRecipeHandler.findFuelsOnceParallel();

        Pair<Boolean, ArrayList<T>> serialHandlersResult = getSerialHandlersWithRecipes();
        boolean err = serialHandlersResult.getLeft();
        ArrayList<T> ret = serialHandlersResult.getRight();

        Pair<Boolean, ArrayList<T>> handlersResult = getHandlersWithRecipes();
        err |= handlersResult.getLeft();
        ret.addAll(handlersResult.getRight());

        ret.sort(NEIClientConfig.HANDLER_COMPARATOR);
        return new ImmutablePair<>(err, ret);
    }

    private Pair<Boolean, ArrayList<T>> getSerialHandlersWithRecipes() {
        AtomicBoolean err = new AtomicBoolean(false);
        ArrayList<T> ret = serialRecipeHandlers.stream().map(handler -> {
            try {
                return recipeHandlerFunction.apply(handler);
            } catch (Throwable t) {
                printLog(t);
                err.set(true);
                return null;
            }
        }).filter(h -> h != null && h.numRecipes() > 0).collect(Collectors.toCollection(ArrayList::new));
        return new ImmutablePair<>(err.get(), ret);
    }

    private Pair<Boolean, ArrayList<T>> getHandlersWithRecipes() throws InterruptedException, ExecutionException {
        AtomicBoolean err = new AtomicBoolean(false);
        ArrayList<T> ret = ItemList.forkJoinPool.submit(() -> recipeHandlers.parallelStream().map(handler -> {
            try {
                return recipeHandlerFunction.apply(handler);
            } catch (Throwable t) {
                printLog(t);
                err.set(true);
                return null;
            }
        }).filter(h -> h != null && h.numRecipes() > 0).collect(Collectors.toCollection(ArrayList::new))).get();
        return new ImmutablePair<>(err.get(), ret);
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
