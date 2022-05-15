package codechicken.nei.recipe;

import codechicken.core.TaskProfiler;
import codechicken.nei.ItemList;
import codechicken.nei.ItemPanels;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.PositionedStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class GuiCraftingRecipe extends GuiRecipe {
    public static boolean openRecipeGui(String outputId, Object... results) {
        return openRecipeGui(outputId, false, results);
    }


    public static boolean openRecipeGui(String outputId, Boolean overlay, Object... results) {
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
            EntityPlayer player = Minecraft.getMinecraft().thePlayer;
            if (player != null) {
                IChatComponent chat = new ChatComponentTranslation("nei.chat.recipe.error");
                chat.getChatStyle().setColor(EnumChatFormatting.RED);
                player.addChatComponentMessage(chat);
            }
            return false;
        } finally {
            profiler.end();
        }

        if (handlers.isEmpty())
            return false;

        handlers.sort(NEIClientConfig.HANDLER_COMPARATOR);

        BookmarkRecipeId recipeId = null;

        if ("item".equals(outputId)) {
            recipeId = getRecipeId(prevscreen, (ItemStack) results[0]);
        }

        GuiCraftingRecipe gui = new GuiCraftingRecipe(prevscreen, handlers, recipeId);

        mc.displayGuiScreen(gui);

        if (NEIClientConfig.saveCurrentRecipeInBookmarksEnabled())
            if (!NEIClientUtils.shiftKey() || overlay) {
                gui.openTargetRecipe(gui.recipeId);
            }

        if (overlay) {
            if (!NEIClientConfig.saveCurrentRecipeInBookmarksEnabled() || gui.recipeId == null) {
                mc.displayGuiScreen(prevscreen);
                return false;
            }
            gui.overlayRecipe(gui.recipeId.position);
        }

        return true;
    }

    protected static BookmarkRecipeId getRecipeId(GuiScreen gui, ItemStack stackover) {

        if (gui instanceof GuiRecipe) {
            final List<PositionedStack> ingredients = ((GuiRecipe) gui).getFocusedRecipeIngredients();
            final String handlerName = ((GuiRecipe) gui).getHandlerName();

            if (ingredients != null && !ingredients.isEmpty()) {
                return new BookmarkRecipeId(handlerName, ingredients);
            }
        }

        return ItemPanels.bookmarkPanel.getBookmarkRecipeId(stackover);
    }

    private GuiCraftingRecipe(GuiScreen prevgui, ArrayList<ICraftingHandler> handlers, BookmarkRecipeId recipeId) {
        this(prevgui, handlers);
        this.recipeId = recipeId;
    }

    private GuiCraftingRecipe(GuiScreen prevgui, ArrayList<ICraftingHandler> handlers) {
        super(prevgui);
        currenthandlers = handlers;
    }

    public static void registerRecipeHandler(ICraftingHandler handler) {
        final String handlerId = handler.getHandlerId();
        if (craftinghandlers.stream().anyMatch(h -> h.getHandlerId().equals(handlerId)) || serialCraftingHandlers.stream().anyMatch(h -> h.getHandlerId().equals(handlerId)))
            return;

        if (NEIClientConfig.serialHandlers.contains(handlerId))
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
