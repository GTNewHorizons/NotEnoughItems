package codechicken.nei;

import java.util.Collections;
import java.util.List;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import org.lwjgl.opengl.GL11;

import codechicken.lib.render.CCRenderState;
import codechicken.lib.vec.Rectangle4i;
import codechicken.nei.AutoFocusWidget.INEIAutoFocusSearchEnable;
import codechicken.nei.api.INEIGuiHandler;
import codechicken.nei.api.TaggedInventoryArea;

public class GuiExtendedCreativeInv extends GuiContainer implements INEIGuiHandler, INEIAutoFocusSearchEnable {

    public GuiExtendedCreativeInv(Container par1Container) {
        super(par1Container);
        ySize = 198;
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float var1, int var2, int var3) {
        GL11.glColor4f(1, 1, 1, 1);
        CCRenderState.changeTexture("nei:textures/gui/inv.png");

        int x = guiLeft;
        int y = guiTop - 4;

        drawTexturedModalRect(x - 23, y, 0, 0, 199, 204);
    }

    @Override
    public VisiblityData modifyVisiblity(GuiContainer gui, VisiblityData currentVisibility) {
        return currentVisibility;
    }

    @Override
    public Iterable<Integer> getItemSpawnSlots(GuiContainer gui, ItemStack item) {
        return NEIServerUtils.getRange(0, 54);
    }

    @Override
    public List<TaggedInventoryArea> getInventoryAreas(GuiContainer gui) {
        return Collections.singletonList(new TaggedInventoryArea("ExtendedCreativeInv", 0, 54, inventorySlots));
    }

    @Override
    public boolean handleDragNDrop(GuiContainer gui, int mousex, int mousey, ItemStack draggedStack, int button) {
        return false;
    }

    @Override
    public boolean hideItemPanelSlot(GuiContainer gui, int x, int y, int w, int h) {
        return (new Rectangle4i(guiLeft - 15, guiTop + 18, 18, 18 * 4 + 4)).intersects(new Rectangle4i(x, y, w, h));
    }

    @Override
    protected void handleMouseClick(Slot p_146984_1_, int p_146984_2_, int p_146984_3_, int p_146984_4_) {

        // Hack for armor slots, because they are outside the container
        if (p_146984_1_ != null && p_146984_4_ == 4 && p_146984_1_.xDisplayPosition < 0) {
            p_146984_4_ = 0;
        }

        super.handleMouseClick(p_146984_1_, p_146984_2_, p_146984_3_, p_146984_4_);
    }
}
