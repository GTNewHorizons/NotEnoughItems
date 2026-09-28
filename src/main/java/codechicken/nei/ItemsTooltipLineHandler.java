package codechicken.nei;

import static codechicken.lib.gui.GuiDraw.fontRenderer;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import codechicken.lib.gui.GuiDraw;
import codechicken.lib.gui.GuiDraw.ITooltipLineHandler;
import codechicken.lib.vec.Rectangle4i;
import codechicken.nei.NEIClientUtils.Alignment;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.recipe.StackInfo;
import codechicken.nei.util.ReadableNumberConverter;

public class ItemsTooltipLineHandler implements ITooltipLineHandler {

    @FunctionalInterface
    public interface AmountRenderer {

        void draw(int x, int y, ItemStack stack, long amount);
    }

    public static class NoAmountRenderer implements AmountRenderer {

        @Override
        public void draw(int x, int y, ItemStack stack, long amount) {
            GuiContainerManager.drawItem(x, y, stack, true, "");
        }

        protected static void drawAmountText(String text, Rectangle4i rect, int color, Alignment alignment) {
            NEIClientUtils.drawNEIOverlayText(text, rect, 1, color, true, alignment);
        }
    }

    public static class TotalAmountRenderer extends NoAmountRenderer {

        private static class AmountText {

            public final String text;
            public final Alignment alignment;

            public AmountText(String text, Alignment alignment) {
                this.text = text;
                this.alignment = alignment;
            }
        }

        private final Map<ItemStack, AmountText> cache = new IdentityHashMap<>();

        @Override
        public void draw(int x, int y, ItemStack stack, long amount) {
            super.draw(x, y, stack, amount);
            drawAmount(new Rectangle4i(x, y, SLOT_SIZE - 2, SLOT_SIZE - 2), stack, amount);
        }

        protected void drawAmount(Rectangle4i rect, ItemStack stack, long amount) {
            final AmountText amountText = this.cache.computeIfAbsent(stack, key -> {
                final boolean isFluidDisplay = StackInfo.isFluidDisplayItem(key);
                final String text = formatAmount(key, amount);

                return new AmountText(
                        isFluidDisplay && !text.isEmpty() ? text + "L" : text,
                        isFluidDisplay ? Alignment.BottomLeft : Alignment.BottomRight);
            });

            if (!amountText.text.isEmpty()) {
                drawAmountText(amountText.text, rect, 0xFFFFFF, amountText.alignment);
            }
        }

        protected String formatAmount(ItemStack stack, long amount) {
            return amount == 0 ? "" : ReadableNumberConverter.INSTANCE.toWideReadableForm(amount);
        }
    }

    public static class StacksAmountRenderer extends NoAmountRenderer {

        private static class StacksText {

            public final String stacks;
            public final String remainder;
            public final int color;

            public StacksText(String stacks, String remainder, int color) {
                this.stacks = stacks;
                this.remainder = remainder;
                this.color = color;
            }
        }

        private final Map<ItemStack, StacksText> cache = new IdentityHashMap<>();

        @Override
        public void draw(int x, int y, ItemStack stack, long amount) {
            super.draw(x, y, stack, amount);
            drawAmount(new Rectangle4i(x, y, SLOT_SIZE - 2, SLOT_SIZE - 2), stack, amount);
        }

        protected void drawAmount(Rectangle4i rect, ItemStack stack, long amount) {
            final StacksText stacksText = this.cache.computeIfAbsent(stack, key -> formatAmount(key, amount));

            if (!stacksText.stacks.isEmpty()) {
                drawAmountText(stacksText.stacks, rect, stacksText.color, Alignment.TopLeft);
            }

            if (!stacksText.remainder.isEmpty()) {
                drawAmountText(stacksText.remainder, rect, stacksText.color, Alignment.BottomRight);
            }
        }

        private StacksText formatAmount(ItemStack stack, long amount) {
            final long stackSize = StackInfo.isFluidDisplayItem(stack) ? FLUID_STACK_SIZE
                    : Math.max(1, stack.getMaxStackSize());
            final long stacks = amount / stackSize;
            final long remainder = amount % stackSize;
            final String prefix = getPrefix(stack);

            return new StacksText(
                    stacks > 0 ? prefix + "x" + ReadableNumberConverter.INSTANCE.toWideReadableForm(stacks) : "",
                    remainder > 0
                            ? (stacks > 0 ? "+" : prefix)
                                    + ReadableNumberConverter.INSTANCE.toWideReadableForm(remainder)
                            : "",
                    getColor(stack));
        }

        protected String getPrefix(ItemStack stack) {
            return "";
        }

        protected int getColor(ItemStack stack) {
            return 0xFFFFFF;
        }
    }

    protected static final int SLOT_SIZE = 18;
    protected static final int ICON_SIZE = 16;
    protected static final int ICON_OFFSET = 1;
    protected static final int MAX_COLUMNS = 11;
    protected static final int MARGIN_TOP = 2;
    protected static final int LABEL_MARGIN = 15;
    protected static final int NAME_MARGIN = 2;
    protected static final int DEFAULT_MAX_ROWS = 5;
    protected static final int FLUID_STACK_SIZE = 144;

    protected final List<ItemStack> items;
    protected final List<Long> amounts = new ArrayList<>();
    protected final List<String> names = new ArrayList<>();

    protected AmountRenderer amountRenderer;

    protected String label;
    protected EnumChatFormatting labelColor = EnumChatFormatting.GRAY;

    protected int activeStackIndex = -1;
    protected Dimension size = new Dimension();

    protected final boolean showNames;

    protected int length = 0;
    protected int rows = 0;
    protected int nameRows = 0;
    protected int gridColumns = 0;
    protected int gridCount = 0;

    public static ItemsTooltipLineHandler grid(String label, List<ItemStack> items, int maxRows) {
        return new ItemsTooltipLineHandler(label, items, true, maxRows, false);
    }

    public static ItemsTooltipLineHandler list(String label, List<ItemStack> items, int maxRows) {
        return new ItemsTooltipLineHandler(label, items, true, maxRows, true);
    }

    public ItemsTooltipLineHandler(String label, List<ItemStack> items) {
        this(label, items, true, DEFAULT_MAX_ROWS, false);
    }

    public ItemsTooltipLineHandler(String label, List<ItemStack> items, boolean saveStackSize, int maxRows) {
        this(label, items, saveStackSize, maxRows, false);
    }

    protected ItemsTooltipLineHandler(String label, List<ItemStack> items, boolean saveStackSize, int maxRows,
            boolean showNames) {
        this.label = label;
        this.items = groupingItemStacks(items);
        this.amountRenderer = saveStackSize ? new TotalAmountRenderer() : new NoAmountRenderer();
        this.length = this.items.size();
        this.showNames = showNames;

        setMaxRows(maxRows);
    }

    protected static int headerHeight() {
        return fontRenderer.FONT_HEIGHT + 2 + MARGIN_TOP;
    }

    public ItemsTooltipLineHandler setMaxRows(int maxRows) {
        if (this.length == 0) {
            return this;
        }

        this.names.clear();
        this.nameRows = 0;

        if (!this.showNames || !layoutWithNames(maxRows)) {
            layoutAsGrid(maxRows);
        }

        this.size.height = this.rows * SLOT_SIZE + headerHeight();

        return this;
    }

    public int getRows() {
        return this.rows;
    }

    protected boolean layoutWithNames(int maxRows) {
        final int maxNames = this.length <= maxRows ? this.length : maxRows - 1;
        final int[] lineWidth = new int[maxNames + 1];
        lineWidth[0] = labelWidth();

        for (int index = 0; index < maxNames; index++) {
            final String name = this.items.get(index).getDisplayName();
            this.names.add(name);
            lineWidth[index + 1] = Math
                    .max(lineWidth[index], SLOT_SIZE + NAME_MARGIN + fontRenderer.getStringWidth(name));
        }

        for (int named = maxNames; named >= 0; named--) {
            final int rest = this.length - named;
            final int width = Math.max(lineWidth[named], Math.min(rest, MAX_COLUMNS) * SLOT_SIZE);
            final int columns = Math.max(1, width / SLOT_SIZE);
            final int restRows = (int) Math.ceil((float) rest / columns);

            if (named + restRows <= maxRows) {
                this.nameRows = named;
                this.gridColumns = columns;
                this.gridCount = rest;
                this.rows = named + restRows;
                this.size.width = width;
                return true;
            }
        }

        this.names.clear();
        return false;
    }

    protected void layoutAsGrid(int maxRows) {
        this.gridColumns = Math.min(MAX_COLUMNS, this.length);
        this.rows = Math.min(maxRows, (int) Math.ceil((float) this.length / this.gridColumns));
        this.gridCount = Math.min(this.length, this.gridColumns * this.rows);
        this.size.width = Math.max(this.gridColumns * SLOT_SIZE, labelWidth());

        if (this.gridCount < this.length) {
            this.gridCount -= reservedColumns(this.length - this.gridCount);
        }
    }

    protected int labelWidth() {
        return fontRenderer.getStringWidth(this.label) + LABEL_MARGIN;
    }

    protected int reservedColumns(int hidden) {
        return (int) Math.ceil((float) (fontRenderer.getStringWidth("+" + hidden) - 2) / SLOT_SIZE);
    }

    public ItemsTooltipLineHandler setAmountRenderer(AmountRenderer amountRenderer) {
        this.amountRenderer = amountRenderer;
        return this;
    }

    public void setActiveStack(ItemStack activeStack) {
        final ItemStack realStack = items.stream().filter(stack -> NEIClientUtils.areStacksSameType(stack, activeStack))
                .findFirst().orElse(null);
        this.activeStackIndex = items.indexOf(realStack);
    }

    public ItemStack getActiveStack() {
        return this.activeStackIndex == -1 ? null : this.items.get(this.activeStackIndex);
    }

    public List<ItemStack> getItems() {
        return this.items;
    }

    public boolean isEmpty() {
        return this.items.isEmpty();
    }

    @Override
    public Dimension getSize() {
        return this.size;
    }

    public void setLabelColor(EnumChatFormatting color) {
        this.labelColor = color;
    }

    public void setLabel(String label) {
        this.label = label;
        this.size.width = Math.max(this.size.width, labelWidth());
    }

    @Override
    public void draw(int x, int y) {
        if (this.length == 0) return;

        y += MARGIN_TOP;

        fontRenderer.drawStringWithShadow(this.labelColor + this.label + ":", x, y, 0);

        GL11.glEnable(GL12.GL_RESCALE_NORMAL);

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_LIGHTING_BIT);
        RenderHelper.enableGUIStandardItemLighting();

        final int xTranslation = x;
        final int yTranslation = y + fontRenderer.FONT_HEIGHT + 2;
        final int zTranslation = GuiContainerManager.TOOLTIP_Z_OFFSET;
        GL11.glTranslatef(xTranslation, yTranslation, zTranslation);

        final int gridShift = gridIndexShift();

        for (int index = 0; index < this.nameRows; index++) {
            drawSlot(0, index * SLOT_SIZE, index);
        }

        for (int index = 0; index < this.gridCount; index++) {
            drawSlot(
                    (index % this.gridColumns) * SLOT_SIZE,
                    (this.nameRows + index / this.gridColumns) * SLOT_SIZE,
                    this.nameRows + gridShift + index);
        }

        NEIClientUtils.gl2DRenderContext(() -> {
            final int textShift = ICON_OFFSET + Math.round((ICON_SIZE - fontRenderer.FONT_HEIGHT) / 2f);

            for (int index = 0; index < this.nameRows; index++) {
                fontRenderer.drawStringWithShadow(
                        EnumChatFormatting.GRAY + this.names.get(index),
                        SLOT_SIZE + NAME_MARGIN,
                        index * SLOT_SIZE + textShift,
                        0xFFFFFF);
            }

            final int hidden = this.length - this.nameRows - this.gridCount;

            if (hidden > 0) {
                final String text = "+" + hidden;
                fontRenderer.drawStringWithShadow(
                        EnumChatFormatting.GRAY + text,
                        this.size.width - fontRenderer.getStringWidth(text) - 2,
                        (this.rows - 1) * SLOT_SIZE + textShift,
                        0xFFFFFF);
            }
        });

        GL11.glTranslatef(-xTranslation, -yTranslation, -zTranslation);
        GL11.glPopAttrib();
    }

    protected int gridIndexShift() {
        if (this.nameRows > 0 || this.activeStackIndex == -1) {
            return 0;
        }

        return Math.max(0, Math.min(this.length - this.gridCount, this.activeStackIndex - this.gridCount + 2));
    }

    protected void drawSlot(int x, int y, int index) {
        if (this.activeStackIndex == index) {
            NEIClientUtils.gl2DRenderContext(() -> GuiDraw.drawRect(x, y, SLOT_SIZE, SLOT_SIZE, 0x66555555));
        }

        drawStackWithAmount(x, y, index);
    }

    protected void drawStackWithAmount(int x, int y, int index) {
        this.amountRenderer.draw(x + ICON_OFFSET, y + ICON_OFFSET, this.items.get(index), this.amounts.get(index));
    }

    private List<ItemStack> groupingItemStacks(List<ItemStack> items) {
        final List<ItemStack> result = new ArrayList<>();

        for (Map.Entry<NBTTagCompound, Long> entry : ItemStackAmount.of(items).entrySet()) {
            result.add(StackInfo.loadFromNBT(entry.getKey(), 0));
            this.amounts.add(Math.max(0, entry.getValue()));
        }

        if (result.isEmpty()) {
            for (ItemStack stack : items) {
                result.add(stack);
                this.amounts.add(0L);
            }
        }

        return result;
    }

}
