package codechicken.nei;

import codechicken.nei.api.API;
import codechicken.nei.api.IBookmarkContainerHandler;
import com.google.common.base.Objects;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiCrafting;
import net.minecraft.client.gui.inventory.GuiInventory;

import java.util.HashMap;

public class BookmarkContainerInfo {

    static final HashMap<Class<? extends GuiContainer>, IBookmarkContainerHandler> handlerMap = new HashMap<>();

    public static void registerBookmarkContainerHandler(Class<? extends GuiContainer> classz, IBookmarkContainerHandler handler) {
        handlerMap.put(classz, handler);
    }

    public static boolean hasBookmarkContainerHandler(Class<? extends GuiContainer> classz) {
        return handlerMap.containsKey(classz);
    }

    public static IBookmarkContainerHandler getBookmarkContainerHandler(GuiContainer gui) {
        return handlerMap.get(gui.getClass());
    }

    public static void load() {
        API.registerBookmarkContainerHandler(GuiChest.class, new DefaultBookmarkContainerHandler());
    }
}
