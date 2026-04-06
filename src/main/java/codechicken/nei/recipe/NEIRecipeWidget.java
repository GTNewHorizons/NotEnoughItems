package codechicken.nei.recipe;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;
import java.util.stream.Stream;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import codechicken.lib.gui.GuiDraw;
import codechicken.lib.vec.Rectangle4i;
import codechicken.nei.FavoriteRecipes;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.NEIServerUtils;
import codechicken.nei.PositionedStack;
import codechicken.nei.Widget;
import codechicken.nei.api.IGuiContainerOverlay;
import codechicken.nei.api.ShortcutInputHandler;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.recipe.GuiRecipeButton.UpdateRecipeButtonsEvent;
import codechicken.nei.recipe.Recipe.RecipeId;
import codechicken.nei.recipe.debug.DebugHandlerWidget;
import codechicken.nei.util.NEIMouseUtils;

public class NEIRecipeWidget extends Widget {

    protected AcceptsFollowingTooltipLineHandler acceptsFollowingTooltipLineHandler;
    protected final Map<PositionedStack, Integer> favoriteIndexes = new WeakHashMap<>();
    protected final Map<PositionedStack, List<ItemStack>> permutations = new WeakHashMap<>();
    protected final Map<PositionedStack, List<Badge>> badgeCache = new WeakHashMap<>();
    protected int favoriteRevision = -1;
    protected boolean update = true;
    protected int cycleticks = 0;
    protected int lastcycle = -1;

    protected final RecipeHandlerRef handlerRef;
    protected final HandlerInfo handlerInfo;

    protected boolean showAsWidget = false;
    protected List<GuiRecipeButton> recipeButtons = null;

    public NEIRecipeWidget(RecipeHandlerRef handlerRef) {
        this.handlerRef = handlerRef;
        this.handlerInfo = GuiRecipeTab.getHandlerInfo(this.handlerRef.handler);
        update();
    }

    public void setLocation(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void update() {

        if (this.showAsWidget || !this.handlerInfo.getUseCustomScroll()) {
            final int recipeHeight = this.handlerRef.handler.getRecipeHeight(this.handlerRef.recipeIndex);
            this.w = Math.max(166, this.handlerInfo.getWidth());
            this.h = recipeHeight > 0 ? recipeHeight : this.handlerInfo.getHeight();
            this.h += this.handlerInfo.getYShift();
        }

        this.update = true;
    }

    public List<GuiRecipeButton> getRecipeButtons() {

        if (this.recipeButtons == null) {
            if (!getOutputs().isEmpty()) {
                this.recipeButtons = initButtons();
            } else {
                this.recipeButtons = Collections.emptyList();
            }
        }

        return Collections.unmodifiableList(this.recipeButtons);
    }

    protected List<GuiRecipeButton> getRecipeButtonsIfInit() {

        if (this.recipeButtons == null) {
            return Collections.emptyList();
        }

        return Collections.unmodifiableList(this.recipeButtons);
    }

    public RecipeHandlerRef getRecipeHandlerRef() {
        return this.handlerRef;
    }

    public Recipe getRecipe() {
        return Recipe.of(this.handlerRef);
    }

    public HandlerInfo getHandlerInfo() {
        return this.handlerInfo;
    }

    public void showAsWidget(boolean show) {
        this.showAsWidget = show;
    }

    protected List<GuiRecipeButton> initButtons() {
        final GuiRecipe<?> guiRecipe = getGuiRecipe();

        if (guiRecipe != null) {
            final UpdateRecipeButtonsEvent.Pre preEvent = new UpdateRecipeButtonsEvent.Pre(
                    guiRecipe,
                    this,
                    this.handlerInfo);

            if (MinecraftForge.EVENT_BUS.post(preEvent)) {
                return preEvent.buttonList;
            } else {
                final UpdateRecipeButtonsEvent.Post postEvent = new UpdateRecipeButtonsEvent.Post(
                        guiRecipe,
                        this,
                        getDefaultButtons());
                MinecraftForge.EVENT_BUS.post(postEvent);
                return postEvent.buttonList;
            }

        } else {
            return getDefaultButtons();
        }

    }

    protected List<GuiRecipeButton> getDefaultButtons() {
        GuiContainer guiContainer = NEIClientUtils.getGuiContainer();
        final List<GuiRecipeButton> buttons = new ArrayList<>();
        final boolean showFavorites = NEIClientConfig.favoritesEnabled() && handlerInfo.getShowFavoritesButton();
        final boolean showOverlay = handlerInfo.getShowOverlayButton();
        final int x = Math.min(168, this.w) - GuiRecipeButton.BUTTON_WIDTH;
        int y = this.h - GuiRecipeButton.BUTTON_HEIGHT - 6;

        if (guiContainer instanceof IGuiContainerOverlay overlay) {
            guiContainer = overlay.getFirstScreen();
        }

        if (showOverlay) {
            buttons.add(new GuiOverlayButton(guiContainer, this.handlerRef, x, y));
            y -= GuiRecipeButton.BUTTON_HEIGHT + 1;
        }

        if (showFavorites) {
            buttons.add(new GuiFavoriteButton(handlerRef, x, y));
        }

        return buttons;
    }

    @Override
    public void draw(int mouseX, int mouseY) {
        final int yShift = this.handlerInfo.getYShift();

        if (this.update) {
            this.update = false;

            if (!NEIClientUtils.shiftKey() && ((this.cycleticks++) / 20) != this.lastcycle || this.lastcycle == -1) {
                this.lastcycle = this.cycleticks / 20;
                updatePermutations();
            }

            for (GuiRecipeButton button : getRecipeButtons()) {
                button.update();
            }
        }

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_LIGHTING_BIT);
        GL11.glTranslatef(this.x, this.y + yShift, 0);

        this.handlerRef.handler.drawBackground(this.handlerRef.recipeIndex);

        RenderHelper.enableGUIStandardItemLighting();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glColor4f(1, 1, 1, 1);

        GuiContainerManager.enableMatrixStackLogging();

        for (PositionedStack pStack : getInputs()) {

            if (!this.permutations.containsKey(pStack)) {
                updatePermutationsFor(pStack);
            }

            drawItem(pStack, mouseX, mouseY, yShift, true);
        }

        for (PositionedStack pStack : getCatalysts()) {

            if (!this.permutations.containsKey(pStack)) {
                updatePermutationsFor(pStack);
            }

            drawItem(pStack, mouseX, mouseY, yShift, true);
        }

        for (PositionedStack pStack : getOutputs()) {
            drawItem(pStack, mouseX, mouseY, yShift, false);
        }

        GuiContainerManager.disableMatrixStackLogging();

        this.handlerRef.handler.drawForeground(this.handlerRef.recipeIndex);

        final GuiRecipeButton overlayButton = forEachButtons(
                button -> (this.showAsWidget ? button instanceof GuiOverlayButton : button.contains(mouseX, mouseY))
                        ? button
                        : null,
                (GuiRecipeButton) null);

        if (overlayButton != null) {
            NEIClientUtils.gl2DRenderContext(() -> overlayButton.drawItemOverlay());
        }

        GL11.glTranslatef(-this.x, -this.y - yShift, 0);
        GL11.glPopAttrib();

        if (!this.showAsWidget) {
            final Minecraft mc = NEIClientUtils.mc();
            for (GuiRecipeButton button : getRecipeButtons()) {
                button.xPosition += this.x;
                button.yPosition += this.y;
                button.drawButton(mc, mouseX, mouseY);
                button.xPosition -= this.x;
                button.yPosition -= this.y;
            }
        }

        DebugHandlerWidget.instance.drawGuiPlaceholder(this);
    }

    protected void drawItem(PositionedStack pStack, int mouseX, int mouseY, int yShift, boolean input) {
        GuiContainerManager.drawItem(pStack.relx, pStack.rely, pStack.item);

        if (this.handlerInfo.getShowBadge()) {
            drawBadge(pStack, input);
        }

        if (pStack.contains(mouseX - this.x, mouseY - this.y - yShift)) {
            NEIClientUtils.gl2DRenderContext(() -> GuiDraw.drawRect(pStack.relx, pStack.rely, 16, 16, 0x80FFFFFF));
        }
    }

    protected List<Badge> getBadges(PositionedStack pStack, boolean input) {

        return this.badgeCache.computeIfAbsent(pStack, k -> {
            final List<Badge> badges = k.getBadges();

            if (badges == null) {
                final String customBadge = k.getCustomBadge();

                if (customBadge != null) {
                    return customBadge.isEmpty() ? Collections.emptyList()
                            : Collections.singletonList(new Badge(customBadge));
                }

                if (input) {
                    if (StackInfo.getAmount(pStack.item) == 0) {
                        return Collections.singletonList(Badge.notConsumed());
                    } else if (pStack.getChance() == 0) {
                        return Collections.singletonList(Badge.notConsumedParallel());
                    } else if (pStack.getChance() != PositionedStack.CHANCE_FULL) {
                        return Collections.singletonList(
                                Badge.consumeChance(pStack.getChance() / (float) PositionedStack.CHANCE_FULL));
                    }
                } else if (pStack.getChance() != PositionedStack.CHANCE_FULL) {
                    return Collections.singletonList(
                            Badge.outputChance(pStack.getChance() / (float) PositionedStack.CHANCE_FULL));
                }

                return Collections.emptyList();
            }

            return badges;
        });

    }

    protected void drawBadge(PositionedStack pStack, boolean input) {
        final List<Badge> badges = getBadges(pStack, input);

        for (Badge badge : badges) {
            badge.draw(new Rectangle4i(pStack.relx, pStack.rely, 16, 16));
        }
    }

    @Override
    public boolean handleKeyPress(int keyID, char keyChar) {

        if (NEIClientConfig.favoritesEnabled() && NEIClientUtils.shiftKey()
                && NEIClientConfig.isKeyHashDown("gui.favorite_item")) {
            final Point mouse = GuiDraw.getMousePosition();
            final int yShift = this.handlerInfo.getYShift();
            PositionedStack pStackOver = null;

            for (PositionedStack pStack : getInputs()) {
                if (pStack.contains(mouse.x - this.x, mouse.y - this.y - yShift)) {
                    pStackOver = pStack;
                    break;
                }
            }

            if (pStackOver != null) {

                if (FavoriteRecipes.containsManual(pStackOver.item)) {
                    FavoriteRecipes.setFavorite(pStackOver.item, null);
                } else {
                    FavoriteRecipes.setFavorite(
                            pStackOver.item,
                            RecipeId.of(
                                    pStackOver.item,
                                    FavoriteRecipes.FAVORITE_ITEM,
                                    Arrays.asList(new ItemStack(Blocks.fire, 1), pStackOver.item)));
                }

                return true;
            }

        }

        return false;
    }

    @Override
    public void lastKeyTyped(int keyID, char keyChar) {
        final Point mouse = GuiDraw.getMousePosition();

        forEachButtons(button -> {

            if (button.contains(mouse.x, mouse.y)) {
                button.lastKeyTyped(keyChar, keyID);
            }

            return null;
        }, null);

    }

    @Override
    public boolean handleClick(int mouseX, int mouseY, int button) {

        if (ShortcutInputHandler.handleMouseClick(getStackMouseOver(mouseX, mouseY))) {
            return true;
        }

        if (button == 0) {
            final Minecraft mc = NEIClientUtils.mc();

            if (forEachButtons(guibutton -> {

                if (guibutton.mousePressed(mc, mouseX, mouseY)) {
                    NEIClientUtils.playClickSound();
                    guibutton.mouseReleased(mouseX, mouseY);
                    return true;
                }

                return null;
            }, false)) {
                return true;
            }

        }

        final GuiRecipe<?> guiRecipe = getGuiRecipe();
        if (guiRecipe == null) {
            return false;
        }

        return this.handlerRef.handler.mouseClicked(guiRecipe, button, this.handlerRef.recipeIndex);
    }

    @Override
    public List<String> handleTooltip(int mouseX, int mouseY, List<String> tooltip) {
        final GuiRecipe<?> guiRecipe = getGuiRecipe();

        if (guiRecipe == null) {
            return tooltip;
        }

        final List<String> localTooltip = this.handlerRef.handler
                .handleTooltip(guiRecipe, tooltip, this.handlerRef.recipeIndex);

        return forEachButtons(
                button -> button.contains(mouseX, mouseY) ? button.handleTooltip(localTooltip) : null,
                localTooltip);
    }

    @Override
    public List<String> handleItemTooltip(ItemStack itemstack, int mousex, int mousey, List<String> tooltip) {
        final GuiRecipe<?> guiRecipe = getGuiRecipe();

        if (guiRecipe == null) {
            return tooltip;
        }

        tooltip = this.handlerRef.handler.handleItemTooltip(guiRecipe, itemstack, tooltip, this.handlerRef.recipeIndex);

        if (itemstack != null) {
            final PositionedStack hovered = getPositionedStackMouseOver(mousex, mousey);

            if (hovered != null && this.handlerInfo.getShowBadge()) {
                final List<Badge> badges = getBadges(hovered, getOutputs().indexOf(hovered) == -1);

                for (Badge badge : badges) {
                    if (badge.getTooltip() != null && !badge.getTooltip().isEmpty()) {
                        tooltip.addAll(badge.getTooltip());
                    }
                }
            }

            if (hovered == null || !NEIClientConfig.showCycledIngredientsTooltip()
                    || this.permutations.getOrDefault(hovered, Collections.emptyList()).size() <= 1
                    || getOutputs().indexOf(hovered) != -1) {
                this.acceptsFollowingTooltipLineHandler = null;
            } else if (this.acceptsFollowingTooltipLineHandler == null
                    || this.acceptsFollowingTooltipLineHandler.tooltipGUID != hovered) {
                        this.acceptsFollowingTooltipLineHandler = AcceptsFollowingTooltipLineHandler
                                .of(hovered, this.permutations.get(hovered), hovered.item);
                    }
        } else if (this.acceptsFollowingTooltipLineHandler != null) {
            this.acceptsFollowingTooltipLineHandler = null;
        }

        if (this.acceptsFollowingTooltipLineHandler != null) {
            tooltip.add(GuiDraw.TOOLTIP_HANDLER + GuiDraw.getTipLineId(this.acceptsFollowingTooltipLineHandler));
        }

        return tooltip;
    }

    @Override
    public Map<String, String> handleHotkeys(int mouseX, int mouseY, Map<String, String> hotkeys) {
        final int yShift = this.handlerInfo.getYShift();
        final Map<String, String> buttonHotkeys = forEachButtons(
                button -> button.contains(mouseX, mouseY) ? button.handleHotkeys(mouseX, mouseY, hotkeys) : null,
                hotkeys);

        if (this.acceptsFollowingTooltipLineHandler != null) {
            buttonHotkeys.put(
                    NEIClientUtils.getKeyName(
                            NEIClientUtils.SHIFT_HASH,
                            NEIMouseUtils.MOUSE_BTN_NONE + NEIMouseUtils.MOUSE_SCROLL),
                    NEIClientUtils.translate("recipe.accepts.scroll"));
        }

        if (getInputs().stream().anyMatch(
                pStack -> this.permutations.getOrDefault(pStack, Collections.emptyList()).size() > 1
                        && pStack.contains(mouseX - this.x, mouseY - this.y - yShift))) {
            hotkeys.put(
                    NEIClientConfig.getKeyName("gui.favorite_item", NEIClientUtils.SHIFT_HASH),
                    NEIClientUtils.translate("recipe.favorite.toggle"));
        }

        return buttonHotkeys;
    }

    @Override
    public boolean onMouseWheel(int scroll, int mx, int my) {

        if (forEachButtons(button -> button.contains(mx, my) ? button.mouseScrolled(scroll) : null, false)) {
            return true;
        }

        if (scrollPermutations(scroll, mx, my)) {
            return true;
        }

        final GuiRecipe<?> guiRecipe = getGuiRecipe();

        return guiRecipe != null
                && this.handlerRef.handler.mouseScrolled(guiRecipe, scroll, this.handlerRef.recipeIndex);
    }

    protected boolean scrollPermutations(int scroll, int mx, int my) {
        if (!NEIClientUtils.shiftKey()) return false;
        final PositionedStack overStack = getPositionedStackMouseOver(mx, my);

        if (overStack != null && this.permutations.getOrDefault(overStack, Collections.emptyList()).size() > 1) {
            final List<ItemStack> items = this.permutations.get(overStack);
            final int stackIndex = indexOf(items, overStack.item);
            final ItemStack stack = items.get((items.size() - scroll + stackIndex) % items.size());

            Stream.concat(getInputs().stream(), getCatalysts().stream()).filter(pStack -> pStack.containsWithNBT(stack))
                    .forEach(pStack -> pStack.setPermutationToRender(stack));

            if (this.acceptsFollowingTooltipLineHandler != null) {
                this.acceptsFollowingTooltipLineHandler
                        .setActiveStack(((PositionedStack) this.acceptsFollowingTooltipLineHandler.tooltipGUID).item);
            }

            return true;
        }

        return false;
    }

    protected int indexOf(List<ItemStack> list, ItemStack stack) {
        for (int i = 0; i < list.size(); i++) {
            if (NEIServerUtils.areStacksSameTypeCraftingWithNBT(list.get(i), stack)) {
                return i;
            }
        }
        return -1;
    }

    protected <R> R forEachButtons(Function<GuiRecipeButton, R> callback, R defaultValue) {

        for (GuiRecipeButton button : getRecipeButtonsIfInit()) {
            button.xPosition += this.x;
            button.yPosition += this.y;

            final R result = callback.apply(button);

            button.xPosition -= this.x;
            button.yPosition -= this.y;

            if (result != null) {
                return result;
            }
        }

        return defaultValue;
    }

    public boolean isFocusedRecipe(int mx, int my) {
        final int yShift = this.handlerInfo.getYShift();

        for (PositionedStack pStackOver : getOutputs()) {
            if (pStackOver.contains(mx - this.x, my - this.y - yShift)) {
                return true;
            }
        }

        return false;
    }

    public PositionedStack getPositionedStackMouseOver(int mx, int my) {
        final int yShift = this.handlerInfo.getYShift();

        for (PositionedStack pStack : getInputs()) {
            if (pStack.contains(mx - this.x, my - this.y - yShift)) {
                return pStack;
            }
        }

        for (PositionedStack pStack : getCatalysts()) {
            if (pStack.contains(mx - this.x, my - this.y - yShift)) {
                return pStack;
            }
        }

        for (PositionedStack pStack : getOutputs()) {
            if (pStack.contains(mx - this.x, my - this.y - yShift)) {
                return pStack;
            }
        }

        return null;
    }

    @Override
    public ItemStack getStackMouseOver(int mx, int my) {
        final PositionedStack pStack = getPositionedStackMouseOver(mx, my);
        return pStack != null ? pStack.item : null;
    }

    protected GuiRecipe<?> getGuiRecipe() {
        final GuiContainer guiContainer = NEIClientUtils.getGuiContainer();

        if (guiContainer instanceof GuiRecipe<?>guiRecipe) {
            return guiRecipe;
        }

        return null;
    }

    protected GuiContainer getFirstGui() {
        final GuiContainer guiContainer = NEIClientUtils.getGuiContainer();

        if (guiContainer instanceof GuiRecipe<?>guiRecipe) {
            return guiRecipe.firstGui;
        }

        return guiContainer;
    }

    protected void updatePermutations() {
        final int rev = FavoriteRecipes.getRevision();

        if (this.favoriteRevision != rev) {
            this.favoriteRevision = rev;
            this.acceptsFollowingTooltipLineHandler = null;
            this.favoriteIndexes.clear();
            this.permutations.clear();
            this.badgeCache.clear();
        }

        for (PositionedStack pStack : getInputs()) {
            updatePermutationsFor(pStack);
        }

        for (PositionedStack pStack : getCatalysts()) {
            updatePermutationsFor(pStack);
        }

        if (this.acceptsFollowingTooltipLineHandler != null) {
            this.acceptsFollowingTooltipLineHandler
                    .setActiveStack(((PositionedStack) this.acceptsFollowingTooltipLineHandler.tooltipGUID).item);
        }

    }

    private void updatePermutationsFor(PositionedStack pStack) {
        List<ItemStack> perms = this.permutations.get(pStack);
        int favoriteIndex = this.favoriteIndexes.getOrDefault(pStack, -1);
        boolean needsUpdate = false;

        if (perms == null) {
            perms = pStack.getFilteredPermutations();
            favoriteIndex = -1;

            if (perms.size() > 1) {
                for (ItemStack item : perms) {
                    if (!FavoriteRecipes.containsManual(item)) break;
                    favoriteIndex++;
                }
            }

            this.permutations.put(pStack, perms);
            this.favoriteIndexes.put(pStack, favoriteIndex);
            needsUpdate = true;
        }

        if (perms.size() > 1 || needsUpdate) {
            final int size = favoriteIndex != -1 ? favoriteIndex + 1 : perms.size();

            if (size > 0) {
                pStack.setPermutationToRender(perms.get(this.lastcycle % size));
            }
        }
    }

    protected List<PositionedStack> getInputs() {
        return this.handlerRef.handler.getIngredientStacks(this.handlerRef.recipeIndex);
    }

    protected List<PositionedStack> getOutputs() {
        final PositionedStack pStackResult = this.handlerRef.handler.getResultStack(this.handlerRef.recipeIndex);
        return pStackResult != null ? Arrays.asList(pStackResult)
                : this.handlerRef.handler.getOtherStacks(this.handlerRef.recipeIndex);
    }

    protected List<PositionedStack> getCatalysts() {
        if (this.handlerRef.handler.getResultStack(this.handlerRef.recipeIndex) == null) {
            return Collections.emptyList();
        }
        return this.handlerRef.handler.getOtherStacks(this.handlerRef.recipeIndex);
    }

}
