package codechicken.nei;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraftforge.fluids.IFluidContainerItem;

import com.google.common.base.Throwables;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

import codechicken.nei.ItemList.ItemsLoadedCallback;
import codechicken.nei.ThreadOperationTimer.TimeoutException;
import codechicken.nei.api.ItemFilter;
import codechicken.nei.api.ItemInfo;
import codechicken.nei.recipe.InformationHandler;
import codechicken.nei.recipe.RecipeHandlerRef;
import codechicken.nei.recipe.RepairRecipeHandler;
import codechicken.nei.search.TooltipFilter;
import cpw.mods.fml.common.FMLLog;

public class ItemListLoader {

    private static final HashSet<Item> erroredItems = new HashSet<>();
    private static final HashSet<String> stackTraces = new HashSet<>();
    private static Map<ItemStack, Integer> ordering = new HashMap<>();

    private ItemListLoader() {}

    private static void damageSearch(Item item, List<ItemStack> permutations) {
        final Set<String> damageIconSet = new HashSet<>();
        final EntityPlayer player = Minecraft.getMinecraft().thePlayer;

        for (int damage = 0; damage < 16; damage++) {
            try {
                final ItemStack itemstack = new ItemStack(item, 1, damage);
                final IIcon icon = item.getIconIndex(itemstack);
                final String key = itemstack.getDisplayName() + "@"
                        + (icon == null ? 0 : icon.hashCode())
                        + "@"
                        + getInformation(itemstack, player);

                if (damageIconSet.add(key)) {
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
    }

    private static String getInformation(ItemStack stack, EntityPlayer player) {
        try {
            final List<String> tooltip = new ArrayList<>();
            stack.getItem().addInformation(stack, player, tooltip, false);
            return String.join("\n", tooltip);
        } catch (Throwable ignored) {}

        return "";
    }

    private static void updateOrdering(List<ItemStack> items) {
        final Map<ItemStack, Integer> newOrdering = new HashMap<>();

        if (!CollapsibleItems.isEmpty()) {
            final HashMap<Integer, Integer> groups = new HashMap<>();
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

        ordering = newOrdering;
    }

    private static List<ItemStack> getPermutations(Item item) {
        final List<ItemStack> permutations = new LinkedList<>(ItemInfo.itemOverrides.get(item));

        if (permutations.isEmpty()) {
            item.getSubItems(item, null, permutations);
        }

        if (permutations.isEmpty()) {
            damageSearch(item, permutations);
        }

        permutations.addAll(ItemInfo.itemVariants.get(item));

        return permutations.stream()
                .filter(
                        stack -> stack.getItem() != null && stack.getItem().delegate.name() != null
                                && !ItemInfo.isHidden(stack))
                .collect(Collectors.toList());
    }

    private static void runChecked(ItemStack stack, Runnable action, String reason) {
        int hashOld = 0;
        if (stack.hasTagCompound()) {
            hashOld = stack.stackTagCompound.hashCode();
        }

        action.run();

        if (stack.hasTagCompound() && hashOld != stack.stackTagCompound.hashCode()) {
            FMLLog.warning(
                    "NEI: Forced tag update with reason (" + reason + ") for " + stack + "(" + stack.getItem() + ")");
        }
    }

    private static void forceTagCompoundInitialization(ItemStack stack) {
        Item item = stack.getItem();
        if (item == null) {
            return;
        }
        if (item instanceof IFluidContainerItem) {
            IFluidContainerItem fluidItem = (IFluidContainerItem) item;
            runChecked(stack, () -> fluidItem.getFluid(stack), "getFluid");
        }
        runChecked(stack, () -> item.isDamaged(stack), "isDamaged");
        runChecked(stack, () -> item.getDamage(stack), "getDamage");
        runChecked(stack, () -> item.showDurabilityBar(stack), "showDurabilityBar");
        runChecked(stack, () -> item.getAttributeModifiers(stack), "getAttributeModifiers");
        runChecked(stack, () -> item.getDurabilityForDisplay(stack), "getDurabilityForDisplay");
        runChecked(stack, () -> item.getItemStackLimit(stack), "getItemStackLimit");
        runChecked(stack, () -> item.getToolClasses(stack), "getToolClasses");
        runChecked(stack, () -> item.getUnlocalizedNameInefficiently(stack), "getUnlocalizedNameInefficiently");
        runChecked(stack, () -> item.hasEffect(stack), "hasEffect");
        runChecked(stack, () -> item.getDigSpeed(stack, Blocks.stone, 0), "getDigSpeed");

        /*
         * unused... for now runChecked(stack, () -> GameRegistry.getFuelValue(stack), "getFuelValue");
         * runChecked(stack, () -> item.getUnlocalizedName(stack), "getUnlocalizedName"); runChecked(stack, () ->
         * item.doesContainerItemLeaveCraftingGrid(stack), "doesContainerItemLeaveCraftingGrid"); runChecked(stack, ()
         * -> item.getItemUseAction(stack), "getItemUseAction"); runChecked(stack, () ->
         * item.getMaxItemUseDuration(stack), "getMaxItemUseDuration"); runChecked(stack, () ->
         * item.getPotionEffect(stack), "getPotionEffect"); runChecked(stack, () -> item.isPotionIngredient(stack),
         * "isPotionIngredient"); runChecked(stack, () -> item.getRarity(stack), "getRarity"); runChecked(stack, () ->
         * item.hasCustomEntity(stack), "hasCustomEntity"); runChecked(stack, () -> item.getSmeltingExperience(stack),
         * "getSmeltingExperience"); runChecked(stack, () -> item.getMaxDamage(stack), "getMaxDamage");
         * runChecked(stack, () -> item.getDamage(stack), "getDamage"); runChecked(stack, stack::isItemDamaged,
         * "isItemDamaged"); runChecked(stack, () -> item.hasEffect(stack, 0), "hasEffect"); runChecked(stack, () ->
         * item.getItemEnchantability(stack), "getItemEnchantability"); runChecked(stack, () ->
         * item.isBeaconPayment(stack), "isBeaconPayment"); runChecked(stack, () -> item.getDamage(stack), "getDamage");
         * runChecked(stack, () -> item.getDamage(stack), "getDamage"); runChecked(stack, () -> item.getDamage(stack),
         * "getDamage");
         */
    }

    @SuppressWarnings("unchecked")
    private static List<ItemStack> gatherItems(ThreadOperationTimer timer) {
        ItemSorter.instance.ordering.clear();

        List<ItemStack> items = new ArrayList<>();
        ListMultimap<Item, ItemStack> itemMap = ArrayListMultimap.create();
        ItemStackSet unique = new ItemStackSet();

        final List<Item> registryItems = new ArrayList<>();
        for (Item item : (Iterable<Item>) Item.itemRegistry) registryItems.add(item);

        registryItems.parallelStream().forEach(item -> {
            if (item == null || item.delegate.name() == null || erroredItems.contains(item)) return;

            try {
                timer.reset(item);
                List<ItemStack> permutations = getPermutations(item);
                timer.reset();

                synchronized (itemMap) {
                    itemMap.putAll(item, permutations);
                }
            } catch (Throwable t) {
                NEIServerConfig.logger.error(
                        "Removing item: {} from list.\n{}: {}",
                        item,
                        t.getMessage(),
                        Throwables.getStackTraceAsString(t));
                erroredItems.add(item);
            }

        });

        int index = 0;
        for (Item item : (Iterable<Item>) Item.itemRegistry) {
            for (ItemStack stack : itemMap.get(item)) {
                ItemSorter.instance.ordering.put(stack, index++);

                if (!unique.contains(stack)) {
                    unique.add(stack);
                    items.add(stack);
                }
            }
        }

        ItemList.itemMap = itemMap;

        return items;
    }

    private static void buildItemCaches(List<ItemStack> items) {
        SearchField.searchParser.clearCache();
        TooltipFilter.clearCache();
        InformationHandler.clearCache();

        items.parallelStream().forEach(stack -> {
            try {
                forceTagCompoundInitialization(stack);
                InformationHandler.populateStacks(stack);
            } catch (Throwable t) {
                NEIServerConfig.logger.error(
                        "Failed to build caches for: {}\n{}: {}",
                        stack,
                        t.getMessage(),
                        Throwables.getStackTraceAsString(t));
            }
        });
    }

    private static void indexCollapsibleItems(List<ItemStack> items) {
        CollapsibleItems.clearCache();
        items.parallelStream().forEach(CollapsibleItems::putItem);
    }

    public static final RestartableTask loadItems = new RestartableTask("NEI Item Loading") {

        @Override
        public void execute() {
            if (!NEIClientConfig.isEnabled() || NEIClientUtils.mc().thePlayer == null) return;

            final ThreadOperationTimer timer = getTimer(NEIClientConfig.getItemLoadingTimeout());
            LayoutManager.itemsLoaded = true;
            ItemList.loadFinished = false;
            warmTooltips.stop();

            List<ItemStack> items = gatherItems(timer);

            if (interrupted()) return;
            buildItemCaches(items);
            indexCollapsibleItems(items);

            if (interrupted()) return;
            ItemSorter.sort(items);

            if (interrupted()) return;
            ItemList.items = items;
            for (ItemsLoadedCallback callback : ItemList.loadCallbacks) callback.itemsLoaded();

            if (interrupted()) return;
            updateOrdering(ItemList.items);

            ItemList.loadFinished = true;

            RepairRecipeHandler.findRepairRecipesOnceParallel();
            RecipeHandlerRef.clearCache();
            FavoriteRecipes.reload();
            SubsetWidget.updateHiddenItems();
            updateFilter.restart();
            warmTooltips.restart();
            loadBookmarks.restart();
        }
    };

    public static final RestartableTask warmTooltips = new RestartableTask("NEI Tooltip Caching") {

        @Override
        public void execute() {
            if (!ItemList.loadFinished) return;
            // Use the common pool, not ItemList.forkJoinPool, so it does not queue behind updateFilter.
            ItemList.items.parallelStream().forEach(stack -> {
                if (interrupted()) return;
                try {
                    TooltipFilter.getSearchTooltip(stack);
                } catch (Throwable ignored) {}
            });
        }
    };

    public static final RestartableTask loadBookmarks = new RestartableTask("NEI Bookmark Loading") {

        @Override
        public void execute() {
            ItemPanels.bookmarkPanel.load();
        }
    };

    public static final RestartableTask refreshItems = new RestartableTask("NEI Item Reordering") {

        @Override
        public void execute() {
            if (!ItemList.loadFinished) return;

            final List<ItemStack> items = new ArrayList<>(ItemList.items);

            indexCollapsibleItems(items);

            if (interrupted()) return;
            ItemSorter.sort(items);

            if (interrupted()) return;
            ItemList.items = items;
            updateOrdering(ItemList.items);

            FavoriteRecipes.reload();
            SubsetWidget.updateHiddenItems();
            updateFilter.restart();
            loadBookmarks.restart();
        }
    };

    public static final RestartableTask updateFilter = new RestartableTask("NEI Item Filtering") {

        @Override
        public void execute() {

            if (!ItemList.loadFinished) return;
            ItemFilter filter = ItemList.getItemListFilter();
            ArrayList<ItemStack> filtered;

            try {
                filtered = ItemList.forkJoinPool.submit(
                        () -> ItemList.items.parallelStream().filter(filter::matches)
                                .collect(Collectors.toCollection(ArrayList::new)))
                        .get();
            } catch (InterruptedException | ExecutionException e) {
                filtered = new ArrayList<>();
                e.printStackTrace();
                stop();
            }

            if (interrupted()) return;

            filtered.sort(Comparator.comparingInt(ordering::get));

            if (interrupted()) return;

            ItemPanel.updateItemList(filtered);
        }
    };
}
