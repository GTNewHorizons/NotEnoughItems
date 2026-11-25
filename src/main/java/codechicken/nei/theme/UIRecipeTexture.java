package codechicken.nei.theme;

import codechicken.nei.drawable.DrawableResource;

public final class UIRecipeTexture {

    public static final DrawableResource WINDOW = UISprite
            .of("recipe/window", 176, 109, new int[] { 6, 6, 6, 6 }, new int[] { 5, 33, 5, 5 });
    public static final DrawableResource WIDGET = UISprite
            .of("recipe/widget", 176, 95, new int[] { 6, 6, 6, 6 }, new int[] { 5, 19, 5, 5 });

    private static final DrawableResource[] TAB = UISprite.frames("recipe/tab", 24, 27, 2);
    private static final DrawableResource[] NAV = UISprite.frames("recipe/icon_nav", 12, 12, 2);
    private static final DrawableResource[] FAVORITE = UISprite.frames("recipe/icon_favorite", 12, 12, 2);
    private static final DrawableResource[] OVERLAY = UISprite.frames("recipe/icon_overlay", 12, 12, 3);
    private static final DrawableResource[] ITEM_STATUS = UISprite.frames("recipe/item_status", 12, 12, 2);

    public static final DrawableResource TAB_SELECTED = TAB[0];
    public static final DrawableResource TAB_UNSELECTED = TAB[1];

    private static final DrawableResource[] CATALYST = UISprite
            .frames("recipe/catalyst_tab", 23, 28, 2, new int[] { 6, 6, 0, 6 }, new int[] { 5, 5, 5, 5 });

    public static final DrawableResource CATALYST_TAB = CATALYST[0];
    public static final DrawableResource CATALYST_TAB_JOINED = CATALYST[1];
    public static final DrawableResource CATALYST_SLOT = UISprite
            .of("recipe/catalyst_slot", 18, 18, UISprite.DEFAULT_SPACE, new int[] { 1, 1, 1, 1 });

    public static final DrawableResource ICON_PREV = NAV[0];
    public static final DrawableResource ICON_NEXT = NAV[1];

    public static final DrawableResource ICON_SEARCH = UISprite.of("recipe/icon_search", 12, 12);
    public static final DrawableResource ICON_PAUSE = UISprite.of("recipe/icon_pause", 12, 12);

    public static final DrawableResource ICON_FAVORITE_OFF = FAVORITE[0];
    public static final DrawableResource ICON_FAVORITE_ON = FAVORITE[1];

    public static final DrawableResource ICON_OVERLAY = OVERLAY[0];
    public static final DrawableResource ICON_OVERLAY_FILL = OVERLAY[1];
    public static final DrawableResource ICON_OVERLAY_FILL_ERROR = OVERLAY[2];

    public static final DrawableResource ITEM_MISSING = ITEM_STATUS[0];
    public static final DrawableResource ITEM_PRESENT = ITEM_STATUS[1];

    private UIRecipeTexture() {}

}
