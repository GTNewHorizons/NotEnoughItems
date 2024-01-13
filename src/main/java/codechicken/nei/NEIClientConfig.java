package codechicken.nei;

import java.io.File;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.storage.SaveFormatComparator;
import net.minecraftforge.common.MinecraftForge;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;

import com.google.common.base.Objects;

import codechicken.core.CCUpdateChecker;
import codechicken.core.ClassDiscoverer;
import codechicken.core.ClientUtils;
import codechicken.core.CommonUtils;
import codechicken.lib.config.ConfigFile;
import codechicken.lib.config.ConfigTag;
import codechicken.lib.config.ConfigTagParent;
import codechicken.nei.api.API;
import codechicken.nei.api.GuiInfo;
import codechicken.nei.api.IConfigureNEI;
import codechicken.nei.api.ItemInfo;
import codechicken.nei.api.NEIInfo;
import codechicken.nei.config.ConfigSet;
import codechicken.nei.config.GuiHighlightTips;
import codechicken.nei.config.GuiNEIOptionList;
import codechicken.nei.config.GuiOptionList;
import codechicken.nei.config.GuiPanelSettings;
import codechicken.nei.config.OptionCycled;
import codechicken.nei.config.OptionGamemodes;
import codechicken.nei.config.OptionList;
import codechicken.nei.config.OptionOpenGui;
import codechicken.nei.config.OptionTextField;
import codechicken.nei.config.OptionToggleButton;
import codechicken.nei.config.OptionToggleButtonBoubs;
import codechicken.nei.config.OptionUtilities;
import codechicken.nei.event.NEIConfigsLoadedEvent;
import codechicken.nei.recipe.GuiRecipeTab;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.RecipeCatalysts;
import codechicken.nei.recipe.RecipeInfo;
import codechicken.obfuscator.ObfuscationRun;

public class NEIClientConfig {

    private static boolean mainNEIConfigLoaded;
    private static boolean pluginNEIConfigLoaded;
    private static boolean enabledOverride;
    private static String worldPath;

    public static Logger logger = LogManager.getLogger("NotEnoughItems");
    public static File configDir = new File(CommonUtils.getMinecraftDir(), "config/NEI/");
    public static ConfigSet global = new ConfigSet(
            new File("saves/NEI/client.dat"),
            new ConfigFile(new File(configDir, "client.cfg")));
    public static ConfigSet world;
    public static final File handlerFile = new File(configDir, "handlers.csv");
    public static final File catalystFile = new File(configDir, "catalysts.csv");
    public static final File serialHandlersFile = new File(configDir, "serialhandlers.cfg");
    public static final File heightHackHandlersFile = new File(configDir, "heighthackhandlers.cfg");
    public static final File handlerOrderingFile = new File(configDir, "handlerordering.csv");
    public static final File hiddenHandlersFile = new File(configDir, "hiddenhandlers.csv");

    @Deprecated
    public static File bookmarkFile;

    // Set of handlers that need to be run in serial
    public static HashSet<String> serialHandlers = new HashSet<>();

    // Set of regexes matching handler ID of handlers that need the hack in GuiRecipe.startHeightHack().
    // We use regex here so that we can apply the height hack to entire mods with one entry.
    public static HashSet<Pattern> heightHackHandlerRegex = new HashSet<>();

    // Set of handler Name or Id of handlers that need hide.
    public static HashSet<String> hiddenHandlers = new HashSet<>();

    // Map of handler ID to sort order.
    // Handlers will be sorted in ascending order, so smaller numbers show up earlier.
    // Any handler not in the map will be assigned to 0, and negative numbers are fine.
    public static HashMap<String, Integer> handlerOrdering = new HashMap<>();

    // Function that extracts the handler ID from a handler, with special logic for
    // TemplateRecipeHandler: prefer using the overlay ID if it exists.
    public static final Function<IRecipeHandler, String> HANDLER_ID_FUNCTION = handler -> Objects
            .firstNonNull(handler.getOverlayIdentifier(), handler.getHandlerId());

    public static int getHandlerOrder(IRecipeHandler handler) {
        if (handlerOrdering.get(handler.getOverlayIdentifier()) != null) {
            return handlerOrdering.get(handler.getOverlayIdentifier());
        }
        if (handlerOrdering.get(handler.getHandlerId()) != null) {
            return handlerOrdering.get(handler.getHandlerId());
        }
        return 0;
    }

    // Comparator that compares handlers using the handlerOrdering map.
    public static final Comparator<IRecipeHandler> HANDLER_COMPARATOR = Comparator
            .comparingInt(NEIClientConfig::getHandlerOrder);

    public static ItemStack[] creativeInv;

    public static boolean hasSMPCounterpart;
    public static HashSet<String> permissableActions = new HashSet<>();
    public static HashSet<String> disabledActions = new HashSet<>();
    public static HashSet<String> enabledActions = new HashSet<>();

    public static ItemStackSet bannedBlocks = new ItemStackSet();

    static {
        if (global.config.getTag("checkUpdates").getBooleanValue(true)) CCUpdateChecker.updateCheck("NotEnoughItems");
        linkOptionList();
        setDefaults();
    }

    private static void setDefaults() {
        ConfigTagParent tag = global.config;
        tag.setComment(
                "Main configuration of NEI.\nMost of these options can be changed ingame.\nDeleting any element will restore it to it's default value");

        tag.getTag("command").useBraces().setComment(
                "Change these options if you have a different mod installed on the server that handles the commands differently, Eg. Bukkit Essentials");
        tag.setNewLineMode(1);

        tag.getTag("inventory.widgetsenabled").getBooleanValue(true);
        API.addOption(new OptionToggleButton("inventory.widgetsenabled"));

        tag.getTag("inventory.hidden").getBooleanValue(false);
        tag.getTag("inventory.cheatmode").getIntValue(2);
        tag.getTag("inventory.lockmode").setComment(
                "For those who can't help themselves.\nSet this to a mode and you will be unable to change it ingame")
                .getIntValue(-1);
        API.addOption(new OptionCycled("inventory.cheatmode", 3) {

            @Override
            public boolean optionValid(int index) {
                return getLockedMode() == -1 || getLockedMode() == index && NEIInfo.isValidMode(index);
            }
        });
        checkCheatMode();

        tag.getTag("inventory.utilities").setDefaultValue("delete, magnet");
        API.addOption(new OptionUtilities("inventory.utilities"));

        tag.getTag("inventory.gamemodes").setDefaultValue("creative, creative+, adventure");
        API.addOption(new OptionGamemodes("inventory.gamemodes"));

        ItemSorter.initConfig(tag);

        tag.getTag("inventory.itemIDs").getIntValue(1);
        API.addOption(new OptionCycled("inventory.itemIDs", 3, true));

        tag.getTag("inventory.searchmode").getIntValue(1);
        API.addOption(new OptionCycled("inventory.searchmode", 3, true));

        tag.getTag("world.highlight_tips").getBooleanValue(false);
        tag.getTag("world.highlight_tips.x").getIntValue(5000);
        tag.getTag("world.highlight_tips.y").getIntValue(100);
        API.addOption(new OptionOpenGui("world.highlight_tips", GuiHighlightTips.class));

        tag.getTag("world.panels.bookmarks.left").getIntValue(0);
        tag.getTag("world.panels.bookmarks.right").getIntValue(0);
        tag.getTag("world.panels.bookmarks.top").getIntValue(0);
        tag.getTag("world.panels.bookmarks.bottom").getIntValue(0);

        tag.getTag("world.panels.items.left").getIntValue(0);
        tag.getTag("world.panels.items.right").getIntValue(0);
        tag.getTag("world.panels.items.top").getIntValue(0);
        tag.getTag("world.panels.items.bottom").getIntValue(0);

        API.addOption(new OptionOpenGui("world.panels", GuiPanelSettings.class));

        tag.getTag("inventory.profileRecipes").getBooleanValue(false);
        API.addOption(new OptionToggleButton("inventory.profileRecipes", true));

        tag.getTag("inventory.disableMouseScrollTransfer").getBooleanValue(false);
        API.addOption(new OptionToggleButton("inventory.disableMouseScrollTransfer", true));

        tag.getTag("inventory.invertMouseScrollTransfer").getBooleanValue(false);
        API.addOption(new OptionToggleButton("inventory.invertMouseScrollTransfer", true) {

            @Override
            public boolean isEnabled() {
                return isMouseScrollTransferEnabled();
            }
        });

        tag.getTag("inventory.cacheItemRendering").getBooleanValue(false);
        API.addOption(new OptionToggleButton("inventory.cacheItemRendering", true));

        tag.getTag("itemLoadingTimeout").getIntValue(500);

        tag.getTag("command.creative").setDefaultValue("/gamemode {0} {1}");
        API.addOption(new OptionTextField("command.creative"));
        tag.getTag("command.item").setDefaultValue("/give {0} {1} {2} {3} {4}");
        API.addOption(new OptionTextField("command.item"));
        tag.getTag("command.time").setDefaultValue("/time set {0}");
        API.addOption(new OptionTextField("command.time"));
        tag.getTag("command.rain").setDefaultValue("/toggledownfall");
        API.addOption(new OptionTextField("command.rain"));
        tag.getTag("command.heal").setDefaultValue("");
        API.addOption(new OptionTextField("command.heal"));

        tag.getTag("inventory.worldSpecificBookmarks").setComment("Global or world specific bookmarks")
                .getBooleanValue(false);
        API.addOption(new OptionToggleButton("inventory.worldSpecificBookmarks", true) {

            @Override
            public boolean onClick(int button) {
                super.onClick(button);
                initBookmarkFile(worldPath);
                return true;
            }
        });

        tag.getTag("inventory.worldSpecificPresets").setComment("Global or world specific presets")
                .getBooleanValue(false);
        API.addOption(new OptionToggleButton("inventory.worldSpecificPresets", true) {

            @Override
            public boolean onClick(int button) {
                super.onClick(button);
                initPresetsFile(worldPath);
                return true;
            }
        });

        tag.getTag("inventory.bookmarksEnabled").setComment("Enable/disable bookmarks").getBooleanValue(true);
        API.addOption(new OptionToggleButton("inventory.bookmarksEnabled", true));

        tag.getTag("inventory.bookmarksAnimationEnabled").setComment("REI Style Animation in Bookmarks")
                .getBooleanValue(true);
        API.addOption(new OptionToggleButton("inventory.bookmarksAnimationEnabled", true));

        tag.getTag("inventory.recipeTooltipsMode").setComment("Show recipe tooltips in Bookmarks").getIntValue(1);
        API.addOption(new OptionCycled("inventory.recipeTooltipsMode", 4, true));

        tag.getTag("inventory.showRecipeMarker").setComment("Show Recipe Marker").getBooleanValue(false);
        API.addOption(new OptionToggleButton("inventory.showRecipeMarker", true));

        tag.getTag("inventory.showItemQuantityWidget").setComment("Show Item Quantity Widget").getBooleanValue(true);
        API.addOption(new OptionToggleButton("inventory.showItemQuantityWidget", true));

        tag.getTag("inventory.centerSearchWidget").setComment("Center Search Widget").getBooleanValue(true);
        API.addOption(new OptionToggleButton("inventory.centerSearchWidget", true));

        tag.getTag("inventory.jei_style_tabs").setComment("Enable/disable JEI Style Tabs").getBooleanValue(true);
        API.addOption(new OptionToggleButtonBoubs("inventory.jei_style_tabs", true));

        tag.getTag("inventory.jei_style_item_presence_overlay")
                .setComment("Enable/disable JEI Style item presence overlay on ?-hover").getBooleanValue(true);
        API.addOption(new OptionToggleButton("inventory.jei_style_item_presence_overlay", true));

        tag.getTag("inventory.jei_style_recipe_catalyst").setComment("Enable/disable JEI Style Recipe Catalysts")
                .getBooleanValue(true);
        API.addOption(new OptionToggleButton("inventory.jei_style_recipe_catalyst", true));

        tag.getTag("inventory.creative_tab_style").setComment("Creative or JEI style tabs").getBooleanValue(false);
        API.addOption(new OptionToggleButton("inventory.creative_tab_style", true));

        tag.getTag("inventory.ignore_potion_overlap").setComment("Ignore overlap with potion effect HUD")
                .getBooleanValue(false);
        API.addOption(new OptionToggleButton("inventory.ignore_potion_overlap", true));

        tag.getTag("inventory.optimize_gui_overlap_computation").setComment("Optimize computation for GUI overlap")
                .getBooleanValue(false);
        API.addOption(new OptionToggleButton("inventory.optimize_gui_overlap_computation", true));

        tag.getTag("inventory.jei_style_cycled_ingredients").setComment("JEI styled cycled ingredients")
                .getBooleanValue(true);
        API.addOption(new OptionToggleButton("inventory.jei_style_cycled_ingredients", true));

        tag.getTag("inventory.shift_overlay_recipe")
                .setComment("Require holding shift to move items when overlaying recipe").getBooleanValue(true);
        API.addOption(new OptionToggleButton("inventory.shift_overlay_recipe", true));

        tag.getTag("tools.handler_load_from_config").setComment("ADVANCED: Load handlers from config")
                .getBooleanValue(false);
        API.addOption(new OptionToggleButton("tools.handler_load_from_config", true) {

            @Override
            public boolean onClick(int button) {
                super.onClick(button);
                GuiRecipeTab.loadHandlerInfo();
                return true;
            }
        });

        tag.getTag("tools.catalyst_load_from_config").setComment("ADVANCED: Load catalysts from config")
                .getBooleanValue(false);
        API.addOption(new OptionToggleButton("tools.catalyst_load_from_config", true) {

            @Override
            public boolean onClick(int button) {
                super.onClick(button);
                RecipeCatalysts.loadCatalystInfo();
                return true;
            }
        });

        setDefaultKeyBindings();
    }

    private static void linkOptionList() {
        OptionList.setOptionList(new OptionList("nei.options") {

            @Override
            public ConfigSet globalConfigSet() {
                return global;
            }

            @Override
            public ConfigSet worldConfigSet() {
                return world;
            }

            @Override
            public OptionList configBase() {
                return this;
            }

            @Override
            public GuiOptionList getGui(GuiScreen parent, OptionList list, boolean world) {
                return new GuiNEIOptionList(parent, list, world);
            }
        });
    }

    private static void setDefaultKeyBindings() {
        API.addHashBind("gui.recipe", Keyboard.KEY_R);
        API.addHashBind("gui.usage", Keyboard.KEY_U);
        API.addKeyBind("gui.back", Keyboard.KEY_BACK);
        API.addHashBind("gui.enchant", Keyboard.KEY_X);
        API.addHashBind("gui.potion", Keyboard.KEY_P);
        API.addKeyBind("gui.prev", Keyboard.KEY_PRIOR);
        API.addKeyBind("gui.next", Keyboard.KEY_NEXT);
        API.addKeyBind("gui.prev_machine", Keyboard.KEY_UP);
        API.addKeyBind("gui.next_machine", Keyboard.KEY_DOWN);
        API.addKeyBind("gui.prev_recipe", Keyboard.KEY_LEFT);
        API.addKeyBind("gui.next_recipe", Keyboard.KEY_RIGHT);
        API.addHashBind("gui.hide", Keyboard.KEY_O);
        API.addHashBind("gui.search", Keyboard.KEY_F);
        API.addHashBind("gui.bookmark", Keyboard.KEY_A);
        API.addHashBind("gui.bookmark_recipe", Keyboard.KEY_A + NEIClientUtils.SHIFT_HASH);
        API.addHashBind("gui.bookmark_count", Keyboard.KEY_A + NEIClientUtils.CTRL_HASH);
        API.addHashBind(
                "gui.bookmark_recipe_count",
                Keyboard.KEY_A + NEIClientUtils.SHIFT_HASH + NEIClientUtils.CTRL_HASH);
        API.addHashBind("gui.bookmark_pull_items", Keyboard.KEY_V);
        API.addHashBind("gui.bookmark_pull_items_ingredients", Keyboard.KEY_V + NEIClientUtils.SHIFT_HASH);
        API.addHashBind("gui.overlay", Keyboard.KEY_S);
        API.addHashBind("gui.overlay_use", Keyboard.KEY_S + NEIClientUtils.SHIFT_HASH);
        API.addHashBind("gui.overlay_hide", Keyboard.KEY_S + NEIClientUtils.CTRL_HASH);
        API.addHashBind("gui.hide_bookmarks", Keyboard.KEY_B);
        API.addKeyBind("gui.getprevioussearch", Keyboard.KEY_UP);
        API.addKeyBind("gui.getnextsearch", Keyboard.KEY_DOWN);
        API.addHashBind("gui.next_tooltip", Keyboard.KEY_Z);

        API.addKeyBind("world.chunkoverlay", Keyboard.KEY_F9);
        API.addKeyBind("world.moboverlay", Keyboard.KEY_F7);
        API.addKeyBind("world.highlight_tips", Keyboard.KEY_NUMPAD0);
        API.addKeyBind("world.dawn", 0);
        API.addKeyBind("world.noon", 0);
        API.addKeyBind("world.dusk", 0);
        API.addKeyBind("world.midnight", 0);
        API.addKeyBind("world.rain", 0);
        API.addKeyBind("world.heal", 0);
        API.addKeyBind("world.creative", 0);
    }

    public static OptionList getOptionList() {
        return OptionList.getOptionList("nei.options");
    }

    public static void loadWorld(String worldPath) {
        NEIClientConfig.worldPath = worldPath;

        setInternalEnabled(true);
        logger.debug("Loading " + (Minecraft.getMinecraft().isSingleplayer() ? "Local" : "Remote") + " World");
        bootNEI(ClientUtils.getWorld());

        final File specificDir = new File(CommonUtils.getMinecraftDir(), "saves/NEI/" + worldPath);
        final boolean newWorld = !specificDir.exists();

        if (newWorld) {
            specificDir.mkdirs();
        }

        initBookmarkFile(worldPath);
        initPresetsFile(worldPath);
        world = new ConfigSet(new File(specificDir, "NEI.dat"), new ConfigFile(new File(specificDir, "NEI.cfg")));
        onWorldLoad(newWorld);
    }

    private static void onWorldLoad(boolean newWorld) {
        world.config.setComment(
                "World based configuration of NEI.\nMost of these options can be changed ingame.\nDeleting any element will restore it to it's default value");

        setWorldDefaults();
        creativeInv = new ItemStack[54];
        LayoutManager.searchField.setText(getSearchExpression());
        ItemPanels.itemPanel.quantity.setText(Integer.toString(getItemQuantity()));
        SubsetWidget.loadHidden();

        if (newWorld && Minecraft.getMinecraft().isSingleplayer()) world.config.getTag("inventory.cheatmode")
                .setIntValue(NEIClientUtils.mc().playerController.isInCreativeMode() ? 2 : 0);

        NEIInfo.load(ClientUtils.getWorld());
    }

    private static void setWorldDefaults() {
        NBTTagCompound nbt = world.nbt;
        if (!nbt.hasKey("search")) nbt.setString("search", "");
        if (!nbt.hasKey("quantity")) nbt.setInteger("quantity", 0);
        if (!nbt.hasKey("validateenchantments")) nbt.setBoolean("validateenchantments", false);

        world.saveNBT();
    }

    public static void unloadWorld() {
        ItemPanels.bookmarkPanel.saveBookmarks();
        world.saveNBT();
    }

    public static int getKeyBinding(String string) {
        return getSetting("keys." + string).getIntValue(Keyboard.KEY_NONE);
    }

    public static void setDefaultKeyBinding(String string, int key) {
        getSetting("keys." + string).getIntValue(key);
    }

    public static boolean isKeyHashDown(String string) {
        final int hash = getKeyBinding(string);
        return hash != Keyboard.CHAR_NONE && hash == NEIClientUtils.getKeyHash();
    }

    public static String getKeyName(int keyBind, boolean useHash) {
        return getKeyName(keyBind, useHash, false);
    }

    public static String getKeyName(int keyBind, boolean useHash, boolean showOnlyHash) {
        String keyText = "";

        if (useHash) {
            final String DELIMITER = " + ";
            if ((keyBind & NEIClientUtils.CTRL_HASH) != 0) {
                keyText += NEIClientUtils.translate(Minecraft.isRunningOnMac ? "key.ctrl.mac" : "key.ctrl") + DELIMITER;
            }
            if ((keyBind & NEIClientUtils.SHIFT_HASH) != 0) {
                keyText += "SHIFT" + DELIMITER;
            }
            if ((keyBind & NEIClientUtils.ALT_HASH) != 0) {
                keyText += "ALT" + DELIMITER;
            }
        }

        if (!showOnlyHash) {
            keyText += Keyboard.getKeyName(unHashKey(keyBind));
        }
        return keyText;
    }

    public static int unHashKey(int keyBind) {
        return keyBind & ~(NEIClientUtils.CTRL_HASH | NEIClientUtils.SHIFT_HASH | NEIClientUtils.ALT_HASH);
    }

    public static void bootNEI(World world) {
        if (mainNEIConfigLoaded) return;

        // main NEI config loading
        ItemInfo.load(world);
        GuiInfo.load();
        RecipeInfo.load();
        LayoutManager.load();
        NEIController.load();
        BookmarkContainerInfo.load();
        mainNEIConfigLoaded = true;

        new Thread("NEI Plugin Loader") {

            @Override
            public void run() {
                logger.debug("Started NEI plugin loading");
                ClassDiscoverer classDiscoverer = new ClassDiscoverer(
                        test -> test.startsWith("NEI") && test.endsWith("Config.class"),
                        IConfigureNEI.class);

                classDiscoverer.findClasses();

                for (Class<?> clazz : classDiscoverer.classes) {
                    try {
                        IConfigureNEI config = (IConfigureNEI) clazz.newInstance();
                        config.loadConfig();
                        NEIModContainer.plugins.add(config);
                        logger.debug("Loaded " + clazz.getName());
                    } catch (Exception e) {
                        logger.error("Failed to Load " + clazz.getName(), e);
                    }
                }

                RecipeCatalysts.loadCatalystInfo();

                // Set pluginNEIConfigLoaded here before posting the NEIConfigsLoadedEvent. This used to be the other
                // way around, but apparently if your modpack includes 800 mods the event poster might not return in
                // time and cause issues when loading a world for a second time as configLoaded is still false. This may
                // cause issues in case one of the event handler calls the (non-thread-safe) NEI API. I don't expect any
                // handler to do this, but who knows what modders have come up with...
                pluginNEIConfigLoaded = true;
                logger.debug("NEI plugin loading finished");
                MinecraftForge.EVENT_BUS.post(new NEIConfigsLoadedEvent());
            }
        }.start();
        ItemSorter.loadConfig();
    }

    private static void initBookmarkFile(String worldPath) {

        if (!global.config.getTag("inventory.worldSpecificBookmarks").getBooleanValue()) {
            worldPath = "global";
        }

        ItemPanels.bookmarkPanel.setBookmarkFile(worldPath);
    }

    private static void initPresetsFile(String worldPath) {

        if (!global.config.getTag("inventory.worldSpecificPresets").getBooleanValue()) {
            worldPath = "global";
        }

        PresetsWidget.loadPresets(worldPath);
    }

    public static boolean isWorldSpecific(String setting) {
        if (world == null) return false;
        ConfigTag tag = world.config.getTag(setting, false);
        return tag != null && tag.value != null;
    }

    public static ConfigTag getSetting(String s) {
        return isWorldSpecific(s) ? world.config.getTag(s) : global.config.getTag(s);
    }

    public static boolean getBooleanSetting(String s) {
        return getSetting(s).getBooleanValue();
    }

    public static boolean isHidden() {
        return !enabledOverride || getBooleanSetting("inventory.hidden");
    }

    public static boolean isBookmarkPanelHidden() {
        return !getBooleanSetting("inventory.bookmarksEnabled");
    }

    public static boolean areBookmarksAnimated() {
        return getBooleanSetting("inventory.bookmarksAnimationEnabled");
    }

    public static int getRecipeTooltipsMode() {
        return getIntSetting("inventory.recipeTooltipsMode");
    }

    public static boolean showRecipeMarker() {
        return getBooleanSetting("inventory.showRecipeMarker");
    }

    public static boolean showItemQuantityWidget() {
        return getBooleanSetting("inventory.showItemQuantityWidget");
    }

    public static boolean isSearchWidgetCentered() {
        return getBooleanSetting("inventory.centerSearchWidget");
    }

    public static boolean areJEIStyleTabsVisible() {
        return getBooleanSetting("inventory.jei_style_tabs");
    }

    public static boolean isJEIStyleItemPresenceOverlayVisible() {
        return getBooleanSetting("inventory.jei_style_item_presence_overlay");
    }

    public static boolean areJEIStyleRecipeCatalystsVisible() {
        return getBooleanSetting("inventory.jei_style_recipe_catalyst");
    }

    public static boolean useCreativeTabStyle() {
        return getBooleanSetting("inventory.creative_tab_style");
    }

    public static boolean ignorePotionOverlap() {
        return getBooleanSetting("inventory.ignore_potion_overlap");
    }

    public static boolean optimizeGuiOverlapComputation() {
        return getBooleanSetting("inventory.optimize_gui_overlap_computation");
    }

    public static boolean useJEIStyledCycledIngredients() {
        return getBooleanSetting("inventory.jei_style_cycled_ingredients");
    }

    public static boolean requireShiftForOverlayRecipe() {
        return getBooleanSetting("inventory.shift_overlay_recipe");
    }

    public static boolean isEnabled() {
        return enabledOverride && getBooleanSetting("inventory.widgetsenabled");
    }

    public static boolean isLoaded() {
        return mainNEIConfigLoaded && pluginNEIConfigLoaded;
    }

    public static boolean loadHandlersFromJar() {
        return !getBooleanSetting("tools.handler_load_from_config");
    }

    public static boolean loadCatalystsFromJar() {
        return !getBooleanSetting("tools.catalyst_load_from_config");
    }

    public static void setEnabled(boolean flag) {
        getSetting("inventory.widgetsenabled").setBooleanValue(flag);
    }

    public static int getItemQuantity() {
        return world.nbt.getInteger("quantity");
    }

    public static int getCheatMode() {
        return getIntSetting("inventory.cheatmode");
    }

    private static void checkCheatMode() {
        if (getLockedMode() != -1) setIntSetting("inventory.cheatmode", getLockedMode());
    }

    public static int getLockedMode() {
        return getIntSetting("inventory.lockmode");
    }

    public static int getLayoutStyle() {
        return getIntSetting("inventory.layoutstyle");
    }

    public static String getStringSetting(String s) {
        return getSetting(s).getValue();
    }

    public static boolean showIDs() {
        int i = getIntSetting("inventory.itemIDs");
        return i == 2 || (i == 1 && isEnabled() && !isHidden());
    }

    public static int getItemLoadingTimeout() {
        return getIntSetting("itemLoadingTimeout");
    }

    public static void toggleBooleanSetting(String setting) {
        ConfigTag tag = getSetting(setting);
        tag.setBooleanValue(!tag.getBooleanValue());
    }

    public static void cycleSetting(String setting, int max) {
        ConfigTag tag = getSetting(setting);
        tag.setIntValue((tag.getIntValue() + 1) % max);
    }

    public static int getIntSetting(String setting) {
        return getSetting(setting).getIntValue();
    }

    public static void setIntSetting(String setting, int val) {
        getSetting(setting).setIntValue(val);
    }

    public static String getSearchExpression() {
        return world.nbt.getString("search");
    }

    public static void setSearchExpression(String expression) {
        world.nbt.setString("search", expression);
    }

    public static boolean isMouseScrollTransferEnabled() {
        return !getBooleanSetting("inventory.disableMouseScrollTransfer");
    }

    public static boolean shouldInvertMouseScrollTransfer() {
        return !getBooleanSetting("inventory.invertMouseScrollTransfer");
    }

    public static boolean shouldCacheItemRendering() {
        return getBooleanSetting("inventory.cacheItemRendering") && OpenGlHelper.framebufferSupported;
    }

    public static boolean getMagnetMode() {
        return enabledActions.contains("magnet");
    }

    public static boolean invCreativeMode() {
        return enabledActions.contains("creative+") && canPerformAction("creative+");
    }

    public static boolean areDamageVariantsShown() {
        return hasSMPCounterPart() || getSetting("command.item").getValue().contains("{3}");
    }

    public static boolean hasSMPCounterPart() {
        return hasSMPCounterpart;
    }

    public static void setHasSMPCounterPart(boolean flag) {
        hasSMPCounterpart = flag;
        permissableActions.clear();
        bannedBlocks.clear();
        disabledActions.clear();
        enabledActions.clear();
    }

    public static boolean canCheatItem(ItemStack stack) {
        return canPerformAction("item") && !bannedBlocks.contains(stack);
    }

    public static boolean canPerformAction(String name) {
        if (!isEnabled()) return false;

        if (!modePermitsAction(name)) return false;

        String base = NEIActions.base(name);
        if (hasSMPCounterpart) return permissableActions.contains(base);

        if (NEIActions.smpRequired(name)) return false;

        String cmd = getStringSetting("command." + base);
        return cmd != null && cmd.startsWith("/");
    }

    private static boolean modePermitsAction(String name) {
        if (getCheatMode() == 0) return false;
        if (getCheatMode() == 2) return true;

        String[] actions = getStringArrSetting("inventory.utilities");
        for (String action : actions) if (action.equalsIgnoreCase(name)) return true;

        return false;
    }

    public static String[] getStringArrSetting(String s) {
        return getStringSetting(s).replace(" ", "").split(",");
    }

    public static void setInternalEnabled(boolean b) {
        enabledOverride = b;
    }

    public static void reloadSaves() {
        File saveDir = new File(CommonUtils.getMinecraftDir(), "saves/NEI/local");
        if (!saveDir.exists()) return;

        List<SaveFormatComparator> saves;
        try {
            saves = Minecraft.getMinecraft().getSaveLoader().getSaveList();
        } catch (Exception e) {
            logger.error("Error loading saves", e);
            return;
        }
        HashSet<String> saveFileNames = new HashSet<>();
        for (SaveFormatComparator save : saves) saveFileNames.add(save.getFileName());

        for (File file : saveDir.listFiles())
            if (file.isDirectory() && !saveFileNames.contains(file.getName())) ObfuscationRun.deleteDir(file, true);
    }
}
