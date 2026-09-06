package codechicken.nei.item;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.fluids.FluidStack;

import org.lwjgl.opengl.GL11;

import codechicken.nei.util.ReadableNumberConverter;

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

        renderIcon(item, fluidDisplay);
        renderAmountOverlay(item, fluidDisplay);
    }

    private void renderIcon(ItemStack item, ItemFluidDisplay fluidDisplay) {
        final IIcon icon = fluidDisplay.getIconIndex(item);
        if (icon == null) {
            return;
        }

        final int color = fluidDisplay.getColorFromItemStack(item, 0);
        final float red = (color >> 16 & 0xFF) / 255F;
        final float green = (color >> 8 & 0xFF) / 255F;
        final float blue = (color & 0xFF) / 255F;

        Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
        GL11.glColor4f(red, green, blue, 1F);

        final Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(0, 16, 0, icon.getMinU(), icon.getMaxV());
        tessellator.addVertexWithUV(16, 16, 0, icon.getMaxU(), icon.getMaxV());
        tessellator.addVertexWithUV(16, 0, 0, icon.getMaxU(), icon.getMinV());
        tessellator.addVertexWithUV(0, 0, 0, icon.getMinU(), icon.getMinV());
        tessellator.draw();

        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    private void renderAmountOverlay(ItemStack item, ItemFluidDisplay fluidDisplay) {
        final FluidStack fluidStack = fluidDisplay.getFluid(item);
        if (fluidStack == null || fluidStack.amount <= 0) {
            return;
        }

        String amountString = "";

        if (fluidStack.amount < 10_000) {
            amountString = String.valueOf(fluidStack.amount) + "L";
        } else {
            amountString = ReadableNumberConverter.INSTANCE.toWideReadableForm(fluidStack.amount) + "L";
        }

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
