package codechicken.nei;

import static codechicken.lib.gui.GuiDraw.drawStringC;
import static codechicken.nei.LayoutManager.bookmarkPanel;
import static codechicken.nei.LayoutManager.bookmarksButton;
import static codechicken.nei.LayoutManager.delete;
import static codechicken.nei.LayoutManager.dropDown;
import static codechicken.nei.LayoutManager.gamemode;
import static codechicken.nei.LayoutManager.heal;
import static codechicken.nei.LayoutManager.itemPanel;
import static codechicken.nei.LayoutManager.itemPresenceOverlays;
import static codechicken.nei.LayoutManager.magnet;
import static codechicken.nei.LayoutManager.options;
import static codechicken.nei.LayoutManager.presetsPanel;
import static codechicken.nei.LayoutManager.rain;
import static codechicken.nei.LayoutManager.searchField;
import static codechicken.nei.LayoutManager.timeButtons;
import static codechicken.nei.NEIClientConfig.canPerformAction;
import static codechicken.nei.NEIClientConfig.disabledActions;
import static codechicken.nei.NEIClientConfig.getMagnetMode;
import static codechicken.nei.NEIClientConfig.isEnabled;

import net.minecraft.client.gui.inventory.GuiContainer;

import org.lwjgl.opengl.GL11;

import codechicken.nei.api.LayoutStyle;
import codechicken.nei.drawable.DrawableBuilder;

public class LayoutStyleMinecraft extends LayoutStyle {

    public int buttonCount;
    public int leftSize;
    public int numButtons;

    @Override
    public String getName() {
        return "minecraft";
    }

    @Override
    public void init() {
        delete.icon = new Image(144, 12, 12, 12);
        rain.icon = new Image(120, 12, 12, 12);
        gamemode.icons[0] = new Image(132, 12, 12, 12);
        gamemode.icons[1] = new Image(156, 12, 12, 12);
        gamemode.icons[2] = new Image(168, 12, 12, 12);
        magnet.icon = new Image(180, 24, 12, 12);
        timeButtons[0].icon = new Image(132, 24, 12, 12);
        timeButtons[1].icon = new Image(120, 24, 12, 12);
        timeButtons[2].icon = new Image(144, 24, 12, 12);
        timeButtons[3].icon = new Image(156, 24, 12, 12);
        heal.icon = new Image(168, 24, 12, 12);
        itemPresenceOverlays[0] = new DrawableBuilder("nei:textures/nei_tabbed_sprites.png", 0, 40, 8, 8).build();
        itemPresenceOverlays[1] = new DrawableBuilder("nei:textures/nei_tabbed_sprites.png", 8, 40, 8, 8).build();
    }

    @Override
    public void reset() {
        buttonCount = 0;
    }

    @Override
    public void layout(GuiContainer gui, VisiblityData visiblity) {
        reset();

        leftSize = ItemPanels.bookmarkPanel.getWidth(gui);
        numButtons = Math.max(leftSize / 18, 1);

        delete.state = 0x4;
        if (NEIController.getDeleteMode()) delete.state |= 1;
        else if (!visiblity.enableDeleteMode) delete.state |= 2;

        rain.state = 0x4;
        if (disabledActions.contains("rain")) rain.state |= 2;
        else if (NEIClientUtils.isRaining()) rain.state |= 1;

        gamemode.state = 0x4;
        if (NEIClientUtils.getGamemode() != 0) {
            gamemode.state |= 0x1;
            gamemode.index = NEIClientUtils.getGamemode() - 1;
        } else {
            if (NEIClientUtils.isValidGamemode("creative")) gamemode.index = 0;
            else if (NEIClientUtils.isValidGamemode("creative+")) gamemode.index = 1;
            else if (NEIClientUtils.isValidGamemode("adventure")) gamemode.index = 2;
        }
        bookmarksButton.index = NEIClientConfig.isBookmarkPanelHidden() ? 0 : 1;
        options.index = NEIClientConfig.getCheatMode();

        magnet.state = 0x4 | (getMagnetMode() ? 1 : 0);

        if (canPerformAction("delete")) layoutButton(delete);
        if (canPerformAction("rain")) layoutButton(rain);
        if (NEIClientUtils.isValidGamemode("creative") || NEIClientUtils.isValidGamemode("creative+")
                || NEIClientUtils.isValidGamemode("adventure"))
            layoutButton(gamemode);
        if (canPerformAction("magnet")) layoutButton(magnet);
        if (canPerformAction("time")) {
            for (int i = 0; i < 4; i++) {
                timeButtons[i].state = disabledActions.contains(NEIActions.timeZones[i]) ? 2 : 0;
                layoutButton(timeButtons[i]);
            }
        }
        if (canPerformAction("heal")) layoutButton(heal);

        itemPanel.resize(gui);
        bookmarkPanel.resize(gui);

        options.x = isEnabled() ? 0 : 6;
        options.y = isEnabled() ? gui.height - 22 : gui.height - 28;
        options.w = 22;
        options.h = 22;

        bookmarksButton.x = 24 + (isEnabled() ? 0 : 6);
        bookmarksButton.y = isEnabled() ? gui.height - 22 : gui.height - 28;
        bookmarksButton.w = 22;
        bookmarksButton.h = 22;

        dropDown.h = 18;
        dropDown.w = 150;
        dropDown.y = 0;
        dropDown.x = (gui.width - gui.xSize) / 2 + gui.xSize - dropDown.w;

        presetsPanel.h = 16;
        presetsPanel.w = 150;
        presetsPanel.y = 2;
        presetsPanel.x = (gui.width - gui.xSize) / 2 + gui.xSize - presetsPanel.w;

        searchField.h = 20;
        if (NEIClientConfig.isSearchWidgetCentered()) {
            searchField.w = 150;
            searchField.x = (gui.width - searchField.w) / 2;
        } else {
            searchField.w = (int) (itemPanel.w * (NEIClientConfig.showItemQuantityWidget() ? 0.7 : 1));
            searchField.x = itemPanel.x;
        }
        searchField.y = gui.height - searchField.h - 2;

        if (!visiblity.showItemSection) {
            searchField.setFocus(false);
        }
    }

    public void layoutButton(Button button) {
        button.x = 2 + (buttonCount % numButtons) * 19;
        button.y = 2 + (buttonCount / numButtons) * 18;

        button.h = 17;
        button.w = button.contentWidth() + 6;

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
        if (state == 1) GL11.glColor4f(0.65F, 0.65F, 0.65F, 1.0F);
        else GL11.glColor4f(1, 1, 1, 1);
        LayoutManager.drawButtonBackground(x, y, w, h, false, state == 0 ? 0 : 1);
        if (text != null) drawStringC(text, x, y, w, h, state == 2 ? 0xFFE0E0E0 : 0xFFA0A0A0);
    }
}
