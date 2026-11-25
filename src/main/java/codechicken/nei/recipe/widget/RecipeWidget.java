package codechicken.nei.recipe.widget;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import codechicken.lib.gui.GuiDraw;
import codechicken.lib.vec.Rectangle4i;
import codechicken.nei.FavoriteRecipes;
import codechicken.nei.KeyManager;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.PositionedStack;
import codechicken.nei.Widget;
import codechicken.nei.api.IGuiContainerOverlay;
import codechicken.nei.api.ShortcutInputHandler;
import codechicken.nei.drawable.DrawableResource;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.recipe.AcceptsFollowingTooltipLineHandler;
import codechicken.nei.recipe.Badge;
import codechicken.nei.recipe.GuiFavoriteButton;
import codechicken.nei.recipe.GuiOverlayButton;
import codechicken.nei.recipe.GuiRecipe;
import codechicken.nei.recipe.GuiRecipeButton;
import codechicken.nei.recipe.GuiRecipeButton.UpdateRecipeButtonsEvent;
import codechicken.nei.recipe.HandlerInfo;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.RecipeHandlerRef;
import codechicken.nei.recipe.StackInfo;
import codechicken.nei.recipe.debug.DebugHandlerWidget;
import codechicken.nei.theme.UIRecipeTexture;
import codechicken.nei.util.NEIMouseUtils;

public class RecipeWidget extends Widget {

    protected static final int TICKS_PER_CYCLE = 20;
    protected static final DrawableResource PAUSE_MARKER = UIRecipeTexture.ICON_PAUSE;
    protected static final int PAUSE_MARKER_OFFSET = 1;

    protected static final int BUTTON_GAP = 2;
    protected static final int BUTTON_SPACING = 2;
    protected static final int BUTTON_BOTTOM_OFFSET = 6;

    protected final RecipeGroup group;
    protected final IRecipeHandler handler;
    protected final HandlerInfo handlerInfo;

    protected final Map<PositionedStack, List<Badge>> badgeCache = new WeakHashMap<>();
    protected final Map<Integer, List<GuiRecipeButton>> buttonCache = new HashMap<>();
    protected final Map<Integer, List<GuiRecipeButton>> layoutCache = new HashMap<>();

    protected AcceptsFollowingTooltipLineHandler acceptsTooltip;

    protected int recipeHeight = 0;
    protected int spaceRight = 0;

    protected final ViewState state;

    protected int activeMember = 0;
    protected int lastcycle = -1;
    protected int favoriteRevision = FavoriteRecipes.getRevision();

    protected boolean showAsWidget = false;
    protected boolean update = true;
    protected boolean paused = false;
    protected Map<Point, ItemStack> pinnedSlots = Collections.emptyMap();

    public RecipeWidget(RecipeHandlerRef handlerRef) {
        this(handlerRef.handler, Collections.singletonList(handlerRef.recipeIndex));
    }

    public RecipeWidget(IRecipeHandler handler, List<Integer> recipeIndices) {
        this(handler, recipeIndices, null);
    }

    public RecipeWidget(IRecipeHandler handler, List<Integer> recipeIndices, ViewState state) {
        this(new RecipeGroup(handler, recipeIndices), state);
    }

    public RecipeWidget(RecipeGroup group) {
        this(group, null);
    }

    public RecipeWidget(RecipeGroup group, ViewState state) {
        this.group = group;
        this.handler = group.getHandler();
        this.handlerInfo = group.getHandlerInfo();
        this.state = state != null ? state : new ViewState();
        update();
        restore();
    }

    public RecipeHandlerRef getRecipeHandlerRef() {
        return RecipeHandlerRef.of(this.handler, getRecipeIndex());
    }

    protected int getRecipeIndex() {
        return this.group.getRecipeIndex(this.activeMember);
    }

    public boolean containsRecipeIndex(int recipeIndex) {
        return this.group.contains(recipeIndex);
    }

    // the state may come from a group with other members, then its cycle no longer fits
    private void restore() {
        final int member = this.group.indexOf(this.state.recipeIndex);

        if (member < 0 || this.state.groupSize != this.group.size()) {
            this.state.cycle = 0;
        }

        this.state.groupSize = this.group.size();

        // a pause on a recipe hidden by the search stays in the state until the recipe is shown again
        if (this.state.pausedRecipeIndex >= 0
                && pinRecipe(this.state.pausedRecipeIndex, this.state.pinnedIngredients)) {
            return;
        }

        setActiveMember(Math.max(member, 0));
        syncCycle();
    }

    public HandlerInfo getHandlerInfo() {
        return this.handlerInfo;
    }

    public void setLocation(int x, int y, int spaceRight) {
        this.x = x;
        this.y = y;

        if (this.spaceRight != spaceRight) {
            this.spaceRight = spaceRight;
            relayoutButtons();
        }
    }

    public void setHeight(int h) {
        if (this.h != h) {
            this.h = h;
            relayoutButtons();
        }
    }

    public void showAsWidget(boolean show) {
        this.showAsWidget = show;
    }

    @Override
    public boolean contains(int px, int py) {
        return super.contains(px, py) || isOverButton(px, py);
    }

    private boolean isOverButton(int px, int py) {
        for (GuiRecipeButton button : getRecipeButtonsIfInit()) {
            if (button.contains(px - this.x, py - this.y)) {
                return true;
            }
        }

        return false;
    }

    public boolean isPaused() {
        return this.paused;
    }

    public List<ItemStack> getPinnedIngredients() {
        return this.state.pinnedIngredients;
    }

    public void setPaused(boolean paused) {
        final boolean wasPaused = this.paused;
        this.paused = paused && this.group.canCycle();
        this.state.pausedRecipeIndex = this.paused ? this.state.recipeIndex : -1;

        if (!this.paused) {
            this.state.pinnedIngredients = null;
            this.pinnedSlots = Collections.emptyMap();

            if (wasPaused) {
                syncCycle();
            }
        }
    }

    /**
     * Pauses the widget on the recipe. Every given ingredient needs an input slot of its own that accepts it, otherwise
     * the recipe is not pinned.
     */
    public boolean pinRecipe(int recipeIndex, List<ItemStack> ingredients) {
        final int member = this.group.indexOf(recipeIndex);

        if (member < 0 || !this.group.canCycle()) {
            return false;
        }

        final Map<Point, ItemStack> slots = ingredients != null
                ? matchIngredients(this.group.getInputs(member), ingredients)
                : Collections.emptyMap();

        if (slots == null) {
            return false;
        }

        this.state.pinnedIngredients = ingredients;
        this.pinnedSlots = slots;
        this.paused = true;
        this.state.pausedRecipeIndex = recipeIndex;
        setActiveMember(member);
        updatePermutations();

        return true;
    }

    // assigns each ingredient to its own slot (bipartite matching), null when some ingredient has no slot
    private Map<Point, ItemStack> matchIngredients(List<PositionedStack> stacks, List<ItemStack> ingredients) {
        final int[] slotOwners = new int[stacks.size()];
        Arrays.fill(slotOwners, -1);

        for (int ingredient = 0; ingredient < ingredients.size(); ingredient++) {
            if (!assignIngredient(ingredient, stacks, ingredients, slotOwners, new boolean[stacks.size()])) {
                return null;
            }
        }

        final Map<Point, ItemStack> slots = new HashMap<>();

        for (int slot = 0; slot < slotOwners.length; slot++) {
            if (slotOwners[slot] != -1) {
                slots.put(this.group.getSlotKey(stacks.get(slot)), ingredients.get(slotOwners[slot]));
            }
        }

        return slots;
    }

    private boolean assignIngredient(int ingredient, List<PositionedStack> stacks, List<ItemStack> ingredients,
            int[] slotOwners, boolean[] visited) {

        for (int slot = 0; slot < stacks.size(); slot++) {
            if (!visited[slot] && stacks.get(slot).getPermutationIndex(ingredients.get(ingredient)) != -1) {
                visited[slot] = true;

                if (slotOwners[slot] == -1
                        || assignIngredient(slotOwners[slot], stacks, ingredients, slotOwners, visited)) {
                    slotOwners[slot] = ingredient;
                    return true;
                }
            }
        }

        return false;
    }

    private void applyPinnedIngredient(PositionedStack pStack) {
        final ItemStack ingredient = this.pinnedSlots.get(this.group.getSlotKey(pStack));

        if (ingredient != null) {
            pStack.setPermutationToRender(ingredient);
        }
    }

    @Override
    public void update() {
        final int prevW = this.w;
        final int prevH = this.h;
        int recipeWidth = 0;
        this.recipeHeight = 0;

        for (int member = 0; member < this.group.size(); member++) {
            final int recipe = this.group.getRecipeIndex(member);
            recipeWidth = Math.max(recipeWidth, this.handler.getRecipeWidth(recipe));
            this.recipeHeight = Math.max(this.recipeHeight, this.handler.getRecipeHeight(recipe));
        }

        this.w = recipeWidth > 0 ? recipeWidth : this.handlerInfo.getWidth();

        if (this.showAsWidget || !this.handlerInfo.isAllowOverflowY()) {
            this.h = getContentHeight();
        }

        if (this.w != prevW || this.h != prevH) {
            relayoutButtons();
        }

        this.update = true;
    }

    public int getContentHeight() {
        return (this.recipeHeight > 0 ? this.recipeHeight : this.handlerInfo.getHeight())
                + this.handlerInfo.getYShift();
    }

    private boolean showFavoritesButton() {
        return NEIClientConfig.favoritesEnabled() && this.handlerInfo.getShowFavoritesButton();
    }

    public List<GuiRecipeButton> getRecipeButtons() {
        return this.buttonCache.computeIfAbsent(this.activeMember, this::createButtons);
    }

    private List<GuiRecipeButton> getRecipeButtonsIfInit() {
        return this.buttonCache.getOrDefault(this.activeMember, Collections.emptyList());
    }

    private List<GuiRecipeButton> createButtons(int member) {

        if (this.group.getOutputs(member).isEmpty()) {
            return Collections.emptyList();
        }

        final List<GuiRecipeButton> buttons = initButtons(
                RecipeHandlerRef.of(this.handler, this.group.getRecipeIndex(member)));
        final List<GuiRecipeButton> unplaced = new ArrayList<>();

        // NEI creates its buttons at (0, 0), so anything else was placed by a mod and keeps its position.
        for (GuiRecipeButton button : buttons) {
            if (button.xPosition == 0 && button.yPosition == 0) {
                unplaced.add(button);
            }
        }

        this.layoutCache.put(member, unplaced);
        layoutButtons(unplaced);

        return Collections.unmodifiableList(buttons);
    }

    private void relayoutButtons() {
        for (List<GuiRecipeButton> buttons : this.layoutCache.values()) {
            layoutButtons(buttons);
        }
    }

    private void layoutButtons(List<GuiRecipeButton> buttons) {
        final int top = this.handlerInfo.getYShift();
        final int bottom = this.h - BUTTON_BOTTOM_OFFSET;
        int columnX = 0;
        int columnWidth = 0;
        int y = bottom;

        for (GuiRecipeButton button : buttons) {
            if (y != bottom && y - button.height < top) {
                columnX += columnWidth + BUTTON_SPACING;
                columnWidth = 0;
                y = bottom;
            }

            y -= button.height;
            button.xPosition = columnX;
            button.yPosition = y;
            y -= BUTTON_SPACING;
            columnWidth = Math.max(columnWidth, button.width);
        }

        final int left = this.w + Math.min(BUTTON_GAP, this.spaceRight - columnX - columnWidth);

        for (GuiRecipeButton button : buttons) {
            button.xPosition += left;
        }
    }

    private List<GuiRecipeButton> initButtons(RecipeHandlerRef ref) {
        final GuiRecipe<?> guiRecipe = getGuiRecipe();

        if (guiRecipe != null) {
            final UpdateRecipeButtonsEvent.Pre preEvent = new UpdateRecipeButtonsEvent.Pre(
                    guiRecipe,
                    ref,
                    this.w,
                    this.h,
                    this.handlerInfo);

            if (MinecraftForge.EVENT_BUS.post(preEvent)) {
                return preEvent.buttonList;
            } else {
                final UpdateRecipeButtonsEvent.Post postEvent = new UpdateRecipeButtonsEvent.Post(
                        guiRecipe,
                        ref,
                        this.w,
                        this.h,
                        getDefaultButtons(ref));
                MinecraftForge.EVENT_BUS.post(postEvent);
                return postEvent.buttonList;
            }

        } else {
            return getDefaultButtons(ref);
        }

    }

    private List<GuiRecipeButton> getDefaultButtons(RecipeHandlerRef ref) {
        GuiContainer guiContainer = NEIClientUtils.getGuiContainer();
        final List<GuiRecipeButton> buttons = new ArrayList<>();

        if (guiContainer instanceof IGuiContainerOverlay overlay) {
            guiContainer = overlay.getFirstScreen();
        }

        if (this.handlerInfo.getShowOverlayButton()) {
            buttons.add(new GuiOverlayButton(guiContainer, ref, 0, 0));
        }

        if (showFavoritesButton()) {
            buttons.add(new GuiFavoriteButton(ref, 0, 0));
        }

        return buttons;
    }

    @Override
    public void draw(int mouseX, int mouseY) {
        final int yShift = this.handlerInfo.getYShift();

        if (this.update) {
            this.update = false;

            if (this.lastcycle == -1) {
                this.lastcycle = this.state.cycleticks / TICKS_PER_CYCLE;
                applyCycle();
            } else if (tickCycle() && !isCycleFrozen(mouseX, mouseY)) {
                this.state.cycle++;
                applyCycle();
            } else if (this.favoriteRevision != FavoriteRecipes.getRevision()) {
                this.favoriteRevision = FavoriteRecipes.getRevision();
                this.badgeCache.clear();
                this.acceptsTooltip = null;
                updatePermutations();
            }

            for (GuiRecipeButton button : getRecipeButtons()) {
                button.update();
            }
        }

        final int recipeIndex = getRecipeIndex();

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_LIGHTING_BIT);
        GL11.glTranslatef(this.x, this.y + yShift, 0);

        this.handler.drawBackground(recipeIndex);

        RenderHelper.enableGUIStandardItemLighting();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glColor4f(1, 1, 1, 1);

        GuiContainerManager.enableMatrixStackLogging();

        for (PositionedStack pStack : getCyclingStacks()) {
            drawItem(pStack, mouseX, mouseY, yShift, true);
        }

        for (PositionedStack pStack : getOutputs()) {
            drawItem(pStack, mouseX, mouseY, yShift, false);
        }

        GuiContainerManager.disableMatrixStackLogging();

        this.handler.drawForeground(recipeIndex);

        final GuiRecipeButton overlayButton = forEachButtons(
                button -> (this.showAsWidget ? button instanceof GuiOverlayButton : button.contains(mouseX, mouseY))
                        ? button
                        : null,
                null);

        if (overlayButton != null) {
            NEIClientUtils.gl2DRenderContext(() -> overlayButton.drawItemOverlay());
        }

        GL11.glTranslatef(-this.x, -this.y - yShift, 0);
        GL11.glPopAttrib();

        if (this.paused) {
            NEIClientUtils.gl2DRenderContext(this::drawPauseMarker);
        }

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

    private void drawPauseMarker() {
        GL11.glColor4f(1, 1, 1, 1);
        PAUSE_MARKER.draw(this.x + PAUSE_MARKER_OFFSET, this.y + PAUSE_MARKER_OFFSET);
    }

    private boolean isPauseMarkerMouseOver(int mouseX, int mouseY) {
        final int left = this.x + PAUSE_MARKER_OFFSET;
        final int top = this.y + PAUSE_MARKER_OFFSET;

        return this.paused && mouseX >= left
                && mouseX < left + PAUSE_MARKER.getWidth()
                && mouseY >= top
                && mouseY < top + PAUSE_MARKER.getHeight();
    }

    private void drawItem(PositionedStack pStack, int mouseX, int mouseY, int yShift, boolean input) {
        pStack.draw(mouseX - this.x, mouseY - this.y - yShift);

        if (this.handlerInfo.getShowBadge()) {
            drawBadge(pStack, input);
        }

        if (pStack.contains(mouseX - this.x, mouseY - this.y - yShift)) {
            NEIClientUtils.gl2DRenderContext(
                    () -> GuiDraw.drawRect(pStack.relx, pStack.rely, pStack.width, pStack.height, 0x80FFFFFF));
        }
    }

    private List<Badge> getBadges(PositionedStack pStack, boolean input) {

        return this.badgeCache.computeIfAbsent(pStack, k -> {
            final List<Badge> badges = k.getBadges();

            if (badges == null) {

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

    private void drawBadge(PositionedStack pStack, boolean input) {
        final List<Badge> badges = getBadges(pStack, input);

        for (Badge badge : badges) {
            badge.draw(new Rectangle4i(pStack.relx, pStack.rely, pStack.width, pStack.height));
        }
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
    public boolean onHotkeyPressed() {
        final Point mouse = GuiDraw.getMousePosition();

        if (NEIClientConfig.favoritesEnabled()
                && KeyManager.isHashDown("bookmark.favorite_item", NEIClientUtils.SHIFT_HASH)) {
            final PositionedStack hovered = getCyclingStackMouseOver(mouse.x, mouse.y);

            if (hovered != null && getPermutations(hovered).size() > 1) {
                FavoriteRecipes.toggleFavoriteItem(hovered.item);
                setPaused(false);
                return true;
            }

        }

        return forEachButtons(
                button -> button.contains(mouse.x, mouse.y) && button.onHotkeyPressed() ? true : null,
                false);
    }

    @Override
    public boolean handleClick(int mouseX, int mouseY, int button) {

        if (button == 0 && isPauseMarkerMouseOver(mouseX, mouseY)) {
            NEIClientUtils.playClickSound();
            setPaused(false);
            return true;
        }

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

        try (GuiRecipe.CompatibilityHacks hack = new GuiRecipe.CompatibilityHacks(guiRecipe, this)) {
            return this.handler.mouseClicked(guiRecipe, button, getRecipeIndex());
        }
    }

    @Override
    public List<String> handleTooltip(int mouseX, int mouseY, List<String> tooltip) {
        final GuiRecipe<?> guiRecipe = getGuiRecipe();

        if (guiRecipe == null) {
            return tooltip;
        }

        if (isPauseMarkerMouseOver(mouseX, mouseY)) {
            tooltip.add(NEIClientUtils.translate("recipe.pinned"));
            return tooltip;
        }

        final List<String> buttonTooltip = forEachButtons(
                button -> button.contains(mouseX, mouseY) ? button.handleTooltip(tooltip) : null,
                null);

        if (buttonTooltip != null) {
            return buttonTooltip;
        }

        try (GuiRecipe.CompatibilityHacks hack = new GuiRecipe.CompatibilityHacks(guiRecipe, this)) {
            return this.handler.handleTooltip(guiRecipe, tooltip, getRecipeIndex());
        }
    }

    @Override
    public List<String> handleItemTooltip(ItemStack itemstack, int mousex, int mousey, List<String> tooltip) {
        final GuiRecipe<?> guiRecipe = getGuiRecipe();

        if (guiRecipe == null) {
            return tooltip;
        }

        tooltip = this.handler.handleItemTooltip(guiRecipe, itemstack, tooltip, getRecipeIndex());
        final PositionedStack cycling = itemstack != null ? getCyclingStackMouseOver(mousex, mousey) : null;
        final PositionedStack hovered = cycling != null ? cycling
                : itemstack != null ? getOutputStackMouseOver(mousex, mousey) : null;

        if (hovered != null) {
            if (this.handlerInfo.getShowBadge()) {
                final List<Badge> badges = getBadges(hovered, cycling != null);

                for (Badge badge : badges) {
                    if (badge.getTooltip() != null && !badge.getTooltip().isEmpty()) {
                        tooltip.addAll(badge.getTooltip());
                    }
                }
            }

            final List<String> customTooltip = hovered.getTooltip();

            if (customTooltip != null && !customTooltip.isEmpty()) {
                tooltip.addAll(customTooltip);
            }
        }

        final Point slotKey = cycling != null ? this.group.getSlotKey(cycling) : null;

        if (slotKey == null || !NEIClientConfig.showCycledIngredientsTooltip()
                || this.group.getPermutations(slotKey).size() <= 1) {
            this.acceptsTooltip = null;
        } else if (this.acceptsTooltip == null || !slotKey.equals(this.acceptsTooltip.tooltipGUID)) {
            this.acceptsTooltip = AcceptsFollowingTooltipLineHandler
                    .of(slotKey, this.group.getPermutations(slotKey), cycling.item);

            if (this.acceptsTooltip != null && cycling.getAcceptsLabel() != null) {
                this.acceptsTooltip.setLabel(cycling.getAcceptsLabel());
            }
        }

        if (this.acceptsTooltip != null) {
            tooltip.add(GuiDraw.TOOLTIP_HANDLER + GuiDraw.getTipLineId(this.acceptsTooltip));
        }

        return tooltip;
    }

    @Override
    public Map<String, String> handleHotkeys(int mouseX, int mouseY, Map<String, String> hotkeys) {
        final Map<String, String> buttonHotkeys = forEachButtons(
                button -> button.contains(mouseX, mouseY) ? button.handleHotkeys(mouseX, mouseY, hotkeys) : null,
                hotkeys);
        final PositionedStack hovered = getCyclingStackMouseOver(mouseX, mouseY);

        if (hovered != null && getPermutations(hovered).size() > 1) {
            hotkeys.put(
                    KeyManager.getKeyName("bookmark.favorite_item", NEIClientUtils.SHIFT_HASH),
                    NEIClientUtils.translate("recipe.favorite.toggle"));
        }

        if (hovered == null && this.group.size() > 1 && getOutputStackMouseOver(mouseX, mouseY) != null) {
            hotkeys.put(
                    NEIClientUtils.getKeyName(
                            NEIClientUtils.SHIFT_HASH,
                            NEIMouseUtils.MOUSE_BTN_NONE + NEIMouseUtils.MOUSE_SCROLL),
                    NEIClientUtils.translate("recipe.group.scroll", this.activeMember + 1, this.group.size()));
        }

        if (hovered != null && this.acceptsTooltip != null) {
            buttonHotkeys.put(
                    NEIClientUtils.getKeyName(
                            NEIClientUtils.SHIFT_HASH,
                            NEIMouseUtils.MOUSE_BTN_NONE + NEIMouseUtils.MOUSE_SCROLL),
                    NEIClientUtils.translate("recipe.accepts.scroll"));
        }

        return buttonHotkeys;
    }

    @Override
    public boolean onMouseWheel(int scroll, int mx, int my) {

        if (forEachButtons(button -> button.contains(mx, my) ? button.mouseScrolled(scroll) : null, false)) {
            return true;
        }

        if (NEIClientUtils.shiftKey()) {
            final PositionedStack hovered = getCyclingStackMouseOver(mx, my);

            if (hovered != null && scrollPermutations(scroll, hovered)) {
                return true;
            }

            if (hovered == null && getOutputStackMouseOver(mx, my) != null && scrollMembers(scroll)) {
                return true;
            }
        }

        final GuiRecipe<?> guiRecipe = getGuiRecipe();
        return guiRecipe != null && this.handler.mouseScrolled(guiRecipe, scroll, getRecipeIndex());
    }

    private boolean scrollPermutations(int scroll, PositionedStack hovered) {
        final Point slotKey = this.group.getSlotKey(hovered);
        final List<ItemStack> items = this.group.getPermutations(slotKey);

        if (items.size() <= 1) {
            return false;
        }

        final int current = Math.max(0, PermutationsCycler.indexOf(items, hovered.item));
        final int direction = scroll > 0 ? -1 : 1;

        for (int step = 1; step <= items.size(); step++) {
            final ItemStack stack = items.get(Math.floorMod(current + direction * step, items.size()));
            final int member = this.group.findMember(slotKey, stack, this.activeMember);

            if (member != -1) {
                switchMember(member, stack);
                return true;
            }
        }

        return false;
    }

    private boolean scrollMembers(int scroll) {
        final int size = this.group.size();

        if (size <= 1) {
            return false;
        }

        switchMember(((this.activeMember - scroll) % size + size) % size, null);

        return true;
    }

    private void switchMember(int member, ItemStack stack) {
        final Map<Point, ItemStack> rendered = new HashMap<>();

        setPaused(false);

        for (PositionedStack pStack : getCyclingStacks()) {
            rendered.put(this.group.getSlotKey(pStack), pStack.item);
        }

        setActiveMember(member);
        syncCycle();
        this.group.refresh(member);
        this.badgeCache.clear();

        for (PositionedStack pStack : getCyclingStacks()) {
            final ItemStack renderStack = containsStack(pStack, stack) ? stack
                    : rendered.get(this.group.getSlotKey(pStack));

            if (containsStack(pStack, renderStack)) {
                pStack.setPermutationToRender(renderStack);
            } else {
                updatePermutationsFor(pStack);
            }
        }

        updateTooltipActiveStack();
        notifyPermutationsChanged();
    }

    private boolean tickCycle() {

        if ((this.state.cycleticks++ / TICKS_PER_CYCLE) != this.lastcycle) {
            this.lastcycle = this.state.cycleticks / TICKS_PER_CYCLE;
            return true;
        }

        return false;
    }

    private boolean isCycleFrozen(int mouseX, int mouseY) {

        if (this.paused || NEIClientUtils.shiftKey()) {
            return true;
        }

        return NEIClientConfig.freezeCycleOnHover() && !this.showAsWidget
                && contains(mouseX, mouseY)
                && (getPositionedStackMouseOver(mouseX, mouseY) != null || isOverButton(mouseX, mouseY));
    }

    private void applyCycle() {

        if (!this.paused) {
            final List<Integer> allowed = this.group.getAllowedMembers();
            setActiveMember(allowed.get(this.state.cycle % allowed.size()));
        }

        this.group.refresh(this.activeMember);
        updatePermutations();
    }

    private int getPermutationIndex() {
        return this.state.cycle / this.group.getAllowedMembers().size();
    }

    // keep the permutation index, move the member part of the counter to the active member
    private void syncCycle() {
        final List<Integer> allowed = this.group.getAllowedMembers();
        final int allowedIndex = allowed.indexOf(this.activeMember);

        if (allowedIndex >= 0) {
            this.state.cycle = this.state.cycle / allowed.size() * allowed.size() + allowedIndex;
        }
    }

    private void updatePermutations() {
        // badges depend on the rendered item
        this.badgeCache.clear();

        for (PositionedStack pStack : getCyclingStacks()) {
            updatePermutationsFor(pStack);
        }

        updateTooltipActiveStack();
        notifyPermutationsChanged();
    }

    private void updateTooltipActiveStack() {

        if (this.acceptsTooltip != null && this.acceptsTooltip.tooltipGUID instanceof Point slotKey) {
            final PositionedStack pStack = this.group.getSlot(this.activeMember, slotKey);

            if (pStack != null) {
                this.acceptsTooltip.setActiveStack(pStack.item);
            }
        }

    }

    private void updatePermutationsFor(PositionedStack pStack) {
        final int index = this.group
                .getCycledPermutationIndex(this.activeMember, this.group.getSlotKey(pStack), getPermutationIndex());

        if (index >= 0) {
            pStack.setPermutationToRender(index);
        }

        applyPinnedIngredient(pStack);
    }

    private void setActiveMember(int member) {

        if (member != this.activeMember) {
            this.activeMember = member;

            for (GuiRecipeButton button : getRecipeButtonsIfInit()) {
                button.update();
            }
        }

        this.state.recipeIndex = this.group.getRecipeIndex(member);
    }

    private boolean containsStack(PositionedStack pStack, ItemStack stack) {
        return pStack != null && this.group.hasPermutation(this.activeMember, this.group.getSlotKey(pStack), stack);
    }

    private List<ItemStack> getPermutations(PositionedStack pStack) {
        return this.group.getPermutations(this.group.getSlotKey(pStack));
    }

    private void notifyPermutationsChanged() {
        for (GuiRecipeButton button : getRecipeButtonsIfInit()) {
            button.onPermutationsChanged();
        }
    }

    private <R> R forEachButtons(Function<GuiRecipeButton, R> callback, R defaultValue) {

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
        final PositionedStack cycling = getCyclingStackMouseOver(mx, my);
        return cycling != null ? cycling : getOutputStackMouseOver(mx, my);
    }

    private PositionedStack getOutputStackMouseOver(int mx, int my) {
        final int yShift = this.handlerInfo.getYShift();

        for (PositionedStack pStack : getOutputs()) {
            if (pStack.contains(mx - this.x, my - this.y - yShift)) {
                return pStack;
            }
        }

        return null;
    }

    private PositionedStack getCyclingStackMouseOver(int mx, int my) {
        final int yShift = this.handlerInfo.getYShift();

        for (PositionedStack pStack : getCyclingStacks()) {
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

    private GuiRecipe<?> getGuiRecipe() {
        final GuiContainer guiContainer = NEIClientUtils.getGuiContainer();

        if (guiContainer instanceof GuiRecipe<?>guiRecipe) {
            return guiRecipe;
        }

        return null;
    }

    protected List<PositionedStack> getOutputs() {
        return this.group.getOutputs(this.activeMember);
    }

    protected List<PositionedStack> getCyclingStacks() {
        return this.group.getCyclingStacks(this.activeMember);
    }

}
