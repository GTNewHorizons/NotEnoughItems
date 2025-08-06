package codechicken.nei.recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.opengl.GL11;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.ItemPanels;
import codechicken.nei.ItemsTooltipLineHandler;
import codechicken.nei.LayoutManager;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.PositionedStack;
import codechicken.nei.bookmark.BookmarkGrid;
import codechicken.nei.drawable.DrawableBuilder;
import codechicken.nei.drawable.DrawableResource;
import codechicken.nei.util.NEIMouseUtils;

public class GuiOverlayButton extends GuiRecipeButton {

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
            LayoutManager.drawItemPresenceOverlay(
                    this.slot.relx,
                    this.slot.rely,
                    this.isPresent,
                    format == ItemOverlayFormat.BACKGROUND);
        }
    }

    protected static final DrawableResource ICON_FILL = new DrawableBuilder(
            "nei:textures/nei_sprites.png",
            28,
            76,
            9,
            10).build();
    protected static final DrawableResource ICON_FILL_ERROR = new DrawableBuilder(
            "nei:textures/nei_sprites.png",
            37,
            76,
            9,
            10).build();
    protected static final DrawableResource ICON_OVERLAY = new DrawableBuilder(
            "nei:textures/nei_sprites.png",
            46,
            76,
            9,
            10).build();
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

        this.canUseOverlayRenderer = this.firstGui != null && handlerRef.getRecipeOverlayRenderer(firstGui) != null;
        this.canFillCraftingGrid = this.firstGui != null && handlerRef.getOverlayHandler(firstGui) != null;
        this.hasOverlay = this.canUseOverlayRenderer || this.canFillCraftingGrid;

        setRequireShiftForOverlayRecipe(NEIClientConfig.requireShiftForOverlayRecipe());
        ingredientsOverlay();
    }

    @Override
    public List<String> handleTooltip(GuiRecipe<?> gui, List<String> currenttip) {
        currenttip.add(NEIClientUtils.translate("recipe.overlay"));

        if (!this.enabled && this.requireShiftForOverlayRecipe && this.canFillCraftingGrid) {
            currenttip.set(currenttip.size() - 1, currenttip.get(currenttip.size() - 1) + GuiDraw.TOOLTIP_LINESPACE);
            currenttip.add(
                    EnumChatFormatting.RED + NEIClientUtils.translate("recipe.overlay.mismatch")
                            + EnumChatFormatting.RESET);
        } else if (this.missedMaterialsTooltipLineHandler != null) {

            if ((!this.requireShiftForOverlayRecipe || NEIClientUtils.shiftKey()) && this.canFillCraftingGrid) {
                this.missedMaterialsTooltipLineHandler.setLabelColor(EnumChatFormatting.RED);
            } else {
                this.missedMaterialsTooltipLineHandler.setLabelColor(EnumChatFormatting.GRAY);
            }

            currenttip.add(GuiDraw.TOOLTIP_HANDLER + GuiDraw.getTipLineId(this.missedMaterialsTooltipLineHandler));
        }

        return currenttip;
    }

    @Override
    public Map<String, String> handleHotkeys(GuiContainer gui, int mousex, int mousey, Map<String, String> hotkeys) {

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
            }

        } else if (canFillCraftingGrid()) {
            hotkeys.put(
                    NEIMouseUtils.getKeyName(NEIMouseUtils.MOUSE_BTN_LMB),
                    NEIClientUtils.translate("recipe.overlay.fillCraftingGrid"));
        }

        hotkeys.put(
                NEIClientConfig.getKeyName("gui.bookmark", NEIClientUtils.SHIFT_HASH),
                NEIClientUtils.translate("recipe.overlay.bookmarkRecipe"));

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
    protected void drawContent(Minecraft minecraft, int y, int x, boolean mouseOver) {
        DrawableResource icon = ICON_OVERLAY;

        if (this.hasOverlay && (!this.requireShiftForOverlayRecipe || NEIClientUtils.shiftKey())) {
            icon = this.missedMaterialsTooltipLineHandler == null ? ICON_FILL : ICON_FILL_ERROR;
        }

        final int iconX = this.xPosition + (this.width - icon.width - 1) / 2;
        final int iconY = this.yPosition + (this.height - icon.height) / 2;

        GL11.glColor4f(1, 1, 1, this.enabled ? 1 : 0.5f);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        icon.draw(iconX, iconY);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(1, 1, 1, 1);
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
        List<PositionedStack> ingredients = this.handlerRef.handler.getIngredientStacks(this.handlerRef.recipeIndex);

        if (this.itemPresenceCache.size() != ingredients.size()) {
            this.itemPresenceCache.clear();

            if (this.canFillCraftingGrid) {
                this.itemPresenceCache.addAll(this.handlerRef.getPresenceOverlay(this.firstGui));
            } else if (this.firstGui != null) {
                this.itemPresenceCache.addAll(presenceOverlay(ingredients));
            }

            List<ItemStack> items = this.itemPresenceCache.stream().filter(state -> !state.isPresent())
                    .map(state -> state.getSlot().item).collect(Collectors.toList());

            if (!items.isEmpty()) {
                this.missedMaterialsTooltipLineHandler = new ItemsTooltipLineHandler(
                        NEIClientUtils.translate("recipe.overlay.missing"),
                        items,
                        true,
                        Integer.MAX_VALUE);
            } else {
                this.missedMaterialsTooltipLineHandler = null;
            }
        }

        return this.itemPresenceCache;
    }

    public void setRequireShiftForOverlayRecipe(boolean require) {
        this.requireShiftForOverlayRecipe = require;
        this.enabled = this.hasOverlay && (this.requireShiftForOverlayRecipe || this.canFillCraftingGrid);
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
        return this.missedMaterialsTooltipLineHandler == null || this.handlerRef.canFillCraftingGrid(this.firstGui);
    }

    public boolean hasOverlay() {
        return this.hasOverlay;
    }

    public Recipe getRecipe() {
        return Recipe.of(this.handlerRef);
    }

    private List<ItemOverlayState> presenceOverlay(List<PositionedStack> ingredients) {
        final List<ItemOverlayState> itemPresenceSlots = new ArrayList<>();
        final List<ItemStack> invStacks = this.firstGui.inventorySlots.inventorySlots.stream()
                .filter(
                        s -> s != null && s.getStack() != null
                                && s.getStack().stackSize > 0
                                && s.isItemValid(s.getStack())
                                && s.canTakeStack(this.firstGui.mc.thePlayer))
                .map(s -> s.getStack().copy()).collect(Collectors.toCollection(ArrayList::new));

        for (PositionedStack stack : ingredients) {
            Optional<ItemStack> used = invStacks.stream().filter(is -> is.stackSize > 0 && stack.contains(is))
                    .findAny();

            itemPresenceSlots.add(new ItemOverlayState(stack, used.isPresent()));

            if (used.isPresent()) {
                ItemStack is = used.get();
                is.stackSize -= 1;
            }
        }

        return itemPresenceSlots;
    }

    public void overlayRecipe(boolean shift) {
        if (!this.hasOverlay) return;

        if (!this.requireShiftForOverlayRecipe || shift) {
            if (canFillCraftingGrid()) {
                this.handlerRef.fillCraftingGrid(this.firstGui, 0);
            }
        } else if (useOverlayRenderer()) {
            this.handlerRef.useOverlayRenderer(this.firstGui);
        }

    }

    @Override
    public void lastKeyTyped(GuiRecipe<?> gui, char keyChar, int keyID) {

        if (NEIClientConfig.isKeyHashDown("gui.bookmark") && NEIClientUtils.shiftKey()) {
            saveRecipeInBookmark();
        }
    }

    public void saveRecipeInBookmark() {
        final Recipe recipe = getRecipe();

        if (!ItemPanels.bookmarkPanel.removeRecipe(recipe.getRecipeId(), BookmarkGrid.DEFAULT_GROUP_ID)) {
            ItemPanels.bookmarkPanel.addRecipe(recipe, BookmarkGrid.DEFAULT_GROUP_ID);
        }
    }

}
