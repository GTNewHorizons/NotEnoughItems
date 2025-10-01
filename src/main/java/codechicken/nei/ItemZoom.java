package codechicken.nei;

import java.awt.Point;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.guihook.IContainerInputHandler;
import codechicken.nei.recipe.StackInfo;

public class ItemZoom extends Widget implements IContainerInputHandler {

    public static final int MIN_ZOOM = 0;
    public static final int MAX_ZOOM = 1000;

    private static final int MIN_SCALE = 2;
    private static final int SLOT_SIZE = 16;

    private Boolean previousKeyboardRepeatEnabled = null;

    private ItemStack stack = null;
    private String displayName = "";
    private int availableAreaWidth = 0;
    private float xPosition = 0;
    private float yPosition = 0;
    private float scale = 1;

    public ItemZoom() {
        GuiContainerManager.addInputHandler(this);
    }

    @Override
    public void draw(int mx, int my) {

        if (this.stack != null && this.availableAreaWidth >= SLOT_SIZE * 2
                && (NEIClientConfig.getBooleanSetting("inventory.itemzoom.enabled")
                        || NEIClientConfig.isKeyHashDown("gui.itemzoom_hold"))
                && GuiContainerManager.shouldShowTooltip(NEIClientUtils.getGuiContainer())) {
            final float inverseScaleFactor = 1.0f / this.scale;
            final float screenScale = getScreenScale();
            final float size = SLOT_SIZE * this.scale;
            int shiftText = 10;

            GL11.glPushMatrix();
            GL11.glScaled(screenScale, screenScale, 1);
            GL11.glScaled(this.scale, this.scale, 2);
            GL11.glTranslated(this.xPosition * inverseScaleFactor, this.yPosition * inverseScaleFactor, 0);

            GuiContainerManager.drawItem(0, 0, this.stack, false, "");

            GL11.glPopMatrix();

            GL11.glPushAttrib(GL11.GL_ENABLE_BIT);
            GuiContainerManager.enable2DRender();

            if (NEIClientConfig.getBooleanSetting("inventory.itemzoom.showName")) {
                String dispalyName = NEIClientUtils
                        .cropText(GuiDraw.fontRenderer, this.displayName, this.availableAreaWidth);
                GuiDraw.drawStringC(
                        dispalyName,
                        (int) ((this.xPosition + size / 2) * screenScale),
                        (int) ((this.yPosition + size) * screenScale) + shiftText,
                        NEIClientConfig.getSetting("inventory.itemzoom.nameColor").getHexValue());
                shiftText += GuiDraw.fontRenderer.FONT_HEIGHT;
            }

            if (NEIClientConfig.getBooleanSetting("inventory.itemzoom.enabled")
                    && NEIClientConfig.getBooleanSetting("inventory.itemzoom.helpText")) {
                String keyName = NEIClientConfig.getKeyName("gui.itemzoom_toggle");

                if (keyName != null) {
                    String helpText = NEIClientUtils.translate("itemzoom.toggle", keyName);
                    List<String> lines = GuiDraw.fontRenderer
                            .listFormattedStringToWidth(helpText, this.availableAreaWidth);

                    for (String line : lines) {
                        GuiDraw.drawStringC(
                                line,
                                (int) ((this.xPosition + size / 2) * screenScale),
                                (int) ((this.yPosition + size) * screenScale) + 10 + shiftText,
                                0x66555555);
                        shiftText += GuiDraw.fontRenderer.FONT_HEIGHT;
                    }
                }
            }

            GL11.glPopAttrib();
        }
    }

    public void resize(GuiContainer gui) {
        final ItemStack stack = getStackMouseOver(gui);

        if (stack != null && (!NEIClientConfig.getBooleanSetting("inventory.itemzoom.onlySolid")
                || Block.getBlockFromItem(stack.getItem()).getMaterial().isSolid())) {
            final float screenScale = 1.0f / getScreenScale();
            final float availableAreaWidth = (gui.width - gui.xSize) / 2 * screenScale;
            final float availableAreaHeight = gui.height * screenScale;
            final Point mouse = GuiDraw.getMousePosition();

            this.scale = Math.round(getZoomAmount(gui) * getPointSize(gui) / SLOT_SIZE);
            this.yPosition = (availableAreaHeight - this.scale * SLOT_SIZE) / 2;
            this.xPosition = (availableAreaWidth - this.scale * SLOT_SIZE) / 2;
            this.availableAreaWidth = (int) (availableAreaWidth / screenScale);

            if (availableAreaWidth / screenScale >= mouse.x) {
                this.xPosition += gui.width * screenScale - availableAreaWidth;
            }

            try {
                this.displayName = stack.getDisplayName();
            } catch (Throwable ignored) {
                this.displayName = "Unnamed";
            }

            this.stack = StackInfo.loadFromNBT(StackInfo.itemStackToNBT(stack), 0);
        } else {
            this.stack = null;
            this.displayName = "";
        }
    }

    private float getPointSize(GuiContainer gui) {
        final float screenScale = 1.0f / getScreenScale();
        final float availableAreaWidth = (gui.width - (gui.xSize + gui.width) / 2) * screenScale;
        return availableAreaWidth / MAX_ZOOM;
    }

    private float getScreenScale() {
        return Minecraft.getMinecraft().currentScreen.width * 1f / Minecraft.getMinecraft().displayWidth;
    }

    private int getZoomAmount(GuiContainer gui) {
        return Math.max(
                Math.round(SLOT_SIZE * MIN_SCALE / getPointSize(gui)),
                NEIClientConfig.getIntSetting("inventory.itemzoom.zoom"));
    }

    private void increaseZoom(GuiContainer gui) {
        final float pointSize = getPointSize(gui);
        final float scale = Math.round(getZoomAmount(gui) * pointSize / SLOT_SIZE);

        NEIClientConfig.setIntSetting(
                "inventory.itemzoom.zoom",
                Math.min(MAX_ZOOM, Math.round((scale + 1) / (pointSize / SLOT_SIZE))));
    }

    private void decreaseZoom(GuiContainer gui) {
        final float pointSize = getPointSize(gui);
        final float scale = Math.round(getZoomAmount(gui) * pointSize / SLOT_SIZE);

        NEIClientConfig.setIntSetting(
                "inventory.itemzoom.zoom",
                Math.max(MIN_ZOOM, Math.round(Math.max(MIN_SCALE, scale - 1) / (pointSize / SLOT_SIZE))));
    }

    private ItemStack getStackMouseOver(GuiContainer gui) {

        if (NEIClientConfig.getBooleanSetting("inventory.itemzoom.neiOnly")) {
            final Point mouse = GuiDraw.getMousePosition();
            ItemStack stack = ItemPanels.itemPanel.getStackMouseOver(mouse.x, mouse.y);

            if (stack == null) {
                stack = ItemPanels.bookmarkPanel.getStackMouseOver(mouse.x, mouse.y);
            }

            if (stack == null) {
                stack = ItemPanels.itemPanel.historyPanel.getStackMouseOver(mouse.x, mouse.y);
            }

            return stack;
        }

        return GuiContainerManager.getStackMouseOver(gui);
    }

    @Override
    public boolean keyTyped(GuiContainer gui, char keyChar, int keyCode) {
        return false;
    }

    @Override
    public boolean mouseClicked(GuiContainer gui, int mousex, int mousey, int button) {
        return false;
    }

    @Override
    public void onKeyTyped(GuiContainer gui, char keyChar, int keyID) {}

    @Override
    public boolean lastKeyTyped(GuiContainer gui, char keyChar, int keyID) {

        if (NEIClientConfig.isKeyHashDown("gui.itemzoom_toggle")) {
            NEIClientConfig.getSetting("inventory.itemzoom.enabled")
                    .setBooleanValue(!NEIClientConfig.getBooleanSetting("inventory.itemzoom.enabled"));
            return true;
        }

        if (this.stack != null && NEIClientConfig.isKeyHashDown("gui.itemzoom_hold")) {
            return true;
        }

        if (this.stack != null && NEIClientConfig.isKeyHashDown("gui.itemzoom_zoom_in")) {
            increaseZoom(gui);
            previousKeyboardRepeatEnabled = Keyboard.areRepeatEventsEnabled();
            Keyboard.enableRepeatEvents(true);
            return true;
        }

        if (this.stack != null && NEIClientConfig.isKeyHashDown("gui.itemzoom_zoom_out")) {
            decreaseZoom(gui);
            previousKeyboardRepeatEnabled = Keyboard.areRepeatEventsEnabled();
            Keyboard.enableRepeatEvents(true);
            return true;
        }

        if (previousKeyboardRepeatEnabled != null) {
            Keyboard.enableRepeatEvents(previousKeyboardRepeatEnabled);
            previousKeyboardRepeatEnabled = null;
        }

        return false;
    }

    @Override
    public void onMouseClicked(GuiContainer gui, int mousex, int mousey, int button) {}

    @Override
    public void onMouseUp(GuiContainer gui, int mousex, int mousey, int button) {}

    @Override
    public boolean mouseScrolled(GuiContainer gui, int mousex, int mousey, int scrolled) {
        return false;
    }

    @Override
    public void onMouseScrolled(GuiContainer gui, int mousex, int mousey, int scrolled) {}

    @Override
    public void onMouseDragged(GuiContainer gui, int mousex, int mousey, int button, long heldTime) {}

}
