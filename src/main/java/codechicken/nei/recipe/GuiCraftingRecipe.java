package codechicken.nei.recipe;

import codechicken.core.TaskProfiler;
import codechicken.nei.ItemList;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.NEIClientUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import codechicken.nei.ItemPanels;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import java.util.ArrayList;
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

        mc.displayGuiScreen(new GuiCraftingRecipe(prevscreen, handlers));
        
        if (NEIClientConfig.saveCurrentRecipeInBookmarksEnabled() && !NEIClientUtils.shiftKey() && outputId.equals("item") && mc.currentScreen instanceof GuiRecipe) {
            GuiCraftingRecipe.openTargetRecipe((GuiRecipe)mc.currentScreen, (ItemStack)results[0]);
        }

        return true;
    }

    protected static void openTargetRecipe(GuiRecipe currentScreen, ItemStack stackover)
    {
        currentScreen.openTargetRecipe(ItemPanels.bookmarkPanel.getBookmarkRecipeId(stackover));
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
