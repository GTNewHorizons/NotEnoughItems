package codechicken.nei;

import codechicken.nei.ThreadOperationTimer.TimeoutException;
import codechicken.nei.api.ItemFilter;
import codechicken.nei.api.ItemFilter.ItemFilterProvider;
import codechicken.nei.api.ItemInfo;
import codechicken.nei.guihook.GuiContainerManager;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ItemList
{
    /**
     * Fields are replaced atomically and contents never modified.
     */
    public static volatile List<ItemStack>  items = new ArrayList<>();
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

    public static class EverythingItemFilter implements ItemFilter
    {
        @Override
        public boolean matches(ItemStack item) {
            return true;
        }
    }

    public static class NothingItemFilter implements ItemFilter
    {
        @Override
        public boolean matches(ItemStack item) {
            return false;
        }
    }

    public static class PatternItemFilter implements ItemFilter
    {
        public Pattern pattern;

        public PatternItemFilter(Pattern pattern) {
            this.pattern = pattern;
        }

        @Override
        public boolean matches(ItemStack item) {
            return pattern.matcher(ItemInfo.getSearchName(item)).find();
        }
    }

    public static class AllMultiItemFilter implements ItemFilter
    {
        public List<ItemFilter> filters;

        public AllMultiItemFilter(List<ItemFilter> filters) {
            this.filters = filters;
        }

        public AllMultiItemFilter() {
            this(new LinkedList<>());
        }

        @Override
        public boolean matches(ItemStack item) {
            for(ItemFilter filter : filters)
                try {
                    if (!filter.matches(item)) return false;
                } catch (Exception e) {
                    NEIClientConfig.logger.error("Exception filtering "+item+" with "+filter, e);
                }

            return true;
        }
    }

    public static class AnyMultiItemFilter implements ItemFilter
    {
        public List<ItemFilter> filters;

        public AnyMultiItemFilter(List<ItemFilter> filters) {
            this.filters = filters;
        }

        public AnyMultiItemFilter() {
            this(new LinkedList<>());
        }

        @Override
        public boolean matches(ItemStack item) {
            for(ItemFilter filter : filters)
                try {
                    if (filter.matches(item)) return true;
                } catch (Exception e) {
                    NEIClientConfig.logger.error("Exception filtering "+item+" with "+filter, e);
                }

            return false;
        }
    }

    public static interface ItemsLoadedCallback
    {
        public void itemsLoaded();
    }

    public static boolean itemMatchesAll(ItemStack item, List<ItemFilter> filters) {
        for(ItemFilter filter : filters) {
            try {
                if (!filter.matches(item))
                    return false;
            } catch (Exception e) {
                NEIClientConfig.logger.error("Exception filtering "+item+" with "+filter, e);
            }
        }

        return true;
    }

    /**
     * @deprecated  use getItemListFilter().matches(item)
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
            for(ItemFilterProvider p : itemFilterers)
                filters.add(p.getFilter());
        }
        return filters;
    }

    public static final RestartableTask loadItems = new RestartableTask("NEI Item Loading")
    {
        private void damageSearch(Item item, List<ItemStack> permutations) {
            HashSet<String> damageIconSet = new HashSet<>();
            for (int damage = 0; damage < 16; damage++)
                try {
                    ItemStack itemstack = new ItemStack(item, 1, damage);
                    IIcon icon = item.getIconIndex(itemstack);
                    String name = GuiContainerManager.concatenatedDisplayName(itemstack, false);
                    String s = name + "@" + (icon == null ? 0 : icon.hashCode());
                    if (!damageIconSet.contains(s)) {
                        damageIconSet.add(s);
                        permutations.add(itemstack);
                    }
                }
                catch(TimeoutException t) {
                    throw t;
                }
                catch(Throwable t) {
                    NEIServerUtils.logOnce(t, stackTraces, "Ommiting "+item+":"+damage+" "+item.getClass().getSimpleName(), item.toString());
                }
        }

        @Override
        @SuppressWarnings("unchecked")
        public void execute() {
            // System.out.println("Executing NEI Item Loading");
            ThreadOperationTimer timer = getTimer(NEIClientConfig.getItemLoadingTimeout());

            LinkedList<ItemStack> items = new LinkedList<>();
            LinkedList<ItemStack> permutations = new LinkedList<>();
            ListMultimap<Item, ItemStack> itemMap = ArrayListMultimap.create();

            timer.setLimit(NEIClientConfig.getItemLoadingTimeout());
            for (Item item : (Iterable<Item>) Item.itemRegistry) {
                if (interrupted()) return;

                if (item == null || erroredItems.contains(item))
                    continue;

                try {
                    timer.reset(item);

                    permutations.clear();
                    permutations.addAll(ItemInfo.itemOverrides.get(item));

                    if (permutations.isEmpty())
                        item.getSubItems(item, null, permutations);

                    if (permutations.isEmpty())
                        damageSearch(item, permutations);

                    permutations.addAll(ItemInfo.itemVariants.get(item));

                    timer.reset();

                    items.addAll(permutations);
                    itemMap.putAll(item, permutations);
                } catch (Throwable t) {
                    NEIServerConfig.logger.error("Removing item: " + item + " from list.", t);
                    erroredItems.add(item);
                }
            }

            if(interrupted()) return;
            ItemList.items = items;
            ItemList.itemMap = itemMap;
            for(ItemsLoadedCallback callback : loadCallbacks)
                callback.itemsLoaded();

            updateFilter.restart();
        }
    };

    public static ForkJoinPool getPool(int poolSize) {
        if(poolSize < 1)
            poolSize = 1;

        return new ForkJoinPool(poolSize);
    }

    public static final int numProcessors = Runtime.getRuntime().availableProcessors();
    public static ForkJoinPool forkJoinPool = getPool(numProcessors * 2 / 3);

    public static final RestartableTask updateFilter = new RestartableTask("NEI Item Filtering")
    {
        @Override
        public void execute() {
            // System.out.println("Executing NEI Item Filtering");
            ArrayList<ItemStack> filtered;
            ItemFilter filter = getItemListFilter();

            try {
                filtered = ItemList.forkJoinPool.submit(() -> items.parallelStream()
                    .filter(filter::matches)
                    .collect(Collectors.toCollection(ArrayList::new))).get();
            } catch (InterruptedException | ExecutionException e) {
                filtered = new ArrayList<>();
                e.printStackTrace();
                stop();
            }

            if(interrupted()) return;
            ItemSorter.sort(filtered);
            if(interrupted()) return;
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
