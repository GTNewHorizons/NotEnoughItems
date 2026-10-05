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
import codechicken.nei.drawable.DrawableBuilder;
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
import codechicken.nei.util.NEIMouseUtils;

public class RecipeWidget extends Widget {

    protected static final int TICKS_PER_CYCLE = 20;
    protected static final DrawableResource PAUSE_MARKER = new DrawableBuilder(
            "nei:textures/pinned_pause.png",
            0,
            0,
            8,
            8).setTextureSize(8, 8).build();
    protected static final int PAUSE_MARKER_OFFSET = 1;

    protected final RecipeGroup group;
    protected final IRecipeHandler handler;
    protected final HandlerInfo handlerInfo;

    protected final Map<PositionedStack, List<Badge>> badgeCache = new WeakHashMap<>();
    protected final Map<Integer, List<GuiRecipeButton>> buttonCache = new HashMap<>();

    protected AcceptsFollowingTooltipLineHandler acceptsTooltip;

    protected int activeMember = 0;
    // single state counter: member = allowed[cycle % size], permutation index = cycle / size
    protected int cycle = 0;
    protected int cycleticks = 0;
    protected int lastcycle = -1;
    protected int favoriteRevision = FavoriteRecipes.getRevision();

    protected boolean showAsWidget = false;
    protected boolean update = true;
    protected boolean paused = false;
    protected List<ItemStack> pinnedIngredients = null;
    protected Map<Point, ItemStack> pinnedSlots = Collections.emptyMap();

    public RecipeWidget(RecipeHandlerRef handlerRef) {
        this(handlerRef.handler, Collections.singletonList(handlerRef.recipeIndex));
    }

    public RecipeWidget(IRecipeHandler handler, List<Integer> recipeIndices) {
        this(new RecipeGroup(handler, recipeIndices));
    }

    public RecipeWidget(RecipeGroup group) {
        this.group = group;
        this.handler = group.getHandler();
        this.handlerInfo = group.getHandlerInfo();
        update();
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

    public HandlerInfo getHandlerInfo() {
        return this.handlerInfo;
    }

    public void setLocation(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void showAsWidget(boolean show) {
        this.showAsWidget = show;
    }

    public boolean isPaused() {
        return this.paused;
    }

    public List<ItemStack> getPinnedIngredients() {
        return this.pinnedIngredients;
    }

    public void setPaused(boolean paused) {
        final boolean wasPaused = this.paused;
        this.paused = paused && this.group.canCycle();

        if (!this.paused) {
            this.pinnedIngredients = null;
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

        this.pinnedIngredients = ingredients;
        this.pinnedSlots = slots;
        this.paused = true;
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

        if (this.showAsWidget || !this.handlerInfo.isAllowOverflowY()) {
            int maxHeight = 0;

            for (int member = 0; member < this.group.size(); member++) {
                final int recipeHeight = this.handler.getRecipeHeight(this.group.getRecipeIndex(member));

                if (recipeHeight > 0) {
                    maxHeight = Math.max(maxHeight, recipeHeight);
                }
            }

            this.w = Math.max(166, this.handlerInfo.getWidth());
            this.h = (maxHeight > 0 ? maxHeight : this.handlerInfo.getHeight()) + this.handlerInfo.getYShift();
        }

        this.update = true;
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

        return Collections
                .unmodifiableList(initButtons(RecipeHandlerRef.of(this.handler, this.group.getRecipeIndex(member))));
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
        final boolean showFavorites = NEIClientConfig.favoritesEnabled() && handlerInfo.getShowFavoritesButton();
        final boolean showOverlay = handlerInfo.getShowOverlayButton();
        final int x = Math.min(166, this.w) - GuiRecipeButton.BUTTON_WIDTH;
        int y = this.h - GuiRecipeButton.BUTTON_HEIGHT - 6;

        if (guiContainer instanceof IGuiContainerOverlay overlay) {
            guiContainer = overlay.getFirstScreen();
        }

        if (showOverlay) {
            buttons.add(new GuiOverlayButton(guiContainer, ref, x, y));
            y -= GuiRecipeButton.BUTTON_HEIGHT + 1;
        }

        if (showFavorites) {
            buttons.add(new GuiFavoriteButton(ref, x, y));
        }

        return buttons;
    }

    @Override
    public void draw(int mouseX, int mouseY) {
        final int yShift = this.handlerInfo.getYShift();

        if (this.update) {
            this.update = false;

            if (this.lastcycle == -1) {
                this.lastcycle = 0;
                applyCycle();
            } else if (tickCycle() && !isCycleFrozen(mouseX, mouseY)) {
                this.cycle++;
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
    public boolean handleKeyPress(int keyID, char keyChar) {

        if (NEIClientConfig.favoritesEnabled()
                && KeyManager.isHashDown("bookmark.favorite_item", NEIClientUtils.SHIFT_HASH)) {
            final Point mouse = GuiDraw.getMousePosition();
            final PositionedStack hovered = getCyclingStackMouseOver(mouse.x, mouse.y);

            if (hovered != null && getPermutations(hovered).size() > 1) {
                FavoriteRecipes.toggleFavoriteItem(hovered.item);
                setPaused(false);
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

        if ((this.cycleticks++ / TICKS_PER_CYCLE) != this.lastcycle) {
            this.lastcycle = this.cycleticks / TICKS_PER_CYCLE;
            return true;
        }

        return false;
    }

    private boolean isCycleFrozen(int mouseX, int mouseY) {

        if (this.paused || NEIClientUtils.shiftKey()) {
            return true;
        }

        return !this.showAsWidget && contains(mouseX, mouseY)
                && (getPositionedStackMouseOver(mouseX, mouseY) != null
                        || forEachButtons(button -> button.contains(mouseX, mouseY) ? true : null, false));
    }

    private void applyCycle() {

        if (!this.paused) {
            final List<Integer> allowed = this.group.getAllowedMembers();
            setActiveMember(allowed.get(this.cycle % allowed.size()));
        }

        this.group.refresh(this.activeMember);
        updatePermutations();
    }

    private int getPermutationIndex() {
        return this.cycle / this.group.getAllowedMembers().size();
    }

    // keep the permutation index, move the member part of the counter to the active member
    private void syncCycle() {
        final List<Integer> allowed = this.group.getAllowedMembers();
        final int allowedIndex = allowed.indexOf(this.activeMember);

        if (allowedIndex >= 0) {
            this.cycle = this.cycle / allowed.size() * allowed.size() + allowedIndex;
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
