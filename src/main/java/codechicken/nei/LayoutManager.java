package codechicken.nei;

import static codechicken.lib.gui.GuiDraw.changeTexture;
import static codechicken.lib.gui.GuiDraw.drawRect;
import static codechicken.lib.gui.GuiDraw.drawTexturedModalRect;
import static codechicken.nei.NEIClientConfig.canPerformAction;
import static codechicken.nei.NEIClientConfig.disabledActions;
import static codechicken.nei.NEIClientConfig.getOptionList;
import static codechicken.nei.NEIClientConfig.getSearchExpression;
import static codechicken.nei.NEIClientConfig.hasSMPCounterPart;
import static codechicken.nei.NEIClientConfig.invCreativeMode;
import static codechicken.nei.NEIClientConfig.isBookmarkPanelHidden;
import static codechicken.nei.NEIClientConfig.isEnabled;
import static codechicken.nei.NEIClientConfig.isHidden;
import static codechicken.nei.NEIClientConfig.showIDs;
import static codechicken.nei.NEIClientConfig.toggleBooleanSetting;
import static codechicken.nei.NEIClientUtils.cycleGamemode;
import static codechicken.nei.NEIClientUtils.decreaseSlotStack;
import static codechicken.nei.NEIClientUtils.deleteEverything;
import static codechicken.nei.NEIClientUtils.deleteHeldItem;
import static codechicken.nei.NEIClientUtils.deleteItemsOfType;
import static codechicken.nei.NEIClientUtils.getGamemode;
import static codechicken.nei.NEIClientUtils.getGuiContainer;
import static codechicken.nei.NEIClientUtils.getHeldItem;
import static codechicken.nei.NEIClientUtils.getNextGamemode;
import static codechicken.nei.NEIClientUtils.healPlayer;
import static codechicken.nei.NEIClientUtils.isValidGamemode;
import static codechicken.nei.NEIClientUtils.setHourForward;
import static codechicken.nei.NEIClientUtils.shiftKey;
import static codechicken.nei.NEIClientUtils.toggleMagnetMode;
import static codechicken.nei.NEIClientUtils.toggleRaining;
import static codechicken.nei.NEIClientUtils.translate;

import java.util.HashMap;
import java.util.List;
import java.util.TreeSet;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.renderer.InventoryEffectRenderer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.KeyManager.IKeyStateTracker;
import codechicken.nei.api.API;
import codechicken.nei.api.GuiInfo;
import codechicken.nei.api.IRecipeOverlayRenderer;
import codechicken.nei.api.ItemInfo;
import codechicken.nei.api.LayoutStyle;
import codechicken.nei.drawable.DrawableBuilder;
import codechicken.nei.drawable.DrawableResource;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.guihook.IContainerDrawHandler;
import codechicken.nei.guihook.IContainerInputHandler;
import codechicken.nei.guihook.IContainerObjectHandler;
import codechicken.nei.guihook.IContainerTooltipHandler;
import codechicken.nei.recipe.GuiRecipeTab;
import codechicken.nei.recipe.RecipeCatalysts;

public class LayoutManager implements IContainerInputHandler, IContainerTooltipHandler, IContainerDrawHandler,
        IContainerObjectHandler, IKeyStateTracker {

    private static LayoutManager instance;

    /** Note: this variable isn't actually used by this mod, but NEI add-ons might need it. */
    private static Widget inputFocused;

    /**
     * Sorted bottom first
     */
    private static TreeSet<Widget> drawWidgets = new TreeSet<>(new WidgetZOrder(false));
    /**
     * Sorted top first
     */
    private static TreeSet<Widget> controlWidgets = new TreeSet<>(new WidgetZOrder(true));

    public static ItemPanel itemPanel;
    public static BookmarkPanel bookmarkPanel;
    public static SubsetWidget dropDown;
    public static PresetsWidget presetsPanel;
    public static SearchField searchField;

    public static ButtonCycled options;
    public static ButtonCycled bookmarksButton;

    @Deprecated
    public static Button more;

    @Deprecated
    public static Button less;

    @Deprecated
    public static ItemQuantityField quantity;

    public static Button delete;
    public static ButtonCycled gamemode;
    public static Button rain;
    public static Button magnet;
    public static Button[] timeButtons = new Button[4];
    public static Button heal;

    public static IRecipeOverlayRenderer overlayRenderer;

    public static HashMap<Integer, LayoutStyle> layoutStyles = new HashMap<>();

    /**
     * This is set to true immediately after item loading thread is started, to prevent duplicated loading thread from
     * being executed. If you want to know if item list is actually finished loading, Use {@link ItemList#loadFinished}
     * instead.
     */
    public static boolean itemsLoaded = false;

    /**
     * Not present, Present
     */
    public static Image[] itemPresenceOverlays = new Image[2];

    public static void load() {
        API.addLayoutStyle(0, new LayoutStyleMinecraft());

        instance = new LayoutManager();
        KeyManager.trackers.add(instance);
        GuiContainerManager.addInputHandler(instance);
        GuiContainerManager.addTooltipHandler(instance);
        GuiContainerManager.addDrawHandler(instance);
        GuiContainerManager.addObjectHandler(instance);
        init();
    }

    @Override
    public void onPreDraw(GuiContainer gui) {
        if (!isHidden() && isEnabled() && gui instanceof InventoryEffectRenderer) // Reset the gui to the center of the
                                                                                  // screen, for potion effect offsets
        // etc
        {
            gui.guiLeft = (gui.width - gui.xSize) / 2;
            gui.guiTop = (gui.height - gui.ySize) / 2;

            if (gui instanceof GuiContainerCreative && gui.buttonList.size() >= 2) {
                GuiButton button1 = (GuiButton) gui.buttonList.get(0);
                GuiButton button2 = (GuiButton) gui.buttonList.get(1);
                button1.xPosition = gui.guiLeft;
                button2.xPosition = gui.guiLeft + gui.xSize - 20;
            }
        }
    }

    @Deprecated
    public static int getSideWidth(GuiContainer gui) {
        return gui.width - 3;
    }

    @Deprecated
    public static int getLeftSize(GuiContainer gui) {
        return getSideWidth(gui);
    }

    @Override
    public void onMouseClicked(GuiContainer gui, int mousex, int mousey, int button) {
        if (isHidden()) return;

        for (Widget widget : controlWidgets) widget.onGuiClick(mousex, mousey);
    }

    @Override
    public boolean mouseClicked(GuiContainer gui, int mousex, int mousey, int button) {
        if (isHidden()) return false;

        if (!isEnabled()) return options.contains(mousex, mousey) && options.handleClick(mousex, mousey, button);

        for (Widget widget : controlWidgets) {
            widget.onGuiClick(mousex, mousey);
            if (widget.contains(mousex, mousey) ? widget.handleClick(mousex, mousey, button)
                    : widget.handleClickExt(mousex, mousey, button))
                return true;
        }

        return false;
    }

    @Override
    public boolean objectUnderMouse(GuiContainer gui, int mousex, int mousey) {
        if (!isHidden() && isEnabled())
            for (Widget widget : drawWidgets) if (widget.contains(mousex, mousey)) return true;

        return false;
    }

    public boolean keyTyped(GuiContainer gui, char keyChar, int keyID) {
        if (isEnabled() && !isHidden()) {
            if (inputFocused != null) return inputFocused.handleKeyPress(keyID, keyChar);

            for (Widget widget : controlWidgets) if (widget.handleKeyPress(keyID, keyChar)) return true;
        }

        return false;
    }

    @Override
    public void onKeyTyped(GuiContainer gui, char keyChar, int keyID) {}

    @Override
    public boolean lastKeyTyped(GuiContainer gui, char keyChar, int keyID) {

        if (NEIClientConfig.isKeyHashDown("gui.hide")) {
            toggleBooleanSetting("inventory.hidden");
            return true;
        }

        if (NEIClientConfig.isKeyHashDown("gui.hide_bookmarks")) {
            toggleBooleanSetting("inventory.bookmarksEnabled");
            return true;
        }

        if (NEIClientConfig.isKeyHashDown("gui.next_tooltip")) {
            GuiContainerManager.incrementTooltipPage();
            return true;
        }

        if (isEnabled() && !isHidden()) {
            for (Widget widget : controlWidgets) if (inputFocused == null) widget.lastKeyTyped(keyID, keyChar);
        }

        return false;
    }

    public void onMouseUp(GuiContainer gui, int mx, int my, int button) {
        if (!isHidden() && isEnabled()) {
            for (Widget widget : controlWidgets) widget.mouseUp(mx, my, button);
        }
    }

    @Override
    public void onMouseDragged(GuiContainer gui, int mx, int my, int button, long heldTime) {
        if (!isHidden() && isEnabled()) {
            for (Widget widget : controlWidgets) widget.mouseDragged(mx, my, button, heldTime);
        }
    }

    @Override
    public ItemStack getStackUnderMouse(GuiContainer gui, int mousex, int mousey) {
        if (!isHidden() && isEnabled()) {
            for (Widget widget : drawWidgets) {
                ItemStack stack = widget.getStackMouseOver(mousex, mousey);
                if (stack != null) return stack;
            }
        }
        return null;
    }

    public void renderObjects(GuiContainer gui, int mousex, int mousey) {
        if (!isHidden()) {
            layout(gui);
            if (isEnabled()) {
                getLayoutStyle().drawBackground(GuiContainerManager.getManager(gui));
                for (Widget widget : drawWidgets) widget.draw(mousex, mousey);
            } else {
                options.draw(mousex, mousey);
                bookmarksButton.draw(mousex, mousey);
            }

            GL11.glEnable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
        }
    }

    @Override
    public void postRenderObjects(GuiContainer gui, int mousex, int mousey) {
        if (!isHidden() && isEnabled()) {
            for (Widget widget : drawWidgets) widget.postDraw(mousex, mousey);
        }
    }

    @Override
    public void postRenderTooltips(GuiContainer gui, int mousex, int mousey, List<String> tooltip) {
        if (!isHidden() && isEnabled() && GuiContainerManager.shouldShowTooltip(gui)) {
            for (Widget widget : drawWidgets) widget.postDrawTooltips(mousex, mousey, tooltip);
        }
    }

    @Override
    public List<String> handleTooltip(GuiContainer gui, int mousex, int mousey, List<String> currenttip) {
        if (!isHidden() && isEnabled() && GuiContainerManager.shouldShowTooltip(gui)) {
            for (Widget widget : drawWidgets) currenttip = widget.handleTooltip(mousex, mousey, currenttip);
        }
        return currenttip;
    }

    @Override
    public List<String> handleItemDisplayName(GuiContainer gui, ItemStack stack, List<String> currenttip) {
        String overridename = ItemInfo.getNameOverride(stack);
        if (overridename != null) currenttip.set(0, overridename);

        String mainname = currenttip.get(0);
        if (showIDs()) {
            mainname += " " + Item.getIdFromItem(stack.getItem());
            if (stack.getItemDamage() != 0) mainname += ":" + stack.getItemDamage();

            currenttip.set(0, mainname);
        }

        return currenttip;
    }

    @Override
    public List<String> handleItemTooltip(GuiContainer gui, ItemStack itemstack, int mousex, int mousey,
            List<String> currenttip) {
        return currenttip;
    }

    public static void layout(GuiContainer gui) {
        VisiblityData visiblity = new VisiblityData();

        if (isHidden()) visiblity.showNEI = false;

        if (isBookmarkPanelHidden()) visiblity.showBookmarkPanel = false;

        if (gui.height - gui.ySize <= 40 && NEIClientConfig.isSearchWidgetCentered())
            visiblity.showSearchSection = false;

        if (visiblity.showBookmarkPanel || gui.guiTop <= 20) visiblity.showSubsetDropdown = false;

        if (!visiblity.showBookmarkPanel || gui.guiTop <= 20) visiblity.showPresetsDropdown = false;

        if (gui.guiLeft - 4 < 76) visiblity.showWidgets = false;

        if (!itemsLoaded) visiblity.showPresetsDropdown = false;

        try {
            GuiInfo.readLock.lock();
            GuiInfo.guiHandlers.forEach(handler -> handler.modifyVisiblity(gui, visiblity));
        } finally {
            GuiInfo.readLock.unlock();
        }

        visiblity.translateDependancies();

        getLayoutStyle().layout(gui, visiblity);

        updateWidgetVisiblities(gui, visiblity);
    }

    private static void init() {
        itemPanel = ItemPanels.itemPanel;
        itemPanel.init();

        bookmarkPanel = ItemPanels.bookmarkPanel;
        bookmarkPanel.init();

        dropDown = new SubsetWidget();
        presetsPanel = new PresetsWidget();
        searchField = new SearchField("search");

        options = new ButtonCycled(3) {

            @Override
            public void init() {
                this.icons[0] = new DrawableBuilder("nei:textures/nei_tabbed_sprites.png", 32, 0, 16, 16).build();
                this.icons[1] = new DrawableBuilder("nei:textures/nei_tabbed_sprites.png", 48, 0, 16, 16).build();
                this.icons[2] = new DrawableBuilder("nei:textures/nei_tabbed_sprites.png", 64, 0, 16, 16).build();
            }

            private boolean getIsAccessibleControlEventKey() {
                if (Minecraft.isRunningOnMac) {
                    return Keyboard.getEventKey() == Keyboard.KEY_LMETA || Keyboard.getEventKey() == Keyboard.KEY_RMETA;
                }
                return Keyboard.getEventKey() == Keyboard.KEY_LCONTROL
                        || Keyboard.getEventKey() == Keyboard.KEY_RCONTROL;
            }

            @Override
            public boolean onButtonPress(boolean rightclick) {
                if (!rightclick) {
                    if (Keyboard.getEventKeyState() && getIsAccessibleControlEventKey()) {
                        NEIClientConfig.cycleSetting("inventory.cheatmode", 3);
                    } else {
                        if (Keyboard.getEventKeyState() && (Keyboard.getEventKey() == Keyboard.KEY_LSHIFT
                                || Keyboard.getEventKey() == Keyboard.KEY_RSHIFT)) {
                            GuiRecipeTab.loadHandlerInfo();
                            RecipeCatalysts.loadCatalystInfo();
                        }
                        getOptionList().openGui(getGuiContainer(), false);
                    }
                    return true;
                }
                return false;
            }

            @Override
            public void addTooltips(List<String> tooltip) {
                tooltip.add(translate("inventory.options.tip"));
                String modeColor = "";
                final int cheatMode = NEIClientConfig.getCheatMode();
                if (cheatMode == 1) modeColor = EnumChatFormatting.GOLD.toString();
                else if (cheatMode == 2) modeColor = EnumChatFormatting.RED.toString();
                String controlKeyLocalization = translate(Minecraft.isRunningOnMac ? "key.ctrl.mac" : "key.ctrl");
                tooltip.add(modeColor + translate("inventory.options.tip.cheatmode." + cheatMode));
                tooltip.add(modeColor + translate("inventory.options.tip.cheatmode.disable", controlKeyLocalization));
            }

            @Override
            public String getRenderLabel() {
                return translate("inventory.options");
            }
        };

        bookmarksButton = new ButtonCycled(2) {

            @Override
            public void init() {
                this.icons[0] = new DrawableBuilder("nei:textures/nei_tabbed_sprites.png", 0, 0, 16, 16).build();
                this.icons[1] = new DrawableBuilder("nei:textures/nei_tabbed_sprites.png", 16, 0, 16, 16).build();
            }

            @Override
            public boolean onButtonPress(boolean rightclick) {
                NEIClientConfig.toggleBooleanSetting("inventory.bookmarksEnabled");
                return true;
            }

            public String getButtonTip() {
                return translate("bookmark.toggle.tip");
            }

            @Override
            public String getRenderLabel() {
                return translate("bookmark.toggle");
            }
        };

        more = ItemPanels.itemPanel.more;
        less = ItemPanels.itemPanel.less;
        quantity = ItemPanels.itemPanel.quantity;
        delete = new Button() {

            @Override
            public boolean onButtonPress(boolean rightclick) {
                if ((state & 0x3) == 2) return false;

                ItemStack held = getHeldItem();
                if (held != null) {
                    if (shiftKey()) {
                        deleteHeldItem();
                        deleteItemsOfType(held);
                    } else if (rightclick) decreaseSlotStack(-999);
                    else deleteHeldItem();
                } else if (shiftKey()) deleteEverything();
                else NEIController.toggleDeleteMode();

                return true;
            }

            public String getButtonTip() {
                if ((state & 0x3) != 2) {
                    if (shiftKey()) return translate("inventory.delete.inv");
                    if (NEIController.canUseDeleteMode()) return getStateTip("delete", state);
                }
                return null;
            }

            @Override
            public void postDraw(int mousex, int mousey) {
                if (contains(mousex, mousey) && getHeldItem() != null && (state & 0x3) != 2) GuiDraw.drawTip(
                        mousex + 9,
                        mousey,
                        translate(
                                "inventory.delete." + (shiftKey() ? "all" : "one"),
                                GuiContainerManager.itemDisplayNameShort(getHeldItem())));
            }
        };
        gamemode = new ButtonCycled(3) {

            @Override
            public boolean onButtonPress(boolean rightclick) {
                if (!rightclick) {
                    cycleGamemode();
                    return true;
                }
                return false;
            }

            public void addTooltips(List<String> tooltip) {
                tooltip.add(translate("inventory.current.gamemode." + getGamemode()));
                tooltip.add(EnumChatFormatting.GRAY + translate("inventory.gamemode." + getNextGamemode()));
            }
        };
        rain = new Button() {

            @Override
            public boolean onButtonPress(boolean rightclick) {
                if (handleDisabledButtonPress("rain", rightclick)) return true;

                if (!rightclick) {
                    toggleRaining();
                    return true;
                }
                return false;
            }

            public String getButtonTip() {
                return getStateTip("rain", state);
            }
        };
        magnet = new Button() {

            @Override
            public boolean onButtonPress(boolean rightclick) {
                if (!rightclick) {
                    toggleMagnetMode();
                    return true;
                }
                return false;
            }

            public String getButtonTip() {
                return getStateTip("magnet", state);
            }
        };
        for (int i = 0; i < 4; i++) {
            final int zone = i;
            timeButtons[i] = new Button() {

                @Override
                public boolean onButtonPress(boolean rightclick) {
                    if (handleDisabledButtonPress(NEIActions.timeZones[zone], rightclick)) return true;

                    if (!rightclick) {
                        setHourForward(zone * 6);
                        return true;
                    }
                    return false;
                }

                @Override
                public void addTooltips(List<String> tooltip) {
                    addTimeTip(NEIActions.timeZones[zone], state, tooltip);
                }
            };
        }
        heal = new Button() {

            @Override
            public boolean onButtonPress(boolean rightclick) {
                if (!rightclick) {
                    healPlayer();
                    return true;
                }
                return false;
            }

            @Override
            public String getButtonTip() {
                return translate("inventory.heal");
            }
        };

        delete.state |= 0x4;
        gamemode.state |= 0x4;
        rain.state |= 0x4;
        magnet.state |= 0x4;
    }

    private static String getStateTip(String name, int state) {
        String sfx = (state & 0x3) == 2 ? "enable" : (state & 0x3) == 1 ? "0" : "1";

        return translate("inventory." + name + "." + sfx);
    }

    private static void addTimeTip(String name, int state, List<String> tooltip) {
        if ((state & 0x3) == 2) {
            tooltip.add(translate("inventory." + name + ".enable"));
        } else {
            tooltip.add(translate("inventory." + name + ".set"));
            tooltip.add(EnumChatFormatting.GRAY + translate("inventory." + name + ".disable"));
        }
    }

    private static boolean handleDisabledButtonPress(String ident, boolean rightclick) {
        if (!NEIActions.canDisable.contains(ident)) return false;
        if (rightclick != disabledActions.contains(ident)) return setPropertyDisabled(ident, rightclick);
        return false;
    }

    private static boolean setPropertyDisabled(String ident, boolean disable) {
        if (disable && NEIActions.base(ident).equals("time")) {
            int count = 0;
            for (int i = 0; i < 4; i++) {
                if (disabledActions.contains(NEIActions.timeZones[i])) count++;
            }
            if (count == 3) return false;
        }
        if (hasSMPCounterPart()) NEICPH.sendSetPropertyDisabled(ident, disable);

        return true;
    }

    public static void markItemsDirty() {
        // If anything has modified data this depends on, we'll need to reload the item list
        itemsLoaded = false;
    }

    @Override
    public void load(GuiContainer gui) {
        if (isEnabled()) {
            setInputFocused(null);

            if (!itemsLoaded) {
                ItemList.loadItems.restart();
                itemsLoaded = true;
            }
            overlayRenderer = null;

            getLayoutStyle().init();
            layout(gui);
        }

        NEIController.load(gui);

        if (checkCreativeInv(gui) && gui.mc.currentScreen instanceof GuiContainerCreative) // override creative with
                                                                                           // creative+
            gui.mc.displayGuiScreen(null); // close the screen and wait for the server to open it for us
    }

    @Override
    public void refresh(GuiContainer gui) {}

    public boolean checkCreativeInv(GuiContainer gui) {
        if (gui instanceof GuiContainerCreative && invCreativeMode()) {
            NEICPH.sendCreativeInv(true);
            return true;
        } else if (gui instanceof GuiExtendedCreativeInv && !invCreativeMode()) {
            NEICPH.sendCreativeInv(false);
            return true;
        }
        return false;
    }

    public static void updateWidgetVisiblities(GuiContainer gui, VisiblityData visiblity) {
        drawWidgets = new TreeSet<>(new WidgetZOrder(false));
        controlWidgets = new TreeSet<>(new WidgetZOrder(true));

        if (!visiblity.showNEI) return;

        addWidget(options);
        addWidget(bookmarksButton);
        if (visiblity.showItemPanel) {

            if (PresetsWidget.inEditMode()) {
                drawWidgets.add(itemPanel);
            } else {
                addWidget(itemPanel);
            }

            itemPanel.setVisible();
        }

        if (visiblity.showBookmarkPanel) {
            addWidget(bookmarkPanel);
            bookmarkPanel.setVisible();
        }

        if (visiblity.showPresetsDropdown) {
            addWidget(presetsPanel);
        }

        searchField.setVisible(visiblity.showSearchSection);
        if (visiblity.showSearchSection) {
            addWidget(searchField);
        }

        if (visiblity.showUtilityButtons) {
            if (canPerformAction("time")) {
                for (int i = 0; i < 4; i++) addWidget(timeButtons[i]);
            }
            if (canPerformAction("rain")) addWidget(rain);
            if (canPerformAction("heal")) addWidget(heal);
            if (canPerformAction("magnet")) addWidget(magnet);
            if (isValidGamemode("creative") || isValidGamemode("creative+") || isValidGamemode("adventure"))
                addWidget(gamemode);
            if (canPerformAction("delete")) addWidget(delete);
        }

        if (visiblity.showSubsetDropdown) {
            // Bookmarks or Subset/dropdown
            addWidget(dropDown);
        }
    }

    public static LayoutStyle getLayoutStyle(int id) {
        LayoutStyle style = layoutStyles.get(id);
        if (style == null) style = layoutStyles.get(0);
        return style;
    }

    public static LayoutStyle getLayoutStyle() {
        return getLayoutStyle(0);
    }

    public static void addWidget(Widget widget) {
        drawWidgets.add(widget);
        controlWidgets.add(widget);
    }

    @Override
    public void guiTick(GuiContainer gui) {
        if (checkCreativeInv(gui)) return;

        if (!isEnabled()) return;

        for (Widget widget : drawWidgets) widget.update();
    }

    @Override
    public boolean mouseScrolled(GuiContainer gui, int mousex, int mousey, int scrolled) {
        if (isHidden() || !isEnabled()) return false;

        for (Widget widget : drawWidgets) if (widget.onMouseWheel(scrolled, mousex, mousey)) return true;

        return false;
    }

    @Override
    public void onMouseScrolled(GuiContainer gui, int mousex, int mousey, int scrolled) {}

    @Override
    public boolean shouldShowTooltip(GuiContainer gui) {
        return itemPanel.draggedStack == null && !bookmarkPanel.inEditingState();
    }

    /** Note: this method isn't actually used by this mod, but NEI add-ons might need it. */
    public static Widget getInputFocused() {
        return inputFocused;
    }

    /** Note: this method isn't actually used by this mod, but NEI add-ons might need it. */
    public static void setInputFocused(Widget widget) {
        if (inputFocused != null) inputFocused.loseFocus();

        inputFocused = widget;
        if (inputFocused != null) inputFocused.gainFocus();
    }

    @Override
    public void renderSlotUnderlay(GuiContainer gui, Slot slot) {
        if (overlayRenderer != null) overlayRenderer.renderOverlay(GuiContainerManager.getManager(gui), slot);
    }

    @Override
    public void renderSlotOverlay(GuiContainer window, Slot slot) {

        if (SearchField.searchInventories()) {
            ItemStack item = slot.getStack();

            if (item == null ? !getSearchExpression().equals("") : !searchField.getFilter().matches(item)) {
                GL11.glDisable(GL11.GL_LIGHTING);
                GL11.glDisable(GL11.GL_DEPTH_TEST);
                GL11.glTranslatef(0, 0, 150);
                drawRect(slot.xDisplayPosition, slot.yDisplayPosition, 16, 16, 0x80000000);
                GL11.glTranslatef(0, 0, -150);
                GL11.glEnable(GL11.GL_LIGHTING);
                GL11.glEnable(GL11.GL_DEPTH_TEST);
            }
        }

    }

    public static void drawIcon(int x, int y, Image image) {
        final boolean isDrawableResource = image instanceof DrawableResource;

        if (!isDrawableResource) changeTexture("nei:textures/nei_sprites.png");

        GL11.glColor4f(1, 1, 1, 1);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        if (!isDrawableResource) drawTexturedModalRect(x, y, image.x, image.y, image.width, image.height);
        else((DrawableResource) image).draw(x, y);
        GL11.glDisable(GL11.GL_BLEND);
    }

    public static void drawButtonBackground(int x, int y, int w, int h, boolean edges, int type) {
        int wTiles = 0;
        int ew = w; // end width
        if (w / 2 > 100) {
            wTiles = (w - 200) / 50 + 1;
            ew = 200;
        }

        final int w1 = ew / 2;
        final int h1 = h / 2;
        final int w2 = (ew + 1) / 2;
        final int h2 = (h + 1) / 2;

        final int x2 = x + w - w2;
        final int y2 = y + h - h2;

        final int ty = 46 + type * 20;
        final int te = (edges ? 0 : 1); // tex edges

        final int ty1 = ty + te;
        final int tx1 = te;
        final int tx3 = 75;
        // halfway the 1 is for odd number adjustment
        final int ty2 = ty + 20 - h2 - te;
        final int tx2 = 200 - w2 - te;

        changeTexture("textures/gui/widgets.png");
        drawTexturedModalRect(x, y, tx1, ty1, w1, h1); // top left
        drawTexturedModalRect(x, y2, tx1, ty2, w1, h2); // bottom left

        for (int tile = 0; tile < wTiles; tile++) {
            final int tileX = x + w1 + 50 * tile;
            drawTexturedModalRect(tileX, y, tx3, ty1, 50, h1); // top
            drawTexturedModalRect(tileX, y2, tx3, ty2, 50, h2); // bottom
        }

        drawTexturedModalRect(x2, y, tx2, ty1, w2, h1); // top right
        drawTexturedModalRect(x2, y2, tx2, ty2, w2, h2); // bottom right
    }

    public static void drawItemPresenceOverlay(int slotX, int slotY, boolean isPresent) {
        Image icon = itemPresenceOverlays[isPresent ? 1 : 0];
        drawIcon(slotX + 16 - icon.width, slotY + 16 - icon.height, icon);
    }

    public static LayoutManager instance() {
        return instance;
    }

    @Override
    public void tickKeyStates() {
        if (Minecraft.getMinecraft().currentScreen != null) return;

        if (KeyManager.keyStates.get("world.dawn").down) timeButtons[0].onButtonPress(false);
        if (KeyManager.keyStates.get("world.noon").down) timeButtons[1].onButtonPress(false);
        if (KeyManager.keyStates.get("world.dusk").down) timeButtons[2].onButtonPress(false);
        if (KeyManager.keyStates.get("world.midnight").down) timeButtons[3].onButtonPress(false);
        if (KeyManager.keyStates.get("world.rain").down) rain.onButtonPress(false);
        if (KeyManager.keyStates.get("world.heal").down) heal.onButtonPress(false);
        if (KeyManager.keyStates.get("world.creative").down) gamemode.onButtonPress(false);
    }
}
