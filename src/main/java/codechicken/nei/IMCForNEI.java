package codechicken.nei;

import net.minecraft.client.gui.GuiRepair;
import net.minecraft.client.gui.inventory.GuiBrewingStand;
import net.minecraft.client.gui.inventory.GuiCrafting;
import net.minecraft.client.gui.inventory.GuiFurnace;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.nbt.NBTTagCompound;

import codechicken.nei.api.API;
import codechicken.nei.recipe.BrewingOverlayHandler;
import codechicken.nei.recipe.BrewingRecipeHandler;
import codechicken.nei.recipe.DefaultOverlayHandler;
import codechicken.nei.recipe.FireworkRecipeHandler;
import codechicken.nei.recipe.FuelRecipeHandler;
import codechicken.nei.recipe.FurnaceRecipeHandler;
import codechicken.nei.recipe.InformationHandler;
import codechicken.nei.recipe.ProfilerRecipeHandler;
import codechicken.nei.recipe.RepairOverlayHandler;
import codechicken.nei.recipe.RepairRecipeHandler;
import codechicken.nei.recipe.ShapedRecipeHandler;
import codechicken.nei.recipe.ShapelessRecipeHandler;
import cpw.mods.fml.common.event.FMLInterModComms;

public class IMCForNEI {

    public static void IMCSender() {

        API.registerRecipeHandler(new ShapedRecipeHandler());
        API.registerUsageHandler(new ShapedRecipeHandler());
        sendHandler("codechicken.nei.recipe.ShapedRecipeHandler", "minecraft:crafting_table");

        API.registerRecipeHandler(new ShapelessRecipeHandler());
        API.registerUsageHandler(new ShapelessRecipeHandler());
        sendHandler("codechicken.nei.recipe.ShapelessRecipeHandler", "minecraft:crafting_table");

        API.registerRecipeHandler(new FireworkRecipeHandler());
        API.registerUsageHandler(new FireworkRecipeHandler());
        sendHandler("codechicken.nei.recipe.FireworkRecipeHandler", "minecraft:fireworks");

        API.registerRecipeHandler(new FurnaceRecipeHandler());
        API.registerUsageHandler(new FurnaceRecipeHandler());
        sendHandler("codechicken.nei.recipe.FurnaceRecipeHandler", "minecraft:furnace");

        API.registerRecipeHandler(new BrewingRecipeHandler());
        API.registerUsageHandler(new BrewingRecipeHandler());
        sendHandler("codechicken.nei.recipe.BrewingRecipeHandler", "minecraft:brewing_stand");

        API.registerRecipeHandler(new FuelRecipeHandler());
        API.registerUsageHandler(new FuelRecipeHandler());
        sendFuelRecipeHandler();

        API.registerRecipeHandler(new InformationHandler());
        API.registerUsageHandler(new InformationHandler());
        sendInformationHandler();

        API.registerRecipeHandler(new RepairRecipeHandler());
        API.registerUsageHandler(new RepairRecipeHandler());
        sendRepairHandler();
        sendCatalyst("repair", "minecraft:anvil");

        API.registerGuiOverlay(GuiCrafting.class, "crafting");
        API.registerGuiOverlay(GuiInventory.class, "crafting2x2", 63, 20);
        API.registerGuiOverlay(GuiFurnace.class, "smelting");
        API.registerGuiOverlay(GuiFurnace.class, "fuel");
        API.registerGuiOverlay(GuiBrewingStand.class, "brewing");
        API.registerGuiOverlay(GuiRepair.class, "repair", 2, 37);

        API.registerGuiOverlayHandler(GuiCrafting.class, new DefaultOverlayHandler(), "crafting");
        API.registerGuiOverlayHandler(GuiInventory.class, new DefaultOverlayHandler(63, 20), "crafting2x2");
        API.registerGuiOverlayHandler(GuiBrewingStand.class, new BrewingOverlayHandler(), "brewing");
        API.registerGuiOverlayHandler(GuiRepair.class, new RepairOverlayHandler(), "repair");

        API.registerRecipeHandler(new ProfilerRecipeHandler(true));
        API.registerUsageHandler(new ProfilerRecipeHandler(false));
        sendProfilerRecipeHandler();
    }

    private static void sendHandler(String aName, String aBlock) {
        NBTTagCompound aNBT = new NBTTagCompound();
        aNBT.setString("handler", aName);
        aNBT.setString("modName", "Minecraft");
        aNBT.setString("modId", "minecraft");
        aNBT.setString("itemName", aBlock);
        aNBT.setInteger("handlerWidth", 166);
        aNBT.setInteger("handlerHeight", 65);
        aNBT.setBoolean("multipleWidgetsAllowed", true);
        aNBT.setInteger("yShift", 0);
        FMLInterModComms.sendMessage("NotEnoughItems", "registerHandlerInfo", aNBT);
    }

    private static void sendRepairHandler() {
        NBTTagCompound aNBT = new NBTTagCompound();
        aNBT.setString("handler", "codechicken.nei.recipe.RepairRecipeHandler");
        aNBT.setString("modName", "Minecraft");
        aNBT.setString("modId", "minecraft");
        aNBT.setString("itemName", "minecraft:anvil");
        aNBT.setInteger("handlerHeight", 39);
        aNBT.setInteger("handlerWidth", 166);
        aNBT.setBoolean("multipleWidgetsAllowed", true);
        aNBT.setBoolean("showFavoritesButton", false);
        aNBT.setInteger("yShift", 0);
        FMLInterModComms.sendMessage("NotEnoughItems", "registerHandlerInfo", aNBT);
    }

    private static void sendFuelRecipeHandler() {
        NBTTagCompound aNBT = new NBTTagCompound();
        aNBT.setString("handler", "codechicken.nei.recipe.FuelRecipeHandler");
        aNBT.setString("modName", "Minecraft");
        aNBT.setString("modId", "minecraft");
        aNBT.setString("imageResource", "nei:textures/nei_tabbed_sprites.png");
        aNBT.setInteger("imageX", 80);
        aNBT.setInteger("imageY", 0);
        aNBT.setInteger("imageWidth", 14);
        aNBT.setInteger("imageHeight", 14);
        aNBT.setInteger("handlerHeight", 65);
        aNBT.setInteger("handlerWidth", 166);
        aNBT.setBoolean("multipleWidgetsAllowed", true);
        aNBT.setInteger("yShift", 0);
        FMLInterModComms.sendMessage("NotEnoughItems", "registerHandlerInfo", aNBT);
    }

    private static void sendInformationHandler() {
        NBTTagCompound aNBT = new NBTTagCompound();
        aNBT.setString("handler", "codechicken.nei.recipe.InformationHandler");
        aNBT.setString("modName", "Not Enough Items");
        aNBT.setString("modId", "nei");
        aNBT.setString("imageResource", "nei:textures/nei_tabbed_sprites.png");
        aNBT.setInteger("imageX", 96);
        aNBT.setInteger("imageY", 0);
        aNBT.setInteger("imageWidth", 16);
        aNBT.setInteger("imageHeight", 16);
        aNBT.setInteger("handlerHeight", 65);
        aNBT.setInteger("handlerWidth", 166);
        aNBT.setBoolean("multipleWidgetsAllowed", true);
        aNBT.setInteger("yShift", 0);
        FMLInterModComms.sendMessage("NotEnoughItems", "registerHandlerInfo", aNBT);
    }

    private static void sendProfilerRecipeHandler() {
        NBTTagCompound aNBT = new NBTTagCompound();
        aNBT.setString("handler", "codechicken.nei.recipe.ProfilerRecipeHandler");
        aNBT.setString("modName", "Not Enough Items");
        aNBT.setString("modId", "nei");
        aNBT.setString("itemName", "minecraft:clock");
        aNBT.setInteger("handlerHeight", 16);
        aNBT.setInteger("handlerWidth", 166);
        aNBT.setBoolean("multipleWidgetsAllowed", true);
        aNBT.setInteger("yShift", 0);
        FMLInterModComms.sendMessage("NotEnoughItems", "registerHandlerInfo", aNBT);
    }

    private static void sendCatalyst(String aName, String aStack) {
        NBTTagCompound aNBT = new NBTTagCompound();
        aNBT.setString("handlerID", aName);
        aNBT.setString("itemName", aStack);
        aNBT.setInteger("priority", 0);
        FMLInterModComms.sendMessage("NotEnoughItems", "registerCatalystInfo", aNBT);
    }

}
