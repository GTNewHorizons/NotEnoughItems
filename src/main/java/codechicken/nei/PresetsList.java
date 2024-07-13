package codechicken.nei;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.item.ItemStack;

import org.apache.commons.io.IOUtils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;

import codechicken.core.CommonUtils;
import codechicken.nei.api.API;
import codechicken.nei.api.IRecipeFilter;
import codechicken.nei.api.IRecipeFilter.IRecipeFilterProvider;
import codechicken.nei.api.ItemFilter;
import codechicken.nei.api.ItemFilter.ItemFilterProvider;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.StackInfo;
import codechicken.nei.util.NBTJson;

public class PresetsList {

    public static enum PresetMode {
        HIDE,
        REMOVE,
        GROUP;
    }

    public static class Preset implements ItemFilter {

        public String name = "";
        public boolean enabled = true;
        public PresetMode mode = PresetMode.HIDE;
        public Set<String> items = new HashSet<>();

        public static String getIdentifier(ItemStack stack) {
            return StackInfo.getItemStackGUID(stack);
        }

        @Override
        public boolean matches(ItemStack item) {
            return items.contains(Preset.getIdentifier(item));
        }

        public Preset copy() {
            Preset preset = new Preset();

            preset.name = name;
            preset.enabled = enabled;
            preset.mode = mode;
            preset.items = new HashSet<>(items);

            return preset;
        }
    }

    protected static class RecipesFilter implements IRecipeFilterProvider, IRecipeFilter {

        public Set<String> cache;

        public IRecipeFilter getFilter() {

            if (cache == null) {
                cache = PresetsList.presets.stream().filter(p -> p.enabled && p.mode == PresetMode.REMOVE)
                        .flatMap(p -> p.items.stream()).collect(Collectors.toSet());
            }

            return cache.isEmpty() ? null : this;
        }

        @Override
        public boolean matches(IRecipeHandler handler, List<PositionedStack> ingredients, PositionedStack result,
                List<PositionedStack> others) {

            if (matchPositionedStack(ingredients, false)) {
                return false;
            }

            if (result != null && matchPositionedStack(result)) {
                return true;
            }

            if (!others.isEmpty() && matchPositionedStack(others, true)) {
                return true;
            }

            return result == null && others.isEmpty();
        }

        private boolean matchPositionedStack(List<PositionedStack> items, boolean dir) {
            for (PositionedStack pStack : items) {
                if (matchPositionedStack(pStack) == dir) {
                    return true;
                }
            }

            return false;
        }

        private boolean matchPositionedStack(PositionedStack pStack) {
            for (ItemStack stack : pStack.items) {
                if (!cache.contains(Preset.getIdentifier(stack))) {
                    return true;
                }
            }

            return false;
        }

    }

    protected static class ItemPanelFilter implements ItemFilterProvider, ItemFilter {

        public Set<String> cache;

        public ItemFilter getFilter() {

            if (cache == null) {
                cache = PresetsList.presets.stream()
                        .filter(p -> p.enabled && (p.mode == PresetMode.HIDE || p.mode == PresetMode.REMOVE))
                        .flatMap(p -> p.items.stream()).collect(Collectors.toSet());
            }

            return cache.isEmpty() ? null : this;
        }

        @Override
        public boolean matches(ItemStack stack) {
            return !cache.contains(Preset.getIdentifier(stack));
        }
    }

    public static final List<Preset> presets = new ArrayList<>();

    protected static File presetsFile;
    protected static RecipesFilter recipeFilter = new RecipesFilter();
    protected static ItemPanelFilter itemFilter = new ItemPanelFilter();

    static {
        API.addItemFilter(itemFilter);
        API.addRecipeFilter(recipeFilter);
        ItemList.loadCallbacks.add(PresetsList::itemsLoaded);
    }

    public static ItemFilter getItemFilter() {
        return itemFilter.getFilter();
    }

    public static void itemsLoaded() {
        recipeFilter.cache = null;
        itemFilter.cache = null;
        ItemList.collapsibleItems.reload();
    }

    public static void setPresetsFile(String worldPath) {
        final File dir = new File(CommonUtils.getMinecraftDir(), "saves/NEI/" + worldPath);

        if (!dir.exists()) {
            dir.mkdirs();
        }

        presetsFile = new File(dir, "presets.ini");

        if (!presetsFile.exists()) {
            final File globalPresets = new File(CommonUtils.getMinecraftDir(), "saves/NEI/global/presets.ini");
            final File configPresets = new File(NEIClientConfig.configDir, "presets.ini");
            final File defaultBookmarks = configPresets.exists() ? configPresets : globalPresets;

            if (defaultBookmarks.exists()) {

                try {
                    presetsFile.createNewFile();

                    InputStream src = new FileInputStream(defaultBookmarks);
                    OutputStream dst = new FileOutputStream(presetsFile);

                    IOUtils.copy(src, dst);

                    src.close();
                    dst.close();

                } catch (IOException e) {}
            }
        }

        loadPresets();
    }

    protected static void loadPresets() {

        if (presetsFile == null || !presetsFile.exists()) {
            return;
        }

        List<String> itemStrings;
        try (FileInputStream reader = new FileInputStream(presetsFile)) {
            NEIClientConfig.logger.info("Loading presets from file {}", presetsFile);
            itemStrings = IOUtils.readLines(reader, StandardCharsets.UTF_8);
        } catch (IOException e) {
            NEIClientConfig.logger.error("Failed to load presets from file {}", presetsFile, e);
            return;
        }

        JsonParser parser = new JsonParser();
        Preset preset = new Preset();
        presets.clear();

        for (String itemStr : itemStrings) {

            try {

                if (itemStr.isEmpty()) {
                    itemStr = "; {}";
                }

                if (itemStr.startsWith("; ")) {
                    JsonObject settings = parser.parse(itemStr.substring(2)).getAsJsonObject();

                    if (!preset.items.isEmpty()) {
                        // do not create empty namespaces
                        presets.add(preset);
                        preset = new Preset();
                    }

                    if (settings.get("name") != null) {
                        preset.name = settings.get("name").getAsString();
                    }

                    if (settings.get("enabled") != null) {
                        preset.enabled = settings.get("enabled").getAsBoolean();
                    }

                    if (settings.get("mode") != null) {
                        preset.mode = PresetMode.valueOf(settings.get("mode").getAsString());
                    }

                } else {
                    preset.items.add(itemStr);
                }

            } catch (IllegalArgumentException | JsonSyntaxException | IllegalStateException e) {
                NEIClientConfig.logger.error("Failed to load presets ItemStack from json string:\n{}", itemStr);
            }
        }

        if (!preset.items.isEmpty()) {
            presets.add(preset);
        }

        itemsLoaded();
    }

    public static void savePresets() {

        if (presetsFile == null) {
            return;
        }

        List<String> strings = new ArrayList<>();

        for (Preset preset : presets) {
            JsonObject settings = new JsonObject();

            settings.add("name", new JsonPrimitive(preset.name));
            settings.add("enabled", new JsonPrimitive(preset.enabled));
            settings.add("mode", new JsonPrimitive(preset.mode.toString()));

            strings.add("; " + NBTJson.toJson(settings));
            strings.addAll(preset.items);
        }

        try (FileOutputStream output = new FileOutputStream(presetsFile)) {
            IOUtils.writeLines(strings, "\n", output, StandardCharsets.UTF_8);
        } catch (IOException e) {
            NEIClientConfig.logger.error("Filed to save presets list to file {}", presetsFile, e);
        }

        itemsLoaded();
    }

}
