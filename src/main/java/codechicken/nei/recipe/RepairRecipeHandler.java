package codechicken.nei.recipe;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import net.minecraft.client.gui.GuiRepair;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

import codechicken.nei.ItemList;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.PositionedStack;
import codechicken.nei.RestartableTask;
import codechicken.nei.drawable.DrawableBuilder;
import codechicken.nei.drawable.DrawableResource;

public class RepairRecipeHandler extends TemplateRecipeHandler {

    private final DrawableResource BG_TEXTURE = new DrawableBuilder("nei:textures/gui/repair.png", 0, 0, 168, 36)
            .setTextureSize(168, 36).build();

    private static final RestartableTask reloadRepairRecipesTask = new RestartableTask("NEI Repair Recipe Reloading") {

        @Override
        public void execute() {
            new RepairRecipeHandler().buildCache();
        }
    };

    private static class ItemStackKey {

        private final ItemStack stack;

        public ItemStackKey(ItemStack stack) {
            this.stack = stack;
        }

        @Override
        public int hashCode() {
            return stack.getItem().hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;

            if (obj instanceof ItemStackKey objeKey) {
                return NEIClientUtils.areStacksSameTypeWithNBT(stack, objeKey.stack);
            }

            return false;
        }

    }

    private static class DamagablePositionedStack extends PositionedStack {

        public DamagablePositionedStack(Object item, int x, int y) {
            super(item, x, y);
        }

        @Override
        public boolean contains(ItemStack ingredient) {
            return ingredient.getItemDamage() > 0 && NEIClientUtils.areStacksSameTypeWithNBT(this.item, ingredient);
        }

    }

    private static List<RepairPair> cachedRecipes;

    private static final int LEFT_X = 25;
    private static final int RIGHT_X = 74;
    private static final int OUTPUT_X = 132;
    private static final int SLOT_Y = 10;

    public class RepairPair extends CachedRecipe {

        final List<PositionedStack> ingredients = new ArrayList<>(2);
        final PositionedStack result;

        RepairPair(ItemStack left, Object right, ItemStack result) {
            ingredients.add(new DamagablePositionedStack(left, LEFT_X, SLOT_Y));
            ingredients.add(new PositionedStack(right, RIGHT_X, SLOT_Y));
            this.result = new PositionedStack(result, OUTPUT_X, SLOT_Y);
        }

        @Override
        public List<PositionedStack> getIngredients() {
            return this.ingredients;
        }

        @Override
        public PositionedStack getResult() {
            return this.result;
        }
    }

    public static void findRepairRecipesOnceParallel() {
        reloadRepairRecipesTask.restart();
    }

    private void buildCache() {
        final Map<ItemStackKey, RepairPair> recipes = new HashMap<>();

        for (ItemStack itemstack1 : ItemList.items) {
            recipes.computeIfAbsent(new ItemStackKey(itemstack1), key -> this.getRepairRecipe(key.stack));
        }

        cachedRecipes = recipes.values().stream().filter(recipe -> recipe != null).collect(Collectors.toList());
    }

    private RepairPair getRepairRecipe(ItemStack itemstack1) {
        if (!itemstack1.isItemStackDamageable()) return null;

        final List<ItemStack> permutations = new ArrayList<>();

        for (ItemStack itemstack2 : ItemList.items) {
            if (itemstack1.getItem().getIsRepairable(itemstack1, itemstack2)) {
                final ItemStack right = itemstack2.copy();
                right.stackSize = 4;
                permutations.add(right);
            }
        }

        if (permutations.isEmpty()) return null;

        final int maxDamage = itemstack1.getMaxDamage();
        final ItemStack result = itemstack1.copy();
        final ItemStack left = itemstack1.copy();

        left.setItemDamage(maxDamage - 1);
        result.setItemDamage(left.getItemDamageForDisplay() - (maxDamage / 4) * 4);

        return new RepairPair(left, permutations, result);
    }

    @Override
    public TemplateRecipeHandler newInstance() {

        if (cachedRecipes == null) {
            buildCache();
        }

        return super.newInstance();
    }

    @Override
    public void loadCraftingRecipes(String outputId, Object... results) {
        if (outputId.equals(getOverlayIdentifier()) && getClass() == RepairRecipeHandler.class) {
            arecipes.addAll(cachedRecipes);
        } else if (!NEIClientConfig.getBooleanSetting("showRepairOnlyForDamageableItems")) {
            super.loadCraftingRecipes(outputId, results);
        }
    }

    @Override
    public void loadCraftingRecipes(ItemStack result) {
        for (RepairPair recipe : cachedRecipes) {
            if (recipe.getResult().contains(result)) arecipes.add(recipe);
        }
    }

    @Override
    public void loadUsageRecipes(String inputId, Object... ingredients) {
        if (inputId.equals(getOverlayIdentifier()) && getClass() == RepairRecipeHandler.class) {
            loadCraftingRecipes(getOverlayIdentifier());
        } else {
            super.loadUsageRecipes(inputId, ingredients);
        }
    }

    @Override
    public void loadUsageRecipes(ItemStack ingredient) {
        final boolean showRepairOnlyForDamageableItems = NEIClientConfig
                .getBooleanSetting("showRepairOnlyForDamageableItems");

        for (RepairPair recipe : cachedRecipes) {
            final List<PositionedStack> ingredients = recipe.getIngredients();

            if (ingredients.get(0).contains(ingredient)) {
                arecipes.add(recipe);
            } else if (!showRepairOnlyForDamageableItems && ingredients.get(1).contains(ingredient)) {
                arecipes.add(new RepairPair(ingredients.get(0).item, ingredient, recipe.getResult().item));
            }
        }

        if (arecipes.isEmpty()) {
            final RepairPair recipe = getRepairRecipe(ingredient);
            if (recipe != null) {
                arecipes.add(recipe);
            }
        }
    }

    @Override
    public void loadTransferRects() {
        transferRects.add(new RecipeTransferRect(new Rectangle(100, 11, 22, 15), getOverlayIdentifier()));
    }

    @Override
    public String getRecipeName() {
        return NEIClientUtils.translate("recipe.repair");
    }

    @Override
    public String getGuiTexture() {
        return "nei:textures/gui/repair.png";
    }

    @Override
    public String getOverlayIdentifier() {
        return "repair";
    }

    @Override
    public Class<? extends GuiContainer> getGuiClass() {
        return GuiRepair.class;
    }

    @Override
    public void drawBackground(int recipe) {
        BG_TEXTURE.draw(0, 0);
    }

    @Override
    public void drawForeground(int recipe) {}

}
