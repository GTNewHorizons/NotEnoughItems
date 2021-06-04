package codechicken.nei.api;

import codechicken.nei.NEIChestGuiHandler;
import codechicken.nei.NEICreativeGuiHandler;
import codechicken.nei.NEIDummySlotHandler;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiContainerCreative;

import java.util.HashSet;
import java.util.Iterator;
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
}
