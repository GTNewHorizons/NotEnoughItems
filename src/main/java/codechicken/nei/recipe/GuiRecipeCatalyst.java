package codechicken.nei.recipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.lwjgl.opengl.GL11;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.NEIServerUtils;
import codechicken.nei.PositionedStack;
import codechicken.nei.Widget;
import codechicken.nei.api.ShortcutInputHandler;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.scroll.ScrollBar;
import codechicken.nei.scroll.ScrollContainer;
import codechicken.nei.theme.UIRecipeTexture;
import codechicken.nei.theme.UIWidgetTexture;

public class GuiRecipeCatalyst extends ScrollContainer {

    protected static class ItemStackWidget extends Widget {

        private final PositionedStack pStack;

        public ItemStackWidget(PositionedStack pStack) {
            this.pStack = pStack;
            this.x = pStack.relx;
            this.y = pStack.rely;
            this.w = SLOT_SIZE;
            this.h = SLOT_SIZE;
        }

        @Override
        public void draw(int mx, int my) {
            GuiContainerManager.drawItem(this.x, this.y, this.pStack.item);

            if (contains(mx, my)) {
                NEIClientUtils.gl2DRenderContext(() -> GuiDraw.drawRect(this.x, this.y, this.w, this.h, 0x80FFFFFF));
            }
        }

        @Override
        public boolean handleClick(int mx, int my, int button) {
            return ShortcutInputHandler.handleMouseClick(this.pStack.item);
        }
    }

    private static final int SLOT_SIZE = 16;
    private static final int BORDER_PADDING = 6;
    private static final int COLUMNS_BOTTOM_INSET = 5;

    private static final ScrollBar VERTICAL_SCROLLBAR = UIWidgetTexture.verticalScrollBar()
            .setOverflowType(ScrollBar.OverflowType.AUTO)
            .setTrackPadding(0, BORDER_PADDING - 1, -13, BORDER_PADDING - 1)
            .setScrollPlace(ScrollBar.ScrollPlace.START);

    private List<PositionedStack> items = Collections.emptyList();
    private boolean showWidget = false;
    private int availableHeight = 0;
    private int style = -1;

    public GuiRecipeCatalyst() {
        setPaddingBlock(BORDER_PADDING, BORDER_PADDING);
    }

    public void setCatalysts(List<PositionedStack> items) {
        this.items = items;
        createWidgets();
    }

    public void setAvailableHeight(int height) {
        if (this.availableHeight != height || this.style != NEIClientConfig.getJEIStyleRecipeCatalysts()) {
            this.availableHeight = height;
            createWidgets();
        }
    }

    public int getAvailableHeight() {
        return this.availableHeight;
    }

    @Override
    public boolean isVisible() {
        return this.showWidget && NEIClientConfig.getJEIStyleRecipeCatalysts() != 0;
    }

    public int getColumnCount() {
        if (NEIClientConfig.getJEIStyleRecipeCatalysts() == 1) {
            final int maxItemsPerColumn = Math
                    .max(0, this.availableHeight - COLUMNS_BOTTOM_INSET - 2 - BORDER_PADDING * 2) / SLOT_SIZE;
            return maxItemsPerColumn > 0 ? NEIServerUtils.divideCeil(this.items.size(), maxItemsPerColumn) : 0;
        }
        return 1;
    }

    public int getRowCount() {
        final int columnCount = getColumnCount();
        return columnCount > 0 ? NEIServerUtils.divideCeil(this.items.size(), columnCount) : 0;
    }

    protected void createWidgets() {
        final int columns = getColumnCount();
        final int rows = getRowCount();

        setVerticalScroll(null);
        setPaddingInline(BORDER_PADDING, BORDER_PADDING - 3);

        this.style = NEIClientConfig.getJEIStyleRecipeCatalysts();
        this.h = rows * SLOT_SIZE + this.paddingBlockStart + this.paddingBlockEnd;
        this.w = columns * SLOT_SIZE + this.paddingInlineStart + this.paddingInlineEnd;

        if (this.style == 2
                && rows * SLOT_SIZE + this.paddingBlockStart + this.paddingBlockEnd > this.availableHeight) {
            this.h = Math.min(this.h, this.availableHeight);
            this.w += VERTICAL_SCROLLBAR.getTrackWidth();

            setVerticalScroll(VERTICAL_SCROLLBAR);
            setPaddingInline(this.paddingInlineStart + VERTICAL_SCROLLBAR.getTrackWidth(), this.paddingInlineEnd);
        }

        this.showWidget = rows * columns > 0;

        if (rows == 0 || columns == 0) {
            clear();
            return;
        }

        final int visibleWidth = getVisibleWidth();
        final List<Widget> widgets = new ArrayList<>();
        int index = 0;

        for (PositionedStack pStack : this.items) {
            pStack.relx = visibleWidth - SLOT_SIZE - (index / rows) * SLOT_SIZE;
            pStack.rely = (index % rows) * SLOT_SIZE;
            index++;

            widgets.add(new ItemStackWidget(pStack));
        }

        setWidgets(widgets);
    }

    @Override
    public void draw(int mx, int my) {
        if (!this.showWidget) return;

        GL11.glColor4f(1, 1, 1, 1);

        if (this.h < this.availableHeight) {
            UIRecipeTexture.CATALYST_TAB_JOINED.draw(this.x, this.y, this.w, this.h);
        } else {
            UIRecipeTexture.CATALYST_TAB.draw(this.x, this.y, this.w, this.h);
        }

        UIRecipeTexture.CATALYST_SLOT.draw(
                this.x + this.paddingInlineStart - 1,
                this.y + this.paddingBlockStart - 1,
                this.w - this.paddingInlineStart - this.paddingInlineEnd + 2,
                this.h - this.paddingBlockStart - this.paddingBlockEnd + 2);

        super.draw(mx, my);
    }

    public PositionedStack getPositionedStackMouseOver(int mx, int my) {
        final Widget widget = getWidgetUnderMouse(mx, my);

        if (widget instanceof ItemStackWidget stackWidget) {
            return stackWidget.pStack;
        }

        return null;
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
