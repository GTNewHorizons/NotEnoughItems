package codechicken.nei.util;

import static com.gtnewhorizon.gtnhlib.client.event.RenderTooltipEvent.*;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;

import com.gtnewhorizon.gtnhlib.client.event.RenderTooltipEvent;

public class RenderTooltipEventHelper {

    private static RenderTooltipEvent event;

    /**
     * @return {@code true} if the event has been canceled
     */
    public static boolean post(ItemStack stack, GuiScreen gui, int x, int y, FontRenderer font) {
        event = new RenderTooltipEvent(
                stack,
                gui,
                ORIGINAL_BG_START,
                ORIGINAL_BG_END,
                ORIGINAL_BORDER_START,
                ORIGINAL_BORDER_END,
                x,
                y,
                font);
        if (stack != null) {
            MinecraftForge.EVENT_BUS.post(event);
            if (event.isCanceled()) {
                flush();
                return true;
            }
        }
        return false;
    }

    public static void flush() {
        event = null;
    }

    public static int getBackgroundStart() {
        return event == null ? ORIGINAL_BG_START : event.backgroundStart;
    }

    public static int getBackgroundEnd() {
        return event == null ? ORIGINAL_BG_END : event.backgroundEnd;
    }

    public static int getBorderStart() {
        return event == null ? ORIGINAL_BORDER_START : event.borderStart;
    }

    public static int getBorderEnd() {
        return event == null ? ORIGINAL_BORDER_END : event.borderEnd;
    }

    public static int getX() {
        return event.x;
    }

    public static int getY() {
        return event.y;
    }

    public static FontRenderer getFont() {
        return event.font;
    }

    public static Consumer<List<String>> getAlternativeRenderer() {
        return event.alternativeRenderer;
    }
}
