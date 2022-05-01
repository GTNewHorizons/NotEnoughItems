package codechicken.nei;

import codechicken.lib.vec.Rectangle4i;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

import static codechicken.lib.gui.GuiDraw.drawRect;

import java.util.ArrayList;

public class ItemPanel extends PanelWidget
{
   
    /**
     *  Backwards compat :-/
     */
    @Deprecated
    private int itemsPerPage = 0;

    @Deprecated
    public static ArrayList<ItemStack> items = new ArrayList<>();

    @Deprecated
    public ArrayList<ItemStack> realItems = new ArrayList<>();

    public Button more;
    public Button less;
    public ItemQuantityField quantity;

    public static class ItemPanelSlot
    {
        public ItemStack item;
        public int slotIndex;

        public ItemPanelSlot(int idx, ItemStack stack)
        {
            slotIndex = idx;
            item = stack;
        }

        @Deprecated
        public ItemPanelSlot(int idx)
        {
            this(idx, ItemPanels.itemPanel.getGrid().getItem(idx));
        }

    }

    protected static class ItemPanelGrid extends ItemsGrid
    {
        public ArrayList<ItemStack> newItems;

        public void setItems(ArrayList<ItemStack> items)
        {
            newItems = items;
        }

        public void refresh(GuiContainer gui)
        {

            if (newItems != null) {
                realItems = newItems;
                newItems = null;
            }

            super.refresh(gui);
        }

        @Override
        protected void drawItem(Rectangle4i rect, int idx, ItemPanelSlot focus)
        {

            if (PresetsWidget.inEditMode() && !PresetsWidget.isHidden(getItem(idx))) {
                drawRect(rect.x, rect.y, rect.w, rect.h, 0xee555555);
            }

            super.drawItem(rect, idx, PresetsWidget.inEditMode()? null: focus);
        }


    }

    public ItemPanel()
    {
        grid = new ItemPanelGrid();
    }

    public static void updateItemList(ArrayList<ItemStack> newItems)
    {
        ((ItemPanelGrid) ItemPanels.itemPanel.getGrid()).setItems(newItems);
        ItemPanels.itemPanel.realItems = newItems;
    }

    @Override
    public void init()
    {
        super.init();

        more = new Button("+")
        {
            @Override
            public boolean onButtonPress(boolean rightclick) {
                if (rightclick)
                    return false;

                int modifier = NEIClientUtils.controlKey() ? 64 : NEIClientUtils.shiftKey() ? 10 : 1;
                int quantity = NEIClientConfig.getItemQuantity() + modifier;

                if (quantity < 0) {
                    quantity = 0;
                }

                ItemPanels.itemPanel.quantity.setText(Integer.toString(quantity));
                return true;
            }
        };
        less = new Button("-")
        {
            @Override
            public boolean onButtonPress(boolean rightclick) {
                if (rightclick)
                    return false;

                int modifier = NEIClientUtils.controlKey() ? -64 : NEIClientUtils.shiftKey() ? -10 : -1;
                int quantity = NEIClientConfig.getItemQuantity() + modifier;

                if (quantity < 0) {
                    quantity = 0;
                }

                ItemPanels.itemPanel.quantity.setText(Integer.toString(quantity));
                return true;
            }
        };

        quantity = new ItemQuantityField("quantity");

    }

    @Deprecated
    public void scroll(int i)
    {
        grid.shiftPage(i);
    }
    
    public String getLabelText()
    {
        return "(" + getPage() + "/" + Math.max(1, getNumPages()) + ")";
    }

    protected String getPositioningSettingName()
    {
        return "world.panels.items";
    }

    public int getMarginLeft(GuiContainer gui)
    {
        return (gui.width + gui.xSize) / 2 + PADDING;
    }

    public int getMarginTop(GuiContainer gui)
    {
        return PADDING;
    }

    public int getWidth(GuiContainer gui)
    {
        return gui.width - (gui.xSize + gui.width) / 2 - PADDING * 2;
    }

    public int getHeight(GuiContainer gui)
    {
        return gui.height - getMarginTop(gui) - PADDING;
    }

    protected int resizeFooter(GuiContainer gui)
    {
        final int BUTTON_SIZE = 16;

        more.w = less.w = BUTTON_SIZE;
        more.h = less.h = quantity.h = BUTTON_SIZE;

        less.x = x;
        more.x = x + w - BUTTON_SIZE;
        more.y = less.y = quantity.y = y + h - BUTTON_SIZE;

        quantity.x = x + BUTTON_SIZE + 2;
        quantity.w = more.x - quantity.x - 2;

        return BUTTON_SIZE + 2;
    }

    @Override
    public void setVisible()
    {
        super.setVisible();
        
        if (grid.getPerPage() > 0) {
            LayoutManager.addWidget(more);
            LayoutManager.addWidget(less);
            LayoutManager.addWidget(quantity);
        }
        
    }

    protected ItemStack getDraggedStackWithQuantity(int mouseDownSlot)
    {
        ItemStack stack = grid.getItem(mouseDownSlot);

        if (stack != null) {
            int amount = NEIClientConfig.getItemQuantity();

            if (amount == 0) {
                amount = stack.getMaxStackSize();
            }

            return NEIServerUtils.copyStack(stack, amount);
        }

        return null;
    }

}
