package codechicken.nei.item;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;

import org.lwjgl.opengl.GL11;

import codechicken.nei.NEIClientUtils;
import codechicken.nei.item.FluidDrawer.FillDirection;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class FluidDisplayRenderer implements IItemRenderer {

    @Override
    public boolean handleRenderType(ItemStack item, ItemRenderType type) {
        return type == ItemRenderType.INVENTORY;
    }

    @Override
    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
        return false;
    }

    @Override
    public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
        if (type != ItemRenderType.INVENTORY || !(item.getItem() instanceof ItemFluidDisplay fluidDisplay)) {
            return;
        }

        renderIcon(item);
        renderAmountOverlay(item, fluidDisplay);
    }

    private void renderIcon(ItemStack item) {
        final Fluid fluid = FluidRegistry.getFluid(item.getItemDamage());
        FluidDrawer.drawFluidArea(fluid != null ? fluid : FluidRegistry.WATER, 0, 0, 16, 16, false, FillDirection.UP);
    }

    private void renderAmountOverlay(ItemStack item, ItemFluidDisplay fluidDisplay) {
        final long amount = fluidDisplay.getAmountLong(item);
        if (amount <= 0) {
            return;
        }

        final String amountString = NEIClientUtils.formatSlotAmount(amount, true);
        final FontRenderer fontRender = Minecraft.getMinecraft().fontRenderer;
        float smallTextScale = fontRender.getUnicodeFlag() ? 3F / 4F : 1F / 2F;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPushMatrix();
        GL11.glScalef(smallTextScale, smallTextScale, 1.0f);

        fontRender
                .drawString(amountString, 0, (int) (16 / smallTextScale) - fontRender.FONT_HEIGHT + 1, 0xFFFFFF, true);
        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }
}
