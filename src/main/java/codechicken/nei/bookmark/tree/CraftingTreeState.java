package codechicken.nei.bookmark.tree;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import codechicken.nei.NEIClientConfig;
import codechicken.nei.bookmark.BookmarkItem;

class CraftingTreeState {

    enum CollapseMode {
        EXPANDED,
        SMART,
        ALL
    }

    enum StatsSection {
        INGREDIENTS_NEEDED,
        INGREDIENTS_AVAILABLE,
        CRAFTING_NEEDED,
        CRAFTING_AVAILABLE,
        RESULTS,
        REMAINDERS,
        HANDLERS
    }

    private static final String KEY_STATS_VISIBLE = "craftingTree.statsVisible";
    private static final String KEY_STATS_SECTIONS = "craftingTree.statsSections";
    private static final String KEY_USE_INVENTORY_SNAPSHOT = "craftingTree.useInventorySnapshot";
    private static final String KEY_COLLAPSE_MODE = "craftingTree.collapseMode";

    public boolean statsPanelVisible = true;
    public final Set<StatsSection> visibleStatsSections = EnumSet.allOf(StatsSection.class);
    public boolean useInventorySnapshot = false;
    public CollapseMode collapseMode = CollapseMode.EXPANDED;

    public final Set<BookmarkItem> collapsedItems = new HashSet<>();
    public ItemTreeSlot replaceRecipeNode;

    public SearchResult searchItemResult;
    public SearchResult searchStatsResult;

    public void load() {
        this.statsPanelVisible = !NEIClientConfig.world.nbt.hasKey(KEY_STATS_VISIBLE)
                || NEIClientConfig.world.nbt.getBoolean(KEY_STATS_VISIBLE);

        final int savedSectionsMask = NEIClientConfig.world.nbt.hasKey(KEY_STATS_SECTIONS)
                ? NEIClientConfig.world.nbt.getInteger(KEY_STATS_SECTIONS)
                : -1;

        this.visibleStatsSections.clear();

        for (StatsSection section : StatsSection.values()) {
            if ((savedSectionsMask & (1 << section.ordinal())) != 0) {
                this.visibleStatsSections.add(section);
            }
        }

        this.useInventorySnapshot = NEIClientConfig.world.nbt.getBoolean(KEY_USE_INVENTORY_SNAPSHOT);

        final CollapseMode[] collapseModes = CollapseMode.values();
        final int savedCollapseMode = NEIClientConfig.world.nbt.getInteger(KEY_COLLAPSE_MODE);
        this.collapseMode = savedCollapseMode >= 0 && savedCollapseMode < collapseModes.length
                ? collapseModes[savedCollapseMode]
                : CollapseMode.EXPANDED;
        this.replaceRecipeNode = null;
    }

    public void toggleStatsPanelVisible() {
        this.statsPanelVisible = !this.statsPanelVisible;
        NEIClientConfig.world.nbt.setBoolean(KEY_STATS_VISIBLE, this.statsPanelVisible);
    }

    public void toggleStatsSection(StatsSection section) {

        if (!this.visibleStatsSections.remove(section)) {
            this.visibleStatsSections.add(section);
        }

        int mask = 0;

        for (StatsSection visibleSection : this.visibleStatsSections) {
            mask |= 1 << visibleSection.ordinal();
        }

        NEIClientConfig.world.nbt.setInteger(KEY_STATS_SECTIONS, mask);
    }

    public void toggleUseInventorySnapshot() {
        this.useInventorySnapshot = !this.useInventorySnapshot;
        NEIClientConfig.world.nbt.setBoolean(KEY_USE_INVENTORY_SNAPSHOT, this.useInventorySnapshot);
    }

    public void cycleCollapseMode() {
        final CollapseMode[] collapseModes = CollapseMode.values();
        this.collapseMode = collapseModes[(this.collapseMode.ordinal() + 1) % collapseModes.length];
        NEIClientConfig.world.nbt.setInteger(KEY_COLLAPSE_MODE, this.collapseMode.ordinal());
    }

    public SearchResult activeSearchResult() {
        return this.searchItemResult != null ? this.searchItemResult : this.searchStatsResult;
    }

}
