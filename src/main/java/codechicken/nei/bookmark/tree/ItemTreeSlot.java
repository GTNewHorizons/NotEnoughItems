package codechicken.nei.bookmark.tree;

import net.minecraft.item.ItemStack;

import codechicken.nei.bookmark.BookmarkItem;
import codechicken.nei.recipe.GuiRecipeTab;
import codechicken.nei.recipe.HandlerInfo;
import codechicken.nei.recipe.StackInfo;

class ItemTreeSlot {

    public final BookmarkItem ingrItem;
    public final BookmarkItem prefItem;

    public final long ingeritRequestedAmount;
    public final long currentRequestedAmount;
    public final long requestedAmount;

    public final long containerAmount;
    public final long producedAmount;
    public final long leftAmount;

    public final ItemStack emptyStack;
    public final ItemStack itemStack;
    public final boolean isFluidDisplay;
    public final boolean isContainerItem;

    public final HandlerInfo info;
    public final long multiplier;

    public ItemTreeSlot(BookmarkItem ingrItem, long ingeritRequestedAmount, long currentRequestedAmount,
            long producedAmount, long containerAmount, BookmarkItem prefItem) {

        this.ingrItem = ingrItem;
        this.ingeritRequestedAmount = ingeritRequestedAmount;
        this.currentRequestedAmount = currentRequestedAmount;
        this.requestedAmount = ingeritRequestedAmount + currentRequestedAmount;

        this.containerAmount = containerAmount;
        this.producedAmount = producedAmount;
        this.leftAmount = producedAmount + containerAmount;
        this.prefItem = prefItem;

        if (prefItem != null) {
            this.info = handlerInfo(prefItem.recipeId.getHandlerName());
            this.multiplier = prefItem.getMultiplierFromAmount(this.requestedAmount);

            this.itemStack = prefItem.getItemStack(this.requestedAmount);
            this.emptyStack = prefItem.getItemStack(0);
        } else {
            this.info = null;
            this.multiplier = ingrItem.getMultiplierFromAmount(this.requestedAmount);
            this.itemStack = ingrItem.getItemStack(this.requestedAmount);
            this.emptyStack = ingrItem.getItemStack(0);
        }

        this.isFluidDisplay = StackInfo.itemStackToNBT(this.emptyStack).hasKey("gtFluidName");
        this.isContainerItem = isContainerItem(this.emptyStack);
    }

    private static HandlerInfo handlerInfo(String handlerName) {
        final HandlerInfo info = GuiRecipeTab.getHandlerInfo(handlerName, null);
        return info != null ? info : GuiRecipeTab.DEFAULT_HANDLER_INFO;
    }

    private static boolean isContainerItem(ItemStack stack) {
        boolean isContainerItem = false;

        if (stack.getItem().hasContainerItem(stack)) {
            final boolean isPausedItemDamageSound = StackInfo.isPausedItemDamageSound();

            try {

                StackInfo.pauseItemDamageSound(true);

                final ItemStack containerItem = stack.getItem().getContainerItem(stack);

                if (containerItem != null) {
                    isContainerItem = stack.getItem() == containerItem.getItem();
                }

            } finally {
                StackInfo.pauseItemDamageSound(isPausedItemDamageSound);
            }
        }

        return isContainerItem;
    }

}
