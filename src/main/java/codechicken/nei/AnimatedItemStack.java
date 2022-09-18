package codechicken.nei;

import static codechicken.nei.ItemsGrid.SLOT_SIZE;

import codechicken.nei.guihook.GuiContainerManager;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;

public class AnimatedItemStack {

    private static final int steep = 10;
    private static final float scaleSpeed = 0.1f;

    private int lastPosX = -1;
    private int lastPosY = -1;
    private float currentScale = 0;
    private final boolean doMoveAnim;
    private final boolean doPopAnim;

    public ItemStack itemStack;

    public AnimatedItemStack(ItemStack itemStack, boolean doMoveAnim, boolean doPopAnim) {
        this.itemStack = itemStack;
        this.doMoveAnim = doMoveAnim;
        this.doPopAnim = doPopAnim;
    }

    public void drawItem(int x, int y) {
        drawItem(x, y, false, null, false);
    }

    public void drawItem(int x, int y, boolean smallAmount, String stackSize, boolean blockMoveAnimOnce) {
        if (NEIClientConfig.areBookmarksAnimated()) {
            if (doPopAnim && currentScale < 1) {
                drawPoppingItem(x, y, smallAmount, stackSize);
                return;
            }
            if (doMoveAnim && (lastPosX != x || lastPosY != y) && !blockMoveAnimOnce) {
                drawMovingItem(x, y, smallAmount, stackSize);
                return;
            }
        }
        currentScale = 1;
        lastPosX = x;
        lastPosY = y;
        GuiContainerManager.drawItem(x, y, itemStack, smallAmount, stackSize);
    }

    private void drawPoppingItem(int x, int y, boolean smallAmount, String stackSize) {
        currentScale += scaleSpeed;
        GL11.glScalef(currentScale, currentScale, currentScale); // push & pop matrix crashes the game
        GuiContainerManager.drawItem(
                (int) ((x + (SLOT_SIZE / 2 - currentScale * SLOT_SIZE / 2)) / currentScale),
                (int) ((y + (SLOT_SIZE / 2 - currentScale * SLOT_SIZE / 2)) / currentScale),
                itemStack,
                smallAmount,
                stackSize);
        GL11.glScalef(1 / currentScale, 1 / currentScale, 1 / currentScale);
    }

    private void drawMovingItem(int x, int y, boolean smallAmount, String stackSize) {
        if (lastPosX != -1 && lastPosY != -1) {
            int xDiff = x - lastPosX;
            int yDiff = y - lastPosY;
            int moveX = xDiff / steep + ((xDiff % steep == 0) ? 0 : (xDiff > 0 ? 1 : -1));
            int moveY = yDiff / steep + ((yDiff % steep == 0) ? 0 : (yDiff > 0 ? 1 : -1));
            GuiContainerManager.drawItem(lastPosX + moveX, lastPosY + moveY, itemStack, smallAmount, stackSize);
            lastPosX = lastPosX + moveX;
            lastPosY = lastPosY + moveY;
        } else {
            lastPosX = x;
            lastPosY = y;
            GuiContainerManager.drawItem(x, y, itemStack, smallAmount, stackSize);
        }
    }
}
