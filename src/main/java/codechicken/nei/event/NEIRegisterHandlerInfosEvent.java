package codechicken.nei.event;

import java.util.function.Consumer;

import net.minecraftforge.common.MinecraftForge;

import codechicken.nei.NEIClientConfig;
import codechicken.nei.recipe.GuiRecipeTab;
import codechicken.nei.recipe.HandlerInfo;
import codechicken.nei.recipe.IRecipeHandler;
import cpw.mods.fml.common.eventhandler.Event;

/**
 * Event is posted every time the handler infos got registered or reloaded. During it, you can safely register your
 * custom handler info. <br>
 * This event does not have {@link HasResult result}.<br>
 * <br>
 * This event is fired on the {@link MinecraftForge#EVENT_BUS}.
 */
@SuppressWarnings("unused")
public class NEIRegisterHandlerInfosEvent extends Event {

    public void registerHandlerInfo(HandlerInfo info) {
        if (GuiRecipeTab.handlerMap.put(info.getHandlerName(), info) != null) {
            NEIClientConfig.logger.info("Replaced handler info for {}", info.getHandlerName());
        } else {
            NEIClientConfig.logger.info("Added handler info for {}", info.getHandlerName());
        }
    }

    public void registerHandlerInfo(String handlerName, String modName, String modId,
            Consumer<HandlerInfo.Builder> builder) {
        HandlerInfo.Builder b = new HandlerInfo.Builder(handlerName, modName, modId);
        builder.accept(b);
        HandlerInfo info = b.build();
        registerHandlerInfo(info);
    }

    public void registerHandlerInfo(Class<? extends IRecipeHandler> handlerClazz, String modName, String modId,
            Consumer<HandlerInfo.Builder> builder) {
        registerHandlerInfo(handlerClazz.getName(), modName, modId, builder);
    }
}
