package codechicken.nei.recipe;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.opengl.GL11;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.NEIServerUtils;
import codechicken.nei.PositionedStack;
import codechicken.nei.Widget;
import codechicken.nei.api.ShortcutInputHandler;
import codechicken.nei.drawable.DrawableBuilder;
import codechicken.nei.drawable.DrawableResource;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.scroll.ScrollBar;
import codechicken.nei.scroll.ScrollContainer;

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
    private static final int TRANSPARENCY_BORDER = 4;
    private static final DrawableResource BG_TEXTURE = new DrawableBuilder(
            "nei:textures/catalyst_tab.png",
            0,
            0,
            28 + TRANSPARENCY_BORDER * 2,
            28 + TRANSPARENCY_BORDER * 2).setTextureSize(28 + TRANSPARENCY_BORDER * 2, 28 + TRANSPARENCY_BORDER * 2)
                    .build();

    private static final DrawableResource FG_TEXTURE = new DrawableBuilder("nei:textures/slot.png", 0, 0, 18, 18)
            .setTextureSize(18, 18).build();

    private static final ScrollBar VERTICAL_SCROLLBAR = ScrollBar.defaultVerticalBar()
            .setOverflowType(ScrollBar.OverflowType.AUTO).setTrackPadding(0, BORDER_PADDING, -13, BORDER_PADDING)
            .setScrollPlace(ScrollBar.ScrollPlace.START);

    private PositionedStack[] items = new PositionedStack[0];
    private boolean showWidget = false;
    private int availableHeight = 0;

    public GuiRecipeCatalyst() {
        setPaddingBlock(BORDER_PADDING + 1, BORDER_PADDING + 1);
    }

    public void setCatalysts(List<PositionedStack> items) {
        this.items = items.toArray(new PositionedStack[0]);
        createWidgets();
    }

    public void setAvailableHeight(int height) {
        if (this.availableHeight != height) {
            this.availableHeight = height;
            createWidgets();
        }
    }

    public int getAvailableHeight() {
        return this.availableHeight;
    }

    public boolean isShowWidget() {
        return this.showWidget;
    }

    public int getColumnCount() {
        if (NEIClientConfig.getJEIStyleRecipeCatalysts() == 1) {
            final int maxItemsPerColumn = Math.max(0, this.availableHeight - 2 - BORDER_PADDING * 2) / SLOT_SIZE;
            return maxItemsPerColumn > 0 ? NEIServerUtils.divideCeil(this.items.length, maxItemsPerColumn) : 0;
        }
        return 1;
    }

    public int getRowCount() {
        final int columnCount = getColumnCount();
        return columnCount > 0 ? NEIServerUtils.divideCeil(this.items.length, columnCount) : 0;
    }

    protected void createWidgets() {
        final int columns = getColumnCount();
        final int rows = getRowCount();

        setVerticalScroll(null);
        setPaddingInline(BORDER_PADDING + 1, BORDER_PADDING - 3);

        this.h = rows * SLOT_SIZE + this.paddingBlockStart + this.paddingBlockEnd;
        this.w = columns * SLOT_SIZE + this.paddingInlineStart + this.paddingInlineEnd;

        if (NEIClientConfig.getJEIStyleRecipeCatalysts() == 2
                && rows * SLOT_SIZE + this.paddingBlockStart + this.paddingBlockEnd > this.availableHeight) {
            this.h = Math.min(this.h, this.availableHeight);
            this.w += VERTICAL_SCROLLBAR.getTrackWidth();

            setVerticalScroll(VERTICAL_SCROLLBAR);
            setPaddingInline(BORDER_PADDING + 1 + VERTICAL_SCROLLBAR.getTrackWidth(), this.paddingInlineEnd);
        }

        this.showWidget = rows * columns > 0;

        if (rows == 0 || columns == 0) {
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
        BG_TEXTURE.draw(
                this.x - TRANSPARENCY_BORDER,
                this.y - TRANSPARENCY_BORDER,
                this.w + TRANSPARENCY_BORDER * 2,
                this.h + TRANSPARENCY_BORDER * 2,
                BORDER_PADDING + TRANSPARENCY_BORDER,
                BORDER_PADDING + TRANSPARENCY_BORDER,
                BORDER_PADDING + TRANSPARENCY_BORDER,
                BORDER_PADDING + TRANSPARENCY_BORDER);
        FG_TEXTURE.draw(
                this.x + this.paddingInlineStart - 1,
                this.y + this.paddingBlockStart - 1,
                this.w - this.paddingInlineStart - this.paddingInlineEnd + 2,
                this.h - this.paddingBlockStart - this.paddingBlockEnd + 2,
                1,
                1,
                1,
                1);

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
