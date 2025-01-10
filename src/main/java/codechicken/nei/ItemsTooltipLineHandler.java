package codechicken.nei;

import static codechicken.lib.gui.GuiDraw.fontRenderer;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import codechicken.lib.gui.GuiDraw.ITooltipLineHandler;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.recipe.StackInfo;
import codechicken.nei.util.ReadableNumberConverter;

public class ItemsTooltipLineHandler implements ITooltipLineHandler {

    protected static int MAX_COLUMNS = 11;
    protected static int MARGIN_TOP = 2;

    protected String label;
    protected EnumChatFormatting labelColor = EnumChatFormatting.GRAY;
    protected List<ItemStack> items;
    protected Dimension size = new Dimension();
    protected boolean saveStackSize = false;
    protected int columns = 0;
    protected int count = 0;
    protected int rows = 0;
    protected int length = 0;

    public ItemsTooltipLineHandler(String label, List<ItemStack> items, boolean saveStackSize) {
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

            this.size.width = Math
                    .max(this.columns * ItemsGrid.SLOT_SIZE, fontRenderer.getStringWidth(this.label) + 15);
            this.size.height = this.rows * ItemsGrid.SLOT_SIZE + fontRenderer.FONT_HEIGHT + 2 + MARGIN_TOP;

            this.count = Math.min(
                    this.length,
                    Math.min(
                            this.columns * this.rows,
                            this.length > MAX_COLUMNS * maxRows ? (MAX_COLUMNS * maxRows - 1) : Integer.MAX_VALUE));
        }

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

        GL11.glPushMatrix();
        RenderHelper.enableGUIStandardItemLighting();

        GL11.glScaled(1, 1, 3);
        GL11.glTranslatef(x, y + fontRenderer.FONT_HEIGHT + 2, 0);
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);

        for (int i = 0; i < this.count; i++) {
            ItemStack drawStack = this.items.get(i);
            int col = i % this.columns;
            int row = i / this.columns;

            String stackSize = !this.saveStackSize || drawStack.stackSize == 0 ? ""
                    : ReadableNumberConverter.INSTANCE.toWideReadableForm(drawStack.stackSize);

            GuiContainerManager
                    .drawItem(col * ItemsGrid.SLOT_SIZE, row * ItemsGrid.SLOT_SIZE, drawStack, true, stackSize);
        }

        if (this.count < this.items.size()) {
            fontRenderer.drawStringWithShadow(
                    "+" + (this.items.size() - this.count),
                    (this.columns - 1) * ItemsGrid.SLOT_SIZE,
                    (this.rows - 1) * ItemsGrid.SLOT_SIZE + (ItemsGrid.SLOT_SIZE - fontRenderer.FONT_HEIGHT) / 2 - 1,
                    0xee555555);
        }

        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        GL11.glPopMatrix();
    }

    private List<ItemStack> groupingItemStacks(List<ItemStack> items) {
        final Map<String, Integer> count = new HashMap<>();
        final Map<String, NBTTagCompound> unique = new LinkedHashMap<>();
        final List<ItemStack> result = new ArrayList<>();

        for (ItemStack stack : items) {
            final NBTTagCompound nbTag = StackInfo.itemStackToNBT(stack, true);
            if (nbTag == null) continue;

            final String GUID = StackInfo.getItemStackGUID(stack);

            if (!unique.containsKey(GUID)) {
                count.put(GUID, nbTag.getInteger("Count"));
                unique.put(GUID, nbTag);
            } else {
                count.put(GUID, count.get(GUID) + nbTag.getInteger("Count"));
            }
        }

        for (String GUID : unique.keySet()) {
            ItemStack stack = StackInfo.loadFromNBT(unique.get(GUID), count.get(GUID));

            if (unique.get(GUID).hasKey("gtFluidName")) {
                stack.stackSize = 0;
            }

            result.add(stack);
        }

        return result;
    }

}
