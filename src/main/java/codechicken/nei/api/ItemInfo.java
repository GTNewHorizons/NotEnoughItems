package codechicken.nei.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;

import net.minecraft.block.Block;
import net.minecraft.client.resources.I18n;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityList.EntityEggInfo;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.monster.EntitySnowman;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemHoe;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemSpade;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.potion.Potion;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.RegistryNamespaced;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.common.IShearable;
import net.minecraftforge.oredict.OreDictionary;

import com.google.common.collect.ArrayListMultimap;

import codechicken.core.featurehack.GameDataManipulator;
import codechicken.nei.InfiniteStackSizeHandler;
import codechicken.nei.InfiniteToolHandler;
import codechicken.nei.ItemList;
import codechicken.nei.ItemList.AnyMultiItemFilter;
import codechicken.nei.ItemList.PatternItemFilter;
import codechicken.nei.ItemMobSpawner;
import codechicken.nei.ItemStackMap;
import codechicken.nei.ItemStackSet;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.PopupInputHandler;
import codechicken.nei.PresetsList;
import codechicken.nei.SearchField.SearchParserProvider;
import codechicken.nei.SearchTokenParser.SearchMode;
import codechicken.nei.config.ArrayDumper;
import codechicken.nei.config.HandlerDumper;
import codechicken.nei.config.ItemPanelDumper;
import codechicken.nei.config.RegistryDumper;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.recipe.BrewingRecipeHandler;
import codechicken.nei.recipe.RecipeItemInputHandler;
import codechicken.nei.search.IdentifierFilter;
import codechicken.nei.search.ModNameFilter;
import codechicken.nei.search.OreDictionaryFilter;
import codechicken.nei.search.TooltipFilter;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.common.registry.GameRegistry.UniqueIdentifier;

/**
 * This is an internal class for storing information about items, to be accessed by the API
 */
public class ItemInfo {

    public static enum Layout {
        HEADER,
        BODY,
        FOOTER
    }

    public static final ArrayListMultimap<Layout, IHighlightHandler> highlightHandlers = ArrayListMultimap.create();
    public static final ItemStackMap<String> nameOverrides = new ItemStackMap<>();
    public static final ItemStackSet hiddenItems = new ItemStackSet();
    public static final AnyMultiItemFilter hiddenItemsRules = new AnyMultiItemFilter();
    public static final ItemStackSet finiteItems = new ItemStackSet();
    public static final ArrayListMultimap<Item, ItemStack> itemOverrides = ArrayListMultimap.create();
    public static final ArrayListMultimap<Item, ItemStack> itemVariants = ArrayListMultimap.create();

    public static final LinkedList<IInfiniteItemHandler> infiniteHandlers = new LinkedList<>();
    public static final ArrayListMultimap<Block, IHighlightHandler> highlightIdentifiers = ArrayListMultimap.create();
    public static final HashSet<Class<? extends Slot>> fastTransferExemptions = new HashSet<>();

    public static final HashMap<Item, String> itemOwners = new HashMap<>();

    public static boolean isHidden(ItemStack stack) {
        return hiddenItems.contains(stack) || hiddenItemsRules.matches(stack);
    }

    @Deprecated
    public static boolean isHidden(Item item) {
        return hiddenItems.containsAll(item);
    }

    public static String getNameOverride(ItemStack stack) {
        return nameOverrides.get(stack);
    }

    public static boolean canBeInfinite(ItemStack stack) {
        return !finiteItems.contains(stack);
    }

    /**
     * @deprecated Use field directly
     */
    @Deprecated
    public static List<ItemStack> getItemOverrides(Item item) {
        return itemOverrides.get(item);
    }

    public static void preInit() {
        addMobSpawnerItem();
    }

    public static void load(World world) {
        addVanillaBlockProperties();
        addDefaultDropDowns();
        searchItems();
        parseModItems();
        ItemMobSpawner.loadSpawners(world);
        addSpawnEggs();
        addInfiniteHandlers();
        addInputHandlers();
        addIDDumps();
        addSearchProviders();
        PresetsList.load();
        ItemList.collapsibleItems.load();
        ItemList.loadCallbacks.add(TooltipFilter::populateSearchMap);
    }

    private static void addSearchProviders() {
        API.addSearchProvider(
                new SearchParserProvider(
                        '\0',
                        "default",
                        EnumChatFormatting.RESET,
                        (pattern) -> new PatternItemFilter(pattern)) {

                    @Override
                    public SearchMode getSearchMode() {
                        return SearchMode.ALWAYS;
                    }

                });
        API.addSearchProvider(
                new SearchParserProvider(
                        '@',
                        "modName",
                        EnumChatFormatting.LIGHT_PURPLE,
                        (pattern) -> new ModNameFilter(pattern)));
        API.addSearchProvider(
                new SearchParserProvider(
                        '$',
                        "oreDict",
                        EnumChatFormatting.AQUA,
                        (pattern) -> new OreDictionaryFilter(pattern)));
        API.addSearchProvider(
                new SearchParserProvider(
                        '#',
                        "tooltip",
                        EnumChatFormatting.YELLOW,
                        (pattern) -> new TooltipFilter(pattern)));
        API.addSearchProvider(
                new SearchParserProvider(
                        '&',
                        "identifier",
                        EnumChatFormatting.GOLD,
                        (pattern) -> new IdentifierFilter(pattern)));
    }

    private static void addIDDumps() {
        API.addOption(new RegistryDumper<Item>("tools.dump.item") {

            @Override
            public String[] header() {
                return new String[] { "Name", "ID", "Has Block", "Mod", "Class", "Display Name" };
            }

            @Override
            public String[] dump(Item item, int id, String name) {
                return new String[] { name, Integer.toString(id),
                        Boolean.toString(Block.getBlockFromItem(item) != Blocks.air), ItemInfo.itemOwners.get(item),
                        item.getClass().getCanonicalName(), EnumChatFormatting.getTextWithoutFormattingCodes(
                                GuiContainerManager.itemDisplayNameShort(new ItemStack(item))) };
            }

            @Override
            public RegistryNamespaced registry() {
                return Item.itemRegistry;
            }
        });
        API.addOption(new RegistryDumper<Block>("tools.dump.block") {

            @Override
            public String[] header() {
                return new String[] { "Name", "ID", "Has Item", "Mod", "Class", "Display Name" };
            }

            @Override
            public String[] dump(Block block, int id, String name) {
                final Item item = Item.getItemFromBlock(block);
                return new String[] { name, Integer.toString(id), Boolean.toString(item != null),
                        ItemInfo.itemOwners.get(block), block.getClass().getCanonicalName(),
                        item != null ? EnumChatFormatting.getTextWithoutFormattingCodes(
                                GuiContainerManager.itemDisplayNameShort(new ItemStack(item))) : "null" };
            }

            @Override
            public RegistryNamespaced registry() {
                return Block.blockRegistry;
            }
        });
        API.addOption(new ArrayDumper<Potion>("tools.dump.potion") {

            public String[] header() {
                return new String[] { "ID", "Unlocalised name", "Class" };
            }

            @Override
            public String[] dump(Potion potion, int id) {
                return new String[] { Integer.toString(id), potion.getName(), potion.getClass().getCanonicalName() };
            }

            @Override
            public Potion[] array() {
                return Potion.potionTypes;
            }
        });
        API.addOption(new ArrayDumper<Enchantment>("tools.dump.enchantment") {

            public String[] header() {
                return new String[] { "ID", "Unlocalised name", "Type", "Min Level", "Max Level", "Class" };
            }

            @Override
            public String[] dump(Enchantment ench, int id) {
                return new String[] { Integer.toString(id), ench.getName(), ench.type.toString(),
                        Integer.toString(ench.getMinLevel()), Integer.toString(ench.getMaxLevel()),
                        ench.getClass().getCanonicalName() };
            }

            @Override
            public Enchantment[] array() {
                return Enchantment.enchantmentsList;
            }
        });
        API.addOption(new ArrayDumper<BiomeGenBase>("tools.dump.biome") {

            @Override
            public String[] header() {
                return new String[] { "ID", "Name", "Temperature", "Rainfall", "Spawn Chance", "Root Height",
                        "Height Variation", "Types", "Class" };
            }

            @Override
            public String[] dump(BiomeGenBase biome, int id) {
                BiomeDictionary.Type[] types = BiomeDictionary.getTypesForBiome(biome);
                StringBuilder s_types = new StringBuilder();
                for (BiomeDictionary.Type t : types) {
                    if (s_types.length() > 0) s_types.append(", ");
                    s_types.append(t.name());
                }

                return new String[] { Integer.toString(id), biome.biomeName,
                        Float.toString(biome.getFloatTemperature(0, 0, 0)), Float.toString(biome.getFloatRainfall()),
                        Float.toString(biome.getSpawningChance()), Float.toString(biome.rootHeight),
                        Float.toString(biome.heightVariation), s_types.toString(),
                        biome.getClass().getCanonicalName() };
            }

            @Override
            public BiomeGenBase[] array() {
                return BiomeGenBase.getBiomeGenArray();
            }
        });
        API.addOption(new ItemPanelDumper("tools.dump.itempanel"));
        API.addOption(new HandlerDumper("tools.dump.handlers"));
    }

    @SuppressWarnings("unchecked")
    private static void parseModItems() {
        HashMap<String, ItemStackSet> modSubsets = new HashMap<>();
        for (Item item : (Iterable<Item>) Item.itemRegistry) {
            UniqueIdentifier ident = null;
            try {
                ident = GameRegistry.findUniqueIdentifierFor(item);
            } catch (Exception ignored) {}

            if (ident == null) {
                NEIClientConfig.logger.error("Failed to find identifier for: " + item);
                continue;
            }

            String modId = ident.modId;
            itemOwners.put(item, modId);
            ItemStackSet itemset = modSubsets.get(modId);
            if (itemset == null) modSubsets.put(modId, itemset = new ItemStackSet());
            itemset.with(item);
        }

        API.addSubset("Mod.Minecraft", modSubsets.remove("minecraft"));
        for (Entry<String, ItemStackSet> entry : modSubsets.entrySet()) {
            ModContainer mc = FMLCommonHandler.instance().findContainerFor(entry.getKey());
            if (mc == null) NEIClientConfig.logger.error("Missing container for " + entry.getKey());
            else API.addSubset("Mod." + mc.getName(), entry.getValue());
        }
    }

    private static void addInputHandlers() {
        GuiContainerManager.addInputHandler(new RecipeItemInputHandler());
        GuiContainerManager.addInputHandler(new PopupInputHandler());
    }

    private static void addMobSpawnerItem() {
        GameDataManipulator.replaceItem(Block.getIdFromBlock(Blocks.mob_spawner), new ItemMobSpawner());
    }

    private static void addInfiniteHandlers() {
        API.addInfiniteItemHandler(new InfiniteStackSizeHandler());
        API.addInfiniteItemHandler(new InfiniteToolHandler());
    }

    private static void addVanillaBlockProperties() {
        API.setOverrideName(new ItemStack(Blocks.flowing_water), "Water Source");
        API.setOverrideName(new ItemStack(Blocks.water), "Water Still");
        API.setOverrideName(new ItemStack(Blocks.flowing_lava), "Lava Source");
        API.setOverrideName(new ItemStack(Blocks.lava), "Lava Still");
        API.setOverrideName(new ItemStack(Blocks.end_portal), "End Portal");
        API.setOverrideName(new ItemStack(Blocks.end_portal_frame), "End Portal Frame");
        API.hideItem(new ItemStack(Blocks.double_stone_slab, 1, OreDictionary.WILDCARD_VALUE));
        API.hideItem(new ItemStack(Blocks.double_wooden_slab, 1, OreDictionary.WILDCARD_VALUE));
        API.hideItem(new ItemStack(Blocks.carrots));
        API.hideItem(new ItemStack(Blocks.potatoes));
        API.hideItem(new ItemStack(Blocks.cocoa));
    }

    private static void addDefaultDropDowns() {
        API.addSubset("Items", item -> Block.getBlockFromItem(item.getItem()) == Blocks.air);
        API.addSubset("Blocks", item -> Block.getBlockFromItem(item.getItem()) != Blocks.air);
        API.addSubset("Blocks.MobSpawners", ItemStackSet.of(Blocks.mob_spawner));
    }

    @SuppressWarnings("unchecked")
    private static void searchItems() {
        ItemStackSet tools = new ItemStackSet();
        ItemStackSet picks = new ItemStackSet();
        ItemStackSet shovels = new ItemStackSet();
        ItemStackSet axes = new ItemStackSet();
        ItemStackSet hoes = new ItemStackSet();
        ItemStackSet swords = new ItemStackSet();
        ItemStackSet chest = new ItemStackSet();
        ItemStackSet helmets = new ItemStackSet();
        ItemStackSet legs = new ItemStackSet();
        ItemStackSet boots = new ItemStackSet();
        ItemStackSet other = new ItemStackSet();
        ItemStackSet ranged = new ItemStackSet();
        ItemStackSet food = new ItemStackSet();
        ItemStackSet potioningredients = new ItemStackSet();

        ArrayList<ItemStackSet> creativeTabRanges = new ArrayList<>(CreativeTabs.creativeTabArray.length);
        List<ItemStack> stackList = new LinkedList<>();

        for (Item item : (Iterable<Item>) Item.itemRegistry) {
            if (item == null) continue;

            for (CreativeTabs itemTab : item.getCreativeTabs()) {
                if (itemTab != null) {
                    while (itemTab.getTabIndex() >= creativeTabRanges.size()) creativeTabRanges.add(null);
                    ItemStackSet set = creativeTabRanges.get(itemTab.getTabIndex());
                    if (set == null) creativeTabRanges.set(itemTab.getTabIndex(), set = new ItemStackSet());

                    try {
                        stackList.clear();
                        item.getSubItems(item, itemTab, stackList);
                        for (ItemStack stack : stackList) set.add(stack);
                    } catch (Exception e) {
                        NEIClientConfig.logger
                                .error("Error loading sub-items for: " + item + ". Tab: " + itemTab.getTabLabel(), e);
                    }
                }
            }

            if (item.isDamageable()) {
                tools.with(item);
                if (item instanceof ItemPickaxe) picks.with(item);
                else if (item instanceof ItemSpade) shovels.with(item);
                else if (item instanceof ItemAxe) axes.with(item);
                else if (item instanceof ItemHoe) hoes.with(item);
                else if (item instanceof ItemSword) swords.with(item);
                else if (item instanceof ItemArmor) switch (((ItemArmor) item).armorType) {
                    case 0:
                        helmets.with(item);
                        break;
                    case 1:
                        chest.with(item);
                        break;
                    case 2:
                        legs.with(item);
                        break;
                    case 3:
                        boots.with(item);
                        break;
                }
                else if (item == Items.arrow || item == Items.bow) ranged.with(item);
                else if (item == Items.fishing_rod || item == Items.flint_and_steel || item == Items.shears)
                    other.with(item);
            }

            if (item instanceof ItemFood) food.with(item);

            try {
                LinkedList<ItemStack> subItems = new LinkedList<>();
                item.getSubItems(item, null, subItems);
                for (ItemStack stack : subItems) {
                    if (item.isPotionIngredient(stack) && item.getPotionEffect(stack) != null) {
                        BrewingRecipeHandler.ingredients.add(stack);
                        potioningredients.add(stack);
                    }
                }

            } catch (Exception e) {
                NEIClientConfig.logger.error("Error loading brewing ingredients for: " + item, e);
            }
        }
        API.addSubset("Items.Tools.Pickaxes", picks);
        API.addSubset("Items.Tools.Shovels", shovels);
        API.addSubset("Items.Tools.Axes", axes);
        API.addSubset("Items.Tools.Hoes", hoes);
        API.addSubset("Items.Tools.Other", other);
        API.addSubset("Items.Weapons.Swords", swords);
        API.addSubset("Items.Weapons.Ranged", ranged);
        API.addSubset("Items.Armor.Chestplates", chest);
        API.addSubset("Items.Armor.Leggings", legs);
        API.addSubset("Items.Armor.Helmets", helmets);
        API.addSubset("Items.Armor.Boots", boots);
        API.addSubset("Items.Food", food);
        API.addSubset("Items.Potions.Ingredients", potioningredients);

        for (CreativeTabs tab : CreativeTabs.creativeTabArray) {
            if (tab.getTabIndex() >= creativeTabRanges.size()) continue;
            ItemStackSet set = creativeTabRanges.get(tab.getTabIndex());
            if (set != null && !set.isEmpty())
                API.addSubset("CreativeTabs." + I18n.format(tab.getTranslatedTabLabel()), set);
        }

        BrewingRecipeHandler.searchPotions();
    }

    private static void addSpawnEggs() {
        addEntityEgg(EntitySnowman.class, 0xEEFFFF, 0xffa221);
        addEntityEgg(EntityIronGolem.class, 0xC5C2C1, 0xffe1cc);
    }

    @SuppressWarnings("unchecked")
    private static void addEntityEgg(Class<?> entity, int i, int j) {
        int id = (Integer) EntityList.classToIDMapping.get(entity);
        EntityList.entityEggs.put(id, new EntityEggInfo(id, i, j));
    }

    public static ArrayList<ItemStack> getIdentifierItems(World world, EntityPlayer player, MovingObjectPosition hit) {
        int x = hit.blockX;
        int y = hit.blockY;
        int z = hit.blockZ;
        Block mouseoverBlock = world.getBlock(x, y, z);

        ArrayList<ItemStack> items = new ArrayList<>();

        ArrayList<IHighlightHandler> handlers = new ArrayList<>();
        if (highlightIdentifiers.containsKey(null)) handlers.addAll(highlightIdentifiers.get(null));
        if (highlightIdentifiers.containsKey(mouseoverBlock)) handlers.addAll(highlightIdentifiers.get(mouseoverBlock));
        for (IHighlightHandler ident : handlers) {
            ItemStack item = ident.identifyHighlight(world, player, hit);
            if (item != null) items.add(item);
        }

        if (items.size() > 0) return items;

        ItemStack pick = mouseoverBlock.getPickBlock(hit, world, x, y, z, player);
        if (pick != null) items.add(pick);

        try {
            items.addAll(mouseoverBlock.getDrops(world, x, y, z, world.getBlockMetadata(x, y, z), 0));
        } catch (Exception ignored) {}
        if (mouseoverBlock instanceof IShearable) {
            IShearable shearable = (IShearable) mouseoverBlock;
            if (shearable.isShearable(new ItemStack(Items.shears), world, x, y, z))
                items.addAll(shearable.onSheared(new ItemStack(Items.shears), world, x, y, z, 0));
        }

        if (items.size() == 0) items.add(0, new ItemStack(mouseoverBlock, 1, world.getBlockMetadata(x, y, z)));

        return items;
    }

    public static void registerHighlightHandler(IHighlightHandler handler, ItemInfo.Layout... layouts) {
        for (ItemInfo.Layout layout : layouts) ItemInfo.highlightHandlers.get(layout).add(handler);
    }

    public static List<String> getText(ItemStack itemStack, World world, EntityPlayer player,
            MovingObjectPosition mop) {
        List<String> retString = new ArrayList<>();

        for (ItemInfo.Layout layout : ItemInfo.Layout.values())
            for (IHighlightHandler handler : ItemInfo.highlightHandlers.get(layout))
                retString = handler.handleTextData(itemStack, world, player, mop, retString, layout);

        return retString;
    }

    @Deprecated
    public static String getSearchName(ItemStack stack) {
        return GuiContainerManager.concatenatedDisplayName(stack, true);
    }
}
