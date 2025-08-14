package codechicken.nei.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import codechicken.core.CommonUtils;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.NEIServerUtils;
import codechicken.nei.recipe.CatalystInfo;
import codechicken.nei.recipe.GuiRecipeTab;
import codechicken.nei.recipe.HandlerInfo;
import codechicken.nei.recipe.InformationHandler;
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
                    case "registerHandlerInfo" -> handleRegisterHandlerInfo(message);
                    case "removeHandlerInfo" -> handleRemoveHandlerInfo(message);
                    case "registerCatalystInfo" -> handleRegisterCatalystInfo(message);
                    case "removeCatalystInfo" -> handleRemoveCatalystInfo(message);
                    case "addItemInfo" -> handleAddItemInfo(message);
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
        final String handler = tag.getString("handler");
        NEIClientConfig.logger.info("Processing registerHandlerInfo `{}` from {}", handler, message.getSender());

        final String modName = tag.getString("modName");
        final String modId = tag.getString("modId");
        final boolean requiresMod = tag.getBoolean("modRequired");
        final String excludedModId = tag.hasKey("excludedModId") ? tag.getString("excludedModId") : null;

        if (handler.isEmpty() || modName.isEmpty() || modId.isEmpty()) {
            NEIClientConfig.logger.warn("Missing relevant information to registerHandlerInfo!");
            return;
        }

        if (requiresMod && !Loader.isModLoaded(modId)) return;
        if (excludedModId != null && Loader.isModLoaded(excludedModId)) return;

        HandlerInfo info = new HandlerInfo(handler, modName, modId, requiresMod, excludedModId);
        final String imageResource = tag.hasKey("imageResource") ? tag.getString("imageResource") : null;
        if (imageResource != null && !imageResource.isEmpty()) {
            info.setImage(
                    imageResource,
                    tag.getInteger("imageX"),
                    tag.getInteger("imageY"),
                    tag.getInteger("imageWidth"),
                    tag.getInteger("imageHeight"));
        }
        if (!info.hasImageOrItem()) {
            final String itemName = tag.getString("itemName");
            if (itemName != null && !itemName.isEmpty()) {
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
            NEIClientConfig.logger.info("Error setting handler dimensions for {}", handler);
        }

        // true if not set to false
        info.setShowFavoritesButton(!tag.hasKey("showFavoritesButton") || tag.getBoolean("showFavoritesButton"));
        info.setShowOverlayButton(!tag.hasKey("showOverlayButton") || tag.getBoolean("showOverlayButton"));

        GuiRecipeTab.handlerAdderFromIMC.remove(handler);
        GuiRecipeTab.handlerAdderFromIMC.put(handler, info);
    }

    private static void handleRemoveHandlerInfo(IMCMessage message) {
        if (!message.isNBTMessage()) {
            logInvalidMessage(message, "NBT");
            return;
        }
        final NBTTagCompound tag = message.getNBTValue();
        final String handler = tag.getString("handler");
        NEIClientConfig.logger.info("Processing removeHandlerInfo `{}` from {}", handler, message.getSender());

        GuiRecipeTab.handlerRemoverFromIMC.add(handler);
    }

    private static void handleRegisterCatalystInfo(IMCMessage message) {
        if (!message.isNBTMessage()) {
            logInvalidMessage(message, "NBT");
            return;
        }

        if (!processedCatalystSenders.contains(message.getSender())) {
            NEIClientConfig.logger.info("Processing registerCatalystInfo from {}", message.getSender());
            processedCatalystSenders.add(message.getSender());
        }
        final NBTTagCompound tag = message.getNBTValue();
        final String handlerID = tag.getString("handlerID");
        if (handlerID.isEmpty()) {
            NEIClientConfig.logger.warn("Missing handlerID for registerCatalystInfo!");
            return;
        }
        final ItemStack itemStack = getItemStackFromIMC(message, "registerCatalystInfo");
        if (itemStack == null) {
            return;
        }
        final int priority = tag.getInteger("priority");
        RecipeCatalysts.addOrPut(RecipeCatalysts.catalystsAdderFromIMC, handlerID, new CatalystInfo(itemStack, priority));
        NEIClientConfig.logger.info("Added catalyst `{}` to handler {}", itemStack.getDisplayName(), handlerID);
    }

    private static void handleRemoveCatalystInfo(IMCMessage message) {
        if (!message.isNBTMessage()) {
            logInvalidMessage(message, "NBT");
            return;
        }

        final NBTTagCompound tag = message.getNBTValue();
        final String handlerID = tag.getString("handlerID");
        if (handlerID.isEmpty()) {
            NEIClientConfig.logger.warn("Missing handlerID for removeCatalystInfo!");
            return;
        }
        final ItemStack itemStack = getItemStackFromIMC(message, "removeCatalystInfo");
        if (itemStack == null) {
            return;
        }

        RecipeCatalysts.catalystsRemoverFromIMC
                .computeIfAbsent(handlerID, k -> new ArrayList<>())
                .add(itemStack);
        NEIClientConfig.logger.info("Removed catalyst `{}` from handler {}", itemStack.getDisplayName(), handlerID);
    }

    private static void handleAddItemInfo(IMCMessage message) {
        if (!message.isNBTMessage()) {
            logInvalidMessage(message, "NBT");
            return;
        }

        final NBTTagCompound tag = message.getNBTValue();
        if (tag.hasKey("pages")) addMultipleInfoPages(message);
        if (tag.hasKey("page")) addSingleInfoPage(message);
    }

    private static void addMultipleInfoPages(IMCMessage message) {
        ItemStack itemStack = getItemStackFromIMC(message, "addItemInfo (multiple pages)");
        if (itemStack == null) {
            return;
        }

        final NBTTagCompound tag = message.getNBTValue();
        final NBTTagList pages = tag.getTagList("pages", 8); // 8 = TAG_String
        for (int i = 0; i < pages.tagCount(); i++) {
            String page = pages.getStringTagAt(i);
            InformationHandler.addInformationPage(itemStack, page);
        }
    }

    private static void addSingleInfoPage(IMCMessage message) {
        ItemStack itemStack = getItemStackFromIMC(message, "addItemInfo (single page)");
        if (itemStack == null) {
            return;
        }

        final NBTTagCompound tag = message.getNBTValue();
        String page = tag.getString("page");
        InformationHandler.addInformationPage(itemStack, page);
    }

    private static ItemStack getItemStackFromIMC(IMCMessage message, String logAction) {
        final NBTTagCompound tag = message.getNBTValue();
        final String itemName = tag.getString("itemName");
        final String nbtInfo = tag.hasKey("nbtInfo") ? tag.getString("nbtInfo") : null;

        NEIClientConfig.logger.info("Processing {} for item `{}` from {}", logAction, itemName, message.getSender());
        final ItemStack itemStack = NEIServerUtils.getModdedItem(itemName, nbtInfo);

        if (itemStack == null) {
            NEIClientConfig.logger.warn("Cannot find item `{}`!", itemName);
        }

        return itemStack;
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
