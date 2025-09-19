package codechicken.nei.recipe;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.NEIServerUtils;
import codechicken.nei.PositionedStack;
import codechicken.nei.Widget;
import codechicken.nei.api.ShortcutInputHandler;
import codechicken.nei.drawable.DrawableBuilder;
import codechicken.nei.drawable.DrawableResource;
import codechicken.nei.guihook.GuiContainerManager;

public class GuiRecipeCatalyst extends Widget {

    private static final int SLOT_SIZE = 16;
    private static final int BORDER_PADDING = 6;
    private static final DrawableResource BG_TEXTURE = new DrawableBuilder(
            "nei:textures/catalyst_tab.png",
            0,
            0,
            28,
            28).setTextureSize(28, 28).build();

    private static final DrawableResource FG_TEXTURE = new DrawableBuilder("nei:textures/slot.png", 0, 0, 18, 18)
            .setTextureSize(18, 18).build();

    private final List<PositionedStack> items = new ArrayList<>();
    private int availableHeight = 0;
    private boolean showWidget = false;

    public void setCatalysts(List<PositionedStack> items) {
        this.items.clear();
        this.items.addAll(items);
    }

    public void setAvailableHeight(int height) {
        this.availableHeight = height;
    }

    public int getAvailableHeight() {
        return this.availableHeight;
    }

    public boolean isShowWidget() {
        return this.showWidget;
    }

    public int getColumnCount() {
        final int maxItemsPerColumn = Math.max(0, this.availableHeight - 2 - BORDER_PADDING * 2) / SLOT_SIZE;
        return maxItemsPerColumn > 0 ? NEIServerUtils.divideCeil(this.items.size(), maxItemsPerColumn) : 0;
    }

    public int getRowCount() {
        final int columnCount = getColumnCount();
        return columnCount > 0 ? NEIServerUtils.divideCeil(this.items.size(), columnCount) : 0;
    }

    @Override
    public void update() {
        final int columns = getColumnCount();
        final int rows = getRowCount();

        this.showWidget = rows * columns > 0;
        this.w = columns * SLOT_SIZE + 2 + BORDER_PADDING * 2;
        this.h = rows * SLOT_SIZE + 2 + BORDER_PADDING * 2;

        if (rows == 0 || columns == 0) {
            return;
        }

        int index = 0;
        for (PositionedStack pStack : this.items) {

            if (pStack.items.length > 1) {
                final int stackIndex = (pStack.items.length + pStack.getPermutationIndex(pStack.item) + 1)
                        % pStack.items.length;
                pStack.setPermutationToRender(stackIndex);
            }

            pStack.relx = this.w - BORDER_PADDING - 1 - SLOT_SIZE - (index / rows) * SLOT_SIZE;
            pStack.rely = BORDER_PADDING + 1 + (index % rows) * SLOT_SIZE;
            index++;
        }

    }

    @Override
    public void draw(int mx, int my) {
        if (!this.showWidget) return;

        GL11.glTranslatef(this.x, this.y, 0);
        GL11.glColor4f(1, 1, 1, 1);

        BG_TEXTURE.draw(0, 0, this.w, this.h, BORDER_PADDING, BORDER_PADDING, BORDER_PADDING, BORDER_PADDING);
        FG_TEXTURE.draw(
                BORDER_PADDING,
                BORDER_PADDING,
                this.w - BORDER_PADDING * 2,
                this.h - BORDER_PADDING * 2,
                1,
                1,
                1,
                1);

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        RenderHelper.enableGUIStandardItemLighting();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);

        for (PositionedStack pStack : this.items) {
            GuiContainerManager.drawItem(pStack.relx, pStack.rely, pStack.item);

            if (pStack.contains(mx - this.x, my - this.y)) {
                NEIClientUtils.gl2DRenderContext(
                        () -> GuiDraw.drawRect(pStack.relx, pStack.rely, SLOT_SIZE, SLOT_SIZE, -2130706433));
            }
        }

        GL11.glPopAttrib();
        GL11.glTranslatef(-this.x, -this.y, 0);
    }

    public PositionedStack getPositionedStackMouseOver(int mx, int my) {

        for (PositionedStack pStack : this.items) {
            if (pStack.contains(mx - this.x, my - this.y)) {
                return pStack;
            }
        }

        return null;
    }

    @Override
    public ItemStack getStackMouseOver(int mx, int my) {
        final PositionedStack pStack = getPositionedStackMouseOver(mx, my);

        if (pStack != null) {
            return pStack.item;
        }

        return null;
    }

    @Override
    public boolean handleClick(int mx, int my, int button) {
        return ShortcutInputHandler.handleMouseClick(getStackMouseOver(mx, my));
    }

    @Deprecated
    public static final int ingredientSize = 16;
    @Deprecated
    public static final int ingredientBorder = 1;
    @Deprecated
    public static final int tabBorder = 5;
    @Deprecated
    public static final int fullBorder = ingredientBorder + tabBorder;

}
