package codechicken.nei;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.apache.commons.io.IOUtils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import codechicken.core.CommonUtils;
import codechicken.nei.api.API;
import codechicken.nei.recipe.Recipe.RecipeId;
import codechicken.nei.recipe.StackInfo;
import codechicken.nei.util.NBTJson;

public class FavoriteRecipes {

    private static final Map<NBTTagCompound, RecipeId> items = new HashMap<>();
    private static final Map<String, RecipeId> fluids = new HashMap<>();
    private static File favoriteFile;

    static {
        API.addSubset("Favorites", FavoriteRecipes::contains);
    }

    private FavoriteRecipes() {}

    public static void load() {
        String worldPath = "global";

        if (NEIClientConfig.getBooleanSetting("inventory.favorites.worldSpecific")) {
            worldPath = NEIClientConfig.getWorldPath();
        }

        final File dir = new File(CommonUtils.getMinecraftDir(), "saves/NEI/" + worldPath);

        if (!dir.exists()) {
            dir.mkdirs();
        }

        favoriteFile = new File(dir, "favorites.ini");

        if (!favoriteFile.exists()) {
            final File globalFavorites = new File(CommonUtils.getMinecraftDir(), "saves/NEI/global/favorites.ini");
            final File configFavorites = new File(NEIClientConfig.configDir, "favorites.ini");
            final File defaultFavorites = configFavorites.exists() ? configFavorites : globalFavorites;

            if (defaultFavorites.exists()) {

                try {
                    if (favoriteFile.createNewFile()) {
                        InputStream src = new FileInputStream(defaultFavorites);
                        OutputStream dst = new FileOutputStream(favoriteFile);

                        IOUtils.copy(src, dst);

                        src.close();
                        dst.close();
                    }
                } catch (IOException e) {}
            }
        }

        loadData();
    }

    private static void loadData() {

        if (favoriteFile == null || !favoriteFile.exists()) {
            return;
        }

        List<String> itemStrings;

        try (FileInputStream reader = new FileInputStream(favoriteFile)) {
            NEIClientConfig.logger.info("Loading favorites from file {}", favoriteFile);
            itemStrings = IOUtils.readLines(reader, StandardCharsets.UTF_8);
        } catch (IOException e) {
            NEIClientConfig.logger.error("Failed to load favorites from file {}", favoriteFile, e);
            return;
        }

        final JsonParser parser = new JsonParser();
        items.clear();

        for (String itemStr : itemStrings) {

            if (itemStr.isEmpty() || itemStr.startsWith("#")) {
                continue;
            }

            try {
                JsonObject jsonObject = parser.parse(itemStr).getAsJsonObject();

                if (jsonObject.get("item") != null && jsonObject.get("recipeId") != null) {
                    final NBTTagCompound itemStackNBT = (NBTTagCompound) NBTJson.toNbt(jsonObject.get("item"));
                    final RecipeId recipeId = RecipeId.of(jsonObject.getAsJsonObject("recipeId"));
                    final ItemStack stack = StackInfo.loadFromNBT(itemStackNBT);

                    if (stack != null) {
                        final String fluidKey = getFluidKey(stack);

                        if (fluidKey != null) {
                            fluids.put(fluidKey, recipeId);
                        }
                    }

                    items.put(itemStackNBT, recipeId);
                }

            } catch (IllegalArgumentException | JsonSyntaxException | IllegalStateException e) {
                NEIClientConfig.logger.error("Failed to load favorite from json string:\n{}", itemStr);
            }

        }

        SubsetWidget.updateHiddenItems();
    }

    public static RecipeId getFavorite(ItemStack stack) {

        if (NEIClientConfig.favoritesEnabled() && stack != null) {
            RecipeId recipeId = items.get(StackInfo.itemStackToNBT(stack, false));

            if (recipeId == null) {
                recipeId = fluids.get(getFluidKey(stack));
            }

            return recipeId;
        }

        return null;
    }

    public static ItemStack getFavorite(RecipeId recipeId) {
        if (NEIClientConfig.favoritesEnabled()) {
            final Optional<Map.Entry<NBTTagCompound, RecipeId>> result = items.entrySet().stream()
                    .filter(entry -> entry.getValue().equals(recipeId)).findAny();

            if (result.isPresent()) {
                return StackInfo.loadFromNBT(result.get().getKey());
            }
        }

        return null;
    }

    public static boolean contains(ItemStack stack) {
        return getFavorite(stack) != null;
    }

    public static void setFavorite(ItemStack stack, RecipeId recipeId) {
        final NBTTagCompound itemStackNBT = StackInfo.itemStackToNBT(stack, false);
        final String fluidKey = getFluidKey(stack);

        items.entrySet().removeIf(entry -> entry.getKey().equals(itemStackNBT) || entry.getValue().equals(recipeId));
        fluids.entrySet().removeIf(entry -> entry.getKey().equals(fluidKey) || entry.getValue().equals(recipeId));

        if (recipeId != null) {
            items.put(itemStackNBT, recipeId);

            if (fluidKey != null) {
                fluids.put(fluidKey, recipeId);
            }
        }

        SubsetWidget.updateHiddenItems();
        ItemList.updateFilter.restart();
    }

    private static String getFluidKey(ItemStack stack) {
        final FluidStack fluid = StackInfo.getFluid(stack);

        if (fluid != null) {
            return FluidRegistry.getFluidName(fluid);
        }

        return null;
    }

    public static void save() {

        if (favoriteFile == null) {
            return;
        }

        final List<String> strings = new ArrayList<>();

        for (Map.Entry<NBTTagCompound, RecipeId> entry : items.entrySet()) {

            try {
                final JsonObject line = new JsonObject();

                line.add("item", NBTJson.toJsonObject(entry.getKey()));
                line.add("recipeId", entry.getValue().toJsonObject());

                strings.add(NBTJson.toJson(line));
            } catch (JsonSyntaxException e) {
                NEIClientConfig.logger.error("Failed to stringify favorites ItemStack to json string");
            }

        }

        try (FileOutputStream output = new FileOutputStream(favoriteFile)) {
            IOUtils.writeLines(strings, "\n", output, StandardCharsets.UTF_8);
        } catch (IOException e) {
            NEIClientConfig.logger.error("Filed to save favorites list to file {}", favoriteFile, e);
        }
    }

}
