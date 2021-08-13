package codechicken.nei.event;

import codechicken.nei.api.IConfigureNEI;
import cpw.mods.fml.common.eventhandler.Event;
import net.minecraftforge.common.MinecraftForge;

/**
 * Event is posted from <font color=red>NEI Plugin Loader Thread</font> after all {@link IConfigureNEI} were loaded.
 * <br>
 * This event does not have a result. {@link HasResult}<br>
 * <br>
 * This event is fired on the {@link MinecraftForge#EVENT_BUS}.
 */
public class NEIConfigsLoadedEvent extends Event {
}
