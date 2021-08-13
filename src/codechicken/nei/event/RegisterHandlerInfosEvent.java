package codechicken.nei.event;

import codechicken.nei.NEIClientConfig;
import codechicken.nei.recipe.GuiRecipeTab;
import codechicken.nei.recipe.HandlerInfo;
import codechicken.nei.recipe.IRecipeHandler;
import cpw.mods.fml.common.eventhandler.Event;
import net.minecraftforge.common.MinecraftForge;

/**
 * Event is posted every time the handler infos got registered or reloaded.
 * During it, you can safely register your custom handler info.
 *
 * <br>
 * This event does not have a result. {@link HasResult}<br>
 * <br>
 * This event is fired on the {@link MinecraftForge#EVENT_BUS}.
 */
public class RegisterHandlerInfosEvent extends Event {
    public void registerHandlerInfo(Class<? extends IRecipeHandler> handlerClazz, HandlerInfo info) {
        if (GuiRecipeTab.handlerMap.put(handlerClazz.getName(), info) != null) {
            NEIClientConfig.logger.info("Replaced handler info for {}", handlerClazz.getName());
        } else {
            NEIClientConfig.logger.info("Added handler info for {}", handlerClazz.getName());
        }
    }
}
