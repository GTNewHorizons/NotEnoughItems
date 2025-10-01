package codechicken.nei;

import static codechicken.lib.gui.GuiDraw.fontRenderer;

import java.awt.Dimension;
import java.util.ArrayList;
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
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.recipe.StackInfo;
import codechicken.nei.util.ReadableNumberConverter;

public class ItemsTooltipLineHandler implements ITooltipLineHandler {

    protected static final int SLOT_SIZE = 18;
    protected static final int MAX_COLUMNS = 11;
    protected static final int MARGIN_TOP = 2;

    protected String label;
    protected EnumChatFormatting labelColor = EnumChatFormatting.GRAY;
    protected List<ItemStack> items;
    protected int activeStackIndex = -1;
    protected Dimension size = new Dimension();
    protected boolean saveStackSize = false;
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
        this.saveStackSize = saveStackSize;
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

    public void setActiveStack(ItemStack activeStack) {
        final ItemStack realStack = items.stream().filter(stack -> StackInfo.equalItemAndNBT(stack, activeStack, true))
                .findFirst().orElse(null);
        this.activeStackIndex = items.indexOf(realStack);
    }

    public ItemStack getActiveStack() {
        return this.activeStackIndex == -1 ? null : this.items.get(this.activeStackIndex);
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

    @Override
    public void draw(int x, int y) {
        if (this.length == 0) return;

        y += MARGIN_TOP;

        fontRenderer.drawStringWithShadow(this.labelColor + this.label + ":", x, y, 0);

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT);
        GL11.glPushMatrix();
        RenderHelper.enableGUIStandardItemLighting();

        GL11.glScaled(1, 1, 3);
        GL11.glTranslatef(x, y + fontRenderer.FONT_HEIGHT + 2, 0);
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);

        int indexShift = 0;

        if (this.activeStackIndex != -1) {
            indexShift = Math.max(0, Math.min(this.items.size() - this.count, this.activeStackIndex - this.count + 2));
        }

        for (int index = 0; index < this.count && index + indexShift < this.items.size(); index++) {
            ItemStack drawStack = this.items.get(index + indexShift);
            int col = index % this.columns;
            int row = index / this.columns;

            String stackSize = !this.saveStackSize || drawStack.stackSize == 0 ? ""
                    : ReadableNumberConverter.INSTANCE.toWideReadableForm(drawStack.stackSize);

            if (this.activeStackIndex == index + indexShift) {
                NEIClientUtils.gl2DRenderContext(
                        () -> GuiDraw.drawRect(col * SLOT_SIZE - 1, row * SLOT_SIZE - 1, 18, 18, 0x66555555));
            }

            drawItem(col * SLOT_SIZE, row * SLOT_SIZE, drawStack, stackSize);
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

        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }

    protected void drawItem(int x, int y, ItemStack drawStack, String stackSize) {
        GuiContainerManager.drawItem(x, y, drawStack, true, stackSize);
    }

    private List<ItemStack> groupingItemStacks(List<ItemStack> items) {
        final List<ItemStack> result = new ArrayList<>();

        for (Map.Entry<NBTTagCompound, Long> entry : ItemStackAmount.of(items).entrySet()) {
            final ItemStack stack = StackInfo.loadFromNBT(entry.getKey(), Math.max(0, entry.getValue()));

            if (entry.getKey().hasKey("gtFluidName")) {
                stack.stackSize = 0;
            }
            result.add(stack);
        }

        if (result.isEmpty()) {
            result.addAll(items);
        }

        return result;
    }

}
