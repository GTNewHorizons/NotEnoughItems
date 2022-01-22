package codechicken.nei.recipe;

import codechicken.nei.api.INEIGuiAdapter;
import codechicken.nei.drawable.GuiElementDuex;
import codechicken.nei.drawable.GuiElementScalable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.util.Rectangle;

public class GuiRecipeCatalyst extends INEIGuiAdapter {
    private GuiRecipe guiRecipe;
    public static final int ingredientSize = 16;
    public static final int ingredientBorder = 1;
    public static final int tabBorder = 5;
    public static final int fullBorder = ingredientBorder + tabBorder;

    private static final Rectangle catalystRect = new Rectangle();
    private static final Rectangle targetRect = new Rectangle();

    public GuiRecipeCatalyst(GuiRecipe guiRecipe) {
        this.guiRecipe = guiRecipe;
    }

    public void draw() {
        if (guiRecipe == null) return;
        int catalystsSize = RecipeCatalysts.getRecipeCatalysts(guiRecipe.getHandler()).size();
        if (catalystsSize == 0) return;

        int availableHeight = RecipeCatalysts.getHeight();
        int columnCount = RecipeCatalysts.getColumnCount(availableHeight, catalystsSize);
        int rowCount = RecipeCatalysts.getRowCount(availableHeight, catalystsSize);
        int width, height, xPos, yPos;

        width = (ingredientBorder * 2) + (tabBorder * 2) + (columnCount * ingredientSize);
        height = (ingredientBorder * 2) + (tabBorder * 2) + (rowCount * ingredientSize);
        xPos = guiRecipe.guiLeft - width + tabBorder + 1;
        yPos = guiRecipe.guiTop;
        drawBordered("nei:textures/catalyst_tab.png", xPos, yPos, width, height, 28, 28, 6, 6, 6, 6);

        width = (ingredientBorder * 2) + (columnCount * ingredientSize);
        height = (ingredientBorder * 2) + (rowCount * ingredientSize);
        xPos = guiRecipe.guiLeft - width + ingredientBorder + 1;
        yPos = guiRecipe.guiTop + fullBorder - ingredientBorder;
        drawBordered("nei:textures/slot.png", xPos, yPos, width, height, 18, 18, 1, 1, 1, 1);
    }

    @Override
    public boolean hideItemPanelSlot(GuiContainer gui, int x, int y, int w, int h) {
        if (!(gui instanceof GuiRecipe)) return false;
        guiRecipe = (GuiRecipe) gui;
        int catalystsSize = RecipeCatalysts.getRecipeCatalysts(guiRecipe.getHandler()).size();
        if (catalystsSize == 0) return false;

        int availableHeight = RecipeCatalysts.getHeight();
        int columnCount = RecipeCatalysts.getColumnCount(availableHeight, catalystsSize);
        int rowCount = RecipeCatalysts.getRowCount(availableHeight, catalystsSize);
        int margin = 4;

        int width = (ingredientBorder * 2) + (tabBorder * 2) + (columnCount * ingredientSize) + margin;
        int height = (ingredientBorder * 2) + (tabBorder * 2) + (rowCount * ingredientSize) + margin;
        int xOffset = guiRecipe.guiLeft - width + tabBorder;
        int yOffset = guiRecipe.guiTop;
        catalystRect.setBounds(xOffset, yOffset, width, height);
        targetRect.setBounds(x, y, w, h);
        return targetRect.intersects(catalystRect);
    }

    private void drawBordered(String location, int xPos, int yPos, int width, int height, int texWidth, int texHeight, int sliceLeft, int sliceRight, int sliceTop, int sliceBottom) {
        Minecraft.getMinecraft().renderEngine.bindTexture(new ResourceLocation(location));

        GuiElementDuex cornerTopLeft = new GuiElementDuex(0, 0, sliceLeft, sliceTop, texWidth, texHeight);
        GuiElementDuex cornerTopRight = new GuiElementDuex(texWidth - sliceRight, 0, sliceRight, sliceTop, texWidth, texHeight);
        GuiElementDuex cornerBottomLeft = new GuiElementDuex(0, texHeight - sliceBottom, sliceLeft, sliceBottom, texWidth, texHeight);
        GuiElementDuex cornerBottomRight = new GuiElementDuex(texWidth - sliceRight, texHeight - sliceBottom, sliceRight, sliceBottom, texWidth, texHeight);

        GuiElementScalable borderTop = new GuiElementScalable(sliceLeft, 0, texWidth - sliceLeft - sliceRight, sliceTop, texWidth, texHeight);
        GuiElementScalable borderBottom = new GuiElementScalable(sliceLeft, texHeight - sliceBottom, texWidth - sliceLeft - sliceRight, sliceBottom, texWidth, texHeight);
        GuiElementScalable borderLeft = new GuiElementScalable(0, sliceTop, sliceLeft, texHeight - sliceTop - sliceBottom, texWidth, texHeight);
        GuiElementScalable borderRight = new GuiElementScalable(texWidth - sliceRight, sliceTop, sliceRight, texHeight - sliceTop - sliceBottom, texWidth, texHeight);
        GuiElementScalable center = new GuiElementScalable(sliceLeft, sliceTop, texWidth - sliceLeft - sliceRight, texHeight - sliceTop - sliceBottom, texWidth, texHeight);

        int midW = width - borderLeft.w - borderRight.w;
        int midH = height - borderTop.h - borderBottom.h;

        // top row
        int x = xPos;
        int y = yPos;
        x += cornerTopLeft.draw(x, y);
        x += borderTop.drawScaledX(x, y, midW);
        cornerTopRight.draw(x, y);

        // center row
        x = xPos;
        y += borderTop.h;
        x += borderLeft.drawScaledY(x, y, midH);
        x += center.drawScaled(x, y, midW, midH);
        borderRight.drawScaledY(x, y, midH);

        // bottom row
        x = xPos;
        y += midH;
        x += cornerBottomLeft.draw(x, y);
        x += borderBottom.drawScaledX(x, y, midW);
        cornerBottomRight.draw(x, y);
    }
}
