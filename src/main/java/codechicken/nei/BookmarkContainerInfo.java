package codechicken.nei;

import java.util.HashMap;

import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiContainer;

import codechicken.nei.api.API;
import codechicken.nei.api.IBookmarkContainerHandler;

public class BookmarkContainerInfo {

    private static final HashMap<Class<? extends GuiContainer>, IBookmarkContainerHandler> handlerMap = new HashMap<>();

    public static void registerBookmarkContainerHandler(Class<? extends GuiContainer> classz,
            IBookmarkContainerHandler handler) {
        handlerMap.put(classz, handler);
    }

    public static boolean hasBookmarkContainerHandler(Class<? extends GuiContainer> classz) {
        return handlerMap.containsKey(classz);
    }

    public static IBookmarkContainerHandler getBookmarkContainerHandler(GuiContainer gui) {
        if (gui != null)
        {
            return handlerMap.get(gui.getClass());
        }
    }

    public static void load() {
        API.registerBookmarkContainerHandler(GuiChest.class, new DefaultBookmarkContainerHandler());
    }
}
