package codechicken.nei;

import java.io.File;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.storage.SaveFormatComparator;
import net.minecraftforge.common.MinecraftForge;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;

import com.google.common.base.Objects;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import codechicken.core.CCUpdateChecker;
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
import codechicken.nei.config.OptionIntegerField;
import codechicken.nei.config.OptionList;
import codechicken.nei.config.OptionOpenGui;
import codechicken.nei.config.OptionTextField;
import codechicken.nei.config.OptionToggleButton;
import codechicken.nei.config.OptionToggleButtonBoubs;
import codechicken.nei.config.OptionUtilities;
import codechicken.nei.config.preset.GuiPresetList;
import codechicken.nei.event.NEIConfigsLoadedEvent;
import codechicken.nei.recipe.GuiRecipeTab;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.InformationHandler;
import codechicken.nei.recipe.RecipeCatalysts;
import codechicken.nei.recipe.RecipeInfo;
import codechicken.nei.util.ItemUntranslator;
import codechicken.nei.util.NEIKeyboardUtils;
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

    public static final Set<Class<?>> pluginsList = new HashSet<>();

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

        tag.getTag("findFuelsParallel").getBooleanValue(true);

        tag.getTag("inventory.autocrafting").getBooleanValue(true);
        tag.getTag("inventory.dynamicFontSize").getBooleanValue(true);
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
        canChangeCheatMode();

        tag.getTag("inventory.utilities").setDefaultValue("delete, magnet");
        API.addOption(new OptionUtilities("inventory.utilities"));

        tag.getTag("inventory.gamemodes").setDefaultValue("creative, creative+, adventure");
        API.addOption(new OptionGamemodes("inventory.gamemodes"));

        ItemSorter.initConfig(tag);
        setInventorySearchDefaults(tag);

        tag.getTag("inventory.bookmarks.enabled").setComment("Enable/Disable Bookmark Panel").getBooleanValue(true);
        API.addOption(new OptionToggleButton("inventory.bookmarks.enabled", true));

        tag.getTag("inventory.bookmarks.worldSpecific").setComment("Global or world specific bookmarks")
                .getBooleanValue(false);
        API.addOption(new OptionToggleButton("inventory.bookmarks.worldSpecific", true) {

            @Override
            public boolean onClick(int button) {
                super.onClick(button);
                ItemPanels.bookmarkPanel.save();
                ItemPanels.bookmarkPanel.load();
                return true;
            }
        });

        tag.getTag("inventory.bookmarks.animation").setComment("REI Style Animation in Bookmarks")
                .getBooleanValue(true);
        API.addOption(new OptionToggleButton("inventory.bookmarks.animation", true));

        tag.getTag("inventory.bookmarks.recipeTooltipsMode").setComment("Show recipe tooltips in Bookmarks")
                .getIntValue(1);
        API.addOption(new OptionCycled("inventory.bookmarks.recipeTooltipsMode", 4, true));

        tag.getTag("inventory.bookmarks.showRecipeMarkerMode").setComment("Show Recipe Marker").getIntValue(0);
        API.addOption(new OptionCycled("inventory.bookmarks.showRecipeMarkerMode", 3, true));

        tag.getTag("inventory.bookmarks.recipeMarkerColor").setComment("Color of the icon marker")
                .getHexValue(0xADADAD);
        API.addOption(
                new OptionIntegerField(
                        "inventory.bookmarks.recipeMarkerColor",
                        0,
                        OptionIntegerField.UNSIGNED_INT_MAX));

        tag.getTag("inventory.bookmarks.recipeChainDir").getIntValue(1);
        API.addOption(new OptionCycled("inventory.bookmarks.recipeChainDir", 2, true));

        tag.getTag("inventory.bookmarks.ignorePotionOverlap").setComment("Ignore overlap with potion effect HUD")
                .getBooleanValue(false);
        API.addOption(new OptionToggleButton("inventory.bookmarks.ignorePotionOverlap", true));

        tag.getTag("inventory.bookmarks.bookmarkItemsWithRecipe").setComment("Bookmark items with recipe")
                .getBooleanValue(true);
        API.addOption(new OptionToggleButton("inventory.bookmarks.bookmarkItemsWithRecipe", true));

        tag.getTag("inventory.guirecipe.jeiStyleTabs").setComment("Enable/disable JEI Style Tabs")
                .getBooleanValue(true);
        API.addOption(new OptionToggleButtonBoubs("inventory.guirecipe.jeiStyleTabs", true));

        tag.getTag("inventory.guirecipe.jeiStyleRecipeCatalyst").setComment("Enable/disable JEI Style Recipe Catalysts")
                .getBooleanValue(true);
        API.addOption(new OptionToggleButton("inventory.guirecipe.jeiStyleRecipeCatalyst", true));

        tag.getTag("inventory.guirecipe.jeiStyleCycledIngredients").setComment("JEI styled cycled ingredients")
                .getBooleanValue(true);
        API.addOption(new OptionToggleButton("inventory.guirecipe.jeiStyleCycledIngredients", true));

        tag.getTag("inventory.guirecipe.cycledIngredientsTooltip").setComment("Show cycled ingredients tooltip")
                .getBooleanValue(true);
        API.addOption(new OptionToggleButton("inventory.guirecipe.cycledIngredientsTooltip", true));

        tag.getTag("inventory.guirecipe.creativeTabStyle").setComment("Creative or JEI style tabs")
                .getBooleanValue(false);
        API.addOption(new OptionToggleButton("inventory.guirecipe.creativeTabStyle", true));

        tag.getTag("inventory.guirecipe.itemPresenceOverlay").setComment("Item presence overlay on ?-hover")
                .getIntValue(1);
        API.addOption(new OptionCycled("inventory.guirecipe.itemPresenceOverlay", 3, true));

        tag.getTag("inventory.guirecipe.slotHighlightPresent").setComment("Highlight Present Item")
                .getBooleanValue(true);
        API.addOption(new OptionToggleButton("inventory.guirecipe.slotHighlightPresent", true));

        tag.getTag("inventory.guirecipe.shiftOverlayRecipe")
                .setComment("Require holding shift to move items when overlaying recipe").getBooleanValue(true);
        API.addOption(new OptionToggleButton("inventory.guirecipe.shiftOverlayRecipe", true));

        tag.getTag("inventory.guirecipe.profile").getBooleanValue(false);
        API.addOption(new OptionToggleButton("inventory.guirecipe.profile", true));

        tag.getTag("inventory.subsets.enabled").setComment("Enable/disable Subsets Dropdown").getBooleanValue(true);
        API.addOption(new OptionToggleButton("inventory.subsets.enabled", true));

        tag.getTag("inventory.subsets.widgetPosition").setComment("Subsets Widget Position").getBooleanValue(true);
        API.addOption(new OptionToggleButton("inventory.subsets.widgetPosition", true));

        tag.getTag("inventory.history.enabled").setComment("Enable/disable History Panel").getBooleanValue(true);
        API.addOption(new OptionToggleButton("inventory.history.enabled", true));

        tag.getTag("inventory.history.color").setComment("Color of the history area display").getHexValue(0xee555555);
        API.addOption(new OptionIntegerField("inventory.history.color", 0, OptionIntegerField.UNSIGNED_INT_MAX));

        tag.getTag("inventory.history.useRows").setComment("Rows used in historical areas").getIntValue(2);
        API.addOption(new OptionIntegerField("inventory.history.useRows", 1, 5));

        tag.getTag("inventory.craftables.enabled").setComment("Enable/disable Craftables Panel").getBooleanValue(false);
        API.addOption(new OptionToggleButton("inventory.craftables.enabled", true));

        tag.getTag("inventory.craftables.color").setComment("Color of the craftables area display")
                .getHexValue(0xee555555);
        API.addOption(new OptionIntegerField("inventory.craftables.color", 0, OptionIntegerField.UNSIGNED_INT_MAX));

        tag.getTag("inventory.craftables.useRows").setComment("Rows used in craftables areas").getIntValue(2);
        API.addOption(new OptionIntegerField("inventory.craftables.useRows", 1, 5));

        tag.getTag("inventory.collapsibleItems.enabled").getBooleanValue(true);
        API.addOption(new OptionToggleButton("inventory.collapsibleItems.enabled", true) {

            @Override
            public boolean onClick(int button) {
                super.onClick(button);
                CollapsibleItems.saveStates();
                CollapsibleItems.load();
                LayoutManager.markItemsDirty();
                return true;
            }
        });

        tag.getTag("inventory.collapsibleItems.customName").getBooleanValue(true);
        API.addOption(new OptionToggleButton("inventory.collapsibleItems.customName", true));

        tag.getTag("inventory.collapsibleItems.expandedColor")
                .setComment("Color of the collapsible item expanded state").getHexValue(0x335555EE);
        API.addOption(
                new OptionIntegerField(
                        "inventory.collapsibleItems.expandedColor",
                        0,
                        OptionIntegerField.UNSIGNED_INT_MAX));

        tag.getTag("inventory.collapsibleItems.collapsedColor")
                .setComment("Color of the collapsible item collapsed state").getHexValue(0x335555EE);
        API.addOption(
                new OptionIntegerField(
                        "inventory.collapsibleItems.collapsedColor",
                        0,
                        OptionIntegerField.UNSIGNED_INT_MAX));

        tag.getTag("inventory.itemzoom.enabled").getBooleanValue(true);
        API.addOption(new OptionToggleButton("inventory.itemzoom.enabled", true));

        tag.getTag("inventory.itemzoom.neiOnly")
                .setComment("Zoom items only from the JEI ingredient and bookmark list overlays.")
                .getBooleanValue(false);
        API.addOption(new OptionToggleButton("inventory.itemzoom.neiOnly", true));

        tag.getTag("inventory.itemzoom.onlySolid").getBooleanValue(false);
        API.addOption(new OptionToggleButton("inventory.itemzoom.onlySolid", true));

        tag.getTag("inventory.itemzoom.helpText")
                .setComment("Display name \"Item Zoom\" and the hotkey to toggle this mod below the zoomed item.")
                .getBooleanValue(true);
        API.addOption(new OptionToggleButton("inventory.itemzoom.helpText", true));

        tag.getTag("inventory.itemzoom.zoom").getIntValue(500);
        API.addOption(new OptionIntegerField("inventory.itemzoom.zoom", ItemZoom.MIN_ZOOM, ItemZoom.MAX_ZOOM));

        tag.getTag("inventory.itemzoom.showName").getBooleanValue(true);
        API.addOption(new OptionToggleButton("inventory.itemzoom.showName", true));

        tag.getTag("inventory.itemzoom.nameColor").getHexValue(0xFFFFFFFF);
        API.addOption(new OptionIntegerField("inventory.itemzoom.nameColor", 0, OptionIntegerField.UNSIGNED_INT_MAX));

        setFavoriteDefaults(tag);

        tag.getTag("inventory.itemIDs").getIntValue(1);
        API.addOption(new OptionCycled("inventory.itemIDs", 3, true));

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

        API.addOption(new OptionOpenGui("world.presets", GuiPresetList.class));

        tag.getTag("world.overlays.lock").getBooleanValue(true);
        API.addOption(new OptionToggleButton("world.overlays.lock", true));

        tag.getTag("inventory.itempanelScale").getIntValue(100);
        ItemsPanelGrid.updateScale();
        API.addOption(new OptionIntegerField("inventory.itempanelScale", 1, 1000) {

            @Override
            public void onTextChange(String text) {
                ItemsPanelGrid.updateScale();
            }
        });

        tag.getTag("inventory.disableMouseScrollTransfer").getBooleanValue(false);
        API.addOption(new OptionToggleButton("inventory.disableMouseScrollTransfer", true));

        tag.getTag("inventory.invertMouseScrollTransfer").getBooleanValue(false);
        API.addOption(new OptionToggleButton("inventory.invertMouseScrollTransfer", true) {

            @Override
            public boolean isEnabled() {
                return isMouseScrollTransferEnabled();
            }
        });

        tag.getTag("inventory.gridRenderingCacheMode").getIntValue(0);
        API.addOption(new OptionCycled("inventory.gridRenderingCacheMode", 3, true));

        tag.getTag("inventory.gridRenderingCacheFPS").getIntValue(8);
        API.addOption(new OptionIntegerField("inventory.gridRenderingCacheFPS", 1, 144) {

            @Override
            public boolean isEnabled() {
                return OpenGlHelper.framebufferSupported && getIntSetting("inventory.gridRenderingCacheMode") == 1;
            }

        });
        tag.getTag("inventory.hotkeysHelpText").getBooleanValue(true);
        API.addOption(new OptionToggleButton("inventory.hotkeysHelpText", true));
        tag.getTag("inventory.showHotkeys").getBooleanValue(true);
        API.addOption(new OptionToggleButton("inventory.showHotkeys", true));

        tag.getTag("loadPluginsInParallel").getBooleanValue(true);
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

        tag.getTag("inventory.showItemQuantityWidget").setComment("Show Item Quantity Widget").getBooleanValue(true);
        API.addOption(new OptionToggleButton("inventory.showItemQuantityWidget", true));

        tag.getTag("inventory.optimize_gui_overlap_computation").setComment("Optimize computation for GUI overlap")
                .getBooleanValue(false);
        API.addOption(new OptionToggleButton("inventory.optimize_gui_overlap_computation", true));

        tag.getTag("inventory.autocrafting").setComment("Autocrafting").getBooleanValue(true);
        API.addOption(new OptionToggleButton("inventory.autocrafting", true));

        tag.getTag("inventory.itemUntranslator").setComment("Item Untranslator").getBooleanValue(true);
        API.addOption(new OptionToggleButton("inventory.itemUntranslator", true) {

            @Override
            public boolean onClick(int button) {
                super.onClick(button);

                if (enableItemUntranslator()) {
                    ItemUntranslator.getInstance().load();
                } else {
                    ItemUntranslator.getInstance().unload();
                }

                return true;
            }

        });

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

    private static void setInventorySearchDefaults(ConfigTagParent tag) {

        tag.getTag("inventory.search.widgetPosition").setComment("Widget Position").getBooleanValue(true);
        API.addOption(new OptionToggleButton("inventory.search.widgetPosition", true));

        tag.getTag("inventory.search.hideUntilSearching").setComment("Hide Items Until Searching")
                .getBooleanValue(false);
        API.addOption(new OptionToggleButton("inventory.search.hideUntilSearching", true) {

            @Override
            public boolean onClick(int button) {
                super.onClick(button);
                ItemList.updateFilter.restart();
                return true;
            }
        });

        tag.getTag("inventory.search.widgetAutofocus")
                .setComment(
                        "Focus Search Widget on Open, blurs/unfocuses on mouse move unless typing has started first")
                .getIntValue(0);
        API.addOption(new OptionCycled("inventory.search.widgetAutofocus", 3, true));

        tag.getTag("inventory.search.patternMode").setComment("Search Mode").getIntValue(1);
        API.addOption(new OptionCycled("inventory.search.patternMode", 4, true) {

            @Override
            public boolean onClick(int button) {
                if (!super.onClick(button)) {
                    return false;
                }
                SearchField.searchParser.clearCache();
                return true;
            }

        });

        tag.getTag("inventory.search.quoteDropItemName").setComment("Quote Drop Item Name").getBooleanValue(true);
        API.addOption(new OptionToggleButton("inventory.search.quoteDropItemName", true));

        tag.getTag("inventory.search.format").setComment("Search Format (true: old format, false: custom format)")
                .getBooleanValue(false);
        API.addOption(new OptionToggleButton("inventory.search.format", true) {

            @Override
            public boolean onClick(int button) {

                if (!super.onClick(button)) {
                    return false;
                }

                if (state()) {
                    NEIClientConfig.setIntSetting("inventory.search.spaceMode", 1);
                    NEIClientConfig.setIntSetting("inventory.search.modNameSearchMode", 0);
                    NEIClientConfig.setIntSetting("inventory.search.tooltipSearchMode", 0);
                    NEIClientConfig.setIntSetting("inventory.search.identifierSearchMode", 0);
                    NEIClientConfig.setIntSetting("inventory.search.oreDictSearchMode", 0);
                    NEIClientConfig.setIntSetting("inventory.search.subsetsSearchMode", 1);
                    tag.getTag("inventory.search.prefixRedefinitions").setValue("{\"%\": \"@\", \"@\": \"%\"}");
                    SearchField.searchParser.prefixRedefinitions.clear();
                    SearchField.searchParser.prefixRedefinitions.put('%', '@');
                    SearchField.searchParser.prefixRedefinitions.put('@', '%');
                    SearchField.searchParser.clearCache();
                } else {
                    NEIClientConfig.setIntSetting("inventory.search.spaceMode", 0);
                    NEIClientConfig.setIntSetting("inventory.search.modNameSearchMode", 1);
                    NEIClientConfig.setIntSetting("inventory.search.tooltipSearchMode", 0);
                    NEIClientConfig.setIntSetting("inventory.search.identifierSearchMode", 0);
                    NEIClientConfig.setIntSetting("inventory.search.oreDictSearchMode", 0);
                    NEIClientConfig.setIntSetting("inventory.search.subsetsSearchMode", 1);
                    tag.getTag("inventory.search.prefixRedefinitions").setValue("{}");
                    SearchField.searchParser.prefixRedefinitions.clear();
                    SearchField.searchParser.clearCache();
                }

                return true;
            }

        });

        tag.getTag("inventory.search.spaceMode").setComment("Search Space Rules").getIntValue(0);
        API.addOption(new OptionCycled("inventory.search.spaceMode", 3, true) {

            @Override
            public boolean onClick(int button) {
                SearchField.searchParser.clearCache();
                return super.onClick(button);
            }

            @Override
            public boolean isEnabled() {
                return !tag.getTag("inventory.search.format").getBooleanValue();
            }

        });

        tag.getTag("inventory.search.modNameSearchMode").setComment("Search mode for Mod Names (prefix: @)")
                .getIntValue(1);
        API.addOption(new OptionCycled("inventory.search.modNameSearchMode", 3, true) {

            @Override
            public boolean onClick(int button) {
                if (!super.onClick(button)) {
                    return false;
                }
                SearchField.searchParser.clearCache();
                return true;
            }

            @Override
            public String getButtonText() {
                return translateN(
                        name + "." + value(),
                        EnumChatFormatting.LIGHT_PURPLE
                                + String.valueOf(SearchField.searchParser.getRedefinedPrefix('@')));
            }

            @Override
            public boolean isEnabled() {
                return !tag.getTag("inventory.search.format").getBooleanValue();
            }

        });

        tag.getTag("inventory.search.tooltipSearchMode").setComment("Search mode for Tooltips (prefix: #)")
                .getIntValue(0);
        API.addOption(new OptionCycled("inventory.search.tooltipSearchMode", 3, true) {

            @Override
            public boolean onClick(int button) {
                if (!super.onClick(button)) {
                    return false;
                }
                SearchField.searchParser.clearCache();
                return true;
            }

            @Override
            public String getButtonText() {
                return translateN(
                        name + "." + value(),
                        EnumChatFormatting.YELLOW + String.valueOf(SearchField.searchParser.getRedefinedPrefix('#')));
            }

            @Override
            public boolean isEnabled() {
                return !tag.getTag("inventory.search.format").getBooleanValue();
            }

        });

        tag.getTag("inventory.search.identifierSearchMode").setComment("Search mode for identifier (prefix: &)")
                .getIntValue(0);
        API.addOption(new OptionCycled("inventory.search.identifierSearchMode", 3, true) {

            @Override
            public boolean onClick(int button) {
                if (!super.onClick(button)) {
                    return false;
                }
                SearchField.searchParser.clearCache();
                return true;
            }

            @Override
            public String getButtonText() {
                return translateN(
                        name + "." + value(),
                        EnumChatFormatting.GOLD + String.valueOf(SearchField.searchParser.getRedefinedPrefix('&')));
            }

            @Override
            public boolean isEnabled() {
                return !tag.getTag("inventory.search.format").getBooleanValue();
            }

        });

        tag.getTag("inventory.search.oreDictSearchMode").setComment("Search mode for Tag Names (prefix: $)")
                .getIntValue(0);
        API.addOption(new OptionCycled("inventory.search.oreDictSearchMode", 3, true) {

            @Override
            public boolean onClick(int button) {
                if (!super.onClick(button)) {
                    return false;
                }
                SearchField.searchParser.clearCache();
                return true;
            }

            @Override
            public String getButtonText() {
                return translateN(
                        name + "." + value(),
                        EnumChatFormatting.AQUA + String.valueOf(SearchField.searchParser.getRedefinedPrefix('$')));
            }

            @Override
            public boolean isEnabled() {
                return !tag.getTag("inventory.search.format").getBooleanValue();
            }

        });

        tag.getTag("inventory.search.subsetsSearchMode").setComment("Search mode for Item Subsets (prefix: %)")
                .getIntValue(1);
        API.addOption(new OptionCycled("inventory.search.subsetsSearchMode", 3, true) {

            @Override
            public boolean onClick(int button) {
                if (!super.onClick(button)) {
                    return false;
                }
                SearchField.searchParser.clearCache();
                return true;
            }

            @Override
            public String getButtonText() {
                return translateN(
                        name + "." + value(),
                        EnumChatFormatting.DARK_PURPLE
                                + String.valueOf(SearchField.searchParser.getRedefinedPrefix('%')));
            }

            @Override
            public boolean isEnabled() {
                return !tag.getTag("inventory.search.format").getBooleanValue();
            }

        });

        tag.getTag("inventory.search.logSearchExceptions").setComment("Search exceptions for extended+")
                .getBooleanValue(false);

        String prefixRedefinitions = tag.getTag("inventory.search.prefixRedefinitions").setComment(
                "Redefine search prefixes by providing a char-to-char map (JSON). The keys are the original prefixes, the values the new ones. Example: { \"$\": \"€\", \"#\": \"+\", \"@\": \"°\", \"%\": \"!\" }")
                .getValue("{}");
        try {
            TypeToken<Map<Character, Character>> typeToken = new TypeToken<Map<Character, Character>>() {

                private static final long serialVersionUID = 1L;
            };
            Map<Character, Character> charMap = new Gson().fromJson(prefixRedefinitions, typeToken.getType());
            SearchField.searchParser.prefixRedefinitions.putAll(charMap);
            SearchField.searchParser.clearCache();
        } catch (JsonParseException e) {
            logger.warn("Failed to convert prefix redefinitions from JSON to CharToCharMap:", e);
        }

    }

    private static void setFavoriteDefaults(ConfigTagParent tag) {

        tag.getTag("inventory.favorites.enabled").getBooleanValue(true);
        API.addOption(new OptionToggleButton("inventory.favorites.enabled", true));

        tag.getTag("inventory.favorites.worldSpecific").setComment("Global or world specific favorites")
                .getBooleanValue(false);
        API.addOption(new OptionToggleButton("inventory.favorites.worldSpecific", true) {

            @Override
            public boolean onClick(int button) {
                super.onClick(button);
                FavoriteRecipes.save();
                FavoriteRecipes.load();
                return true;
            }
        });

        tag.getTag("inventory.favorites.showRecipeTooltipInPanel").setComment("Show recipe tooltips in Items Panel")
                .getBooleanValue(true);
        API.addOption(new OptionToggleButton("inventory.favorites.showRecipeTooltipInPanel", true));

        tag.getTag("inventory.favorites.showRecipeTooltipInGui").setComment("Show recipe tooltips in Recipe Gui")
                .getBooleanValue(false);
        API.addOption(new OptionToggleButton("inventory.favorites.showRecipeTooltipInGui", true));

        tag.getTag("inventory.favorites.depth").setComment("Bookmark creation depth").getIntValue(3);
        API.addOption(new OptionIntegerField("inventory.favorites.depth", 0, 100));
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
        API.addKeyBind("gui.bookmark", Keyboard.KEY_A);
        API.addHashBind("gui.favorite", Keyboard.KEY_F + NEIClientUtils.SHIFT_HASH);
        API.addHashBind("gui.remove_recipe", Keyboard.KEY_A + NEIClientUtils.SHIFT_HASH);
        API.addKeyBind("gui.bookmark_pull_items", Keyboard.KEY_V);
        API.addKeyBind("gui.overlay", Keyboard.KEY_S);
        API.addKeyBind("gui.craft_items", Keyboard.KEY_C);
        API.addHashBind("gui.hide_bookmarks", Keyboard.KEY_B);
        API.addKeyBind("gui.getprevioussearch", Keyboard.KEY_UP);
        API.addKeyBind("gui.getnextsearch", Keyboard.KEY_DOWN);
        API.addKeyBind("gui.next_tooltip", Keyboard.KEY_Z);

        API.addHashBind("gui.itemzoom_toggle", Keyboard.KEY_Z + NEIClientUtils.SHIFT_HASH);
        API.addHashBind("gui.itemzoom_hold", 0);
        API.addHashBind("gui.itemzoom_zoom_in", 0);
        API.addHashBind("gui.itemzoom_zoom_out", 0);

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
        API.addHashBind("gui.copy_name", Keyboard.KEY_C + NEIClientUtils.CTRL_HASH);
        API.addHashBind("gui.copy_oredict", Keyboard.KEY_D + NEIClientUtils.CTRL_HASH);
        API.addHashBind("gui.chat_link_item", Keyboard.KEY_L + NEIClientUtils.CTRL_HASH);
    }

    public static OptionList getOptionList() {
        return OptionList.getOptionList("nei.options");
    }

    public static void loadWorld(String worldPath) {
        unloadWorld();
        setInternalEnabled(true);

        if (!worldPath.equals(NEIClientConfig.worldPath)) {
            NEIClientConfig.worldPath = worldPath;

            logger.debug("Loading " + (Minecraft.getMinecraft().isSingleplayer() ? "Local" : "Remote") + " World");

            final File specificDir = new File(CommonUtils.getMinecraftDir(), "saves/NEI/" + worldPath);
            final boolean newWorld = !specificDir.exists();

            if (newWorld) {
                specificDir.mkdirs();
            }

            world = new ConfigSet(new File(specificDir, "NEI.dat"), new ConfigFile(new File(specificDir, "NEI.cfg")));
            bootNEI();
            onWorldLoad(newWorld);
        }
    }

    public static String getWorldPath() {
        return NEIClientConfig.worldPath;
    }

    private static void onWorldLoad(boolean newWorld) {
        world.config.setComment(
                "World based configuration of NEI.\nMost of these options can be changed ingame.\nDeleting any element will restore it to it's default value");

        setWorldDefaults();
        creativeInv = new ItemStack[54];
        LayoutManager.searchField.setText(getSearchExpression());
        LayoutManager.quantity.setText(Integer.toString(getItemQuantity()));

        if (newWorld && Minecraft.getMinecraft().isSingleplayer()) {
            world.config.getTag("inventory.cheatmode")
                    .setIntValue(NEIClientUtils.mc().playerController.isInCreativeMode() ? 2 : 0);
        }

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
        if (world == null) {
            return;
        }

        if (ItemPanels.bookmarkPanel != null) {
            ItemPanels.bookmarkPanel.save();
        }

        SubsetWidget.saveHidden();
        FavoriteRecipes.save();
        CollapsibleItems.saveStates();

        if (world != null) {
            world.saveNBT();
            NEIClientConfig.worldPath = null;
            world = null;
        }
    }

    private static final Map<String, String> keySettings = new HashMap<>();

    public static int getKeyBinding(String string) {
        final String key = keySettings.computeIfAbsent(string, s -> "keys." + s);
        return getSetting(key).getIntValue(Keyboard.KEY_NONE);
    }

    public static void setDefaultKeyBinding(String string, int key) {
        getSetting("keys." + string).getIntValue(key);
    }

    public static boolean isKeyHashDown(String string) {
        final int hash = getKeyBinding(string);

        if (hash != Keyboard.CHAR_NONE && Keyboard.getEventKeyState()) {
            return KeyManager.keyStates.containsKey(string) ? Keyboard.isKeyDown(NEIKeyboardUtils.unhash(hash))
                    : hash == NEIClientUtils.getKeyHash();
        }

        return false;
    }

    public static String getKeyName(String keyBind) {
        return getKeyName(keyBind, 0);
    }

    public static String getKeyName(String keyBind, int meta, int mouseBind) {
        final int hash = getKeyBinding(keyBind);

        if (hash == Keyboard.CHAR_NONE) {
            return null;
        }

        return NEIClientUtils.getKeyName(hash + meta, mouseBind);
    }

    public static String getKeyName(String keyBind, int meta) {
        final int hash = getKeyBinding(keyBind);

        if (hash == Keyboard.CHAR_NONE) {
            return null;
        }

        return NEIKeyboardUtils.getKeyName(hash + meta);
    }

    public static void bootNEI() {

        if (!mainNEIConfigLoaded) {
            // main NEI config loading
            ItemInfo.load();
            GuiInfo.load();
            RecipeInfo.load();
            HeldItemHandler.load();
            LayoutManager.load();
            NEIController.load();
            BookmarkContainerInfo.load();
            InformationHandler.load();
            mainNEIConfigLoaded = true;

            new Thread("NEI Plugin Loader") {

                @Override
                public void run() {
                    final Stream<Class<?>> stream = NEIClientConfig.getBooleanSetting("loadPluginsInParallel")
                            ? NEIClientConfig.pluginsList.parallelStream()
                            : NEIClientConfig.pluginsList.stream();

                    stream.forEach(clazz -> {
                        try {
                            IConfigureNEI config = (IConfigureNEI) clazz.getConstructor().newInstance();
                            config.loadConfig();
                            NEIModContainer.plugins.add(config);
                            logger.debug("Loaded {}", clazz.getName());
                        } catch (Throwable e) {
                            logger.error("Failed to Load {}", clazz.getName(), e);
                        }
                    });

                    RecipeCatalysts.loadCatalystInfo();
                    SubsetWidget.loadCustomSubsets();
                    SubsetWidget.loadHidden();
                    CollapsibleItems.load();
                    ItemSorter.loadConfig();
                    FavoriteRecipes.load();

                    // Set pluginNEIConfigLoaded here before posting the NEIConfigsLoadedEvent. This used to be the
                    // other way around, but apparently if your modpack includes 800 mods the event poster might not
                    // return in time and cause issues when loading a world for a second time as configLoaded is still
                    // false. This may cause issues in case one of the event handler calls the (non-thread-safe) NEI
                    // API. I don't expect any handler to do this, but who knows what modders have come up with...
                    pluginNEIConfigLoaded = true;
                    MinecraftForge.EVENT_BUS.post(new NEIConfigsLoadedEvent());

                    ItemList.loadItems.restart();
                }
            }.start();
        } else {
            SubsetWidget.loadHidden();
            ItemList.loadItems.restart();
        }

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

    public static boolean autocraftingEnabled() {
        return getBooleanSetting("inventory.autocrafting");
    }

    public static boolean favoritesEnabled() {
        return getBooleanSetting("inventory.favorites.enabled");
    }

    public static boolean isBookmarkPanelHidden() {
        return !getBooleanSetting("inventory.bookmarks.enabled");
    }

    public static boolean areBookmarksAnimated() {
        return getBooleanSetting("inventory.bookmarks.animation");
    }

    public static int getRecipeTooltipsMode() {
        return getIntSetting("inventory.bookmarks.recipeTooltipsMode");
    }

    public static boolean showRecipeTooltipInPanel() {
        return getBooleanSetting("inventory.favorites.showRecipeTooltipInPanel");
    }

    public static boolean showRecipeTooltipInGui() {
        return getBooleanSetting("inventory.favorites.showRecipeTooltipInGui");
    }

    public static int showRecipeMarkerMode() {
        return getIntSetting("inventory.bookmarks.showRecipeMarkerMode");
    }

    public static boolean showItemQuantityWidget() {
        return getBooleanSetting("inventory.showItemQuantityWidget");
    }

    public static boolean hideItemsUntilSearching() {
        return getBooleanSetting("inventory.search.hideUntilSearching");
    }

    public static boolean isSearchWidgetCentered() {
        return getBooleanSetting("inventory.search.widgetPosition");
    }

    public static boolean showSubsetWidget() {
        return getBooleanSetting("inventory.subsets.enabled");
    }

    public static boolean subsetWidgetOnTop() {
        return getBooleanSetting("inventory.subsets.widgetPosition");
    }

    public static int searchWidgetAutofocus() {
        return getIntSetting("inventory.search.widgetAutofocus");
    }

    public static boolean areJEIStyleTabsVisible() {
        return getBooleanSetting("inventory.guirecipe.jeiStyleTabs");
    }

    public static int itemPresenceOverlay() {
        return getIntSetting("inventory.guirecipe.itemPresenceOverlay");
    }

    public static boolean isSlotHighlightPresent() {
        return getBooleanSetting("inventory.guirecipe.slotHighlightPresent");
    }

    public static boolean areJEIStyleRecipeCatalystsVisible() {
        return getBooleanSetting("inventory.guirecipe.jeiStyleRecipeCatalyst");
    }

    public static boolean useCreativeTabStyle() {
        return getBooleanSetting("inventory.guirecipe.creativeTabStyle");
    }

    public static boolean ignorePotionOverlap() {
        return getBooleanSetting("inventory.bookmarks.ignorePotionOverlap");
    }

    public static int recipeChainDir() {
        return getIntSetting("inventory.bookmarks.recipeChainDir");
    }

    public static boolean optimizeGuiOverlapComputation() {
        return getBooleanSetting("inventory.optimize_gui_overlap_computation");
    }

    public static boolean useJEIStyledCycledIngredients() {
        return getBooleanSetting("inventory.guirecipe.jeiStyleCycledIngredients");
    }

    public static boolean showCycledIngredientsTooltip() {
        return getBooleanSetting("inventory.guirecipe.cycledIngredientsTooltip");
    }

    public static boolean requireShiftForOverlayRecipe() {
        return getBooleanSetting("inventory.guirecipe.shiftOverlayRecipe");
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

    public static boolean isProfileRecipeEnabled() {
        return NEIClientConfig.getBooleanSetting("inventory.guirecipe.profile");
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

    public static boolean canChangeCheatMode() {
        if (getLockedMode() != -1) {
            setIntSetting("inventory.cheatmode", getLockedMode());
            return false;
        }

        return true;
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

    public static boolean showHistoryPanelWidget() {
        return getBooleanSetting("inventory.history.enabled");
    }

    public static boolean showCraftablesPanelWidget() {
        return getBooleanSetting("inventory.craftables.enabled");
    }

    public static int getGridRenderingCacheMode() {
        return OpenGlHelper.framebufferSupported ? getIntSetting("inventory.gridRenderingCacheMode") : 0;
    }

    public static boolean enableCollapsibleItems() {
        return getBooleanSetting("inventory.collapsibleItems.enabled");
    }

    public static boolean enableItemUntranslator() {
        return getBooleanSetting("inventory.itemUntranslator");
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
