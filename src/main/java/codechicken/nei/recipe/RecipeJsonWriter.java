package codechicken.nei.recipe;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import net.minecraft.event.ClickEvent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import codechicken.core.CommonUtils;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.PositionedStack;
import codechicken.nei.config.GuiItemIconDumper;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.recipe.Recipe.RecipeIngredient;
import codechicken.nei.util.NBTJson;
import cpw.mods.fml.relauncher.ReflectionHelper;

/**
 * Writes recipes as JSON, one object per line, with stacks in the format of {@link StackInfo#itemStackToNBT}.
 */
public class RecipeJsonWriter {

    protected static Field mEUt = null;
    protected static Field mDuration = null;
    protected static Field mRecipe = null;

    static {
        try {
            final ClassLoader loader = RecipeJsonWriter.class.getClassLoader();
            final Class<?> gtRecipe = ReflectionHelper
                    .getClass(loader, "gregtech.api.util.GTRecipe", "gregtech.api.util.GT_Recipe");
            final Class<?> cachedDefaultRecipe = ReflectionHelper.getClass(
                    loader,
                    "gregtech.nei.GTNEIDefaultHandler$CachedDefaultRecipe",
                    "gregtech.nei.GT_NEI_DefaultHandler$CachedDefaultRecipe");

            mEUt = gtRecipe.getField("mEUt");
            mDuration = gtRecipe.getField("mDuration");
            mRecipe = cachedDefaultRecipe.getField("mRecipe");
        } catch (Exception ignored) {}
    }

    private static final File DUMP_DIR = new File(CommonUtils.getMinecraftDir(), "dumps/recipes");

    private int dumped = 0;
    private int skipped = 0;

    public static void dump(IRecipeHandler handler, List<Integer> recipeIndices) {
        final RecipeJsonWriter dumper = new RecipeJsonWriter();
        final File file = new File(DUMP_DIR, fileName(handler) + ".jsonl");

        try {
            dumper.writeFile(file, Collections.singletonList(handler), h -> recipeIndices);
            dumper.printDumped(file);
        } catch (Exception e) {
            printFailed(e);
        }
    }

    /**
     * Loads all recipes of every crafting handler and writes them either all to {@code dumps/recipes.jsonl} or each
     * handler to its own file in {@code dumps/recipes}.
     */
    public static void dumpAll(boolean separate) {
        final RecipeJsonWriter dumper = new RecipeJsonWriter();

        try {
            final List<ICraftingHandler> handlers = GuiCraftingRecipe.getCraftingHandlers("all");

            if (separate) {
                if (DUMP_DIR.exists()) {
                    for (File f : DUMP_DIR.listFiles()) if (f.isFile()) f.delete();
                } else DUMP_DIR.mkdirs();

                for (ICraftingHandler handler : handlers) {
                    final String name = fileName(handler);
                    File file = new File(DUMP_DIR, name + ".jsonl");
                    for (int i = 2; file.exists(); i++) file = new File(DUMP_DIR, name + '_' + i + ".jsonl");

                    dumper.writeFile(file, Collections.singletonList(handler), RecipeJsonWriter::filteredRecipes);
                }

                dumper.printDumped(DUMP_DIR);
            } else {
                final File file = new File(CommonUtils.getMinecraftDir(), "dumps/recipes.jsonl");
                dumper.writeFile(file, handlers, RecipeJsonWriter::filteredRecipes);
                dumper.printDumped(file);
            }
        } catch (Exception e) {
            printFailed(e);
        }
    }

    private static String fileName(IRecipeHandler handler) {
        return GuiItemIconDumper
                .cleanFileName(EnumChatFormatting.getTextWithoutFormattingCodes(handler.getRecipeName()).trim());
    }

    private static List<Integer> filteredRecipes(IRecipeHandler handler) {
        return new SearchRecipeHandler<>(handler).getFilteredRecipes();
    }

    private void writeFile(File file, List<? extends IRecipeHandler> handlers,
            Function<IRecipeHandler, List<Integer>> recipeIndices) throws IOException {
        file.getParentFile().mkdirs();

        try (PrintWriter writer = new PrintWriter(file, StandardCharsets.UTF_8.name())) {
            for (IRecipeHandler handler : handlers) {
                write(writer, handler, recipeIndices.apply(handler));
            }
        }
    }

    private void printDumped(File file) {
        final Path minecraftDir = CommonUtils.getMinecraftDir().getAbsoluteFile().toPath().normalize();
        final String path = minecraftDir.relativize(file.getAbsoluteFile().toPath().normalize()).toString()
                .replace('\\', '/');
        final ChatComponentText chatLink = new ChatComponentText(path);
        chatLink.getChatStyle().setChatClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, file.getAbsolutePath()));
        chatLink.getChatStyle().setUnderlined(true);
        NEIClientUtils.printChatMessage(
                new ChatComponentTranslation("nei.options.tools.dump.recipes.dumped", this.dumped, chatLink));

        if (this.skipped > 0) {
            NEIClientUtils.printChatMessage(
                    new ChatComponentTranslation("nei.options.tools.dump.recipes.skipped", this.skipped));
        }
    }

    private static void printFailed(Exception e) {
        NEIClientConfig.logger.error("Error dumping recipes", e);
        NEIClientUtils.printChatMessage(
                new ChatComponentTranslation("nei.options.tools.dump.recipes.failed", e.getMessage()));
    }

    private void write(PrintWriter writer, IRecipeHandler handler, List<Integer> recipeIndices) {
        for (int recipeIndex : recipeIndices) {
            final String line;

            try {
                line = recipeJson(handler, recipeIndex);
            } catch (Exception e) {
                NEIClientConfig.logger.warn("Skipping recipe {} of {}", recipeIndex, handler.getRecipeName(), e);
                this.skipped++;
                continue;
            }

            writer.println(line);
            this.dumped++;
        }
    }

    private static String recipeJson(IRecipeHandler handler, int recipeIndex) throws IllegalAccessException {
        final Recipe recipe = Recipe.of(handler, recipeIndex);
        final JsonObject json = new JsonObject();
        json.addProperty("handler", recipe.getHandlerName());
        json.add("ingredients", slotsJson(recipe.getIngredients(), handler instanceof ShapedRecipeHandler));
        json.add("results", slotsJson(recipe.getResults(), false));

        final JsonObject gt = gtJson(handler, recipeIndex);
        if (gt != null) {
            json.add("gt", gt);
        }

        return NBTJson.toJson(json);
    }

    /**
     * Each slot's shown stack, with {@code anyOf} listing every accepted stack. In a crafting grid, an ingredient's
     * container item (an empty bucket, a damaged tool) goes under {@code returns}.
     */
    private static JsonArray slotsJson(List<RecipeIngredient> slots, boolean craftingGrid) {
        final JsonArray array = new JsonArray();

        for (RecipeIngredient slot : slots) {
            final ItemStack stack = slot.getItemStack();
            final JsonObject json = stackJson(stack);

            if (json == null) {
                continue;
            }

            if (slot.getChance() != PositionedStack.CHANCE_FULL) {
                json.addProperty("chance", slot.getChance());
            }

            if (slot.getPermutations().size() > 1) {
                final JsonArray anyOf = new JsonArray();

                for (ItemStack permutation : slot.getPermutations()) {
                    final JsonObject option = stackJson(permutation);

                    if (option != null) {
                        option.remove("Count");
                        anyOf.add(option);
                    }
                }

                json.add("anyOf", anyOf);
            }

            if (craftingGrid) {
                final JsonObject container = stackJson(containerItem(stack));

                if (container != null) {
                    json.add("returns", container);
                }
            }

            array.add(json);
        }

        return array;
    }

    private static JsonObject stackJson(ItemStack stack) {
        final NBTTagCompound nbTag = stack != null ? StackInfo.itemStackToNBT(stack) : null;

        if (nbTag == null) {
            return null;
        }

        final JsonObject json = NBTJson.toJsonObject(nbTag).getAsJsonObject();
        json.addProperty(
                "name",
                EnumChatFormatting.getTextWithoutFormattingCodes(GuiContainerManager.itemDisplayNameShort(stack)));
        return json;
    }

    private static ItemStack containerItem(ItemStack stack) {
        if (!stack.getItem().hasContainerItem(stack)) {
            return null;
        }

        final boolean isPausedItemDamageSound = StackInfo.isPausedItemDamageSound();

        try {
            StackInfo.pauseItemDamageSound(true);
            return stack.getItem().getContainerItem(stack);
        } finally {
            StackInfo.pauseItemDamageSound(isPausedItemDamageSound);
        }
    }

    private static JsonObject gtJson(IRecipeHandler handler, int recipeIndex) throws IllegalAccessException {
        if (mRecipe == null || !(handler instanceof TemplateRecipeHandler templateHandler)
                || recipeIndex >= templateHandler.arecipes.size()) {
            return null;
        }

        final Object cachedRecipe = templateHandler.arecipes.get(recipeIndex);

        if (!mRecipe.getDeclaringClass().isInstance(cachedRecipe)) {
            return null;
        }

        final Object gtRecipe = mRecipe.get(cachedRecipe);
        final long eut = ((Number) mEUt.get(gtRecipe)).longValue();
        final long duration = ((Number) mDuration.get(gtRecipe)).longValue();
        final JsonObject gt = new JsonObject();
        gt.addProperty("eut", eut);
        gt.addProperty("durationTicks", duration);
        gt.addProperty("totalEU", eut * duration);
        return gt;
    }
}
