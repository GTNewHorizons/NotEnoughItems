package codechicken.nei.item;

import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.IIcon;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.lwjgl.opengl.GL11;

import codechicken.nei.api.IFluidRenderer;
import codechicken.nei.scroll.GuiHelper;
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

        public FillDirection resolve(Fluid fluid) {
            if (this != AUTO) return this;
            return fluid.isGaseous() ? DOWN : UP;
        }
    }

    private static final Map<Fluid, IFluidRenderer> fluidRenderers = new IdentityHashMap<>();

    private FluidDrawer() {}

    public static void registerFluidRenderer(Fluid fluid, IFluidRenderer renderer) {
        if (renderer == null) {
            fluidRenderers.remove(fluid);
        } else {
            fluidRenderers.put(fluid, renderer);
        }
    }

    public static void drawTank(int x, int y, int width, int height, int capacity, FluidStack fluidStack,
            boolean flowing, FillDirection direction) {
        if (fluidStack.amount <= 0) {
            return;
        }

        final Fluid fluid = fluidStack.getFluid();
        direction = direction.resolve(fluid);

        final int tankSize = direction.isHorizontal() ? width : height;
        final int tankCapacity = capacity > 0 ? capacity : fluidStack.amount;
        final int fillSize = Math
                .max(1, (int) ((long) tankSize * Math.min(fluidStack.amount, tankCapacity) / tankCapacity));

        final int fillWidth = direction.isHorizontal() ? Math.min(fillSize, width) : width;
        final int fillHeight = direction.isHorizontal() ? height : Math.min(fillSize, height);
        final int fillX = direction == FillDirection.LEFT ? x + width - fillWidth : x;
        final int fillY = direction == FillDirection.DOWN ? y : y + height - fillHeight;

        GL11.glDisable(GL11.GL_LIGHTING);
        drawFluidArea(fluid, fillX, fillY, fillWidth, fillHeight, flowing, direction);
        GL11.glEnable(GL11.GL_LIGHTING);
    }

    /**
     * Fills the area with fluid tiles. The tiles start from the side the fluid fills from, so a partial tile ends up at
     * the fluid surface.
     */
    public static void drawFluidArea(Fluid fluid, int x, int y, int width, int height, boolean flowing,
            FillDirection direction) {
        direction = direction.resolve(fluid);
        final boolean anchorRight = direction == FillDirection.LEFT;
        final boolean anchorBottom = direction != FillDirection.DOWN;
        final IFluidRenderer renderer = fluidRenderers.get(fluid);

        if (renderer == null) {
            drawDefaultFluid(fluid, x, y, width, height, flowing, anchorRight, anchorBottom);
        } else {
            drawCustomFluid(renderer, fluid, x, y, width, height, anchorRight, anchorBottom);
        }
    }

    private static void drawCustomFluid(IFluidRenderer renderer, Fluid fluid, int x, int y, int width, int height,
            boolean anchorRight, boolean anchorBottom) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_LIGHTING_BIT);
        GL11.glDisable(GL11.GL_LIGHTING);

        final boolean clip = width % 16 != 0 || height % 16 != 0;

        if (clip) {
            GuiHelper.pushScissorFrame(x, y, width, height);
        }

        try {
            for (int tx = 0; tx < width; tx += 16) {
                final int tileX = anchorRight ? x + width - tx - 16 : x + tx;

                for (int ty = 0; ty < height; ty += 16) {
                    final int tileY = anchorBottom ? y + height - ty - 16 : y + ty;
                    renderer.renderFluid(fluid, tileX, tileY);
                }
            }
        } finally {
            if (clip) {
                GuiHelper.popScissorFrame();
            }

            GL11.glPopAttrib();
            GL11.glColor4f(1F, 1F, 1F, 1F);
        }
    }

    private static void drawDefaultFluid(Fluid fluid, int x, int y, int width, int height, boolean flowing,
            boolean anchorRight, boolean anchorBottom) {
        IIcon icon = flowing ? fluid.getFlowingIcon() : null;

        if (icon == null) {
            icon = fluid.getStillIcon();
        }

        if (icon == null) {
            icon = FluidRegistry.WATER.getStillIcon();
        }

        if (icon == null) {
            return;
        }

        final int color = fluid.getColor();
        final float red = (color >> 16 & 0xFF) / 255F;
        final float green = (color >> 8 & 0xFF) / 255F;
        final float blue = (color & 0xFF) / 255F;

        Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
        GL11.glColor4f(red, green, blue, 1F);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper
                .glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);

        final double iconMinU = icon.getMinU();
        final double iconMaxU = icon.getMaxU();
        final double iconMinV = icon.getMinV();
        final double iconMaxV = icon.getMaxV();
        final Tessellator tessellator = Tessellator.instance;

        tessellator.startDrawingQuads();
        for (int tx = 0; tx < width; tx += 16) {
            final int tileWidth = Math.min(16, width - tx);
            // a cropped tile keeps the texture edge that stays seamless against the neighbouring tile
            final double uSize = (iconMaxU - iconMinU) * tileWidth / 16D;
            final double uMin = anchorRight ? iconMaxU - uSize : iconMinU;
            final double uMax = anchorRight ? iconMaxU : iconMinU + uSize;
            final int leftX = anchorRight ? x + width - tx - tileWidth : x + tx;
            final int rightX = leftX + tileWidth;

            for (int ty = 0; ty < height; ty += 16) {
                final int tileHeight = Math.min(16, height - ty);
                final double vSize = (iconMaxV - iconMinV) * tileHeight / 16D;
                final double vMin = anchorBottom ? iconMaxV - vSize : iconMinV;
                final double vMax = anchorBottom ? iconMaxV : iconMinV + vSize;
                final int bottomY = anchorBottom ? y + height - ty : y + ty + tileHeight;
                final int topY = bottomY - tileHeight;

                tessellator.addVertexWithUV(leftX, bottomY, 0, uMin, vMax);
                tessellator.addVertexWithUV(rightX, bottomY, 0, uMax, vMax);
                tessellator.addVertexWithUV(rightX, topY, 0, uMax, vMin);
                tessellator.addVertexWithUV(leftX, topY, 0, uMin, vMin);
            }
        }
        tessellator.draw();

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }
}
