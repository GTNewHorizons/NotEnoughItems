package codechicken.nei.api;

import java.util.LinkedList;

import net.minecraft.world.World;

import codechicken.nei.NEIClientConfig;
import codechicken.nei.config.GuiOptionList;
import codechicken.nei.config.OptionCycled;

public class NEIInfo {

    public static final LinkedList<INEIModeHandler> modeHandlers = new LinkedList<>();

    public static void load(World world) {
        OptionCycled modeOption = (OptionCycled) NEIClientConfig.getOptionList().getOption("inventory.cheatmode");
        // kept alive so the WeakReference in Option.slotRef isn't GC'd before copyGlobals/cycle use it
        GuiOptionList env = modeOption.parent.synthesizeEnvironment(false);
        if (!modeOption.optionValid(modeOption.value())) {
            modeOption.copyGlobals();
            modeOption.cycle();
        }
    }

    public static boolean isValidMode(int mode) {
        for (INEIModeHandler handler : modeHandlers) if (!handler.isModeValid(mode)) return false;

        return true;
    }
}
