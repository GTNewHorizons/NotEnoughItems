package codechicken.nei.recipe;

import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import net.minecraft.client.gui.inventory.GuiBrewingStand;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiCrafting;
import net.minecraft.client.gui.inventory.GuiFurnace;
import net.minecraft.client.gui.inventory.GuiInventory;

import com.google.common.base.Objects;

import codechicken.nei.OffsetPositioner;
import codechicken.nei.api.API;
import codechicken.nei.api.IOverlayHandler;
import codechicken.nei.api.IStackPositioner;

public class RecipeInfo {

    private static class OverlayKey {

        final String ident;
        final Class<? extends GuiContainer> guiClass;

        public OverlayKey(Class<? extends GuiContainer> classz, String ident) {
            this.guiClass = classz;
            this.ident = ident;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof OverlayKey item) {
                return Objects.equal(ident, item.ident) && guiClass == item.guiClass;
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(ident, guiClass);
        }
    }

    static final HashMap<OverlayKey, IOverlayHandler> overlayMap = new HashMap<>();
    static final HashMap<OverlayKey, IStackPositioner> positionerMap = new HashMap<>();
    static final HashMap<Class<? extends GuiContainer>, int[]> offsets = new HashMap<>();

    public static void registerOverlayHandler(Class<? extends GuiContainer> classz, IOverlayHandler handler,
            String ident) {
        overlayMap.put(new OverlayKey(classz, ident), handler);
    }

    public static void registerGuiOverlay(Class<? extends GuiContainer> classz, String ident,
            IStackPositioner positioner) {
        positionerMap.put(new OverlayKey(classz, ident), positioner);
        if (positioner instanceof OffsetPositioner && !offsets.containsKey(classz)) {
            OffsetPositioner p = (OffsetPositioner) positioner;
            setGuiOffset(classz, p.offsetx, p.offsety);
        }
    }

    public static void setGuiOffset(Class<? extends GuiContainer> classz, int x, int y) {
        offsets.put(classz, new int[] { x, y });
    }

    public static boolean hasDefaultOverlay(GuiContainer gui, String ident) {
        return positionerMap.containsKey(new OverlayKey(gui.getClass(), ident));
    }

    public static boolean hasOverlayHandler(GuiContainer gui, String ident) {
        return overlayMap.containsKey(new OverlayKey(gui.getClass(), ident));
    }

    public static boolean hasOverlayHandler(Class<? extends GuiContainer> classz, String ident) {
        return overlayMap.containsKey(new OverlayKey(classz, ident));
    }

    public static IOverlayHandler getOverlayHandler(GuiContainer gui, String ident) {
        return overlayMap.get(new OverlayKey(gui.getClass(), ident));
    }

    public static List<String> getOverlayHandlerIdents(GuiContainer gui) {
        final Class<? extends GuiContainer> classz = gui.getClass();
        return overlayMap.keySet().stream().filter(key -> key.ident != null && key.guiClass.equals(classz))
                .map(key -> key.ident).collect(Collectors.toList());
    }

    public static IStackPositioner getStackPositioner(GuiContainer gui, String ident) {
        return positionerMap.get(new OverlayKey(gui.getClass(), ident));
    }

    public static int[] getGuiOffset(GuiContainer gui) {
        int[] offset = offsets.get(gui.getClass());
        return offset == null ? new int[] { 5, 11 } : offset;
    }

    public static void load() {
        API.registerRecipeHandler(new ShapedRecipeHandler());
        API.registerUsageHandler(new ShapedRecipeHandler());
        API.registerRecipeHandler(new ShapelessRecipeHandler());
        API.registerUsageHandler(new ShapelessRecipeHandler());
        API.registerRecipeHandler(new FireworkRecipeHandler());
        API.registerUsageHandler(new FireworkRecipeHandler());
        API.registerRecipeHandler(new FurnaceRecipeHandler());
        API.registerUsageHandler(new FurnaceRecipeHandler());
        API.registerRecipeHandler(new BrewingRecipeHandler());
        API.registerUsageHandler(new BrewingRecipeHandler());
        API.registerRecipeHandler(new FuelRecipeHandler());
        API.registerUsageHandler(new FuelRecipeHandler());

        API.registerGuiOverlay(GuiCrafting.class, "crafting");
        API.registerGuiOverlay(GuiInventory.class, "crafting2x2", 63, 20);
        API.registerGuiOverlay(GuiFurnace.class, "smelting");
        API.registerGuiOverlay(GuiFurnace.class, "fuel");
        API.registerGuiOverlay(GuiBrewingStand.class, "brewing");

        API.registerGuiOverlayHandler(GuiCrafting.class, new DefaultOverlayHandler(), "crafting");
        API.registerGuiOverlayHandler(GuiInventory.class, new DefaultOverlayHandler(63, 20), "crafting2x2");
        API.registerGuiOverlayHandler(GuiBrewingStand.class, new BrewingOverlayHandler(), "brewing");

        API.registerRecipeHandler(new ProfilerRecipeHandler(true));
        API.registerUsageHandler(new ProfilerRecipeHandler(false));
    }
}
