package codechicken.nei.event;

import java.util.function.Consumer;

import net.minecraftforge.common.MinecraftForge;

import codechicken.nei.NEIClientConfig;
import codechicken.nei.recipe.GuiRecipeTab;
import codechicken.nei.recipe.HandlerInfo;
import codechicken.nei.recipe.IRecipeHandler;
import cpw.mods.fml.common.eventhandler.Event;

/**
 * Event is posted every time the handler infos get registered or reloaded. During it, you can safely register your
 * custom handler info.
 *
 * <br>
 * This event does not have a result. {@link HasResult}<br>
 * <br>
 * This event is fired on the {@link MinecraftForge#EVENT_BUS}.
 */
public class NEIRegisterHandlerInfosEvent extends Event {

    /**
     * @param handlerID handler identifier as returned by {@link IRecipeHandler#getHandlerId}
     * @param modName   display name of the mod to which the recipe handler belongs
     * @param modId     modId of the mod for which the recipe handler is responsible
     * @param builder
     */
    public void registerHandlerInfo(String handlerID, String modName, String modId,
            Consumer<HandlerInfo.Builder> builder) {
        HandlerInfo.Builder b = new HandlerInfo.Builder(handlerID, modName, modId);
        builder.accept(b);
        HandlerInfo info = b.build();
        if (GuiRecipeTab.handlerMap.put(info.getHandlerName(), info) != null) {
            NEIClientConfig.logger.info("Replaced handler info for {}", info.getHandlerName());
        } else {
            NEIClientConfig.logger.info("Added handler info for {}", info.getHandlerName());
        }
    }

    public void registerHandlerInfo(Class<? extends IRecipeHandler> handlerClazz, String modName, String modId,
            Consumer<HandlerInfo.Builder> builder) {
        registerHandlerInfo(handlerClazz.getName(), modName, modId, builder);
    }
}
