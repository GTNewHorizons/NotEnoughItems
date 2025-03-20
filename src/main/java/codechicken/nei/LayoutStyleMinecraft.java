package codechicken.nei;

import static codechicken.lib.gui.GuiDraw.drawStringC;

import net.minecraft.client.gui.inventory.GuiContainer;

import org.lwjgl.opengl.GL11;

import codechicken.lib.vec.Rectangle4i;
import codechicken.nei.api.LayoutStyle;
import codechicken.nei.drawable.DrawableBuilder;

public class LayoutStyleMinecraft extends LayoutStyle {

    protected static final int BUTTON_SIZE = 20;
    protected static final int MARGIN = 2;

    public int buttonCount;
    public int leftSize;
    public int numButtons;

    @Override
    public String getName() {
        return "minecraft";
    }

    @Override
    public void init() {
        LayoutManager.delete.icon = new Image(144, 12, 12, 12);
        LayoutManager.rain.icon = new Image(120, 12, 12, 12);
        LayoutManager.gamemode.icons[0] = new Image(132, 12, 12, 12);
        LayoutManager.gamemode.icons[1] = new Image(156, 12, 12, 12);
        LayoutManager.gamemode.icons[2] = new Image(168, 12, 12, 12);
        LayoutManager.magnet.icon = new Image(180, 24, 12, 12);
        LayoutManager.timeButtons[0].icon = new Image(132, 24, 12, 12);
        LayoutManager.timeButtons[1].icon = new Image(120, 24, 12, 12);
        LayoutManager.timeButtons[2].icon = new Image(144, 24, 12, 12);
        LayoutManager.timeButtons[3].icon = new Image(156, 24, 12, 12);
        LayoutManager.heal.icon = new Image(168, 24, 12, 12);
        LayoutManager.itemPresenceOverlays[0] = new DrawableBuilder("nei:textures/nei_tabbed_sprites.png", 0, 40, 8, 8)
                .build();
        LayoutManager.itemPresenceOverlays[1] = new DrawableBuilder("nei:textures/nei_tabbed_sprites.png", 8, 40, 8, 8)
                .build();
    }

    @Override
    public void reset() {
        buttonCount = 0;
    }

    @Override
    public void layout(GuiContainer gui, VisiblityData visiblity) {
        reset();

        leftSize = ItemPanels.bookmarkPanel.getWidth(gui);
        numButtons = Math.max(leftSize / 20, 1);

        LayoutManager.delete.state = 0x4;
        if (NEIController.getDeleteMode()) {
            LayoutManager.delete.state |= 1;
        } else if (!visiblity.enableDeleteMode) {
            LayoutManager.delete.state |= 2;
        }

        LayoutManager.rain.state = 0x4;
        if (NEIClientConfig.disabledActions.contains("rain")) {
            LayoutManager.rain.state |= 2;
        } else if (NEIClientUtils.isRaining()) {
            LayoutManager.rain.state |= 1;
        }

        LayoutManager.gamemode.state = 0x4;
        if (NEIClientUtils.getGamemode() != 0) {
            LayoutManager.gamemode.state |= 0x1;
            LayoutManager.gamemode.index = NEIClientUtils.getGamemode() - 1;
        } else {
            if (NEIClientUtils.isValidGamemode("creative")) {
                LayoutManager.gamemode.index = 0;
            } else if (NEIClientUtils.isValidGamemode("creative+")) {
                LayoutManager.gamemode.index = 1;
            } else if (NEIClientUtils.isValidGamemode("adventure")) {
                LayoutManager.gamemode.index = 2;
            }
        }
        LayoutManager.bookmarksButton.index = NEIClientConfig.isBookmarkPanelHidden() ? 0 : 1;
        LayoutManager.options.index = NEIClientConfig.getCheatMode();

        LayoutManager.magnet.state = 0x4 | (NEIClientConfig.getMagnetMode() ? 1 : 0);

        if (NEIClientConfig.canPerformAction("delete")) {
            layoutButton(LayoutManager.delete);
        }

        if (NEIClientConfig.canPerformAction("rain")) {
            layoutButton(LayoutManager.rain);
        }

        if (NEIClientUtils.isValidGamemode("creative") || NEIClientUtils.isValidGamemode("creative+")
                || NEIClientUtils.isValidGamemode("adventure")) {
            layoutButton(LayoutManager.gamemode);
        }

        if (NEIClientConfig.canPerformAction("magnet")) {
            layoutButton(LayoutManager.magnet);
        }

        if (NEIClientConfig.canPerformAction("time")) {
            for (int i = 0; i < 4; i++) {
                LayoutManager.timeButtons[i].state = NEIClientConfig.disabledActions.contains(NEIActions.timeZones[i])
                        ? 2
                        : 0;
                layoutButton(LayoutManager.timeButtons[i]);
            }
        }

        if (NEIClientConfig.canPerformAction("heal")) {
            layoutButton(LayoutManager.heal);
        }

        layoutFooter(gui, visiblity);

        LayoutManager.itemPanel.resize(gui);
        LayoutManager.bookmarkPanel.resize(gui);
        LayoutManager.itemZoom.resize(gui);
    }

    protected void layoutFooter(GuiContainer gui, VisiblityData visiblity) {

        LayoutManager.options.x = MARGIN;
        LayoutManager.options.y = gui.height - BUTTON_SIZE - MARGIN;
        LayoutManager.options.w = LayoutManager.options.h = BUTTON_SIZE;

        LayoutManager.bookmarksButton.x = LayoutManager.options.x + LayoutManager.options.w + MARGIN;
        LayoutManager.bookmarksButton.y = gui.height - BUTTON_SIZE - MARGIN;
        LayoutManager.bookmarksButton.w = LayoutManager.bookmarksButton.h = BUTTON_SIZE;

        final Rectangle4i searchArea = getSearchFieldArea(gui);
        final Rectangle4i quantityArea = getQuantityFieldArea(visiblity);

        LayoutManager.searchField.x = searchArea.x;
        LayoutManager.searchField.y = searchArea.y;
        LayoutManager.searchField.w = searchArea.w;
        LayoutManager.searchField.h = searchArea.h;

        if (!NEIClientConfig.subsetWidgetOnTop() && visiblity.showSubsetDropdown) {
            LayoutManager.dropDown.x = searchArea.x;
            LayoutManager.dropDown.y = searchArea.y;
            LayoutManager.dropDown.h = LayoutManager.dropDown.w = BUTTON_SIZE;

            LayoutManager.searchField.x += BUTTON_SIZE;
            LayoutManager.searchField.w -= BUTTON_SIZE;
        } else {
            LayoutManager.dropDown.h = 16;
            LayoutManager.dropDown.w = 150;
            LayoutManager.dropDown.y = MARGIN;
            LayoutManager.dropDown.x = (gui.width - LayoutManager.dropDown.w) / 2;
        }

        if (quantityArea.w == ItemPanels.itemPanel.w) {
            LayoutManager.more.w = LayoutManager.less.w = BUTTON_SIZE;
            LayoutManager.more.h = LayoutManager.less.h = LayoutManager.quantity.h = quantityArea.h;
            LayoutManager.more.y = LayoutManager.less.y = LayoutManager.quantity.y = quantityArea.y;

            LayoutManager.less.x = quantityArea.x;
            LayoutManager.more.x = quantityArea.x2() - BUTTON_SIZE;

            LayoutManager.quantity.x = LayoutManager.less.x + LayoutManager.less.w + 1;
            LayoutManager.quantity.w = LayoutManager.more.x - LayoutManager.quantity.x - 1;
        } else {
            LayoutManager.quantity.x = quantityArea.x;
            LayoutManager.quantity.w = quantityArea.w - 18;
            LayoutManager.quantity.y = LayoutManager.more.y = quantityArea.y;
            LayoutManager.quantity.h = quantityArea.h;

            LayoutManager.more.w = LayoutManager.less.w = 18;
            LayoutManager.more.x = LayoutManager.less.x = LayoutManager.quantity.x + LayoutManager.quantity.w;
            LayoutManager.more.h = LayoutManager.less.h = LayoutManager.quantity.h / 2;
            LayoutManager.less.y = LayoutManager.more.y + LayoutManager.more.h;
        }

    }

    private Rectangle4i getSearchFieldArea(GuiContainer gui) {
        final Rectangle4i area = new Rectangle4i(0, 0, 0, BUTTON_SIZE);

        if (NEIClientConfig.isSearchWidgetCentered()) {
            area.w = Math.min(gui.xSize, 176) - MARGIN * 2;
            area.x = (gui.width - area.w) / 2;
            area.y = gui.height - BUTTON_SIZE - MARGIN;
        } else if (NEIClientConfig.showItemQuantityWidget()) {
            area.w = ItemPanels.itemPanel.w - (int) Math.max(BUTTON_SIZE * 2.5f, ItemPanels.itemPanel.w * 0.3f) - 1;
            area.x = ItemPanels.itemPanel.x;
            area.y = ItemPanels.itemPanel.y + ItemPanels.itemPanel.h - BUTTON_SIZE;
        } else {
            area.w = ItemPanels.itemPanel.w;
            area.x = ItemPanels.itemPanel.x;
            area.y = ItemPanels.itemPanel.y + ItemPanels.itemPanel.h - BUTTON_SIZE;
        }

        return area;
    }

    private Rectangle4i getQuantityFieldArea(VisiblityData visiblity) {
        final Rectangle4i area = new Rectangle4i(0, 0, 0, BUTTON_SIZE);

        if (!visiblity.showSearchSection || NEIClientConfig.isSearchWidgetCentered()) {
            area.w = ItemPanels.itemPanel.w;
            area.x = ItemPanels.itemPanel.x;
        } else {
            area.w = (int) Math.max(BUTTON_SIZE * 2.5f, ItemPanels.itemPanel.w * 0.3f);
            area.x = ItemPanels.itemPanel.x + ItemPanels.itemPanel.w - area.w;
        }

        area.y = ItemPanels.itemPanel.y + ItemPanels.itemPanel.h - BUTTON_SIZE;

        return area;
    }

    public void layoutButton(Button button) {

        button.h = 17;
        button.w = button.contentWidth() + 6;

        button.x = MARGIN + (buttonCount % numButtons) * (button.w + MARGIN);
        button.y = MARGIN + (buttonCount / numButtons) * (button.h + MARGIN);

        buttonCount++;
    }

    @Override
    public void drawButton(Button b, int mousex, int mousey) {
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glColor4f(1, 1, 1, 1);

        int tex;
        if ((b.state & 0x3) == 2) tex = 0;
        else if ((b.state & 0x4) == 0 && b.contains(mousex, mousey) || // not a state button and mouseover
                (b.state & 0x3) == 1) // state active
            tex = 2;
        else tex = 1;
        LayoutManager.drawButtonBackground(b.x, b.y, b.w, b.h, true, tex);

        Image icon = b.getRenderIcon();
        if (icon == null) {
            int colour = tex == 2 ? 0xffffa0 : tex == 0 ? 0x601010 : 0xe0e0e0;

            drawStringC(b.getRenderLabel(), b.x + b.w / 2, b.y + (b.h - 8) / 2, colour);
        } else {
            GL11.glColor4f(1, 1, 1, 1);

            final int iconX = b.x + (b.w - icon.width) / 2;
            final int iconY = b.y + (b.h - icon.height) / 2;

            LayoutManager.drawIcon(iconX, iconY, icon);
        }
    }

    @Override
    public void drawSubsetTag(String text, int x, int y, int w, int h, int state, boolean mouseover) {
        if (state == 1) {
            GL11.glColor4f(0.65F, 0.65F, 0.65F, 1.0F);
        } else {
            GL11.glColor4f(1, 1, 1, 1);
        }

        LayoutManager.drawButtonBackground(x, y, w, h, false, state == 0 ? 0 : 1);

        if (text != null) {
            drawStringC(text, x, y, w, h, mouseover ? 0xFFFFFFA0 : (state == 2 ? 0xFFE0E0E0 : 0xFFA0A0A0));
        }
    }
}
