package codechicken.nei;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.regex.Pattern;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

import codechicken.nei.api.ItemFilter;
import codechicken.nei.api.ItemFilter.ItemFilterProvider;
import codechicken.nei.util.ItemUntranslator;

public class ItemList {

    /**
     * Fields are replaced atomically and contents never modified.
     */
    public static volatile List<ItemStack> items = new ArrayList<>();
    /**
     * Fields are replaced atomically and contents never modified.
     */
    public static volatile ListMultimap<Item, ItemStack> itemMap = ArrayListMultimap.create();
    /**
     * Updates to this should be synchronised on this
     */
    public static final List<ItemFilterProvider> itemFilterers = new LinkedList<>();
    public static final List<ItemsLoadedCallback> loadCallbacks = new LinkedList<>();

    /**
     * Unlike {@link LayoutManager#itemsLoaded}, this indicates whether item loading is actually finished or not.
     */
    public static boolean loadFinished;

    public static class EverythingItemFilter implements ItemFilter {

        @Override
        public boolean matches(ItemStack item) {
            return true;
        }
    }

    public static class NothingItemFilter implements ItemFilter {

        @Override
        public boolean matches(ItemStack item) {
            return false;
        }
    }

    public static class NegatedItemFilter implements ItemFilter {

        public ItemFilter filter;

        public NegatedItemFilter(ItemFilter filter) {
            this.filter = filter;
        }

        @Override
        public boolean matches(ItemStack item) {
            return this.filter == null || !this.filter.matches(item);
        }
    }

    public static class PatternItemFilter implements ItemFilter {

        public Pattern pattern;

        public PatternItemFilter(Pattern pattern) {
            this.pattern = pattern;
        }

        @Override
        public boolean matches(ItemStack item) {
            String displayName = EnumChatFormatting.getTextWithoutFormattingCodes(item.getDisplayName());

            if (displayName != null && !displayName.isEmpty() && this.pattern.matcher(displayName).find()) {
                return true;
            }

            displayName = ItemUntranslator.getInstance().getItemStackDisplayName(item);

            if (!displayName.isEmpty() && this.pattern.matcher(displayName).find()) {
                return true;
            }

            if (item.hasDisplayName()) {
                displayName = EnumChatFormatting
                        .getTextWithoutFormattingCodes(item.getItem().getItemStackDisplayName(item));

                return displayName != null && !displayName.isEmpty() && this.pattern.matcher(displayName).find();
            }

            return false;
        }
    }

    public static class AllMultiItemFilter implements ItemFilter {

        public List<ItemFilter> filters;

        public AllMultiItemFilter(List<ItemFilter> filters) {
            this.filters = filters;
        }

        public AllMultiItemFilter(ItemFilter... filters) {
            this(new LinkedList<>(Arrays.asList(filters)));
        }

        public AllMultiItemFilter() {
            this(new LinkedList<>());
        }

        @Override
        public boolean matches(ItemStack item) {
            for (ItemFilter filter : filters) try {
                if (filter != null && !filter.matches(item)) return false;
            } catch (Exception e) {
                NEIClientConfig.logger
                        .error("Exception filtering " + item + " with " + filter + " (" + e.getMessage() + ")", e);
            }

            return true;
        }
    }

    public static class AnyMultiItemFilter implements ItemFilter {

        public List<ItemFilter> filters;

        public AnyMultiItemFilter(List<ItemFilter> filters) {
            this.filters = filters;
        }

        public AnyMultiItemFilter() {
            this(new LinkedList<>());
        }

        @Override
        public boolean matches(ItemStack item) {
            for (ItemFilter filter : filters) try {
                if (filter != null && filter.matches(item)) return true;
            } catch (Exception e) {
                NEIClientConfig.logger
                        .error("Exception filtering " + item + " with " + filter + " (" + e.getMessage() + ")", e);
            }

            return false;
        }
    }

    public static interface ItemsLoadedCallback {

        public void itemsLoaded();
    }

    public static boolean itemMatchesAll(ItemStack item, List<ItemFilter> filters) {
        for (ItemFilter filter : filters) {
            try {
                if (filter != null && !filter.matches(item)) return false;
            } catch (Exception e) {
                NEIClientConfig.logger
                        .error("Exception filtering " + item + " with " + filter + " (" + e.getMessage() + ")", e);
            }
        }

        return true;
    }

    /**
     * @deprecated use getItemListFilter().matches(item)
     */
    @Deprecated
    public static boolean itemMatches(ItemStack item) {
        return getItemListFilter().matches(item);
    }

    public static ItemFilter getItemListFilter() {
        return new AllMultiItemFilter(getItemFilters());
    }

    public static List<ItemFilter> getItemFilters() {
        LinkedList<ItemFilter> filters = new LinkedList<>();
        synchronized (itemFilterers) {
            for (ItemFilterProvider p : itemFilterers) {
                filters.add(p.getFilter());
            }
        }
        return filters;
    }

    public static ForkJoinPool forkJoinPool;

    static {
        final ForkJoinPool.ForkJoinWorkerThreadFactory factory = new ForkJoinPool.ForkJoinWorkerThreadFactory() {

            private int workerId;

            @Override
            public ForkJoinWorkerThread newThread(ForkJoinPool pool) {
                final ForkJoinWorkerThread worker = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
                worker.setName("NEI-worker-thread-" + workerId++);
                return worker;
            }
        };
        int poolSize = Runtime.getRuntime().availableProcessors() * 2 / 3;
        if (poolSize < 1) poolSize = 1;
        forkJoinPool = new ForkJoinPool(poolSize, factory, null, false);
    }

    // The item list pipeline lives in ItemListLoader; these references are kept for backward compatibility.
    public static final RestartableTask loadItems = ItemListLoader.loadItems;

    public static final RestartableTask refreshItems = ItemListLoader.refreshItems;

    public static final RestartableTask updateFilter = ItemListLoader.updateFilter;

    /**
     * @deprecated Use updateFilter.restart()
     */
    @Deprecated
    public static void updateFilter() {
        updateFilter.restart();
    }

    /**
     * @deprecated Use loadItems.restart()
     */
    @Deprecated
    public static void loadItems() {
        loadItems.restart();
    }
}
