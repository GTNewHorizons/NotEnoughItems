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
    protected static final int FLUID_STACK_SIZE = 144;

    protected final List<ItemStack> items;
    protected final List<Long> amounts = new ArrayList<>();

    protected AmountRenderer amountRenderer;

    protected String label;
    protected EnumChatFormatting labelColor = EnumChatFormatting.GRAY;

    protected int activeStackIndex = -1;
    protected Dimension size = new Dimension();

    protected int columns = 0;
    protected int count = 0;
    protected int rows = 0;
    protected int length = 0;

    public ItemsTooltipLineHandler(String label, List<ItemStack> items) {
        this(label, items, true, 5);
    }

    public ItemsTooltipLineHandler(String label, List<ItemStack> items, boolean saveStackSize, int maxRows) {
        this.label = label;
        this.items = groupingItemStacks(items);
        this.amountRenderer = saveStackSize ? new TotalAmountRenderer() : new NoAmountRenderer();
        this.length = this.items.size();

        if (this.length > 0) {
            this.columns = Math.min(MAX_COLUMNS, this.length);
            this.rows = Math.min(maxRows, (int) Math.ceil((float) this.length / this.columns));

            this.size.width = Math.max(this.columns * SLOT_SIZE, fontRenderer.getStringWidth(this.label) + 15);
            this.size.height = this.rows * SLOT_SIZE + fontRenderer.FONT_HEIGHT + 2 + MARGIN_TOP;

            this.count = Math.min(
                    this.length,
                    Math.min(
                            this.columns * this.rows,
                            this.length > MAX_COLUMNS * maxRows ? (MAX_COLUMNS * maxRows) : Integer.MAX_VALUE));

            if (this.items.size() > this.count) {
                String text = "+" + (this.items.size() - this.count);
                this.count -= (int) Math.ceil((float) (fontRenderer.getStringWidth(text) - 2) / SLOT_SIZE);
            }
        }

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
        this.size.width = Math.max(this.columns * SLOT_SIZE, fontRenderer.getStringWidth(this.label) + 15);
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

        int indexShift = 0;

        if (this.activeStackIndex != -1) {
            indexShift = Math.max(0, Math.min(this.items.size() - this.count, this.activeStackIndex - this.count + 2));
        }

        for (int index = 0; index < this.count && index + indexShift < this.items.size(); index++) {
            int col = index % this.columns;
            int row = index / this.columns;

            if (this.activeStackIndex == index + indexShift) {
                NEIClientUtils.gl2DRenderContext(
                        () -> GuiDraw.drawRect(col * SLOT_SIZE, row * SLOT_SIZE, SLOT_SIZE, SLOT_SIZE, 0x66555555));
            }

            drawStackWithAmount(col * SLOT_SIZE, row * SLOT_SIZE, index + indexShift);
        }

        if (this.count < this.items.size()) {
            final String text = "+" + (this.items.size() - this.count);

            NEIClientUtils.gl2DRenderContext(() -> {
                fontRenderer.drawStringWithShadow(
                        text,
                        MAX_COLUMNS * SLOT_SIZE - fontRenderer.getStringWidth(text) - 2,
                        (this.rows - 1) * SLOT_SIZE + (SLOT_SIZE - fontRenderer.FONT_HEIGHT) / 2,
                        0xee555555);
            });
        }

        GL11.glTranslatef(-xTranslation, -yTranslation, -zTranslation);
        GL11.glPopAttrib();
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
