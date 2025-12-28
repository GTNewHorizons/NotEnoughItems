package codechicken.nei.util;

import java.io.BufferedWriter;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.util.StringTranslate;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidContainerRegistry.FluidContainerData;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import codechicken.nei.ClientHandler;
import codechicken.nei.ItemList;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.SubsetWidget;
import codechicken.nei.api.API;
import codechicken.nei.recipe.StackInfo;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.registry.LanguageRegistry;
import cpw.mods.fml.relauncher.ReflectionHelper;

public class ItemUntranslator {

    private class LanguageRegistryPatch implements AutoCloseable {
        // RAII-style helper that temporarily swaps language data in
        // LanguageRegistry / StringTranslate using reflection. This allows
        // reading translation entries for a different language (eg. en_US)
        // even when the game is using another language at runtime.

        private String secondLanguage = "en_US";
        private String language;

        private static Map<String, Properties> modLanguageDataSymlink;
        private Properties languageDataCache;

        private static Map<String, String> secondLanguageListCache;
        private static Map<String, String> languageListCache;

        private static Field languageListField;
        private static Field fieldStringTranslateInstance;

        static {
            try {
                final Field modLanguageDataField = ReflectionHelper
                        .findField(LanguageRegistry.class, "modLanguageData");

                languageListField = ReflectionHelper.findField(StringTranslate.class, "languageList", "field_74816_c");
                fieldStringTranslateInstance = ReflectionHelper
                        .findField(StringTranslate.class, "instance", "field_74817_a");

                modLanguageDataSymlink = (Map<String, Properties>) modLanguageDataField
                        .get(LanguageRegistry.instance());
                languageListCache = (Map<String, String>) languageListField.get(fieldStringTranslateInstance.get(null));
            } catch (Throwable e) {
                languageListField = null;
                fieldStringTranslateInstance = null;
            }
        }

        private LanguageRegistryPatch(String secondLanguage) {
            this.language = FMLCommonHandler.instance().getCurrentLanguage();
            this.secondLanguage = secondLanguage;
            patchLanguageRegistry();
            patchStringTranslate();
        }

        @Override
        public void close() {
            restoreStringTranslate();
            restoreLanguageRegistry();
        }

        private void patchLanguageRegistry() {
            this.languageDataCache = modLanguageDataSymlink.get(this.language);
            modLanguageDataSymlink.put(this.language, modLanguageDataSymlink.get(this.secondLanguage));
        }

        private void patchStringTranslate() {

            if (languageListField != null) {

                if (secondLanguageListCache == null) {
                    secondLanguageListCache = new HashMap<>(languageListCache);
                    modLanguageDataSymlink.get(secondLanguage)
                            .forEach((k, v) -> secondLanguageListCache.put((String) k, (String) v));
                }

                try {
                    languageListField.set(fieldStringTranslateInstance.get(null), secondLanguageListCache);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException("Failed to access StringTranslate language list", e);
                }
            }
        }

        private void restoreLanguageRegistry() {
            final String language = FMLCommonHandler.instance().getCurrentLanguage();
            modLanguageDataSymlink.put(language, languageDataCache);
            languageDataCache = null;
        }

        private void restoreStringTranslate() {
            if (languageListField != null && languageListCache != null) {
                try {
                    languageListField.set(fieldStringTranslateInstance.get(null), languageListCache);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException("Failed to restore StringTranslate language list", e);
                }
            }
        }
    }

    private static final String CONFIG_FILE = "untranslator.cfg";
    private static final ItemUntranslator instance = new ItemUntranslator();

    private final Map<String, String> secondNames = new HashMap<>();
    private final Map<String, String> processedNames = new HashMap<>();

    private ItemUntranslator() {}

    public static ItemUntranslator getInstance() {
        return instance;
    }

    public void generateUntranslatedNames() {
        final Map<String, String> items = new HashMap<>();

        items.put("language", FMLCommonHandler.instance().getCurrentLanguage());

        for (Fluid fluid : FluidRegistry.getRegisteredFluids().values()) {
            items.put(
                    fluid.getUnlocalizedName().toLowerCase(),
                    stripFormatting(StatCollector.translateToLocal(fluid.getUnlocalizedName())));
        }

        for (CreativeTabs tab : CreativeTabs.creativeTabArray) {
            final List<ItemStack> tabItems = new ArrayList<>();

            tab.displayAllReleventItems(tabItems);

            for (ItemStack stack : tabItems) {
                collectDisplayNameForStack(stack, items);
            }
        }

        for (ItemStack stack : ItemList.items) {
            collectDisplayNameForStack(stack, items);
        }

        for (FluidContainerData data : FluidContainerRegistry.getRegisteredFluidContainerData()) {
            if (data.filledContainer != null) {
                collectDisplayNameForStack(data.filledContainer, items);
            }
        }

        for (Map.Entry<String, String> entry : LanguageRegistryPatch.languageListCache.entrySet()) {
            final String key = entry.getKey().toLowerCase();
            if (key.endsWith(".name") && !items.containsKey(key) && !entry.getValue().contains("%")) {
                items.put(key, entry.getValue());
            }
        }

        saveMappings(items);
    }

    private void collectDisplayNameForStack(ItemStack stack, Map<String, String> items) {
        final String displayName = stripFormatting(stack.getDisplayName());
        final String guidKey = determineGUIDForGeneration(stack, displayName, items);

        if (!items.containsKey(guidKey)) {
            items.put(guidKey, displayName);
        }
    }

    private String determineGUIDForGeneration(ItemStack stack, String displayName, Map<String, String> items) {
        final String unlocalizedName = stack.getUnlocalizedName().toLowerCase();

        if (displayName.equals(stripFormatting(StatCollector.translateToLocal(unlocalizedName)))) {
            return unlocalizedName;
        }

        if (!unlocalizedName.endsWith(".name")
                && displayName.equals(stripFormatting(StatCollector.translateToLocal(unlocalizedName + ".name")))) {
            return unlocalizedName + ".name";
        }

        final FluidStack fluid = StackInfo.getFluid(stack);

        if (fluid != null) {
            final String fluidUnlocalizedName = fluid.getUnlocalizedName().toLowerCase();

            if (displayName.equals(items.get(fluidUnlocalizedName))) {
                return fluidUnlocalizedName;
            }

            if (!fluidUnlocalizedName.startsWith("fluid.")
                    && displayName.equals(items.get("fluid." + fluidUnlocalizedName))) {
                return "fluid." + fluidUnlocalizedName;
            }

            return (Item.itemRegistry.getNameForObject(stack.getItem()) + ":"
                    + stack.getItemDamage()
                    + "@"
                    + fluid.getUnlocalizedName()).toLowerCase();
        }

        if (stack.hasTagCompound()) {
            try {
                final ItemStack stackCopy = new ItemStack(stack.getItem(), 1, stack.getItemDamage());

                if (displayName.equals(stripFormatting(stackCopy.getDisplayName()))) {
                    return (Item.itemRegistry.getNameForObject(stack.getItem()) + ":" + stack.getItemDamage())
                            .toLowerCase();
                }
            } catch (Exception e) {}
        }

        return StackInfo.getItemStackGUID(stack);
    }

    private String stripFormatting(String name) {
        return EnumChatFormatting.getTextWithoutFormattingCodes(name).trim();
    }

    public synchronized String getItemStackDisplayName(ItemStack stack) {

        if (stack == null || this.secondNames.isEmpty()) {
            return "";
        }

        final String guidKey = StackInfo.getItemStackGUID(stack);

        return this.processedNames.computeIfAbsent(guidKey, stackKey -> {
            String displayName = this.secondNames.get(stackKey);

            if (displayName == null) {
                final String guid = findBestGUIDForLookup(stack);
                displayName = this.secondNames
                        .computeIfAbsent(guid == null ? stackKey : guid, g -> getFallbackDisplayName(stack));
            }

            return displayName.equals(stripFormatting(stack.getDisplayName())) ? "" : displayName;
        });
    }

    public String findBestGUIDForLookup(ItemStack stack) {
        String guidKey = (Item.itemRegistry.getNameForObject(stack.getItem()) + ":" + stack.getItemDamage())
                .toLowerCase();

        if (this.secondNames.containsKey(guidKey)) {
            return guidKey;
        }

        final FluidStack fluid = StackInfo.getFluid(stack);

        if (fluid != null) {
            guidKey = (Item.itemRegistry.getNameForObject(stack.getItem()) + ":"
                    + stack.getItemDamage()
                    + "@"
                    + fluid.getUnlocalizedName()).toLowerCase();

            if (this.secondNames.containsKey(guidKey)) {
                return guidKey;
            }

            final String fluidUnlocalizedName = fluid.getUnlocalizedName().toLowerCase();

            if (!fluidUnlocalizedName.startsWith("fluid.")
                    && this.secondNames.containsKey("fluid." + fluidUnlocalizedName)) {
                return "fluid." + fluidUnlocalizedName;
            }

            if (this.secondNames.containsKey(fluidUnlocalizedName)) {
                return fluidUnlocalizedName;
            }
        }

        final String unlocalizedName = stack.getUnlocalizedName().toLowerCase();

        if (!unlocalizedName.endsWith(".name") && this.secondNames.containsKey(unlocalizedName + ".name")) {
            return unlocalizedName + ".name";
        }

        if (this.secondNames.containsKey(unlocalizedName)) {
            return unlocalizedName;
        }

        return null;
    }

    private String getFallbackDisplayName(ItemStack stack) {
        final String unlocalizedName = stack.getUnlocalizedName();
        final String secondLanguage = this.secondNames.get("language");
        String name = LanguageRegistry.instance().getStringLocalization(unlocalizedName, secondLanguage);

        if (name == null && !unlocalizedName.endsWith(".name")) {
            name = LanguageRegistry.instance().getStringLocalization(unlocalizedName + ".name", secondLanguage);
        }

        if (name == null || name.contains("%")) {
            try (LanguageRegistryPatch lrPatch = new LanguageRegistryPatch(secondLanguage)) {
                name = stack.getDisplayName();
            }
        }

        final FluidStack fluid = StackInfo.getFluid(stack);

        if (fluid != null) {
            final String unlocalizedFluidName = fluid.getUnlocalizedName().toLowerCase();
            String fluidName = this.secondNames.get(unlocalizedFluidName);

            if (fluidName == null && !unlocalizedFluidName.startsWith("fluid.")) {
                fluidName = this.secondNames.get("fluid." + unlocalizedFluidName);
            }

            if (fluidName != null && name != null && !name.isEmpty()) {
                return name.replace(fluid.getLocalizedName(), fluidName);
            } else {
                name = fluidName;
            }
        }

        return name == null ? "" : name;
    }

    protected void saveMappings(Map<String, String> items) {
        try (BufferedWriter writer = Files.newBufferedWriter(
                (new File(NEIClientConfig.configDir, CONFIG_FILE)).toPath(),
                StandardCharsets.UTF_8)) {
            for (Map.Entry<String, String> entry : items.entrySet()) {
                if (entry.getValue().isEmpty()) continue;

                writer.write(entry.getKey() + "=" + entry.getValue());
                writer.newLine();
            }
        } catch (Exception e) {
            NEIClientConfig.logger.error("Error saving untranslator", e);
        }

        NEIClientConfig.logger.info("Untranslator saved");
    }

    public void unload() {
        this.secondNames.clear();
        LanguageRegistryPatch.secondLanguageListCache = null;
        this.processedNames.clear();

        SubsetWidget.removeTag("Untranslated");
    }

    public void load() {
        this.secondNames.clear();
        LanguageRegistryPatch.secondLanguageListCache = null;
        this.processedNames.clear();

        if (ClientHandler.loadSettingsFile(CONFIG_FILE, null, this::parseStream)
                || ClientHandler.loadSettingsResource(CONFIG_FILE, this::parseStream)) {
            NEIClientConfig.logger.info("Untranslator loaded");
        }

        if (!this.secondNames.containsKey("language")
                || FMLCommonHandler.instance().getCurrentLanguage().equals(this.secondNames.get("language"))) {
            this.secondNames.clear();
        }

        API.addSubset("Untranslated", item -> instance.getItemStackDisplayName(item).isEmpty());
    }

    private void parseStream(Stream<String> lines) {
        lines.forEach(line -> {
            final int sepIndex = line.indexOf('=');

            if (sepIndex != -1) {
                this.secondNames.put(line.substring(0, sepIndex).trim(), line.substring(sepIndex + 1).trim());
            }
        });
    }

}
