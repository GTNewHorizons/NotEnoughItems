package codechicken.nei.api;

@SuppressWarnings("unused")
public enum ClickContext {
    /**
     * ItemStack is dragged from ItemPanel, and not real item player holds
     */
    GHOST_DRAGGED_ITEM_PANEL,
    /**
     * ItemStack is dragged from BookmarkPanel, and not real item player holds
     */
    GHOST_DRAGGED_BOOKMARK_PANEL,
    /**
     * Normal click. ItemStack is actually held by player
     */
    REAL_ITEM;

    public boolean isGhostDragged() {
        return this == GHOST_DRAGGED_ITEM_PANEL || this == GHOST_DRAGGED_BOOKMARK_PANEL;
    }
}
