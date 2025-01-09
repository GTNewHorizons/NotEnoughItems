package codechicken.nei;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

import codechicken.nei.ThreadOperationTimer.TimeoutException;
import codechicken.nei.api.ItemFilter;
import codechicken.nei.api.ItemFilter.ItemFilterProvider;
import codechicken.nei.api.ItemInfo;

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

    private static final HashSet<Item> erroredItems = new HashSet<>();
    private static final HashSet<String> stackTraces = new HashSet<>();
    private static HashMap<ItemStack, Integer> ordering = new HashMap<>();
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

            if (displayName != null && !displayName.isEmpty() && pattern.matcher(displayName).find()) {
                return true;
            }

            if (item.hasDisplayName()) {
                displayName = EnumChatFormatting
                        .getTextWithoutFormattingCodes(item.getItem().getItemStackDisplayName(item));

                return displayName != null && !displayName.isEmpty() && pattern.matcher(displayName).find();
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

    public static final RestartableTask loadItems = new RestartableTask("NEI Item Loading") {

        private void damageSearch(Item item, List<ItemStack> permutations) {
            HashSet<String> damageIconSet = new HashSet<>();
            for (int damage = 0; damage < 16; damage++) try {
                ItemStack itemstack = new ItemStack(item, 1, damage);
                IIcon icon = item.getIconIndex(itemstack);
                String name = getTooltip(itemstack);
                String s = name + "@" + (icon == null ? 0 : icon.hashCode());
                if (!damageIconSet.contains(s)) {
                    damageIconSet.add(s);
                    permutations.add(itemstack);
                }
            } catch (TimeoutException t) {
                throw t;
            } catch (Throwable t) {
                NEIServerUtils.logOnce(
                        t,
                        stackTraces,
                        "Ommiting " + item + ":" + damage + " " + item.getClass().getSimpleName(),
                        item.toString());
            }
        }

        private String getTooltip(ItemStack stack) {
            try {
                return String.join("\n", stack.getTooltip(Minecraft.getMinecraft().thePlayer, false));
            } catch (Throwable ignored) {}

            return "";
        }

        private void updateOrdering(List<ItemStack> items) {
            ItemSorter.sort(items);

            HashMap<ItemStack, Integer> newOrdering = new HashMap<>();

            if (!CollapsibleItems.isEmpty()) {
                HashMap<Integer, Integer> groups = new HashMap<>();
                int orderIndex = 0;

                for (ItemStack stack : items) {
                    final int groupIndex = CollapsibleItems.getGroupIndex(stack);

                    if (groupIndex == -1) {
                        newOrdering.put(stack, orderIndex++);
                    } else {

                        if (!groups.containsKey(groupIndex)) {
                            groups.put(groupIndex, orderIndex++);
                        }

                        newOrdering.put(stack, groups.get(groupIndex));
                    }
                }
            } else {
                int orderIndex = 0;

                for (ItemStack stack : items) {
                    newOrdering.put(stack, orderIndex++);
                }
            }

            ItemList.ordering = newOrdering;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void execute() {
            ThreadOperationTimer timer = getTimer(NEIClientConfig.getItemLoadingTimeout());
            LayoutManager.itemsLoaded = true;
            loadFinished = false;

            List<ItemStack> items = new LinkedList<>();
            List<ItemStack> permutations = new LinkedList<>();
            ListMultimap<Item, ItemStack> itemMap = ArrayListMultimap.create();

            timer.setLimit(NEIClientConfig.getItemLoadingTimeout());
            for (Item item : (Iterable<Item>) Item.itemRegistry) {
                if (interrupted()) return;

                if (item == null || erroredItems.contains(item)) continue;

                try {
                    timer.reset(item);

                    permutations.clear();
                    permutations.addAll(ItemInfo.itemOverrides.get(item));

                    if (permutations.isEmpty()) {
                        item.getSubItems(item, null, permutations);
                    }

                    if (permutations.isEmpty()) {
                        damageSearch(item, permutations);
                    }

                    permutations.addAll(ItemInfo.itemVariants.get(item));

                    timer.reset();

                    permutations = permutations.stream().filter(stack -> !ItemInfo.isHidden(stack))
                            .collect(Collectors.toCollection(ArrayList::new));

                    items.addAll(permutations);
                    itemMap.putAll(item, permutations);
                } catch (Throwable t) {
                    NEIServerConfig.logger.error("Removing item: " + item + " from list.", t);
                    erroredItems.add(item);
                }
            }

            if (interrupted()) return;
            ItemList.items = items;
            ItemList.itemMap = itemMap;
            for (ItemsLoadedCallback callback : loadCallbacks) callback.itemsLoaded();

            if (interrupted()) return;
            CollapsibleItems.updateCache(items);
            updateOrdering(items);

            loadFinished = true;
            updateFilter.restart();
        }
    };

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

    public static final RestartableTask updateFilter = new RestartableTask("NEI Item Filtering") {

        @Override
        public void execute() {

            if (!loadFinished) return;
            ItemFilter filter = getItemListFilter();
            ArrayList<ItemStack> filtered;

            try {
                filtered = ItemList.forkJoinPool.submit(
                        () -> items.parallelStream().filter(filter::matches)
                                .collect(Collectors.toCollection(ArrayList::new)))
                        .get();
            } catch (InterruptedException | ExecutionException e) {
                filtered = new ArrayList<>();
                e.printStackTrace();
                stop();
            }

            if (interrupted()) return;

            Comparator<ItemStack> comparator = Comparator.comparingInt(ItemList.ordering::get);
            filtered.sort(comparator::compare);

            if (interrupted()) return;

            ItemPanel.updateItemList(filtered);
        }
    };

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
