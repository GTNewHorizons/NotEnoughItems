package codechicken.nei.item;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.IIcon;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class FluidDrawer {

    public enum FillDirection {

        AUTO,
        UP,
        DOWN,
        LEFT,
        RIGHT;

        public boolean isHorizontal() {
            return this == LEFT || this == RIGHT;
        }

        public FillDirection resolve(FluidStack fluidStack) {
            if (this != AUTO) return this;
            return fluidStack.getFluid().isGaseous(fluidStack) ? DOWN : UP;
        }
    }

    private FluidDrawer() {}

    public static void drawFluid(int x, int y, int width, int height, int capacity, FluidStack fluidStack,
            boolean flowing, FillDirection direction) {
        final Fluid fluid = fluidStack.getFluid();
        IIcon icon = flowing ? fluid.getFlowingIcon() : null;

        if (icon == null) {
            icon = fluid.getIcon(fluidStack);
        }

        if (icon == null || fluidStack.amount <= 0) {
            return;
        }

        direction = direction.resolve(fluidStack);

        final int tankSize = direction.isHorizontal() ? width : height;
        final int tankCapacity = capacity > 0 ? capacity : fluidStack.amount;
        final int fillSize = Math
                .max(1, (int) ((long) tankSize * Math.min(fluidStack.amount, tankCapacity) / tankCapacity));

        final int fillWidth = direction.isHorizontal() ? Math.min(fillSize, width) : width;
        final int fillHeight = direction.isHorizontal() ? height : Math.min(fillSize, height);
        final int fillX = direction == FillDirection.LEFT ? x + width - fillWidth : x;
        final int fillY = direction == FillDirection.DOWN ? y : y + height - fillHeight;
        final boolean anchorRight = direction == FillDirection.LEFT;
        final boolean anchorBottom = direction != FillDirection.DOWN;

        final int color = fluid.getColor(fluidStack);
        final float red = (color >> 16 & 0xFF) / 255F;
        final float green = (color >> 8 & 0xFF) / 255F;
        final float blue = (color & 0xFF) / 255F;

        Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
        GL11.glColor3f(red, green, blue);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_LIGHTING);

        final Tessellator tessellator = Tessellator.instance;

        tessellator.startDrawingQuads();
        for (int tx = 0; tx < fillWidth; tx += 16) {
            final int tileWidth = Math.min(16, fillWidth - tx);
            // a cropped tile keeps the texture edge that stays seamless against the neighbouring tile
            final double uSize = (icon.getMaxU() - icon.getMinU()) * tileWidth / 16D;
            final double uMin = anchorRight ? icon.getMaxU() - uSize : icon.getMinU();
            final double uMax = anchorRight ? icon.getMaxU() : icon.getMinU() + uSize;
            final int leftX = anchorRight ? fillX + fillWidth - tx - tileWidth : fillX + tx;
            final int rightX = leftX + tileWidth;

            for (int ty = 0; ty < fillHeight; ty += 16) {
                final int tileHeight = Math.min(16, fillHeight - ty);
                final double vSize = (icon.getMaxV() - icon.getMinV()) * tileHeight / 16D;
                final double vMin = anchorBottom ? icon.getMaxV() - vSize : icon.getMinV();
                final double vMax = anchorBottom ? icon.getMaxV() : icon.getMinV() + vSize;
                final int bottomY = anchorBottom ? fillY + fillHeight - ty : fillY + ty + tileHeight;
                final int topY = bottomY - tileHeight;

                tessellator.addVertexWithUV(leftX, bottomY, 0, uMin, vMax);
                tessellator.addVertexWithUV(rightX, bottomY, 0, uMax, vMax);
                tessellator.addVertexWithUV(rightX, topY, 0, uMax, vMin);
                tessellator.addVertexWithUV(leftX, topY, 0, uMin, vMin);
            }
        }
        tessellator.draw();

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }
}
