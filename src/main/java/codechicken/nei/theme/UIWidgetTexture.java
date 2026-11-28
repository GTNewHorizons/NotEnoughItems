package codechicken.nei.theme;

import codechicken.nei.drawable.Drawable;
import codechicken.nei.scroll.ScrollBar;
import codechicken.nei.scroll.ScrollBar.ScrollType;

public final class UIWidgetTexture {

    private static final Drawable[] BTN = UISprite
            .frames("widgets/button", 12, 12, 4, UISprite.DEFAULT_SPACE, new int[] { 2, 2, 2, 3 });

    public static final Drawable BTN_NORMAL = BTN[0];
    public static final Drawable BTN_HOVER = BTN[1];
    public static final Drawable BTN_ACTIVE = BTN[2];
    public static final Drawable BTN_DISABLED = BTN[3];
    public static final ButtonSkin BUTTON = new ButtonSkin(BTN_NORMAL, BTN_HOVER, BTN_ACTIVE, BTN_DISABLED);

    private static final int[] THUMB_INSET = new int[] { 1, 1, 1, 1 };

    // frame 0 is the track, frame 1 the thumb drawn inside a transparent THUMB_INSET border of the same frame size
    public static final Drawable SCROLL_V_TRACK = UISprite
            .frames("widgets/scroll_v", 8, 11, 2, UISprite.DEFAULT_SPACE, new int[] { 1, 1, 1, 1 })[0];
    public static final Drawable SCROLL_V_THUMB = UISprite
            .frames("widgets/scroll_v", 6, 9, 2, THUMB_INSET, new int[] { 1, 1, 1, 2 })[1];

    public static final Drawable SCROLL_H_TRACK = UISprite
            .frames("widgets/scroll_h", 11, 8, 2, UISprite.DEFAULT_SPACE, new int[] { 1, 1, 1, 1 })[0];
    public static final Drawable SCROLL_H_THUMB = UISprite
            .frames("widgets/scroll_h", 9, 6, 2, THUMB_INSET, new int[] { 1, 1, 2, 1 })[1];

    public static final int SCROLL_TRACK_WIDTH = 8;

    private UIWidgetTexture() {}

    public static ScrollBar verticalScrollBar() {
        return new ScrollBar().setTrackWidth(SCROLL_TRACK_WIDTH).setTrackTexture(SCROLL_V_TRACK)
                .setThumbTexture(SCROLL_V_THUMB).setThumbPadding(1, 1, 1, 1);
    }

    public static ScrollBar horizontalScrollBar() {
        return new ScrollBar().setScrollType(ScrollType.HORIZONTAL).setTrackWidth(SCROLL_TRACK_WIDTH)
                .setTrackTexture(SCROLL_H_TRACK).setThumbTexture(SCROLL_H_THUMB).setThumbPadding(1, 1, 1, 1);
    }

}
