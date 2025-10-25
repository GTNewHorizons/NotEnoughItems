package codechicken.nei.scroll;

import java.awt.Color;

import org.lwjgl.opengl.GL11;

import codechicken.lib.vec.Rectangle4i;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.drawable.DrawableBuilder;
import codechicken.nei.drawable.DrawableResource;

public class ScrollBar {

    /** The minimum length of the scrollbar cursor. */
    private static final int MINIMUM_THUMB_SIZE = 6;

    private static final Color BACKGROUND_COLOR = new Color(0x90B1D4E0);
    private static final Color THUMB_COLOR = new Color(0xF0145DA0);
    private static final Color THUMB_SELECTED_COLOR = new Color(0xF02E8BC0);

    public enum ScrollType {
        HORIZONTAL,
        VERTICAL
    }

    public enum OverflowType {
        NONE,
        AUTO,
        SCROLL,
        OVERLAY
    }

    public enum ScrollPlace {
        START,
        END
    }

    private ScrollType scrollType = ScrollType.VERTICAL;
    private OverflowType overflowType = OverflowType.SCROLL;
    private ScrollPlace scrollPlace = ScrollPlace.END;
    private float handleClickOffset = -1;

    private int scrollbarSize = 6;
    private int thumbPadding = 0;

    private int padding = 0;
    private int marginStart = 0;
    private int marginEnd = 0;

    private DrawableResource thumbTexture;
    private int thumbSliceStart;
    private int thumbSliceEnd;

    private DrawableResource trackTexture;
    private int trackSliceStart;
    private int trackSliceEnd;

    public static ScrollBar defaultScrollBar() {
        return new ScrollBar();
    }

    public static ScrollBar defaultVerticalBar() {
        return new ScrollBar().setScrollbarSize(8)
                .setTrackTexture(new DrawableBuilder("nei:textures/nei_sprites.png", 28, 95, 11, 11).build(), 3, 4)
                .setThumbTexture(new DrawableBuilder("nei:textures/nei_sprites.png", 21, 96, 7, 9).build(), 3, 2)
                .setThumbPadding(1);
    }

    public static ScrollBar defaultHorizontalBar() {
        return new ScrollBar().setScrollbarSize(8).setOverflowType(OverflowType.SCROLL).setScrollPlace(ScrollPlace.END)
                .setTrackTexture(new DrawableBuilder("nei:textures/nei_sprites.png", 0, 97, 11, 8).build(), 3, 4)
                .setThumbTexture(new DrawableBuilder("nei:textures/nei_sprites.png", 12, 97, 9, 7).build(), 3, 2)
                .setThumbPadding(1);
    }

    public ScrollBar setScrollbarSize(int scrollbarSize) {
        this.scrollbarSize = scrollbarSize;
        return this;
    }

    public ScrollBar setThumbPadding(int thumbPadding) {
        this.thumbPadding = thumbPadding;
        return this;
    }

    public ScrollBar setPadding(int padding) {
        this.padding = padding;
        return this;
    }

    public int getPadding() {
        return this.padding;
    }

    public ScrollBar setMarginStart(int start) {
        this.marginStart = start;
        return this;
    }

    public int getMarginStart() {
        return this.marginStart;
    }

    public ScrollBar setMarginEnd(int end) {
        this.marginEnd = end;
        return this;
    }

    public int getMarginEnd() {
        return this.marginEnd;
    }

    public int getScrollbarSize() {
        return this.scrollbarSize;
    }

    public ScrollBar setThumbTexture(DrawableResource texture, int sliceStart, int sliceEnd) {
        this.thumbTexture = texture;
        this.thumbSliceStart = sliceStart;
        this.thumbSliceEnd = sliceEnd;
        return this;
    }

    public ScrollBar setTrackTexture(DrawableResource texture, int sliceStart, int sliceEnd) {
        this.trackTexture = texture;
        this.trackSliceStart = sliceStart;
        this.trackSliceEnd = sliceEnd;
        return this;
    }

    public ScrollBar setOverflowType(OverflowType overflowType) {
        this.overflowType = overflowType;
        return this;
    }

    public OverflowType getOverflowType() {
        return this.overflowType;
    }

    public ScrollBar setScrollPlace(ScrollPlace scrollPlace) {
        this.scrollPlace = scrollPlace;
        return this;
    }

    public ScrollPlace getScrollPlace() {
        return this.scrollPlace;
    }

    public ScrollBar setScrollType(ScrollType scrollType) {
        this.scrollType = scrollType;
        return this;
    }

    private int getActualSize(ScrollContainer container) {
        if (this.scrollType == ScrollType.HORIZONTAL) {
            return container.getActualWidth();
        } else {
            return container.getActualHeight();
        }
    }

    public int getVisibleSize(ScrollContainer container) {
        if (this.scrollType == ScrollType.HORIZONTAL) {
            return container.getVisibleWidth();
        } else {
            return container.getVisibleHeight();
        }
    }

    private int getMaxThumbSize(ScrollContainer container) {
        return getMaxTrackSize(container) - this.thumbPadding * 2;
    }

    private int getMaxTrackSize(ScrollContainer container) {
        if (this.scrollType == ScrollType.HORIZONTAL) {
            return container.w - this.marginStart - this.marginEnd;
        } else {
            return container.h - this.marginStart - this.marginEnd;
        }
    }

    private int getMinThumbSize() {

        if (this.thumbTexture != null) {
            return this.scrollType == ScrollType.HORIZONTAL ? this.thumbTexture.getWidth()
                    : this.thumbTexture.getHeight();
        }

        return MINIMUM_THUMB_SIZE;
    }

    public float getScrollOffset(ScrollContainer container) {
        final float thumbSize = getMaxThumbSize(container) - calculateMainAxisSize(container);

        if (this.scrollType == ScrollType.HORIZONTAL) {
            return container.getHorizontalScrollOffset()
                    / (float) (container.getActualWidth() - container.getVisibleWidth())
                    * thumbSize;
        } else {
            return container.getVerticalScrollOffset()
                    / (float) (container.getActualHeight() - container.getVisibleHeight())
                    * thumbSize;
        }
    }

    public float calculateMainAxisSize(ScrollContainer container) {
        final int visibleSize = getVisibleSize(container);
        final int actualSize = getActualSize(container);
        final int trackSize = getMaxThumbSize(container);

        return Math.max(Math.min(trackSize, (trackSize / (float) actualSize) * visibleSize), getMinThumbSize());
    }

    public Rectangle4i trackBounds(ScrollContainer container) {

        if (this.scrollType == ScrollType.HORIZONTAL) {

            if (this.scrollPlace == ScrollPlace.START) {
                return new Rectangle4i(
                        container.x + this.marginStart,
                        container.y - this.padding - this.scrollbarSize,
                        getMaxTrackSize(container),
                        this.scrollbarSize);
            } else {
                return new Rectangle4i(
                        container.x + this.marginStart,
                        container.y + container.h + this.padding,
                        getMaxTrackSize(container),
                        this.scrollbarSize);
            }

        } else {

            if (this.scrollPlace == ScrollPlace.START) {
                return new Rectangle4i(
                        container.x - this.padding - this.scrollbarSize,
                        container.y + this.marginStart,
                        this.scrollbarSize,
                        getMaxTrackSize(container));
            } else {
                return new Rectangle4i(
                        container.x + container.w + this.padding,
                        container.y + this.marginStart,
                        this.scrollbarSize,
                        getMaxTrackSize(container));
            }

        }

    }

    public Rectangle4i thumbBounds(ScrollContainer container) {
        final int mainAxisSize = (int) calculateMainAxisSize(container);
        final int offset = (int) Math.ceil(getScrollOffset(container));
        final Rectangle4i bounds = trackBounds(container);

        if (this.scrollType == ScrollType.HORIZONTAL) {
            return new Rectangle4i(bounds.x + this.thumbPadding + offset, bounds.y, mainAxisSize, bounds.h);
        } else {
            return new Rectangle4i(bounds.x, bounds.y + this.thumbPadding + offset, bounds.w, mainAxisSize);
        }
    }

    public boolean setScrollOffset(ScrollContainer container, int offset) {
        if (this.scrollType == ScrollType.HORIZONTAL) {
            return container.setHorizontalScrollOffset(offset);
        } else {
            return container.setVerticalScrollOffset(offset);
        }
    }

    public boolean onClick(ScrollContainer container, int mx, int my, int button) {
        final Rectangle4i bounds = trackBounds(container);

        if (bounds.contains(mx, my)) {
            final float mainAxisSize = calculateMainAxisSize(container);
            final float thumbSize = getMaxThumbSize(container) - mainAxisSize;
            final Rectangle4i thumbBounds = thumbBounds(container);

            if (this.scrollType == ScrollType.HORIZONTAL) {
                final int relativeX = mx - bounds.x - this.thumbPadding;

                if (mx < thumbBounds.x || mx > thumbBounds.x + thumbBounds.w) {
                    this.handleClickOffset = mainAxisSize / 2;
                } else {
                    this.handleClickOffset = mx - thumbBounds.x;
                }

                final float newOffset = ((relativeX - this.handleClickOffset)
                        * (container.getActualWidth() - container.getVisibleWidth())) / thumbSize;
                container.setHorizontalScrollOffset((int) newOffset);
            } else {
                final int relativeY = my - bounds.y - this.thumbPadding;

                if (my < thumbBounds.y || my > thumbBounds.y + thumbBounds.h) {
                    this.handleClickOffset = mainAxisSize / 2f;
                } else {
                    this.handleClickOffset = my - thumbBounds.y;
                }

                final float newOffset = ((relativeY - this.handleClickOffset)
                        * (container.getActualHeight() - container.getVisibleHeight())) / thumbSize;

                container.setVerticalScrollOffset((int) newOffset);
            }

            return true;
        }

        return false;
    }

    public boolean onMouseDragged(ScrollContainer container, int mx, int my, int buttonId, long heldTime) {

        if (this.handleClickOffset >= 0) {
            final Rectangle4i bounds = trackBounds(container);
            final float mainAxisSize = calculateMainAxisSize(container);
            final float thumbSize = getMaxThumbSize(container) - mainAxisSize;

            if (this.scrollType == ScrollType.HORIZONTAL) {
                final int relativeX = mx - bounds.x - this.thumbPadding;
                final float newOffset = ((relativeX - this.handleClickOffset)
                        * (container.getActualWidth() - container.getVisibleWidth())) / thumbSize;
                container.setHorizontalScrollOffset((int) newOffset);
            } else {
                int relativeY = my - bounds.y - this.thumbPadding;
                final float newOffset = ((relativeY - this.handleClickOffset)
                        * (container.getActualHeight() - container.getVisibleHeight())) / thumbSize;

                container.setVerticalScrollOffset((int) newOffset);
            }

            return true;
        }

        return false;
    }

    public boolean onClickReleased(ScrollContainer container, int mx, int my, int buttonId) {

        if (this.handleClickOffset >= 0) {
            this.handleClickOffset = -1;
            return true;
        }

        return false;
    }

    public boolean isMouseDragged() {
        return this.handleClickOffset >= 0;
    }

    public void draw(ScrollContainer container, int mx, int my, float opacity) {

        if (opacity <= 0 && this.overflowType == OverflowType.OVERLAY) {
            return;
        }

        if (this.overflowType != OverflowType.OVERLAY) {
            opacity = 1;
        }

        final Rectangle4i trackBounds = trackBounds(container);
        final Rectangle4i thumbBounds = thumbBounds(container);
        final boolean selected = isMouseDragged() || thumbBounds.contains(mx, my);
        final boolean disabled = getVisibleSize(container) >= getActualSize(container);

        if (this.trackTexture != null) {
            drawTexture(this.trackTexture, this.trackSliceStart, this.trackSliceEnd, trackBounds, false, opacity);
        } else {
            drawTrackRect(container, trackBounds, opacity);
        }

        if (this.thumbTexture != null) {
            drawTexture(this.thumbTexture, this.thumbSliceStart, this.thumbSliceEnd, thumbBounds, disabled, opacity);
        } else {
            drawThumbRect(container, thumbBounds, selected, opacity);
        }
    }

    private void drawTrackRect(ScrollContainer container, Rectangle4i bounds, float opacity) {
        final float trackSize = scrollbarSize / 3f;
        final Color trackColor = new Color(
                BACKGROUND_COLOR.getRed(),
                BACKGROUND_COLOR.getGreen(),
                BACKGROUND_COLOR.getBlue(),
                (int) (BACKGROUND_COLOR.getAlpha() * opacity));

        if (this.scrollType == ScrollType.HORIZONTAL) {
            NEIClientUtils.drawRect(bounds.x, bounds.y + trackSize, bounds.w, trackSize, trackColor);
        } else {
            NEIClientUtils.drawRect(bounds.x + trackSize, bounds.y, trackSize, bounds.h, trackColor);
        }
    }

    private void drawThumbRect(ScrollContainer container, Rectangle4i bounds, boolean selected, float opacity) {
        final Color color = selected ? THUMB_SELECTED_COLOR : THUMB_COLOR;
        final Color thumbColor = new Color(
                color.getRed(),
                color.getGreen(),
                color.getBlue(),
                (int) (color.getAlpha() * opacity));

        NEIClientUtils.drawRect(bounds.x, bounds.y, bounds.w, bounds.h, thumbColor);
    }

    private void drawTexture(DrawableResource texture, int sliceStart, int sliceEnd, Rectangle4i bounds,
            boolean disabled, float opacity) {
        final float c = disabled ? 0.65f : 1f;

        GL11.glColor4f(c, c, c, opacity);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        if (this.scrollType == ScrollType.HORIZONTAL) {

            if (this.scrollPlace == ScrollPlace.START) {
                texture.draw(
                        bounds.x,
                        bounds.y + (bounds.h - texture.getHeight()) / 2,
                        bounds.w,
                        texture.getHeight(),
                        sliceStart,
                        sliceEnd,
                        0,
                        0);
            } else {
                texture.draw(bounds.x, bounds.y, bounds.w, texture.getHeight(), sliceStart, sliceEnd, 0, 0);
            }

        } else {

            if (this.scrollPlace == ScrollPlace.START) {
                texture.draw(
                        bounds.x + (bounds.w - texture.getWidth()) / 2,
                        bounds.y,
                        texture.getWidth(),
                        bounds.h,
                        0,
                        0,
                        sliceStart,
                        sliceEnd);
            } else {
                texture.draw(bounds.x, bounds.y, texture.getWidth(), bounds.h, 0, 0, sliceStart, sliceEnd);
            }
        }

    }

}
