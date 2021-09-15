package codechicken.nei.config;

import codechicken.core.CommonUtils;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.recipe.GuiRecipeTab;
import codechicken.nei.recipe.HandlerInfo;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.event.FMLInterModComms;
import cpw.mods.fml.common.event.FMLInterModComms.IMCMessage;
import net.minecraft.nbt.NBTTagCompound;

import java.util.List;

public class IMCHandler {
    private IMCHandler() {}
    
    public static void processIMC(List<FMLInterModComms.IMCMessage> messages) {
        for (FMLInterModComms.IMCMessage message : messages) {
            String type = message.key;
            if (type == null || type.isEmpty()) continue;
            if (CommonUtils.isClient()) {
                if (type.equals("registerHandlerInfo")) {
                    handleRegisterHandlerInfo(message);
                } else if (type.equals("removeHandlerInfo")) {
                    handleRemoveHandlerInfo(message);
                }
            }

        }
    }

    private static void handleRegisterHandlerInfo(IMCMessage message) {
        if (!message.isNBTMessage())  {
            logInvalidMessage(message, "NBT");
            return;
        }
        final NBTTagCompound tag = message.getNBTValue();
        final String handler = tag.getString("handler");
        NEIClientConfig.logger.info("Processing registerHandlerInfo `" + handler + "` from " + message.getSender());

        final String modName = tag.getString("modName");
        final String modId = tag.getString("modId");
        final boolean requiresMod = tag.getBoolean("modRequired");
        final String excludedModId = tag.hasKey("excludedModId") ? tag.getString("excludedModId") : null;

        if(handler.equals("") || modName.equals("") || modId.equals("")) {
            NEIClientConfig.logger.warn("Missing relevant information to registerHandlerInfo!");
            return;
        }

        if (requiresMod && !Loader.isModLoaded(modId)) return;
        if (excludedModId != null && Loader.isModLoaded(excludedModId)) return;

        HandlerInfo info = new HandlerInfo(handler, modName, modId, requiresMod, excludedModId);
        final String imageResource = tag.hasKey("imageResource") ? tag.getString("imageResource") : null;
        if(imageResource != null && !imageResource.equals("")) {
            info.setImage(imageResource, tag.getInteger("imageX"), tag.getInteger("imageY"), tag.getInteger("imageWidth"), tag.getInteger("imageHeight"));
        }
        if(!info.hasImageOrItem()) {
            final String itemName = tag.getString("itemName");
            if (itemName != null && !itemName.equals("")) {
                info.setItem(itemName, tag.hasKey("nbtInfo") ? tag.getString("nbtInfo") : null);
            }
        }
        final int yShift = tag.hasKey("yShift") ? tag.getInteger("yShift") : 0;
        info.setYShift(yShift);

        try {
            final int imageHeight = tag.hasKey("handlerHeight") ? tag.getInteger("handlerHeight") : HandlerInfo.DEFAULT_HEIGHT;
            final int imageWidth = tag.hasKey("handlerWidth") ? tag.getInteger("handlerWidth") : HandlerInfo.DEFAULT_WIDTH;
            final int maxRecipesPerPage = tag.hasKey("maxRecipesPerPage") ? tag.getInteger("maxRecipesPerPage") : HandlerInfo.DEFAULT_MAX_PER_PAGE;
            info.setHandlerDimensions(imageHeight, imageWidth, maxRecipesPerPage);
        } catch (NumberFormatException ignored) {
            NEIClientConfig.logger.info("Error setting handler dimensions for " + handler);
        }

        GuiRecipeTab.handlerMap.remove(handler);
        GuiRecipeTab.handlerMap.put(handler, info);
    }

    private static void handleRemoveHandlerInfo(IMCMessage message) {
        if (!message.isNBTMessage())  {
            logInvalidMessage(message, "NBT");
            return;
        }
        final NBTTagCompound tag = message.getNBTValue();
        final String handler = tag.getString("handler");
        NEIClientConfig.logger.info("Processing removeHandlerInfo `" + handler + "` from " + message.getSender());

        GuiRecipeTab.handlerMap.remove(handler);
    }


    private static void logInvalidMessage(FMLInterModComms.IMCMessage message, String type) {
        FMLLog.bigWarning(String.format("Received invalid IMC '%s' from %s. Not a %s Message.", message.key, message.getSender(), type));
    }
}
