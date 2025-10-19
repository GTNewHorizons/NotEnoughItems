package codechicken.nei.scroll;

import java.awt.Point;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import codechicken.lib.gui.GuiDraw;
import codechicken.lib.vec.Rectangle4i;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.Widget;
import codechicken.nei.WidgetContainer;
import codechicken.nei.scroll.ScrollBar.OverflowType;
import codechicken.nei.scroll.ScrollBar.ScrollType;

public class ScrollContainer extends WidgetContainer {

    /** The mouse scroll wheel scroll speed */
    protected static final int MOUSE_SCROLL_SPEED = 16;

    /** The keyboard arrow keys scroll speed */
    protected static final int KEYBOARD_SCROLL_SPEED = 36;

    /** The scrollbar will fade away over this many ticks */
    protected static final int FADE_TICKS = 24;

    protected int xScroll = 0;
    protected int yScroll = 0;

    protected int actualWidth = 0;
    protected int actualHeight = 0;

    protected int paddingInlineStart = 0;
    protected int paddingInlineEnd = 0;
    protected int paddingBlockStart = 0;
    protected int paddingBlockEnd = 0;

    protected final Map<Widget, Point> relativePostion = new HashMap<>();
    protected ScrollBar horizontalScrollBar;
    protected ScrollBar verticalScrollBar;
    protected int fade = 0;

    public Rectangle4i boundsOutside() {
        int width = this.w;
        int height = this.h;
        int x = this.x;
        int y = this.y;

        if (canScrollVertical() && (this.verticalScrollBar.getOverflowType() == OverflowType.SCROLL
                || this.verticalScrollBar.getOverflowType() == OverflowType.AUTO)) {
            final Rectangle4i trackBounds = this.verticalScrollBar.trackBounds(this);
            x = Math.min(x, trackBounds.x);
            y = Math.min(y, trackBounds.y);
            height = Math.max(this.y + height, trackBounds.y + trackBounds.h) - y;
            width = Math.max(this.x + width, trackBounds.x + trackBounds.w) - x;
        }

        if (canScrollHorizontal() && (this.horizontalScrollBar.getOverflowType() == OverflowType.SCROLL
                || this.horizontalScrollBar.getOverflowType() == OverflowType.AUTO)) {
            final Rectangle4i trackBounds = this.horizontalScrollBar.trackBounds(this);
            x = Math.min(x, trackBounds.x);
            y = Math.min(y, trackBounds.y);
            height = Math.max(this.y + height, trackBounds.y + trackBounds.h) - y;
            width = Math.max(this.x + width, trackBounds.x + trackBounds.w) - x;
        }

        return new Rectangle4i(x, y, width, height);
    }

    public Rectangle4i boundsInside() {
        return new Rectangle4i(
                x + this.paddingInlineStart,
                y + this.paddingBlockStart,
                getVisibleWidth(),
                getVisibleHeight());
    }

    @Override
    public void clear() {
        super.clear();
        this.relativePostion.clear();
    }

    @Override
    public boolean removeIf(Predicate<Widget> filter) {
        this.relativePostion.keySet().removeIf(filter);
        return super.removeIf(filter);
    }

    public void addWidget(Widget widget) {
        super.addWidget(widget);
        this.relativePostion.put(widget, new Point(widget.x, widget.y));
    }

    public Point getWidgetPosition(Widget widget) {
        return this.relativePostion.get(widget);
    }

    public void setPaddingInline(int start, int end) {
        this.paddingInlineStart = start;
        this.paddingInlineEnd = end;
    }

    public void setPaddingBlock(int start, int end) {
        this.paddingBlockStart = start;
        this.paddingBlockEnd = end;
    }

    public int getVisibleWidth() {
        return this.w - this.paddingInlineStart - this.paddingInlineEnd;
    }

    public int getActualWidth() {
        return this.actualWidth;
    }

    public boolean setHorizontalScrollOffset(int offset) {

        if (!canScrollHorizontal() && getVisibleWidth() < getActualWidth()) {
            offset = 0;
        }

        final int scroll = this.xScroll;
        this.xScroll = Math.max(0, Math.min(getActualWidth() - getVisibleWidth(), offset));

        if (scroll != this.xScroll) {

            for (Widget widget : this.widgets) {
                widget.x += scroll - this.xScroll;
            }

            this.fade = FADE_TICKS;
            return true;
        }

        return false;
    }

    public int getHorizontalScrollOffset() {
        return this.xScroll;
    }

    public boolean canScrollHorizontal() {
        return this.horizontalScrollBar != null && (this.horizontalScrollBar.getOverflowType() != OverflowType.NONE
                && getVisibleWidth() < getActualWidth()
                || this.horizontalScrollBar.getOverflowType() == OverflowType.SCROLL);
    }

    public ScrollBar getHorizontalScroll() {
        return this.horizontalScrollBar;
    }

    public ScrollContainer setHorizontalScroll() {
        return setHorizontalScroll(ScrollBar.defaultScrollBar());
    }

    public ScrollContainer setHorizontalScroll(ScrollBar scrollBar) {
        this.horizontalScrollBar = scrollBar;

        if (this.horizontalScrollBar != null) {
            this.horizontalScrollBar.setScrollType(ScrollType.HORIZONTAL);
        }

        return this;
    }

    public int getVisibleHeight() {
        return this.h - this.paddingBlockStart - this.paddingBlockEnd;
    }

    public int getActualHeight() {
        return this.actualHeight;
    }

    public boolean setVerticalScrollOffset(int offset) {

        if (!canScrollVertical() && getVisibleHeight() < getActualHeight()) {
            offset = 0;
        }

        final int scroll = this.yScroll;
        this.yScroll = Math.max(0, Math.min(getActualHeight() - getVisibleHeight(), offset));

        if (scroll != this.yScroll) {

            for (Widget widget : this.widgets) {
                widget.y += scroll - this.yScroll;
            }

            this.fade = FADE_TICKS;
            return true;
        }

        return false;
    }

    public int getVerticalScrollOffset() {
        return this.yScroll;
    }

    public boolean canScrollVertical() {
        return this.verticalScrollBar != null && (this.verticalScrollBar.getOverflowType() != OverflowType.NONE
                && getVisibleHeight() < getActualHeight()
                || this.verticalScrollBar.getOverflowType() == OverflowType.SCROLL);
    }

    public ScrollBar getVerticalScroll() {
        return this.verticalScrollBar;
    }

    public ScrollContainer setVerticalScroll() {
        return setVerticalScroll(ScrollBar.defaultScrollBar());
    }

    public ScrollContainer setVerticalScroll(ScrollBar scrollBar) {
        this.verticalScrollBar = scrollBar;

        if (this.verticalScrollBar != null) {
            this.verticalScrollBar.setScrollType(ScrollType.VERTICAL);
        }

        return this;
    }

    @Override
    public void draw(int mousex, int mousey) {
        final boolean canScrollVertical = canScrollVertical();
        final boolean canScrollHorizontal = canScrollHorizontal();

        if (canScrollVertical || canScrollHorizontal) {
            GuiHelper.useScissor(
                    this.x + this.paddingInlineStart,
                    this.y + this.paddingBlockStart,
                    this.w - this.paddingInlineStart - this.paddingInlineEnd,
                    this.h - this.paddingBlockStart - this.paddingBlockEnd,
                    () -> drawContent(mousex, mousey));

            GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_LIGHTING_BIT);

            if (canScrollVertical) {
                this.verticalScrollBar.draw(this, mousex, mousey, getScrollBarOpacity());
            }

            if (canScrollHorizontal) {
                this.horizontalScrollBar.draw(this, mousex, mousey, getScrollBarOpacity());
            }

            GL11.glPopAttrib();
        } else {
            drawContent(mousex, mousey);
        }

    }

    protected void drawContent(int mousex, int mousey) {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        GL11.glColor4f(1, 1, 1, 1);

        super.draw(mousex, mousey);

        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }

    private float getScrollBarOpacity() {
        return Math.min(2f * this.fade, FADE_TICKS) / FADE_TICKS;
    }

    @Override
    public void update() {
        this.actualHeight = 0;
        this.actualWidth = 0;

        for (Map.Entry<Widget, Point> entry : this.relativePostion.entrySet()) {
            entry.getKey().x = entry.getValue().x;
            entry.getKey().y = entry.getValue().y;
        }

        super.update();

        final int x = this.x + this.paddingInlineStart - this.xScroll;
        final int y = this.y + this.paddingBlockStart - this.yScroll;

        for (Widget widget : this.widgets) {
            this.actualWidth = Math.max(this.actualWidth, widget.x + widget.w);
            this.actualHeight = Math.max(this.actualHeight, widget.y + widget.h);

            widget.x += x;
            widget.y += y;
        }

        if (this.fade > 0) {
            final Point mouse = GuiDraw.getMousePosition();

            if (canScrollVertical() && (this.verticalScrollBar.isMouseDragged()
                    || this.verticalScrollBar.trackBounds(this).contains(mouse.x, mouse.y))) {
                this.fade = FADE_TICKS;
            } else if (canScrollHorizontal() && (this.horizontalScrollBar.isMouseDragged()
                    || this.horizontalScrollBar.trackBounds(this).contains(mouse.x, mouse.y))) {
                        this.fade = FADE_TICKS;
                    } else {
                        this.fade--;
                    }
        }

        setHorizontalScrollOffset(this.xScroll);
        setVerticalScrollOffset(this.yScroll);
    }

    @Override
    public boolean onMouseWheel(int direction, int mousex, int mousey) {

        if (super.onMouseWheel(direction, mousex, mousey)) {
            return true;
        }

        if (boundsOutside().contains(mousex, mousey)) {

            if (canScrollHorizontal() && (NEIClientUtils.shiftKey()
                    || this.horizontalScrollBar.trackBounds(this).contains(mousex, mousey))) {
                this.horizontalScrollBar.setScrollOffset(this, this.xScroll - direction * MOUSE_SCROLL_SPEED);
                return true;
            }

            if (canScrollVertical()) {
                this.verticalScrollBar.setScrollOffset(this, this.yScroll - direction * MOUSE_SCROLL_SPEED);
                return true;
            }

        }

        return false;
    }

    @Override
    public boolean handleClick(int mousex, int mousey, int button) {

        if (canScrollVertical() && this.verticalScrollBar.onClick(this, mousex, mousey, button)) {
            return true;
        }

        if (canScrollHorizontal() && this.horizontalScrollBar.onClick(this, mousex, mousey, button)) {
            return true;
        }

        if (super.handleClick(mousex, mousey, button)) {
            return true;
        }

        return false;
    }

    @Override
    public void mouseDragged(int mousex, int mousey, int button, long heldTime) {

        if (canScrollVertical() && this.verticalScrollBar.onMouseDragged(this, mousex, mousey, button, heldTime)) {
            return;
        }

        if (canScrollHorizontal() && this.horizontalScrollBar.onMouseDragged(this, mousex, mousey, button, heldTime)) {
            return;
        }

        super.mouseDragged(mousex, mousey, button, heldTime);
    }

    @Override
    public void mouseUp(int mousex, int mousey, int button) {

        if (canScrollVertical()) {
            this.verticalScrollBar.onClickReleased(this, mousex, mousey, button);
        }

        if (canScrollHorizontal()) {
            this.horizontalScrollBar.onClickReleased(this, mousex, mousey, button);
        }

        super.mouseUp(mousex, mousey, button);
    }

    @Override
    public boolean handleKeyPress(int keyCode, char keyChar) {

        if (this.isFocused()) {
            switch (keyCode) {
                case Keyboard.KEY_UP:
                    return canScrollVertical()
                            && this.verticalScrollBar.setScrollOffset(this, this.yScroll - KEYBOARD_SCROLL_SPEED);
                case Keyboard.KEY_DOWN:
                    return canScrollVertical()
                            && this.verticalScrollBar.setScrollOffset(this, this.yScroll + KEYBOARD_SCROLL_SPEED);
                case Keyboard.KEY_LEFT:
                    return canScrollHorizontal()
                            && this.horizontalScrollBar.setScrollOffset(this, this.xScroll - KEYBOARD_SCROLL_SPEED);
                case Keyboard.KEY_RIGHT:
                    return canScrollHorizontal()
                            && this.horizontalScrollBar.setScrollOffset(this, this.xScroll + KEYBOARD_SCROLL_SPEED);
            }
        }

        return super.handleKeyPress(keyCode, keyChar);
    }

}
