package codechicken.nei.api;

import codechicken.nei.VisiblityData;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

/**
 * Lets you just override those things you want to
 */
public class INEIGuiAdapter implements INEIGuiHandler {

    static final Pattern SPECIAL_REGEX_CHARS = Pattern.compile("[{}()\\[\\].+*?^$\\\\|]");

    @Override
    public VisiblityData modifyVisiblity(GuiContainer gui, VisiblityData currentVisibility) {
        return currentVisibility;
    }

    @Override
    public Iterable<Integer> getItemSpawnSlots(GuiContainer gui, ItemStack item) {
        return Collections.emptyList();
    }

    @Override
    public List<TaggedInventoryArea> getInventoryAreas(GuiContainer gui) {
        return null;
    }

    @Override
    public boolean handleDragNDrop(GuiContainer gui, int mousex, int mousey, ItemStack draggedStack, int button) {
        return false;
    }

    @Override
    public boolean hideItemPanelSlot(GuiContainer gui, int x, int y, int w, int h) {
        return false;
    }

    protected String formattingText(String displayName) {
        return SPECIAL_REGEX_CHARS
                .matcher(EnumChatFormatting.getTextWithoutFormattingCodes(displayName))
                .replaceAll("\\\\$0");
    }
}
