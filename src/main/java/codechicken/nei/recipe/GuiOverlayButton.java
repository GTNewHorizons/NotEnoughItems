package codechicken.nei.recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.ItemPanels;
import codechicken.nei.ItemsTooltipLineHandler;
import codechicken.nei.KeyManager;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.PositionedStack;
import codechicken.nei.api.IOverlayHandler;
import codechicken.nei.bookmark.BookmarkGrid;
import codechicken.nei.bookmark.BookmarkPayload;
import codechicken.nei.drawable.Drawable;
import codechicken.nei.theme.ButtonSkin;
import codechicken.nei.theme.UIRecipeTexture;
import codechicken.nei.theme.UIWidgetTexture;
import codechicken.nei.util.NEIMouseUtils;

public class GuiOverlayButton extends GuiRecipeButton {

    protected static Drawable notPresent = UIRecipeTexture.ITEM_MISSING;
    protected static Drawable present = UIRecipeTexture.ITEM_PRESENT;

    public enum ItemOverlayFormat {

        BACKGROUND,
        ICON;

        public static ItemOverlayFormat from(int overlay) {
            return overlay == 2 ? ItemOverlayFormat.BACKGROUND : ItemOverlayFormat.ICON;
        }
    }

    public static class ItemOverlayState {

        protected PositionedStack slot;
        protected boolean isPresent;

        public ItemOverlayState(PositionedStack slot, boolean isPresent) {
            this.slot = slot;
            this.isPresent = isPresent;
        }

        public boolean isPresent() {
            return this.isPresent;
        }

        public PositionedStack getSlot() {
            return this.slot;
        }

        public void draw(ItemOverlayFormat format) {

            if (format == ItemOverlayFormat.BACKGROUND) {
                NEIClientUtils.gl2DRenderContext(
                        () -> GuiDraw.drawRect(
                                this.slot.relx,
                                this.slot.rely,
                                this.slot.width,
                                this.slot.height,
                                isPresent ? 0x8000AA00 : 0x80AA0000));
            } else {
                final Drawable icon = isPresent ? present : notPresent;
                icon.draw(
                        this.slot.relx + this.slot.width - icon.getWidth(),
                        this.slot.rely + this.slot.height - icon.getHeight());
            }

        }
    }

    protected static final Drawable ICON_FILL = UIRecipeTexture.ICON_OVERLAY_FILL;
    protected static final Drawable ICON_OVERLAY = UIRecipeTexture.ICON_OVERLAY;
    protected static final int BUTTON_ID_SHIFT = 4;

    public final GuiContainer firstGui;

    protected final List<ItemOverlayState> itemPresenceCache = new ArrayList<>();
    protected ItemsTooltipLineHandler missedMaterialsTooltipLineHandler;
    protected boolean requireShiftForOverlayRecipe = true;
    protected boolean canUseOverlayRenderer = false;
    protected boolean canFillCraftingGrid = false;
    protected boolean hasOverlay = false;

    public GuiOverlayButton(GuiContainer firstGui, RecipeHandlerRef handlerRef, int x, int y) {
        super(handlerRef, x, y, handlerRef.recipeIndex + BUTTON_ID_SHIFT, "+");
        this.firstGui = firstGui != null && firstGui.inventorySlots != null ? firstGui : null;

        IOverlayHandler overlayHandler = null;

        if (this.firstGui != null) {
            overlayHandler = this.handlerRef.getOverlayHandler(this.firstGui);
            this.canUseOverlayRenderer = this.handlerRef.getRecipeOverlayRenderer(this.firstGui) != null;
            this.canFillCraftingGrid = this.handlerRef.canFillCraftingGrid(this.firstGui);
            this.hasOverlay = this.canUseOverlayRenderer || overlayHandler != null;
        }

        setRequireShiftForOverlayRecipe(
                overlayHandler != null ? overlayHandler.requireShiftForOverlayRecipe()
                        : NEIClientConfig.requireShiftForOverlayRecipe());
        ingredientsOverlay();
    }

    @Override
    public List<String> handleTooltip(List<String> currenttip) {

        if (!this.enabled || this.missedMaterialsTooltipLineHandler != null && this.canFillCraftingGrid) {
            currenttip.add(NEIClientUtils.translate("recipe.overlay") + GuiDraw.TOOLTIP_LINESPACE);
        } else {
            currenttip.add(NEIClientUtils.translate("recipe.overlay"));
        }

        if (!this.enabled) {
            currenttip.add(EnumChatFormatting.RED + NEIClientUtils.translate("recipe.overlay.mismatch"));
        }

        if (this.missedMaterialsTooltipLineHandler != null && this.canFillCraftingGrid) {

            if (!requireShiftForOverlayRecipe() || NEIClientUtils.shiftKey()) {
                this.missedMaterialsTooltipLineHandler.setLabelColor(EnumChatFormatting.RED);
            } else {
                this.missedMaterialsTooltipLineHandler.setLabelColor(EnumChatFormatting.GRAY);
            }

            currenttip.add(GuiDraw.TOOLTIP_HANDLER + GuiDraw.getTipLineId(this.missedMaterialsTooltipLineHandler));
        }

        return currenttip;
    }

    @Override
    public Map<String, String> handleHotkeys(int mousex, int mousey, Map<String, String> hotkeys) {

        if (requireShiftForOverlayRecipe()) {

            if (useOverlayRenderer()) {
                hotkeys.put(
                        NEIMouseUtils.getKeyName(NEIMouseUtils.MOUSE_BTN_LMB),
                        NEIClientUtils.translate("recipe.overlay.overlayRecipe"));
            }

            if (canFillCraftingGrid()) {
                hotkeys.put(
                        NEIClientUtils.getKeyName(NEIClientUtils.SHIFT_HASH, NEIMouseUtils.MOUSE_BTN_LMB),
                        NEIClientUtils.translate("recipe.overlay.fillCraftingGrid"));
                hotkeys.put(
                        NEIClientUtils.getKeyName(
                                NEIClientUtils.SHIFT_HASH + NEIClientUtils.CTRL_HASH,
                                NEIMouseUtils.MOUSE_BTN_LMB),
                        NEIClientUtils.translate("recipe.overlay.fillCraftingGridOnce"));
            }

        } else if (canFillCraftingGrid()) {
            hotkeys.put(
                    NEIMouseUtils.getKeyName(NEIMouseUtils.MOUSE_BTN_LMB),
                    NEIClientUtils.translate("recipe.overlay.fillCraftingGrid"));
            hotkeys.put(
                    NEIClientUtils.getKeyName(NEIClientUtils.CTRL_HASH, NEIMouseUtils.MOUSE_BTN_LMB),
                    NEIClientUtils.translate("recipe.overlay.fillCraftingGridOnce"));
        }

        hotkeys.put(
                KeyManager.getKeyName("bookmark.add", NEIClientUtils.SHIFT_HASH),
                NEIClientUtils.translate("recipe.overlay.bookmarkRecipe"));

        hotkeys.put(
                KeyManager.getKeyName("bookmark.add", NEIClientUtils.SHIFT_HASH + NEIClientUtils.CTRL_HASH),
                NEIClientUtils.translate("recipe.overlay.bookmarkRecipeAndCount"));

        hotkeys.put(
                KeyManager.getKeyName("bookmark.chat_link", NEIClientUtils.SHIFT_HASH + NEIClientUtils.CTRL_HASH),
                NEIClientUtils.translate("recipe.overlay.sendRecipeToChat"));

        return hotkeys;
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY) {
        if (this.firstGui != null) {
            this.firstGui.mc.displayGuiScreen(this.firstGui);
            overlayRecipe(NEIClientUtils.shiftKey());
        }
    }

    @Override
    protected Drawable getIcon(boolean mouseOver) {
        return isFillMode() ? ICON_FILL : ICON_OVERLAY;
    }

    @Override
    protected ButtonSkin getSkin() {
        if (isFillMode() && this.missedMaterialsTooltipLineHandler != null) {
            return UIWidgetTexture.BUTTON_OVERLAY_ERROR;
        }

        return super.getSkin();
    }

    private boolean isFillMode() {
        return this.canFillCraftingGrid && (!requireShiftForOverlayRecipe() || NEIClientUtils.shiftKey());
    }

    @Override
    public void drawItemOverlay() {
        final int presenceOverlay = NEIClientConfig.itemPresenceOverlay();

        if (presenceOverlay > 0) {
            final boolean highlightPresentItem = NEIClientConfig.isSlotHighlightPresent();
            final ItemOverlayFormat format = ItemOverlayFormat.from(presenceOverlay);

            for (ItemOverlayState overlay : ingredientsOverlay()) {
                if (highlightPresentItem || !overlay.isPresent()) {
                    overlay.draw(format);
                }
            }
        }
    }

    protected List<ItemOverlayState> ingredientsOverlay() {
        final List<PositionedStack> ingredients = this.handlerRef.handler
                .getIngredientStacks(this.handlerRef.recipeIndex);

        if (this.itemPresenceCache.size() != ingredients.size()) {
            this.itemPresenceCache.clear();

            if (this.firstGui != null) {
                this.itemPresenceCache.addAll(this.handlerRef.getPresenceOverlay(this.firstGui));

                if (this.itemPresenceCache.isEmpty()) {
                    this.itemPresenceCache.addAll(presenceOverlay(ingredients));
                }
            }

            final List<ItemStack> items = this.itemPresenceCache.stream().filter(state -> !state.isPresent())
                    .map(state -> state.getSlot().item).collect(Collectors.toList());

            if (!items.isEmpty()) {
                this.missedMaterialsTooltipLineHandler = ItemsTooltipLineHandler
                        .of(NEIClientUtils.translate("recipe.overlay.missing"), items, Integer.MAX_VALUE, 0);
            } else {
                this.missedMaterialsTooltipLineHandler = null;
            }

            updateEnabled();
        }

        return this.itemPresenceCache;
    }

    @Override
    public void onPermutationsChanged() {
        this.itemPresenceCache.clear();
    }

    public void setRequireShiftForOverlayRecipe(boolean require) {
        this.requireShiftForOverlayRecipe = require;
        updateEnabled();
    }

    private void updateEnabled() {
        this.enabled = hasOverlay() || this.canFillCraftingGrid
                && (this.requireShiftForOverlayRecipe || this.missedMaterialsTooltipLineHandler == null);
    }

    public void setCanUseOverlayRenderer(boolean use) {
        this.canUseOverlayRenderer = use;
    }

    public boolean useOverlayRenderer() {
        return this.canUseOverlayRenderer;
    }

    public boolean requireShiftForOverlayRecipe() {
        return this.requireShiftForOverlayRecipe;
    }

    public boolean canFillCraftingGrid() {
        return this.canFillCraftingGrid && this.missedMaterialsTooltipLineHandler == null;
    }

    public boolean hasOverlay() {
        return this.hasOverlay;
    }

    public Recipe getRecipe() {
        return Recipe.of(this.handlerRef);
    }

    protected List<ItemOverlayState> presenceOverlay(List<PositionedStack> ingredients) {
        return NEIClientUtils.presenceOverlay(
                ingredients,
                this.firstGui.inventorySlots.inventorySlots.stream()
                        .filter(
                                s -> s != null && s.getStack() != null
                                        && s.getStack().stackSize > 0
                                        && s.isItemValid(s.getStack())
                                        && s.canTakeStack(this.firstGui.mc.thePlayer))
                        .map(s -> s.getStack().copy()).collect(Collectors.toList()));
    }

    public void overlayRecipe(boolean shift) {

        if (!requireShiftForOverlayRecipe() || shift) {
            if (this.canFillCraftingGrid) {
                this.handlerRef.fillCraftingGrid(this.firstGui, NEIClientUtils.controlKey() ? 1 : 0);
            }
        } else if (hasOverlay() && useOverlayRenderer()) {
            this.handlerRef.useOverlayRenderer(this.firstGui);
        }

    }

    @Override
    public void lastKeyTyped(char keyChar, int keyID) {}

    @Override
    public boolean onHotkeyPressed() {
        if (KeyManager.isKeyDown("bookmark.add") && NEIClientUtils.shiftKey()) {
            saveRecipeInBookmark();
            return true;
        }

        if (KeyManager.isKeyDown("bookmark.chat_link") && NEIClientUtils.controlKey() && NEIClientUtils.shiftKey()) {
            sendRecipeChatLink();
            return true;
        }

        return false;
    }

    public void saveRecipeInBookmark() {
        final Recipe recipe = getRecipe();

        if (!ItemPanels.bookmarkPanel.removeRecipe(recipe.getRecipeId(), BookmarkGrid.DEFAULT_GROUP_ID)) {
            ItemPanels.bookmarkPanel.addRecipe(
                    recipe,
                    NEIClientUtils.controlKey()
                            || NEIClientConfig.getBooleanSetting("inventory.bookmarks.bookmarkRecipeWithCount") ? 1 : 0,
                    BookmarkGrid.DEFAULT_GROUP_ID);
        }
    }

    private void sendRecipeChatLink() {
        NEIClientUtils.sendChatItemLink(BookmarkPayload.of(getRecipe()).toNBT());
    }
}
