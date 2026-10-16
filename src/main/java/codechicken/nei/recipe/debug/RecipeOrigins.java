package codechicken.nei.recipe.debug;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;

import codechicken.nei.NEIServerUtils;
import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.ShapedRecipeHandler;
import codechicken.nei.recipe.ShapedRecipeHandler.CachedShapedRecipe;
import codechicken.nei.recipe.TemplateRecipeHandler.CachedRecipe;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;

/**
 * Tracks which mod registered each crafting recipe, so the recipe handler debug widget can show it in the recipe
 * tooltip. {@link TrackingList} is spliced in as the type of {@link CraftingManager}'s internal recipe list by
 * {@link codechicken.nei.asm.NEITransformer} (see the {@code <init>} transform there) in place of the plain
 * {@code ArrayList} vanilla constructs it with. Patching the constructor itself, rather than swapping the field later
 * on, means every single recipe is observed, including vanilla's own, which are added by that same constructor before
 * any mod code ever runs. Opt-in via {@code -Dnei.recipeOrigins=true}, since capturing a stack trace per recipe slows
 * down loading.
 */
public class RecipeOrigins {

    private static final int MAX_FRAMES = 10;

    private static final Map<IRecipe, List<String>> origins = Collections.synchronizedMap(new IdentityHashMap<>());

    private static class OutputIndex {

        final int size;
        final Map<Item, List<IRecipe>> byItem;

        OutputIndex(int size, Map<Item, List<IRecipe>> byItem) {
            this.size = size;
            this.byItem = byItem;
        }
    }

    private static final Map<CachedRecipe, List<List<String>>> cache = new WeakHashMap<>();

    private static volatile OutputIndex outputIndex = new OutputIndex(-1, Collections.emptyMap());

    private RecipeOrigins() {}

    public static List<List<String>> findOrigins(IRecipeHandler handler, int recipeIndex) {
        if (!(handler instanceof ShapedRecipeHandler shaped)) return Collections.emptyList();

        final CachedRecipe recipe = shaped.arecipes.get(recipeIndex);
        List<List<String>> found;

        synchronized (cache) {
            found = cache.get(recipe);
        }

        if (found == null) {
            found = findOrigins(shaped, recipe.getResult(), recipe.getIngredients());

            synchronized (cache) {
                cache.put(recipe, found);
            }
        }

        return found;
    }

    public static boolean hasDuplicates(IRecipeHandler handler, int recipeIndex) {
        return handler instanceof ShapedRecipeHandler shaped
                && hasDuplicateCandidates(shaped.arecipes.get(recipeIndex).getResult())
                && findOrigins(handler, recipeIndex).size() > 1;
    }

    private static boolean hasDuplicateCandidates(PositionedStack result) {
        if (origins.isEmpty() || result == null || result.item == null) return false;

        int count = 0;

        for (IRecipe recipe : getRecipesByOutput(result.item.getItem())) {
            if (origins.containsKey(recipe)
                    && NEIServerUtils.areStacksSameTypeCrafting(recipe.getRecipeOutput(), result.item)
                    && ++count > 1) {
                return true;
            }
        }

        return false;
    }

    private static List<List<String>> findOrigins(ShapedRecipeHandler handler, PositionedStack result,
            List<PositionedStack> ingredients) {
        if (origins.isEmpty() || result == null || result.item == null) return Collections.emptyList();
        final List<List<String>> found = new ArrayList<>();

        for (IRecipe recipe : getRecipesByOutput(result.item.getItem())) {
            final List<String> origin = origins.get(recipe);

            if (origin != null && NEIServerUtils.areStacksSameTypeCrafting(recipe.getRecipeOutput(), result.item)
                    && matchesIngredients(handler, recipe, ingredients)) {
                final List<String> entry = new ArrayList<>(origin);
                entry.add(recipe.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(recipe)));
                found.add(entry);
            }
        }

        return found;
    }

    private static List<IRecipe> getRecipesByOutput(Item item) {
        final List<IRecipe> recipes = CraftingManager.getInstance().getRecipeList();
        OutputIndex index = outputIndex;

        if (recipes.size() != index.size) {
            synchronized (RecipeOrigins.class) {
                index = outputIndex;

                if (recipes.size() != index.size) {
                    final Map<Item, List<IRecipe>> byItem = new HashMap<>();

                    for (IRecipe recipe : recipes) {
                        final ItemStack output = recipe.getRecipeOutput();
                        if (output != null)
                            byItem.computeIfAbsent(output.getItem(), k -> new ArrayList<>()).add(recipe);
                    }

                    outputIndex = index = new OutputIndex(recipes.size(), byItem);
                }
            }
        }

        return index.byItem.getOrDefault(item, Collections.emptyList());
    }

    private static boolean matchesIngredients(ShapedRecipeHandler handler, IRecipe recipe,
            List<PositionedStack> ingredients) {
        if (recipe instanceof ShapedRecipes shaped) {
            return matchesGrid(handler.new CachedShapedRecipe(shaped), ingredients);
        }
        if (recipe instanceof ShapedOreRecipe shaped)
            return matchesGrid(handler.forgeShapedRecipe(shaped), ingredients);
        if (recipe instanceof ShapelessRecipes shapeless) return matchesUnordered(shapeless.recipeItems, ingredients);
        if (recipe instanceof ShapelessOreRecipe shapeless) return matchesUnordered(shapeless.getInput(), ingredients);
        return false;
    }

    private static boolean matchesGrid(CachedShapedRecipe reference, List<PositionedStack> ingredients) {
        if (reference == null || reference.ingredients.size() != ingredients.size()) return false;

        for (PositionedStack expected : reference.ingredients) {
            final PositionedStack actual = ingredients.stream()
                    .filter(stack -> stack.relx == expected.relx && stack.rely == expected.rely).findFirst()
                    .orElse(null);

            if (actual == null || !matchesItems(expected.items, actual.items)) return false;
        }

        return true;
    }

    private static boolean matchesItems(ItemStack[] expected, ItemStack[] actual) {
        for (ItemStack candidate : expected) {
            for (ItemStack item : actual) {
                if (NEIServerUtils.areStacksSameTypeCrafting(candidate, item)) return true;
            }
        }

        return false;
    }

    private static boolean matchesUnordered(List<?> inputs, List<PositionedStack> ingredients) {
        final List<PositionedStack> remaining = new ArrayList<>(ingredients);

        for (Object input : inputs) {
            if (input == null) continue;

            final PositionedStack match = remaining.stream().filter(stack -> matchesInput(input, stack)).findFirst()
                    .orElse(null);
            if (match == null) return false;
            remaining.remove(match);
        }

        return remaining.isEmpty();
    }

    private static boolean matchesInput(Object input, PositionedStack stack) {
        final List<?> candidates = input instanceof List<?>list ? list : Collections.singletonList(input);

        for (Object candidate : candidates) {
            if (!(candidate instanceof ItemStack candidateStack)) continue;
            for (ItemStack item : stack.items) {
                if (NEIServerUtils.areStacksSameTypeCrafting(candidateStack, item)) return true;
            }
        }

        return false;
    }

    public static class TrackingList extends ArrayList<IRecipe> {

        @Override
        public boolean add(IRecipe recipe) {
            origins.put(recipe, captureOrigin());
            return super.add(recipe);
        }

        @Override
        public void add(int index, IRecipe recipe) {
            origins.put(recipe, captureOrigin());
            super.add(index, recipe);
        }

        @Override
        public IRecipe set(int index, IRecipe recipe) {
            origins.put(recipe, captureOrigin());
            return super.set(index, recipe);
        }

        @Override
        public boolean addAll(Collection<? extends IRecipe> recipes) {
            final List<String> origin = captureOrigin();
            for (IRecipe recipe : recipes) origins.put(recipe, origin);
            return super.addAll(recipes);
        }

        @Override
        public boolean addAll(int index, Collection<? extends IRecipe> recipes) {
            final List<String> origin = captureOrigin();
            for (IRecipe recipe : recipes) origins.put(recipe, origin);
            return super.addAll(index, recipes);
        }

        @Override
        public IRecipe remove(int index) {
            final IRecipe removed = super.remove(index);
            origins.remove(removed);
            return removed;
        }

        @Override
        public boolean remove(Object recipe) {
            final boolean removed = super.remove(recipe);
            if (removed) origins.remove(recipe);
            return removed;
        }

        @Override
        public void clear() {
            origins.clear();
            super.clear();
        }
    }

    private static List<String> captureOrigin() {
        final List<StackTraceElement> frames = findCallerFrames();

        if (frames.isEmpty()) return Collections.singletonList("Unknown");

        final ModContainer container = Loader.instance().activeModContainer();
        final List<String> lines = new ArrayList<>(frames.size() + 1);

        lines.add(container != null ? container.getName() : "Unknown");

        for (int i = 0; i < frames.size(); i++) {
            lines.add(formatFrame(frames.get(i)));
        }

        return lines;
    }

    private static String formatFrame(StackTraceElement frame) {
        return frame.getFileName() != null ? frame.getClassName() + "."
                + frame.getMethodName()
                + "("
                + frame.getFileName()
                + ":"
                + frame.getLineNumber()
                + ")" : frame.getClassName() + "." + frame.getMethodName();
    }

    private static List<StackTraceElement> findCallerFrames() {
        final List<StackTraceElement> frames = new ArrayList<>(MAX_FRAMES);

        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            final String className = element.getClassName();

            if (className.startsWith("java.") || className.startsWith("net.minecraft.")
                    || className.startsWith("net.minecraftforge.")
                    || className.startsWith("cpw.mods.fml.")
                    || className.startsWith("sun.")
                    || className.startsWith("jdk.")
                    || className.startsWith("org.spongepowered.")
                    || className.startsWith("codechicken.nei.recipe.debug.RecipeOrigins")) {
                continue;
            }

            frames.add(element);
            if (frames.size() >= MAX_FRAMES) break;
        }

        return frames;
    }
}
