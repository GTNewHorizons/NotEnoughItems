package codechicken.nei.bookmark.tree;

import net.minecraft.item.ItemStack;

import codechicken.lib.vec.Rectangle4i;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.NEIClientUtils.Alignment;
import codechicken.nei.util.ReadableNumberConverter;

/** Draws the amount of an item in the corner of a slot of the crafting tree. */
final class SlotAmountLabel {

    private static final int SLOT_SIZE = CraftingTreeCanvas.SLOT_SIZE;
    private static final int ITEM_SIZE = 16;
    private static final int LINE_HEIGHT = 5;

    private SlotAmountLabel() {}

    /**
     * Fluids show their amount followed by "L". Holding shift shows an amount above one stack as stacks and remainder,
     * e.g. 210 -> 3x64 / +18, and anything else shows the plain amount.
     *
     * @param prefix text put in front of the amount, "~" for amounts that are not exact
     */
    static void draw(ItemStack stack, boolean isFluidDisplay, long amount, String prefix, int color, int x, int y) {
        final int maxStackSize = stack.getMaxStackSize();
        final Alignment alignment = isFluidDisplay ? Alignment.BottomLeft : Alignment.BottomRight;

        if (isFluidDisplay) {
            drawLine(prefix + format(amount) + "L", x, y, color, alignment, 0);
        } else if (NEIClientUtils.shiftKey() && maxStackSize > 1 && amount > maxStackSize) {
            final long remainder = amount % maxStackSize;

            // the stacks stay at the bottom, where a single amount is drawn, and the remainder is above them
            drawLine(prefix + format(amount / maxStackSize) + "×" + maxStackSize, x, y, color, alignment, 0);

            if (remainder > 0) {
                drawLine("+" + remainder, x, y, color, alignment, 1);
            }
        } else {
            drawLine(prefix + format(amount), x, y, color, alignment, 0);
        }
    }

    /** Large numbers are shortened (12345 becomes 12k) to fit in a slot. */
    private static String format(long amount) {
        return amount < 10_000 ? String.valueOf(amount) : ReadableNumberConverter.INSTANCE.toWideReadableForm(amount);
    }

    private static void drawLine(String text, int x, int y, int color, Alignment alignment, int linesAbove) {
        final int itemOffset = (SLOT_SIZE - ITEM_SIZE) / 2;

        NEIClientUtils.drawNEIOverlayText(
                text,
                new Rectangle4i(x + itemOffset, y + itemOffset - linesAbove * LINE_HEIGHT, ITEM_SIZE, ITEM_SIZE),
                1,
                color,
                true,
                alignment);
    }

}
