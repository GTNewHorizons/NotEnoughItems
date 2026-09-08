package codechicken.nei.bookmark.tree;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import codechicken.lib.gui.GuiDraw;
import codechicken.lib.vec.Rectangle4i;
import codechicken.nei.ItemSorter;
import codechicken.nei.ItemStackAmount;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.NEIClientUtils.Alignment;
import codechicken.nei.Widget;
import codechicken.nei.bookmark.BookmarkItem;
import codechicken.nei.drawable.DrawableBuilder;
import codechicken.nei.drawable.DrawableResource;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.recipe.GuiRecipeTab;
import codechicken.nei.recipe.HandlerInfo;
import codechicken.nei.recipe.Recipe.RecipeId;
import codechicken.nei.recipe.StackInfo;
import codechicken.nei.recipe.chain.RecipeChainMath;
import codechicken.nei.scroll.ScrollBar;
import codechicken.nei.scroll.ScrollContainer;
import codechicken.nei.util.ReadableNumberConverter;

class StatsPanel extends ScrollContainer {

    public static class HandlerStats {

        public long iterations;
        public long recipeCount;
    }

    private static class SectionLabel extends Widget {

        public final String text;

        public SectionLabel(String text) {
            this.text = text;
            this.w = GuiDraw.fontRenderer.getStringWidth(text);
            this.h = GuiDraw.fontRenderer.FONT_HEIGHT + 4;
        }

        @Override
        public void draw(int mx, int my) {
            NEIClientUtils.gl2DRenderContext(
                    () -> GuiDraw.fontRenderer.drawStringWithShadow(this.text, this.x, this.y, 0xFFFFFFFF));
        }
    }

    public static class ItemStatsSlot extends Widget {

        public final ItemStack stack;
        public final long stackSize;
        public final RecipeId recipeId;
        public final long multiplier;
        public final HandlerInfo info;
        public final DrawableResource icon;
        private final boolean isFluidDisplay;
        public boolean highlight = false;

        public ItemStatsSlot(ItemStack stack, long stackSize, DrawableResource icon, RecipeId recipeId,
                long multiplier) {
            this.stack = StackInfo.withAmount(stack, 0);
            this.stackSize = stackSize;
            this.icon = icon;
            this.recipeId = recipeId;
            this.multiplier = multiplier;
            this.isFluidDisplay = StackInfo.itemStackToNBT(stack).hasKey("gtFluidName");
            this.info = this.recipeId != null ? handlerInfo(this.recipeId.getHandlerName()) : null;
            this.w = SLOT_SIZE;
            this.h = SLOT_SIZE;
        }

        public ItemStatsSlot(ItemStack stack, long stackSize, DrawableResource icon) {
            this(stack, stackSize, icon, null, 1);
        }

        @Override
        public void draw(int mx, int my) {

            if (this.highlight) {
                SLOT_HIGHLIGHTED.draw(this.x, this.y);
            } else {
                this.icon.draw(this.x, this.y);
            }

            GuiContainerManager
                    .drawItem(this.x + (SLOT_SIZE - 16) / 2, this.y + (SLOT_SIZE - 16) / 2, this.stack, true, "");

            drawStackSize();

            if (mx >= this.x && mx < this.x + SLOT_SIZE && my >= this.y && my < this.y + SLOT_SIZE) {
                NEIClientUtils.gl2DRenderContext(
                        () -> GuiDraw.drawRect(this.x + 1, this.y + 1, SLOT_SIZE - 2, SLOT_SIZE - 2, HOVER_COLOR));
            }

            if (this.recipeId != null) {
                drawHandlerBadge();
            }
        }

        private void drawHandlerBadge() {
            final float scale = 0.6f;
            final float badgeX = x - 6 * scale;

            GL11.glPushMatrix();
            GL11.glColor4f(1, 1, 1, 1);
            GL11.glTranslatef(badgeX, y - 6 * scale, 3.0f);
            GL11.glScalef(scale, scale, 1.0f);

            if (this.info.hasImageOrItem()) {
                final DrawableResource image = this.info.getImage();

                if (image != null) {
                    image.draw(0, 0);
                } else {
                    GuiContainerManager.drawItem(0, 0, this.info.getItemStack(), true);
                }

            } else {
                ICON_CRAFTING.draw(0, 0);
            }

            GL11.glPopMatrix();
        }

        private void drawStackSize() {
            String amountString = stackSize < 10_000 ? String.valueOf(stackSize)
                    : ReadableNumberConverter.INSTANCE.toWideReadableForm(stackSize);

            if (this.isFluidDisplay) {
                amountString += "L";
            }

            NEIClientUtils.drawNEIOverlayText(
                    amountString,
                    new Rectangle4i(
                            x + (SLOT_SIZE - 16) / 2,
                            y + (SLOT_SIZE - 16) / 2,
                            SLOT_SIZE - (SLOT_SIZE - 16),
                            SLOT_SIZE - (SLOT_SIZE - 16)),
                    1,
                    0xFFFFFF,
                    true,
                    this.isFluidDisplay ? Alignment.BottomLeft : Alignment.BottomRight);
        }

    }

    public static class HandlerStatsSlot extends Widget {

        public final String handlerName;
        public final HandlerInfo info;
        public final HandlerStats stats;
        public boolean highlight = false;

        public HandlerStatsSlot(String handlerName, HandlerStats stats) {
            this.handlerName = handlerName;
            this.info = handlerInfo(handlerName);
            this.stats = stats;
            this.w = SLOT_SIZE;
            this.h = SLOT_SIZE;
        }

        @Override
        public void draw(int mx, int my) {

            if (this.highlight) {
                SLOT_HIGHLIGHTED.draw(this.x, this.y);
            } else {
                SLOT_DEFAULT.draw(this.x, this.y);
            }

            if (this.info.hasImageOrItem()) {
                final DrawableResource image = this.info.getImage();

                if (image != null) {
                    image.draw(this.x + (SLOT_SIZE - 16) / 2, this.y + (SLOT_SIZE - 16) / 2);
                } else {
                    GuiContainerManager.drawItem(
                            this.x + (SLOT_SIZE - 16) / 2,
                            this.y + (SLOT_SIZE - 16) / 2,
                            this.info.getItemStack(),
                            true);
                }
            } else {
                ICON_CRAFTING.draw(this.x + (SLOT_SIZE - 16) / 2, this.y + (SLOT_SIZE - 16) / 2);
            }

            NEIClientUtils.drawNEIOverlayText(
                    "x" + ReadableNumberConverter.INSTANCE.toWideReadableForm(this.stats.iterations),
                    new Rectangle4i(this.x + 1, this.y + 1, SLOT_SIZE - 2, SLOT_SIZE - 2),
                    1,
                    0xFFFFFF,
                    true,
                    Alignment.TopLeft);

            NEIClientUtils.drawNEIOverlayText(
                    ReadableNumberConverter.INSTANCE.toWideReadableForm(this.stats.recipeCount),
                    new Rectangle4i(this.x + 1, this.y + 1, SLOT_SIZE - 2, SLOT_SIZE - 2),
                    1,
                    0xFFFFFF,
                    true,
                    Alignment.BottomRight);

            if (mx >= this.x && mx < this.x + SLOT_SIZE && my >= this.y && my < this.y + SLOT_SIZE) {
                NEIClientUtils.gl2DRenderContext(
                        () -> GuiDraw.drawRect(this.x + 1, this.y + 1, SLOT_SIZE - 2, SLOT_SIZE - 2, HOVER_COLOR));
            }
        }

    }

    private static final int SLOT_SIZE = 18;

    private static final int SLOT_INLINE_GAP = 2;
    private static final int SLOT_BLOCK_GAP = 2;
    private static final int COLUMN_GAP = 10;

    private static final int HOVER_COLOR = 0x66999999;

    private static final DrawableResource BG_TEXTURE = new DrawableBuilder(
            "nei:textures/gui/craftingtree.png",
            26,
            84,
            46,
            16).build();

    private static final DrawableResource SLOT_DEFAULT = new DrawableBuilder(
            "nei:textures/gui/craftingtree.png",
            68,
            26,
            18,
            18).build();

    private static final DrawableResource SLOT_MISSING = new DrawableBuilder(
            "nei:textures/gui/craftingtree.png",
            92,
            26,
            18,
            18).build();

    private static final DrawableResource SLOT_CRAFTING = new DrawableBuilder(
            "nei:textures/gui/craftingtree.png",
            116,
            26,
            18,
            18).build();

    private static final DrawableResource SLOT_DISABLED = new DrawableBuilder(
            "nei:textures/gui/craftingtree.png",
            140,
            26,
            18,
            18).build();

    private static final DrawableResource SLOT_HIGHLIGHTED = new DrawableBuilder(
            "nei:textures/gui/craftingtree.png",
            164,
            26,
            18,
            18).build();

    private static final DrawableResource ICON_CRAFTING = new DrawableBuilder(
            "nei:textures/gui/craftingtree.png",
            67,
            48,
            16,
            16).build();

    private static final int Z_OFFSET = 100;

    public StatsPanel() {
        final ScrollBar scrollbar = new ScrollBar().setTrackWidth(4)
                .setThumbTexture(new DrawableBuilder("nei:textures/gui/craftingtree.png", 26, 100, 24, 4).build(), 0, 0)
                .setTrackTexture(new DrawableBuilder("nei:textures/gui/craftingtree.png", 26, 104, 24, 4).build(), 0, 0)
                .setTrackPadding(1, -5, 1, 0).setOverflowType(ScrollBar.OverflowType.AUTO);

        setHorizontalScroll(scrollbar);
        setPaddingBlock(4, 4);
        setPaddingInline(1, 1);
    }

    public void rebuild(RecipeChainMath math, CraftingTreeGraph graph, boolean useInventorySnapshot,
            Set<CraftingTreeState.StatsSection> sections, int widgetX, int widgetY, int widgetW, int widgetH) {

        clear();

        final List<String> labels = new ArrayList<>();
        final List<List<Widget>> sectionGroups = new ArrayList<>();
        final List<Widget> inputsAndAvailable = new ArrayList<>();

        if (sections.contains(CraftingTreeState.StatsSection.INGREDIENTS_NEEDED)) {
            inputsAndAvailable.addAll(collectInputs(math, useInventorySnapshot));
        }

        if (sections.contains(CraftingTreeState.StatsSection.INGREDIENTS_AVAILABLE)) {
            inputsAndAvailable.addAll(collectAvailable(math));
        }

        if (!inputsAndAvailable.isEmpty()) {
            labels.add(NEIClientUtils.translate("bookmark.tree.stats.ingredients"));
            sectionGroups.add(inputsAndAvailable);
        }

        final boolean showCraftingNeeded = sections.contains(CraftingTreeState.StatsSection.CRAFTING_NEEDED);
        final boolean showCraftingAvailable = sections.contains(CraftingTreeState.StatsSection.CRAFTING_AVAILABLE);

        if (showCraftingNeeded || showCraftingAvailable) {
            final List<Widget> craftingNeededSlot = new ArrayList<>();
            final List<Widget> craftingAvailableSlot = new ArrayList<>();
            final Map<BookmarkItem, Boolean> recipeMissing = new LinkedHashMap<>();
            final Map<BookmarkItem, Integer> recipeLevel = new HashMap<>();

            for (Map.Entry<ItemTreeSlot, List<ItemTreeSlot>> entry : graph.childrenOf.entrySet()) {
                final ItemTreeSlot node = entry.getKey();
                final boolean needsCrafting = graph.childrenNeedCraftingCache.contains(node);

                recipeMissing.merge(node.prefItem, needsCrafting, (a, b) -> a || b);
                recipeLevel.merge(node.prefItem, treeLevel(node, graph.parentOf), Math::max);
            }

            final List<Map.Entry<BookmarkItem, Boolean>> sortedRecipeMissing = new ArrayList<>(
                    recipeMissing.entrySet());

            sortedRecipeMissing.sort(
                    Comparator.<Map.Entry<BookmarkItem, Boolean>>comparingInt(entry -> recipeLevel.get(entry.getKey()))
                            .thenComparing(entry -> entry.getKey().itemStack, ItemSorter.instance));

            for (Map.Entry<BookmarkItem, Boolean> entry : sortedRecipeMissing) {
                final BookmarkItem resultItem = entry.getKey();
                final long amount = resultItem.getAmount();

                if (amount > 0) {
                    final boolean missingIngredients = entry.getValue();

                    if (missingIngredients ? !showCraftingNeeded : !showCraftingAvailable) {
                        continue;
                    }

                    final List<Widget> slot = missingIngredients ? craftingNeededSlot : craftingAvailableSlot;

                    slot.add(
                            new ItemStatsSlot(
                                    resultItem.itemStack,
                                    resultItem.getStackSize(amount),
                                    missingIngredients ? SLOT_DEFAULT : SLOT_CRAFTING,
                                    resultItem.recipeId,
                                    resultItem.getMultiplier()));
                }
            }

            final List<Widget> craftingSlots = new ArrayList<>(craftingNeededSlot);
            craftingSlots.addAll(craftingAvailableSlot);

            if (!craftingSlots.isEmpty()) {
                labels.add(NEIClientUtils.translate("bookmark.tree.stats.crafting"));
                sectionGroups.add(craftingSlots);
            }
        }

        final List<Widget> outputsAndRemainders = new ArrayList<>();

        if (sections.contains(CraftingTreeState.StatsSection.RESULTS)) {
            outputsAndRemainders.addAll(collectOutputs(math));
        }

        if (sections.contains(CraftingTreeState.StatsSection.REMAINDERS)) {
            outputsAndRemainders.addAll(collectRemainder(math));
        }

        if (!outputsAndRemainders.isEmpty()) {
            labels.add(NEIClientUtils.translate("bookmark.tree.stats.results"));
            sectionGroups.add(outputsAndRemainders);
        }

        if (sections.contains(CraftingTreeState.StatsSection.HANDLERS)) {
            final List<Widget> handlers = collectHandlers(math);

            if (!handlers.isEmpty()) {
                labels.add(NEIClientUtils.translate("bookmark.tree.stats.handlers"));
                sectionGroups.add(handlers);
            }
        }

        layoutSections(labels, sectionGroups, widgetX, widgetY, widgetW, widgetH);
    }

    private void layoutSections(List<String> labels, List<List<Widget>> sections, int widgetX, int widgetY, int widgetW,
            int widgetH) {
        final int maxRowWidth = Math.max(SLOT_SIZE, widgetW);
        final int sectionCount = sections.size();
        final int[] sizes = new int[sectionCount];
        final int[] labelWidths = new int[sectionCount];
        final int[] columns = new int[sectionCount];
        final int maxRows = widgetH / ((GuiDraw.fontRenderer.FONT_HEIGHT + 4) + SLOT_SIZE * 2 + SLOT_BLOCK_GAP) > 3 ? 2
                : 1;

        for (int i = 0; i < sectionCount; i++) {
            sizes[i] = sections.get(i).size();
            labelWidths[i] = GuiDraw.fontRenderer.getStringWidth(labels.get(i) + ":");
            columns[i] = sizes[i] == 0 ? 0 : (sizes[i] + maxRows - 1) / maxRows;
        }

        int compactWidth = COLUMN_GAP * Math.max(0, sectionCount - 1);

        for (int i = 0; i < sectionCount; i++) {
            compactWidth += sectionWidth(columns[i], labelWidths[i]);
        }

        if (compactWidth <= maxRowWidth) {
            int budget = (maxRowWidth - compactWidth) / (SLOT_SIZE + SLOT_INLINE_GAP);

            while (budget > 0) {
                boolean grew = false;

                for (int i = 0; i < sectionCount && budget > 0; i++) {
                    if (columns[i] < sizes[i]) {
                        columns[i]++;
                        --budget;
                        grew = true;
                    }
                }

                if (!grew) {
                    break;
                }
            }
        }

        final List<Widget> widgets = new ArrayList<>();
        int cursorX = 0;
        int maxHeight = 0;

        for (int i = 0; i < sectionCount; i++) {
            final int height = placeSection(widgets, labels.get(i), sections.get(i), columns[i], cursorX, 0);
            maxHeight = Math.max(maxHeight, height);
            cursorX += sectionWidth(columns[i], labelWidths[i]) + COLUMN_GAP;
        }

        final int contentW = Math.max(0, cursorX - COLUMN_GAP);
        final int contentH = maxHeight;

        final int maxVisibleH = maxRows * SLOT_SIZE + Math.max(0, maxRows - 1) * SLOT_BLOCK_GAP
                + GuiDraw.fontRenderer.FONT_HEIGHT
                + 4;

        final int visibleHeight = Math.min(contentH, maxVisibleH);

        this.w = widgetW;
        this.x = widgetX;
        this.h = visibleHeight + this.paddingBlockStart + this.paddingBlockEnd;
        this.y = widgetY + widgetH - this.h;

        final int xShift = contentW < this.w ? (this.w - contentW) / 2 : 0;

        for (Widget widget : widgets) {
            widget.x += xShift;
        }

        setWidgets(widgets);
        update();
    }

    private static int sectionWidth(int columns, int labelWidth) {
        return Math.max(columns * SLOT_SIZE + Math.max(0, columns - 1) * SLOT_INLINE_GAP, labelWidth);
    }

    private static int placeSection(List<Widget> widgets, String label, List<Widget> group, int columns, int x, int y) {
        final SectionLabel sectionLabel = new SectionLabel(label + ":");
        sectionLabel.x = x;
        sectionLabel.y = y;
        widgets.add(sectionLabel);

        final int gridY = y + GuiDraw.fontRenderer.FONT_HEIGHT + 4;
        final int rowWidth = columns * SLOT_SIZE + Math.max(0, columns - 1) * SLOT_INLINE_GAP;
        int cursorX = 0;
        int row = 0;
        boolean any = false;

        for (Widget entry : group) {

            if (cursorX > 0) {
                if (cursorX + SLOT_INLINE_GAP + SLOT_SIZE > rowWidth) {
                    cursorX = 0;
                    row++;
                } else {
                    cursorX += SLOT_INLINE_GAP;
                }
            } else if (cursorX + SLOT_SIZE > rowWidth) {
                cursorX = 0;
                row++;
            }

            entry.x = x + cursorX;
            entry.y = gridY + row * (SLOT_SIZE + SLOT_BLOCK_GAP);
            widgets.add(entry);

            cursorX += SLOT_SIZE;
            any = true;
        }

        final int rows = any ? row + 1 : 0;
        return (gridY - y) + rows * SLOT_SIZE + Math.max(0, rows - 1) * SLOT_BLOCK_GAP;
    }

    public ItemStack getStackMouseOver(int mouseX, int mouseY) {
        final Widget widget = getWidgetUnderMouse(mouseX, mouseY);
        return widget instanceof ItemStatsSlot slot ? slot.stack : null;
    }

    public void setHighlight(SearchResult searchResult) {
        for (Widget widget : getWidgets()) {
            if (widget instanceof HandlerStatsSlot slot) {
                slot.highlight = searchResult != null && searchResult.matchesGUID(slot.handlerName);
            } else if (widget instanceof ItemStatsSlot slot) {
                slot.highlight = searchResult != null && searchResult.matchesGUID(slot.stack);
            }
        }
    }

    public Object getWidgetGUIDUnderMouse(int mouseX, int mouseY) {
        final Widget widget = getWidgetUnderMouse(mouseX, mouseY);

        if (widget instanceof HandlerStatsSlot slot) {
            return slot.handlerName;
        } else if (widget instanceof ItemStatsSlot slot) {
            return slot.stack;
        }

        return null;
    }

    public String getHandlerMouseOver(int mouseX, int mouseY) {
        final Widget widget = getWidgetUnderMouse(mouseX, mouseY);

        if (widget instanceof HandlerStatsSlot slot) {
            return slot.handlerName;
        } else if (widget instanceof ItemStatsSlot slot && slot.recipeId != null) {
            return slot.recipeId.getHandlerName();
        }

        return null;
    }

    public RecipeId getRecipeIdMouseOver(int mouseX, int mouseY) {
        final Widget widget = getWidgetUnderMouse(mouseX, mouseY);
        return widget instanceof ItemStatsSlot slot ? slot.recipeId : null;
    }

    public boolean isDraggingScrollbar() {
        return canScrollHorizontal() && getHorizontalScroll().isMouseDragged();
    }

    @Override
    public boolean onMouseWheel(int direction, int mouseX, int mouseY) {

        if (canScrollHorizontal() && boundsOutside().contains(mouseX, mouseY)) {
            getHorizontalScroll().setScrollOffset(this, getHorizontalScrollOffset() - direction * MOUSE_SCROLL_SPEED);
            return true;
        }

        return super.onMouseWheel(direction, mouseX, mouseY);
    }

    public void draw(int mouseX, int mouseY) {
        GL11.glTranslatef(0, 0, Z_OFFSET);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_LIGHTING_BIT);
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glColor4f(1, 1, 1, 1);
        RenderHelper.enableGUIStandardItemLighting();
        GL11.glDisable(GL11.GL_LIGHTING);

        BG_TEXTURE.draw(this.x, this.y, this.w, this.h, 0, 0, 2, 0);

        super.draw(mouseX, mouseY);

        GL11.glPopAttrib();
        GL11.glTranslatef(0, 0, -Z_OFFSET);
    }

    public void drawFull() {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_LIGHTING_BIT);
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glColor4f(1, 1, 1, 1);
        RenderHelper.enableGUIStandardItemLighting();
        GL11.glDisable(GL11.GL_LIGHTING);

        drawContent(-1, -1);
        GL11.glPopAttrib();
    }

    private List<Widget> collectAvailable(RecipeChainMath math) {
        final ItemStackAmount items = new ItemStackAmount();
        final List<Widget> widgets = new ArrayList<>();

        for (BookmarkItem item : math.initialItems) {
            final long amount = math.requiredAmount.getOrDefault(item, 0L);

            if (amount > 0) {
                items.add(item.itemStack, item.getStackSize(amount));
            }

        }

        for (Map.Entry<NBTTagCompound, Long> entry : items.entrySet()) {
            if (entry.getValue() > 0) {
                widgets.add(
                        new ItemStatsSlot(StackInfo.loadFromNBT(entry.getKey(), 0), entry.getValue(), SLOT_DEFAULT));
            }
        }

        return widgets;
    }

    private List<Widget> collectInputs(RecipeChainMath math, boolean useInventorySnapshot) {
        final ItemStackAmount items = new ItemStackAmount();
        final List<Widget> widgets = new ArrayList<>();

        for (BookmarkItem item : math.recipeIngredients) {
            final long amount = math.requiredAmount.containsKey(math.preferredItems.get(item)) ? 0
                    : math.requiredAmount.getOrDefault(item, item.getAmount());

            if (amount > 0) {
                items.add(item.itemStack, item.getStackSize(amount));
            }
        }

        for (Map.Entry<NBTTagCompound, Long> entry : items.entrySet()) {
            if (entry.getValue() > 0) {
                widgets.add(
                        new ItemStatsSlot(
                                StackInfo.loadFromNBT(entry.getKey(), 0),
                                entry.getValue(),
                                useInventorySnapshot ? SLOT_MISSING : SLOT_DEFAULT));
            }
        }

        return widgets;
    }

    private List<Widget> collectOutputs(RecipeChainMath math) {
        final ItemStackAmount items = new ItemStackAmount();
        final List<Widget> widgets = new ArrayList<>();

        for (BookmarkItem item : math.recipeResults) {
            final long amount = item.getAmount() - math.requiredAmount.getOrDefault(item, 0L);

            if (amount > 0 && math.outputRecipes.containsKey(item.recipeId)) {
                items.add(item.itemStack, item.getStackSize(amount));
            }
        }

        for (Map.Entry<NBTTagCompound, Long> entry : items.entrySet()) {
            if (entry.getValue() > 0) {
                widgets.add(
                        new ItemStatsSlot(StackInfo.loadFromNBT(entry.getKey(), 0), entry.getValue(), SLOT_DEFAULT));
            }
        }

        return widgets;
    }

    private List<Widget> collectRemainder(RecipeChainMath math) {
        final ItemStackAmount items = new ItemStackAmount();
        final List<Widget> widgets = new ArrayList<>();

        for (BookmarkItem item : math.recipeResults) {
            final long amount = item.getAmount() - math.requiredAmount.getOrDefault(item, 0L);

            if (amount > 0 && !math.outputRecipes.containsKey(item.recipeId)) {
                items.add(item.itemStack, item.getStackSize(amount));
            }
        }

        for (ItemStack stack : math.containerItemsInventory) {
            if (stack != null) {
                items.add(stack);
            }
        }

        for (ItemStack stack : math.containerItemsCrafting) {
            if (stack != null) {
                items.add(stack);
            }
        }

        for (Map.Entry<NBTTagCompound, Long> entry : items.entrySet()) {
            if (entry.getValue() > 0) {
                widgets.add(
                        new ItemStatsSlot(StackInfo.loadFromNBT(entry.getKey(), 0), entry.getValue(), SLOT_DISABLED));
            }
        }

        return widgets;
    }

    private List<Widget> collectHandlers(RecipeChainMath math) {
        final Map<String, HandlerStats> handlerStats = new HashMap<>();
        final List<Widget> widgets = new ArrayList<>();

        for (BookmarkItem item : math.recipeResults) {
            final HandlerStats stats = handlerStats
                    .computeIfAbsent(item.recipeId.getHandlerName(), k -> new HandlerStats());
            stats.iterations += item.getMultiplier();
            stats.recipeCount++;
        }

        for (Map.Entry<String, HandlerStats> entry : handlerStats.entrySet()) {
            widgets.add(new HandlerStatsSlot(entry.getKey(), entry.getValue()));
        }

        return widgets;
    }

    private static int treeLevel(ItemTreeSlot node, Map<ItemTreeSlot, ItemTreeSlot> parentOf) {
        int level = 0;

        for (ItemTreeSlot current = parentOf.get(node); current != null; current = parentOf.get(current)) {
            level++;
        }

        return level;
    }

    private static HandlerInfo handlerInfo(String handlerName) {
        final HandlerInfo info = GuiRecipeTab.getHandlerInfo(handlerName, null);
        return info != null ? info : GuiRecipeTab.DEFAULT_HANDLER_INFO;
    }

}
