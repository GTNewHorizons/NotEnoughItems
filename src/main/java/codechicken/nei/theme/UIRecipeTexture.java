package codechicken.nei.theme;

import codechicken.nei.drawable.Drawable;

public final class UIRecipeTexture {

    public static final Drawable WINDOW = UISprite
            .of("recipe/window", 176, 109, new int[] { 6, 6, 6, 6 }, new int[] { 5, 33, 5, 5 });
    public static final Drawable WIDGET = UISprite
            .of("recipe/widget", 176, 95, new int[] { 6, 6, 6, 6 }, new int[] { 5, 19, 5, 5 });

    private static final Drawable[] TAB = UISprite.frames("recipe/tab", 24, 27, 2);
    private static final Drawable[] NAV = UISprite.frames("recipe/icon_nav", 12, 12, 2);
    private static final Drawable[] OVERLAY = UISprite.frames("recipe/icon_overlay", 12, 12, 2);
    private static final Drawable[] ITEM_STATUS = UISprite.frames("recipe/item_status", 12, 12, 2);

    public static final Drawable TAB_SELECTED = TAB[0];
    public static final Drawable TAB_UNSELECTED = TAB[1];

    private static final Drawable[] CATALYST = UISprite
            .frames("recipe/catalyst_tab", 23, 28, 2, new int[] { 6, 6, 0, 6 }, new int[] { 5, 5, 5, 5 });

    public static final Drawable CATALYST_TAB = CATALYST[0];
    public static final Drawable CATALYST_TAB_JOINED = CATALYST[1];
    public static final Drawable CATALYST_SLOT = UISprite
            .of("recipe/catalyst_slot", 18, 18, UISprite.DEFAULT_SPACE, new int[] { 1, 1, 1, 1 });

    public static final Drawable ICON_PREV = NAV[0];
    public static final Drawable ICON_NEXT = NAV[1];

    public static final Drawable ICON_SEARCH = UISprite.of("recipe/icon_search", 12, 12);
    public static final Drawable ICON_PAUSE = UISprite.of("recipe/icon_pause", 12, 12);

    public static final Drawable ICON_FAVORITE = UISprite.of("recipe/icon_favorite", 12, 12);

    public static final Drawable ICON_OVERLAY = OVERLAY[0];
    public static final Drawable ICON_OVERLAY_FILL = OVERLAY[1];

    public static final Drawable ITEM_MISSING = ITEM_STATUS[0];
    public static final Drawable ITEM_PRESENT = ITEM_STATUS[1];

    private UIRecipeTexture() {}

}
