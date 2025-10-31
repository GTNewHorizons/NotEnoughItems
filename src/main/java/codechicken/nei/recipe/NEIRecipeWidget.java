package codechicken.nei.recipe;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;
import java.util.stream.Stream;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.FavoriteRecipes;
import codechicken.nei.LayoutManager;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.NEIServerUtils;
import codechicken.nei.PositionedStack;
import codechicken.nei.Widget;
import codechicken.nei.api.IGuiContainerOverlay;
import codechicken.nei.api.ShortcutInputHandler;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.recipe.GuiRecipeButton.UpdateRecipeButtonsEvent;

public class NEIRecipeWidget extends Widget {

    protected final WeakHashMap<PositionedStack, List<ItemStack>> permutations = new WeakHashMap<>();
    protected boolean needUpdate = true;
    protected int cycleticks = 0;
    protected int lastcycle = 0;

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

        if (!this.needUpdate && !NEIClientUtils.shiftKey() && (this.cycleticks++) / 20 != this.lastcycle) {
            this.lastcycle = this.cycleticks / 20;
            this.needUpdate = true;
        }
    }

    public List<GuiRecipeButton> getRecipeButtons() {

        if (this.recipeButtons == null) {
            if (this.handlerRef.handler.getResultStack(this.handlerRef.recipeIndex) != null
                    || !this.handlerRef.handler.getOtherStacks(this.handlerRef.recipeIndex).isEmpty()) {
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
                        getDefatulButtons());
                MinecraftForge.EVENT_BUS.post(postEvent);
                return postEvent.buttonList;
            }

        } else {
            return getDefatulButtons();
        }

    }

    protected List<GuiRecipeButton> getDefatulButtons() {
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

        if (this.needUpdate) {
            this.needUpdate = false;
            updatePermutations();
        }

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_LIGHTING_BIT);
        GL11.glTranslatef(this.x, this.y + yShift, 0);

        this.handlerRef.handler.drawBackground(this.handlerRef.recipeIndex);

        RenderHelper.enableGUIStandardItemLighting();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glColor4f(1, 1, 1, 1);

        GuiContainerManager.enableMatrixStackLogging();

        for (PositionedStack pStack : this.handlerRef.handler.getIngredientStacks(this.handlerRef.recipeIndex)) {
            GuiContainerManager.drawItem(pStack.relx, pStack.rely, pStack.item);

            if (pStack.contains(mouseX - this.x, mouseY - this.y - yShift)) {
                NEIClientUtils.gl2DRenderContext(() -> GuiDraw.drawRect(pStack.relx, pStack.rely, 16, 16, 0x80FFFFFF));
            }
        }

        for (PositionedStack pStack : this.handlerRef.handler.getOtherStacks(this.handlerRef.recipeIndex)) {
            GuiContainerManager.drawItem(pStack.relx, pStack.rely, pStack.item);

            if (pStack.contains(mouseX - this.x, mouseY - this.y - yShift)) {
                NEIClientUtils.gl2DRenderContext(() -> GuiDraw.drawRect(pStack.relx, pStack.rely, 16, 16, 0x80FFFFFF));
            }
        }

        final PositionedStack pStackResult = this.handlerRef.handler.getResultStack(this.handlerRef.recipeIndex);
        if (pStackResult != null) {
            GuiContainerManager.drawItem(pStackResult.relx, pStackResult.rely, pStackResult.item);
            if (pStackResult.contains(mouseX - this.x, mouseY - this.y - yShift)) {
                NEIClientUtils.gl2DRenderContext(
                        () -> GuiDraw.drawRect(pStackResult.relx, pStackResult.rely, 16, 16, 0x80FFFFFF));
            }
        }

        GuiContainerManager.disableMatrixStackLogging();

        this.handlerRef.handler.drawForeground(this.handlerRef.recipeIndex);

        final GuiRecipeButton overlayButton = getRecipeButtons().stream()
                .filter(
                        b -> this.showAsWidget ? b instanceof GuiOverlayButton
                                : b.contains(mouseX - this.x, mouseY - this.y - yShift))
                .findAny().orElse(null);

        if (overlayButton != null && overlayButton.enabled) {
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

        LayoutManager.recipeHandlerWidget.drawGuiPlaceholder(this);
    }

    @Override
    public boolean handleKeyPress(int keyID, char keyChar) {
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

        return this.handlerRef.handler.handleItemTooltip(guiRecipe, itemstack, tooltip, this.handlerRef.recipeIndex);
    }

    @Override
    public Map<String, String> handleHotkeys(int mouseX, int mouseY, Map<String, String> hotkeys) {
        return forEachButtons(
                button -> button.contains(mouseX, mouseY) ? button.handleHotkeys(mouseX, mouseY, hotkeys) : null,
                hotkeys);
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

        if (overStack != null && overStack.items.length > 1) {
            final List<ItemStack> items = overStack.getFilteredPermutations();
            final int stackIndex = indexOf(items, overStack.item);
            final ItemStack stack = items.get((items.size() - scroll + stackIndex) % items.size());

            Stream.concat(
                    this.handlerRef.handler.getIngredientStacks(this.handlerRef.recipeIndex).stream(),
                    this.handlerRef.handler.getOtherStacks(this.handlerRef.recipeIndex).stream())
                    .filter(pStack -> pStack.containsWithNBT(stack))
                    .forEach(pStack -> pStack.setPermutationToRender(stack));

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

        final PositionedStack pStackResult = this.handlerRef.handler.getResultStack(this.handlerRef.recipeIndex);
        if (pStackResult != null && pStackResult.contains(mx - this.x, my - this.y - yShift)) {
            return true;
        }

        for (PositionedStack pStackOver : this.handlerRef.handler.getOtherStacks(this.handlerRef.recipeIndex)) {
            if (pStackOver.contains(mx - this.x, my - this.y - yShift)) {
                return true;
            }
        }

        return false;
    }

    public PositionedStack getPositionedStackMouseOver(int mx, int my) {
        final int yShift = this.handlerInfo.getYShift();

        for (PositionedStack pStack : this.handlerRef.handler.getIngredientStacks(this.handlerRef.recipeIndex)) {
            if (pStack.contains(mx - this.x, my - this.y - yShift)) {
                return pStack;
            }
        }

        for (PositionedStack pStack : this.handlerRef.handler.getOtherStacks(this.handlerRef.recipeIndex)) {
            if (pStack.contains(mx - this.x, my - this.y - yShift)) {
                return pStack;
            }
        }

        final PositionedStack pStackResult = this.handlerRef.handler.getResultStack(this.handlerRef.recipeIndex);
        if (pStackResult != null && pStackResult.contains(mx - this.x, my - this.y - yShift)) {
            return pStackResult;
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

        for (PositionedStack pStack : this.handlerRef.handler.getIngredientStacks(this.handlerRef.recipeIndex)) {
            if (pStack.items.length > 1) {
                final List<ItemStack> permutations = this.permutations
                        .computeIfAbsent(pStack, stack -> stack.getFilteredPermutations(FavoriteRecipes::contains));
                pStack.setPermutationToRender(permutations.get(this.lastcycle % permutations.size()));
            }
        }

        for (PositionedStack pStack : this.handlerRef.handler.getOtherStacks(this.handlerRef.recipeIndex)) {
            if (pStack.items.length > 1) {
                final List<ItemStack> permutations = this.permutations
                        .computeIfAbsent(pStack, stack -> stack.getFilteredPermutations(FavoriteRecipes::contains));
                pStack.setPermutationToRender(permutations.get(this.lastcycle % permutations.size()));
            }
        }

        final PositionedStack pStackResult = this.handlerRef.handler.getResultStack(this.handlerRef.recipeIndex);
        if (pStackResult != null && pStackResult.items.length > 1) {
            final List<ItemStack> permutations = this.permutations
                    .computeIfAbsent(pStackResult, stack -> stack.getFilteredPermutations(FavoriteRecipes::contains));
            pStackResult.setPermutationToRender(permutations.get(this.lastcycle % permutations.size()));
        }

    }

}
