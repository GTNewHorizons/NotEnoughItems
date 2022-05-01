package codechicken.nei.api;

import codechicken.lib.vec.Rectangle4i;
import codechicken.nei.NEIChestGuiHandler;
import codechicken.nei.NEICreativeGuiHandler;
import codechicken.nei.NEIDummySlotHandler;
import codechicken.nei.recipe.GuiRecipeCatalyst;
import codechicken.nei.recipe.SearchInputDropHandler;
import codechicken.nei.recipe.FillFluidContainerHandler;
import codechicken.nei.recipe.CheatItemHandler;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiContainerCreative;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class GuiInfo {
    public static final LinkedList<INEIGuiHandler> guiHandlers = new LinkedList<>();
    public static final HashSet<Class<? extends GuiContainer>> customSlotGuis = new HashSet<>();
    public static ReentrantReadWriteLock guiHandlersLock = new ReentrantReadWriteLock();
    public static Lock writeLock = guiHandlersLock.writeLock();
    public static Lock readLock = guiHandlersLock.readLock();


    public static void load() {
        API.registerNEIGuiHandler(new NEICreativeGuiHandler());
        API.registerNEIGuiHandler(new NEIChestGuiHandler());
        API.registerNEIGuiHandler(new NEIDummySlotHandler());
        API.registerNEIGuiHandler(new GuiRecipeCatalyst(null));
        API.registerNEIGuiHandler(new SearchInputDropHandler());
        API.registerNEIGuiHandler(new FillFluidContainerHandler());
        API.registerNEIGuiHandler(new CheatItemHandler());
        customSlotGuis.add(GuiContainerCreative.class);
    }

    public static void clearGuiHandlers() {
        try {
            GuiInfo.writeLock.lock();
            guiHandlers.removeIf(ineiGuiHandler -> ineiGuiHandler instanceof GuiContainer);
        } finally {
            GuiInfo.writeLock.unlock();
        }
    }

    public static boolean hasCustomSlots(GuiContainer gui) {
        return customSlotGuis.contains(gui.getClass());
    }

    public static boolean hideItemPanelSlot(GuiContainer gui, Rectangle4i rect)
    {
        try {
            readLock.lock();
            if (guiHandlers.stream().anyMatch(handler -> handler.hideItemPanelSlot(gui, rect.x, rect.y, rect.w, rect.h))) {
                return true;
            }
        } finally {
            readLock.unlock();
        }

        for (Object item : gui.buttonList) {
            GuiButton button = (GuiButton) item;

            if ((new Rectangle4i(button.xPosition, button.yPosition, button.width, button.height)).intersects(rect)) {
                return true;
            }

        }

        return false;
    }

}
