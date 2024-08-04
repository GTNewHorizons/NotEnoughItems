package codechicken.nei.recipe;

import static codechicken.nei.NEIClientUtils.translate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.event.GuiScreenEvent;

import codechicken.lib.gui.GuiDraw;
import codechicken.lib.vec.Rectangle4i;
import codechicken.nei.BookmarkPanel.BookmarkRecipe;
import codechicken.nei.GuiNEIButton;
import codechicken.nei.ItemPanels;
import codechicken.nei.ItemsTooltipLineHandler;
import codechicken.nei.LayoutManager;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.PositionedStack;
import codechicken.nei.api.IOverlayHandler;
import cpw.mods.fml.common.eventhandler.Cancelable;

public class GuiOverlayButton extends GuiNEIButton {

    public static class UpdateOverlayButtonsEvent extends GuiScreenEvent {

        public List<GuiOverlayButton> buttonList;

        public UpdateOverlayButtonsEvent(GuiRecipe<?> gui, List<GuiOverlayButton> buttonList) {
            super(gui);
            this.buttonList = new ArrayList<>(buttonList);
        }

        @Cancelable
        public static class Pre extends UpdateOverlayButtonsEvent {

            public int xOffset;
            public int yOffset;
            public int height;
            public HandlerInfo handlerInfo;

            public Pre(GuiRecipe<?> gui, int xOffset, int yOffset, int height, HandlerInfo handlerInfo) {
                super(gui, new ArrayList<>());
                this.xOffset = xOffset;
                this.yOffset = yOffset;
                this.height = height;
                this.handlerInfo = handlerInfo;
            }
        }

        public static class Post extends UpdateOverlayButtonsEvent {

            public Post(GuiRecipe<?> gui, List<GuiOverlayButton> buttonList) {
                super(gui, buttonList);
            }
        }
    }

    private final static int OVERLAY_BUTTON_ID_START = 4;

    public final GuiContainer firstGui;
    public final IRecipeHandler handler;
    public final int recipeIndex;

    protected ItemsTooltipLineHandler missedMaterialsTooltipLineHandler;

    protected final IOverlayHandler overlayHandler;
    protected final List<Boolean> itemPresenceCache = new ArrayList<>();
    protected boolean requireShiftForOverlayRecipe = true;
    protected boolean useOverlayRenderer = false;
    protected boolean hasOverlay = false;

    public GuiOverlayButton(GuiContainer firstGui, IRecipeHandler handler, int recipeIndex, int x, int y, int width,
            int height) {
        super(OVERLAY_BUTTON_ID_START + recipeIndex, x, y, width, height, "+");
        this.firstGui = firstGui != null && firstGui.inventorySlots != null ? firstGui : null;
        this.handler = handler;
        this.recipeIndex = recipeIndex;
        this.hasOverlay = this.firstGui != null
                && handler.hasOverlay(this.firstGui, this.firstGui.inventorySlots, recipeIndex);
        this.useOverlayRenderer = this.hasOverlay && handler.getOverlayRenderer(firstGui, recipeIndex) != null;
        this.overlayHandler = this.hasOverlay ? handler.getOverlayHandler(firstGui, recipeIndex) : null;

        setRequireShiftForOverlayRecipe(NEIClientConfig.requireShiftForOverlayRecipe());
        ingredientsOverlay();
    }

    public List<String> handleTooltip(GuiRecipe<?> gui, List<String> currenttip) {
        currenttip.add(translate("recipe.overlay"));

        if (!this.enabled && this.requireShiftForOverlayRecipe && this.overlayHandler == null) {
            currenttip.set(currenttip.size() - 1, currenttip.get(currenttip.size() - 1) + GuiDraw.TOOLTIP_LINESPACE);
            currenttip.add(EnumChatFormatting.RED + translate("recipe.overlay.mismatch") + EnumChatFormatting.RESET);
        } else if (this.missedMaterialsTooltipLineHandler != null) {

            if ((!this.requireShiftForOverlayRecipe || NEIClientUtils.shiftKey()) && this.overlayHandler != null) {
                this.missedMaterialsTooltipLineHandler.setLabelColor(EnumChatFormatting.RED);
            } else {
                this.missedMaterialsTooltipLineHandler.setLabelColor(EnumChatFormatting.GRAY);
            }

            currenttip.add(GuiDraw.TOOLTIP_HANDLER + GuiDraw.getTipLineId(this.missedMaterialsTooltipLineHandler));
        }

        return currenttip;
    }

    public Map<String, String> handleHotkeys(GuiContainer gui, int mousex, int mousey, Map<String, String> hotkeys) {

        if (this.requireShiftForOverlayRecipe) {
            hotkeys.put(translate("recipe.overlay.fillCraftingGrid.key"), translate("recipe.overlay.fillCraftingGrid"));
        }

        String keyName = NEIClientConfig.getKeyName(NEIClientConfig.getKeyBinding("gui.bookmark"), true);
        if (keyName != null) {
            hotkeys.put(keyName, translate("recipe.overlay.bookmark"));
        }

        keyName = NEIClientConfig.getKeyName(NEIClientConfig.getKeyBinding("gui.bookmark_recipe"), true);
        if (keyName != null) {
            hotkeys.put(keyName, translate("recipe.overlay.bookmarkRecipe"));
        }

        keyName = NEIClientConfig.getKeyName(NEIClientConfig.getKeyBinding("gui.bookmark_count"), true);
        if (keyName != null) {
            hotkeys.put(keyName, translate("recipe.overlay.bookmarkAmount"));
        }

        keyName = NEIClientConfig.getKeyName(NEIClientConfig.getKeyBinding("gui.bookmark_recipe_count"), true);
        if (keyName != null) {
            hotkeys.put(keyName, translate("recipe.overlay.bookmarkRecipeAndAmount"));
        }

        return hotkeys;
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY) {
        if (this.firstGui != null) {
            this.firstGui.mc.displayGuiScreen(this.firstGui);
            overlayRecipe(NEIClientUtils.shiftKey());
        }
    }

    public void drawItemPresenceOverlay() {
        final int presenceOverlay = NEIClientConfig.itemPresenceOverlay();
        final boolean highlightPresentItem = NEIClientConfig.isSlotHighlightPresent();
        final List<PositionedStack> ingredients = this.handler.getIngredientStacks(recipeIndex);
        final List<Boolean> itemPresenceCache = ingredientsOverlay();

        for (int j = 0; j < ingredients.size(); j++) {
            PositionedStack stack = ingredients.get(j);
            Boolean isPresent = j < itemPresenceCache.size() ? itemPresenceCache.get(j) : null;

            if (isPresent != null && (highlightPresentItem || !isPresent)) {
                LayoutManager.drawItemPresenceOverlay(stack.relx, stack.rely, isPresent, presenceOverlay == 2);
            }
        }
    }

    protected List<Boolean> ingredientsOverlay() {
        List<PositionedStack> ingredients = this.handler.getIngredientStacks(recipeIndex);

        if (this.itemPresenceCache.size() != ingredients.size()) {
            this.itemPresenceCache.clear();

            if (this.overlayHandler != null) {
                this.itemPresenceCache
                        .addAll(this.overlayHandler.presenceOverlay(this.firstGui, this.handler, this.recipeIndex));
            } else if (this.firstGui != null) {
                this.itemPresenceCache.addAll(presenceOverlay(ingredients));
            }

            List<ItemStack> items = new ArrayList<>();

            for (int j = 0; j < ingredients.size(); j++) {
                if (j >= this.itemPresenceCache.size() || !this.itemPresenceCache.get(j)) {
                    items.add(ingredients.get(j).item);
                }
            }

            if (!items.isEmpty()) {
                this.missedMaterialsTooltipLineHandler = new ItemsTooltipLineHandler(
                        translate("recipe.overlay.missing"),
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
        this.enabled = this.hasOverlay && (this.requireShiftForOverlayRecipe || this.overlayHandler != null);
    }

    public void setUseOverlayRenderer(boolean use) {
        this.useOverlayRenderer = use;
    }

    public boolean useOverlayRenderer() {
        return this.useOverlayRenderer;
    }

    public boolean canFillCraftingGrid() {
        return this.missedMaterialsTooltipLineHandler == null;
    }

    public boolean hasOverlay() {
        return this.hasOverlay;
    }

    private List<Boolean> presenceOverlay(List<PositionedStack> ingredients) {
        final List<Boolean> itemPresenceSlots = new ArrayList<>();
        @SuppressWarnings("unchecked")
        final List<ItemStack> invStacks = ((List<Slot>) this.firstGui.inventorySlots.inventorySlots).stream()
                .filter(
                        s -> s != null && s.getStack() != null
                                && s.getStack().stackSize > 0
                                && s.isItemValid(s.getStack())
                                && s.canTakeStack(this.firstGui.mc.thePlayer))
                .map(s -> s.getStack().copy()).collect(Collectors.toCollection(ArrayList::new));

        for (PositionedStack stack : ingredients) {
            Optional<ItemStack> used = invStacks.stream().filter(is -> is.stackSize > 0 && stack.contains(is))
                    .findFirst();

            itemPresenceSlots.add(used.isPresent());

            if (used.isPresent()) {
                ItemStack is = used.get();
                is.stackSize -= 1;
            }
        }

        return itemPresenceSlots;
    }

    public void overlayRecipe(boolean shift) {
        if (!this.hasOverlay) return;

        if (!this.useOverlayRenderer || !this.requireShiftForOverlayRecipe || shift) {
            if (this.overlayHandler != null) {
                this.overlayHandler.overlayRecipe(
                        this.firstGui,
                        this.handler,
                        this.recipeIndex,
                        !this.requireShiftForOverlayRecipe || shift);
            }
        } else {
            LayoutManager.overlayRenderer = this.handler.getOverlayRenderer(this.firstGui, this.recipeIndex);
        }

    }

    public void lastKeyTyped(GuiRecipe<?> gui, char keyChar, int keyID) {

        if (NEIClientConfig.isKeyHashDown("gui.bookmark")) {
            saveRecipeInBookmark(false, false);
        } else if (NEIClientConfig.isKeyHashDown("gui.bookmark_recipe")) {
            saveRecipeInBookmark(true, false);
        } else if (NEIClientConfig.isKeyHashDown("gui.bookmark_count")) {
            saveRecipeInBookmark(false, true);
        } else if (NEIClientConfig.isKeyHashDown("gui.bookmark_recipe_count")) {
            saveRecipeInBookmark(true, true);
        }

    }

    public void saveRecipeInBookmark(boolean saveIngredients, boolean saveStackSize) {
        final HandlerInfo handlerInfo = GuiRecipeTab.getHandlerInfo(this.handler);
        final List<PositionedStack> ingredients = this.handler.getIngredientStacks(this.recipeIndex);
        final BookmarkRecipeId recipeId = new BookmarkRecipeId(handlerInfo.getHandlerName(), ingredients);

        if (!ItemPanels.bookmarkPanel.removeBookmarkRecipeId(recipeId)) {
            BookmarkRecipe recipe = new BookmarkRecipe();
            recipe.handlerName = recipeId.handlerName;
            recipe.recipeId = recipeId;

            if (saveIngredients) {
                for (PositionedStack stack : ingredients) {
                    recipe.ingredients.add(stack.item);
                }
            }

            PositionedStack result = this.handler.getResultStack(this.recipeIndex);

            if (result != null) {
                recipe.result.add(result.item);
            } else {
                for (PositionedStack stack : this.handler.getOtherStacks(this.recipeIndex)) {
                    recipe.result.add(stack.item);
                }
            }

            ItemPanels.bookmarkPanel.addRecipe(recipe, saveStackSize);
        }
    }

    public int getResultStackSize(ItemStack stackover) {
        final List<PositionedStack> stacks = this.handler.getOtherStacks(this.recipeIndex);
        int stackSize = stackover.stackSize;

        for (PositionedStack pStack : stacks) {
            if (StackInfo.equalItemAndNBT(pStack.item, stackover, true)) {
                stackSize += pStack.item.stackSize;
            }
        }

        return stackSize;
    }

    public Rectangle4i bounds() {
        return new Rectangle4i(this.xPosition, this.yPosition, this.width, this.height);
    }

    public boolean contains(int mx, int my) {
        return bounds().contains(mx, my);
    }
}
