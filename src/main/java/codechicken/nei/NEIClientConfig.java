package codechicken.nei;

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
import com.google.common.base.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.storage.SaveFormatComparator;
import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;

import java.io.File;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

public class NEIClientConfig {
    private static boolean configLoaded;
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

    @Deprecated
    public static File bookmarkFile;

    // Set of handlers that need to be run in serial
    public static HashSet<String> serialHandlers = new HashSet<>();

    // Set of regexes matching handler ID of handlers that need the hack in GuiRecipe.startHeightHack().
    // We use regex here so that we can apply the height hack to entire mods with one entry.
    public static HashSet<Pattern> heightHackHandlerRegex = new HashSet<>();

    // Map of handler ID to sort order.
    // Handlers will be sorted in ascending order, so smaller numbers show up earlier.
    // Any handler not in the map will be assigned to 0, and negative numbers are fine.
    public static HashMap<String, Integer> handlerOrdering = new HashMap<>();

    // Function that extracts the handler ID from a handler, with special logic for
    // TemplateRecipeHandler: prefer using the overlay ID if it exists.
    public static final Function<IRecipeHandler, String> HANDLER_ID_FUNCTION =
            handler -> Objects.firstNonNull(
                            handler.getOverlayIdentifier(),
                            handler.getHandlerId());

    // Comparator that compares handlers using the handlerOrdering map.
    public static final Comparator<IRecipeHandler> HANDLER_COMPARATOR =
            Comparator.comparingInt(
                    handler -> handlerOrdering.getOrDefault(
                            HANDLER_ID_FUNCTION.apply(handler), 0));


    public static ItemStack[] creativeInv;

    public static boolean hasSMPCounterpart;
    public static HashSet<String> permissableActions = new HashSet<>();
    public static HashSet<String> disabledActions = new HashSet<>();
    public static HashSet<String> enabledActions = new HashSet<>();

    public static ItemStackSet bannedBlocks = new ItemStackSet();

    static {
        if (global.config.getTag("checkUpdates").getBooleanValue(true))
            CCUpdateChecker.updateCheck("NotEnoughItems");
        linkOptionList();
        setDefaults();
    }

    private static void setDefaults() {
        ConfigTagParent tag = global.config;
        tag.setComment("Main configuration of NEI.\nMost of these options can be changed ingame.\nDeleting any element will restore it to it's default value");

        tag.getTag("command").useBraces().setComment("Change these options if you have a different mod installed on the server that handles the commands differently, Eg. Bukkit Essentials");
        tag.setNewLineMode(1);

        tag.getTag("inventory.widgetsenabled").getBooleanValue(true);
        API.addOption(new OptionToggleButton("inventory.widgetsenabled"));

        tag.getTag("inventory.hidden").getBooleanValue(false);
        tag.getTag("inventory.cheatmode").getIntValue(2);
        tag.getTag("inventory.lockmode").setComment("For those who can't help themselves.\nSet this to a mode and you will be unable to change it ingame").getIntValue(-1);
        API.addOption(new OptionCycled("inventory.cheatmode", 3)
        {
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

        tag.getTag("inventory.worldSpecificBookmarks").setComment("Global or world specific bookmarks").getBooleanValue(false);
        API.addOption(new OptionToggleButton("inventory.worldSpecificBookmarks", true) {
            @Override
            public boolean onClick(int button) {
                super.onClick(button);
                initBookmarkFile(worldPath);
                return true;
            }
        });
        tag.getTag("inventory.bookmarksEnabled").setComment("Enable/disable bookmarks").getBooleanValue(true);
        API.addOption(new OptionToggleButton("inventory.bookmarksEnabled", true));
        tag.getTag("inventory.saveCurrentRecipeInBookmarksEnabled").setComment("Save Current Recipe in Bookmarks").getBooleanValue(true);
        API.addOption(new OptionToggleButton("inventory.saveCurrentRecipeInBookmarksEnabled", true));
        tag.getTag("inventory.useNBTInBookmarks").setComment("Use NBT in Bookmarks").getBooleanValue(true);
        API.addOption(new OptionToggleButton("inventory.useNBTInBookmarks", true));

        tag.getTag("inventory.jei_style_tabs").setComment("Enable/disable JEI Style Tabs").getBooleanValue(true);
        API.addOption(new OptionToggleButtonBoubs("inventory.jei_style_tabs", true));
        tag.getTag("inventory.jei_style_item_presence_overlay").setComment("Enable/disable JEI Style item presence overlay on ?-hover").getBooleanValue(true);
        API.addOption(new OptionToggleButton("inventory.jei_style_item_presence_overlay", true));
        tag.getTag("inventory.jei_style_recipe_catalyst").setComment("Enable/disable JEI Style Recipe Catalysts [experimental]").getBooleanValue(false);
        API.addOption(new OptionToggleButton("inventory.jei_style_recipe_catalyst", true));


        tag.getTag("inventory.creative_tab_style").setComment("Creative or JEI style tabs").getBooleanValue(false);
        API.addOption(new OptionToggleButton("inventory.creative_tab_style", true));

        tag.getTag("tools.handler_load_from_config").setComment("ADVANCED: Load handlers from config").getBooleanValue(false);
        API.addOption(new OptionToggleButton("tools.handler_load_from_config", true) {
            @Override
            public boolean onClick(int button) {
                super.onClick(button);
                GuiRecipeTab.loadHandlerInfo();
                return true;
            }
        });

        tag.getTag("tools.catalyst_load_from_config").setComment("ADVANCED: Load catalysts from config").getBooleanValue(false);
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
        OptionList.setOptionList(new OptionList("nei.options")
        {
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
        API.addKeyBind("gui.recipe", Keyboard.KEY_R);
        API.addKeyBind("gui.usage", Keyboard.KEY_U);
        API.addKeyBind("gui.back", Keyboard.KEY_BACK);
        API.addKeyBind("gui.enchant", Keyboard.KEY_X);
        API.addKeyBind("gui.potion", Keyboard.KEY_P);
        API.addKeyBind("gui.prev", Keyboard.KEY_PRIOR);
        API.addKeyBind("gui.next", Keyboard.KEY_NEXT);
        API.addKeyBind("gui.prev_machine", Keyboard.KEY_UP);
        API.addKeyBind("gui.next_machine", Keyboard.KEY_DOWN);
        API.addKeyBind("gui.prev_recipe", Keyboard.KEY_LEFT);
        API.addKeyBind("gui.next_recipe", Keyboard.KEY_RIGHT);
        API.addKeyBind("gui.hide", Keyboard.KEY_O);
        API.addKeyBind("gui.search", Keyboard.KEY_F);
        API.addKeyBind("gui.bookmark", Keyboard.KEY_A);
        API.addKeyBind("gui.hide_bookmarks", Keyboard.KEY_B);
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
        logger.debug("Loading "+(Minecraft.getMinecraft().isSingleplayer() ? "Local" : "Remote")+" World");
        bootNEI(ClientUtils.getWorld());

        final File specificDir = new File(CommonUtils.getMinecraftDir(), "saves/NEI/" + worldPath);
        final boolean newWorld = !specificDir.exists();

        if (newWorld) {
            specificDir.mkdirs();
        }

        initBookmarkFile(worldPath);
        world = new ConfigSet(new File(specificDir, "NEI.dat"), new ConfigFile(new File(specificDir, "NEI.cfg")));
        onWorldLoad(newWorld);
    }

    private static void onWorldLoad(boolean newWorld) {
        world.config.setComment("World based configuration of NEI.\nMost of these options can be changed ingame.\nDeleting any element will restore it to it's default value");

        setWorldDefaults();
        creativeInv = new ItemStack[54];
        LayoutManager.searchField.setText(getSearchExpression());
        LayoutManager.quantity.setText(Integer.toString(getItemQuantity()));
        SubsetWidget.loadHidden();

        if (newWorld && Minecraft.getMinecraft().isSingleplayer())
            world.config.getTag("inventory.cheatmode").setIntValue(NEIClientUtils.mc().playerController.isInCreativeMode() ? 2 : 0);

        NEIInfo.load(ClientUtils.getWorld());
    }

    private static void setWorldDefaults() {
        NBTTagCompound nbt = world.nbt;
        if (!nbt.hasKey("search")) nbt.setString("search", "");
        if (!nbt.hasKey("quantity")) nbt.setInteger("quantity", 0);
        if (!nbt.hasKey("validateenchantments")) nbt.setBoolean("validateenchantments", false);

        world.saveNBT();
    }

    public static int getKeyBinding(String string) {
        return getSetting("keys." + string).getIntValue();
    }

    public static void setDefaultKeyBinding(String string, int key) {
        getSetting("keys." + string).getIntValue(key);
    }

    public static void bootNEI(World world) {
        if (configLoaded)
            return;

        ItemInfo.load(world);
        GuiInfo.load();
        RecipeInfo.load();
        LayoutManager.load();
        NEIController.load();
        RecipeCatalysts.loadCatalystInfo();

        configLoaded = true;

        new Thread("NEI Plugin Loader") {
            @Override
            public void run() {
                ClassDiscoverer classDiscoverer = new ClassDiscoverer(test -> test.startsWith("NEI") && test.endsWith("Config.class"), IConfigureNEI.class);

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

                MinecraftForge.EVENT_BUS.post(new NEIConfigsLoadedEvent());
            }
        }.start();
        ItemSorter.loadConfig();
    }

    private static void initBookmarkFile(String worldPath)
    {
        
        if (!global.config.getTag("inventory.worldSpecificBookmarks").getBooleanValue()) {
            worldPath = "global";
        }

        ItemPanels.bookmarkPanel.setBookmarkFile(worldPath);
    }

    public static boolean isWorldSpecific(String setting) {
        if(world == null) return false;
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
    public static boolean saveCurrentRecipeInBookmarksEnabled() {
        return getBooleanSetting("inventory.saveCurrentRecipeInBookmarksEnabled");
    }
    public static boolean useNBTInBookmarks() {
        return getBooleanSetting("inventory.useNBTInBookmarks");
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
    public static boolean isEnabled() {
        return enabledOverride && getBooleanSetting("inventory.widgetsenabled");
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
        if (getLockedMode() != -1)
            setIntSetting("inventory.cheatmode", getLockedMode());
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
        world.saveNBT();
    }

    public static boolean isMouseScrollTransferEnabled() {
        return !getBooleanSetting("inventory.disableMouseScrollTransfer");
    }

    public static boolean shouldInvertMouseScrollTransfer() {
        return !getBooleanSetting("inventory.invertMouseScrollTransfer");
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
        if (!isEnabled())
            return false;

        if (!modePermitsAction(name))
            return false;

        String base = NEIActions.base(name);
        if (hasSMPCounterpart)
            return permissableActions.contains(base);

        if (NEIActions.smpRequired(name))
            return false;

        String cmd = getStringSetting("command." + base);
        return cmd != null && cmd.startsWith("/");
    }

    private static boolean modePermitsAction(String name) {
        if (getCheatMode() == 0) return false;
        if (getCheatMode() == 2) return true;

        String[] actions = getStringArrSetting("inventory.utilities");
        for (String action : actions)
            if (action.equalsIgnoreCase(name))
                return true;

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
        if (!saveDir.exists())
            return;

        List<SaveFormatComparator> saves;
        try {
            saves = Minecraft.getMinecraft().getSaveLoader().getSaveList();
        } catch (Exception e) {
            logger.error("Error loading saves", e);
            return;
        }
        HashSet<String> saveFileNames = new HashSet<>();
        for (SaveFormatComparator save : saves)
            saveFileNames.add(save.getFileName());

        for (File file : saveDir.listFiles())
            if (file.isDirectory() && !saveFileNames.contains(file.getName()))
                ObfuscationRun.deleteDir(file, true);
    }

}
