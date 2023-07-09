package codechicken.nei.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import codechicken.core.CommonUtils;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.NEIServerUtils;
import codechicken.nei.recipe.CatalystInfo;
import codechicken.nei.recipe.GuiRecipeTab;
import codechicken.nei.recipe.HandlerInfo;
import codechicken.nei.recipe.RecipeCatalysts;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.event.FMLInterModComms;
import cpw.mods.fml.common.event.FMLInterModComms.IMCMessage;

public class IMCHandler {

    private static final Set<String> processedCatalystSenders = new HashSet<>();

    private IMCHandler() {}

    public static void processIMC(List<FMLInterModComms.IMCMessage> messages) {
        for (FMLInterModComms.IMCMessage message : messages) {
            String type = message.key;
            if (type == null || type.isEmpty()) continue;
            if (CommonUtils.isClient()) {
                switch (type) {
                    case "registerHandlerInfo":
                        handleRegisterHandlerInfo(message);
                        break;
                    case "removeHandlerInfo":
                        handleRemoveHandlerInfo(message);
                        break;
                    case "registerCatalystInfo":
                        handleRegisterCatalystInfo(message);
                        break;
                    case "removeCatalystInfo":
                        handleRemoveCatalystInfo(message);
                        break;
                }
            }
        }
    }

    private static void handleRegisterHandlerInfo(IMCMessage message) {
        if (!message.isNBTMessage()) {
            logInvalidMessage(message, "NBT");
            return;
        }
        final NBTTagCompound tag = message.getNBTValue();
        final String handlerID;
        if (tag.hasKey("handler")) {
            NEIClientConfig.logger.warn(
                    "Received tag 'handler' for registerHandlerInfo IMC from {}, this tag has been renamed to 'handlerID'",
                    message.getSender());
            handlerID = tag.getString("handler");
        } else {
            handlerID = tag.getString("handlerID");
        }

        NEIClientConfig.logger.info("Processing registerHandlerInfo for '{}' from {}", handlerID, message.getSender());
        final String modName = tag.getString("modName");
        final String modId = tag.getString("modId");
        final boolean requiresMod = tag.getBoolean("modRequired");
        final String excludedModId = tag.hasKey("excludedModId") ? tag.getString("excludedModId") : null;

        if (handlerID.equals("") || modName.equals("") || modId.equals("")) {
            NEIClientConfig.logger.warn(
                    "registerHandlerInfo IMC from {} is missing required tags 'handlerID', 'modName', and 'modID'",
                    message.getSender());
            return;
        }

        if (requiresMod && !Loader.isModLoaded(modId)) return;
        if (excludedModId != null && Loader.isModLoaded(excludedModId)) return;

        HandlerInfo info = new HandlerInfo(handlerID, modName, modId, requiresMod, excludedModId);
        final String imageResource = tag.hasKey("imageResource") ? tag.getString("imageResource") : null;
        if (imageResource != null && !imageResource.equals("")) {
            info.setImage(
                    imageResource,
                    tag.getInteger("imageX"),
                    tag.getInteger("imageY"),
                    tag.getInteger("imageWidth"),
                    tag.getInteger("imageHeight"));
        }
        if (!info.hasImageOrItem()) {
            final String itemName = tag.getString("itemName");
            if (itemName != null && !itemName.equals("")) {
                info.setItem(itemName, tag.hasKey("nbtInfo") ? tag.getString("nbtInfo") : null);
            }
        }
        final int yShift = tag.hasKey("yShift") ? tag.getInteger("yShift") : 0;
        info.setYShift(yShift);

        try {
            final int imageHeight = tag.hasKey("handlerHeight") ? tag.getInteger("handlerHeight")
                    : HandlerInfo.DEFAULT_HEIGHT;
            final int imageWidth = tag.hasKey("handlerWidth") ? tag.getInteger("handlerWidth")
                    : HandlerInfo.DEFAULT_WIDTH;
            final int maxRecipesPerPage = tag.hasKey("maxRecipesPerPage") ? tag.getInteger("maxRecipesPerPage")
                    : HandlerInfo.DEFAULT_MAX_PER_PAGE;
            info.setHandlerDimensions(imageHeight, imageWidth, maxRecipesPerPage);
        } catch (NumberFormatException ignored) {
            NEIClientConfig.logger.info("Error setting handler dimensions for '{}'", handlerID);
        }

        GuiRecipeTab.handlerAdderFromIMC.remove(handlerID);
        GuiRecipeTab.handlerAdderFromIMC.put(handlerID, info);
    }

    private static void handleRemoveHandlerInfo(IMCMessage message) {
        if (!message.isNBTMessage()) {
            logInvalidMessage(message, "NBT");
            return;
        }
        final NBTTagCompound tag = message.getNBTValue();
        final String handlerID;
        if (tag.hasKey("handler")) {
            NEIClientConfig.logger.warn(
                    "Received tag 'handler' for removeHandlerInfo IMC from {}, this tag has been renamed to 'handlerID'",
                    message.getSender());
            handlerID = tag.getString("handler");
        } else {
            handlerID = tag.getString("handlerID");
        }

        NEIClientConfig.logger
                .info("Processing removeHandlerInfo IMC for '{}' from {}", handlerID, message.getSender());
        GuiRecipeTab.handlerRemoverFromIMC.add(handlerID);
    }

    private static void handleRegisterCatalystInfo(IMCMessage message) {
        if (!message.isNBTMessage()) {
            logInvalidMessage(message, "NBT");
            return;
        }

        if (!processedCatalystSenders.contains(message.getSender())) {
            processedCatalystSenders.add(message.getSender());
        }
        final NBTTagCompound tag = message.getNBTValue();
        final String catalystHandlerID;
        if (tag.hasKey("handlerID")) {
            NEIClientConfig.logger.warn(
                    "Received tag 'handlerID' for registerCatalystInfo IMC from {}, this is deprecated, please use 'catalystHandlerID' instead",
                    message.getSender());
            catalystHandlerID = tag.getString("handlerID");
        } else {
            catalystHandlerID = tag.getString("catalystHandlerID");
        }
        if (catalystHandlerID.equals("")) {
            NEIClientConfig.logger.warn(
                    "registerCatalystInfo IMC from {} is missing required tag 'catalystHandlerID'",
                    message.getSender());
            return;
        }
        NEIClientConfig.logger
                .info("Processing registerCatalystInfo IMC for '{}' from {}", catalystHandlerID, message.getSender());

        final String modId = tag.hasKey("modId") ? tag.getString("modId") : null;
        final boolean requiresMod = tag.getBoolean("modRequired");
        final String excludedModId = tag.hasKey("excludedModId") ? tag.getString("excludedModId") : null;

        if (requiresMod && modId != null && !Loader.isModLoaded(modId)) return;
        if (excludedModId != null && Loader.isModLoaded(excludedModId)) return;

        final String itemName = tag.getString("itemName");
        final String nbtInfo = tag.hasKey("nbtInfo") ? tag.getString("nbtInfo") : null;
        if (itemName.isEmpty()) {
            NEIClientConfig.logger.warn(
                    "registerCatalystInfo IMC for '{}' from {} is missing required tag 'itemName'",
                    catalystHandlerID,
                    message.getSender());
            return;
        }
        final ItemStack itemStack = NEIServerUtils.getModdedItem(itemName, nbtInfo);
        if (itemStack == null) {
            NEIClientConfig.logger.warn("Cannot find item '{}'!", itemName);
            return;
        }
        final int priority = tag.getInteger("priority");

        RecipeCatalysts.addOrPut(
                RecipeCatalysts.catalystsAdderFromIMC,
                catalystHandlerID,
                new CatalystInfo(itemStack, priority));
        NEIClientConfig.logger
                .info("Added catalyst '{}' to catalyst handler {}", itemStack.getDisplayName(), catalystHandlerID);
    }

    private static void handleRemoveCatalystInfo(IMCMessage message) {
        if (!message.isNBTMessage()) {
            logInvalidMessage(message, "NBT");
            return;
        }

        NEIClientConfig.logger.info("Processing removeCatalystInfo IMC from {}", message.getSender());
        final NBTTagCompound tag = message.getNBTValue();
        final String catalystHandlerID;
        if (tag.hasKey("handlerID")) {
            NEIClientConfig.logger.warn(
                    "Received tag 'handlerID' for removeCatalystInfo IMC from {}, this is deprecated, please use 'catalystHandlerID' instead",
                    message.getSender());
            catalystHandlerID = tag.getString("handlerID");
        } else {
            catalystHandlerID = tag.getString("catalystHandlerID");
        }
        if (catalystHandlerID.isEmpty()) {
            NEIClientConfig.logger.warn(
                    "removeCatalystInfo IMC from {} is missing required tag 'catalystHandlerID'",
                    message.getSender());
            return;
        }
        final String itemName = tag.getString("itemName");
        final String nbtInfo = tag.hasKey("nbtInfo") ? tag.getString("nbtInfo") : null;
        if (itemName.isEmpty()) {
            NEIClientConfig.logger.warn(
                    "removeCatalystInfo IMC for '{}' from {} is missing required tag 'itemName'",
                    catalystHandlerID,
                    message.getSender());
            return;
        }
        final ItemStack itemStack = NEIServerUtils.getModdedItem(itemName, nbtInfo);
        if (itemStack == null) {
            NEIClientConfig.logger.warn("Cannot find item '{}'!", itemName);
            return;
        }

        if (RecipeCatalysts.catalystsRemoverFromIMC.containsKey(catalystHandlerID)) {
            RecipeCatalysts.catalystsRemoverFromIMC.get(catalystHandlerID).add(itemStack);
        } else {
            RecipeCatalysts.catalystsRemoverFromIMC
                    .put(catalystHandlerID, new ArrayList<>(Collections.singletonList(itemStack)));
        }
        NEIClientConfig.logger
                .info("Removed catalyst '{}' from catalyst handler {}", itemStack.getDisplayName(), catalystHandlerID);
    }

    private static void logInvalidMessage(FMLInterModComms.IMCMessage message, String type) {
        FMLLog.bigWarning(
                String.format(
                        "Received invalid IMC '%s' from %s. Not a %s Message.",
                        message.key,
                        message.getSender(),
                        type));
    }
}
