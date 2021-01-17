package codechicken.nei.recipe;

import codechicken.nei.NEIClientUtils;
import codechicken.nei.PositionedStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import static codechicken.nei.NEIClientUtils.translate;

public class FuelRecipeHandler extends FurnaceRecipeHandler
{
    public class CachedFuelRecipe extends CachedRecipe
    {
        public final FuelPair fuel;

        public CachedFuelRecipe(FuelPair fuel) {
            this.fuel = fuel;
        }

        @Override
        public PositionedStack getIngredient() {
            return mfurnace.get(cycleticks / 48 % mfurnace.size()).ingred;
        }

        @Override
        public PositionedStack getResult() {
            return mfurnace.get(cycleticks / 48 % mfurnace.size()).result;
        }

        @Override
        public PositionedStack getOtherStack() {
            return fuel.stack;
        }
    }

    private final ArrayList<SmeltingPair> mfurnace = new ArrayList<>();

    public FuelRecipeHandler() {
        super();
        loadAllSmelting();
    }

    public String getRecipeName() {
        return NEIClientUtils.translate("recipe.fuel");
    }

    @SuppressWarnings("unchecked")
    private void loadAllSmelting() {
        // Note: Not safe as written for parallelStream
        final Map<ItemStack, ItemStack> smeltingRecipes = (Map<ItemStack, ItemStack>) FurnaceRecipes.smelting().getSmeltingList();
        smeltingRecipes.entrySet().stream()
            .map(recipe -> new SmeltingPair(recipe.getKey(), recipe.getValue()))
            .collect(Collectors.toCollection(() -> mfurnace));
        
    }

    @Override
    public void loadCraftingRecipes(String outputId, Object... results) {
        // Note: Not safe as written for parallelStream
        if (outputId.equals("fuel") && getClass() == FuelRecipeHandler.class)
            afuels.stream()
                .map(CachedFuelRecipe::new)
                .collect(Collectors.toCollection(() -> arecipes));
    }

    public void loadUsageRecipes(ItemStack ingredient) {
        // Note: Not safe as written for parallelStream
        afuels.stream()
            .filter(fuel -> fuel != null && fuel.stack != null && fuel.stack.contains(ingredient))
            .map(CachedFuelRecipe::new)
            .collect(Collectors.toCollection(() -> arecipes));

    }

    public String getOverlayIdentifier() {
        return "fuel";
    }

    @Override
    public List<String> handleItemTooltip(GuiRecipe gui, ItemStack stack, List<String> currenttip, int recipe) {
        CachedFuelRecipe crecipe = (CachedFuelRecipe) arecipes.get(recipe);
        FuelPair fuel = crecipe.fuel;
        float burnTime = fuel.burnTime / 200F;

        if (gui.isMouseOver(fuel.stack, recipe) && burnTime < 1) {
            burnTime = 1F / burnTime;
            String s_time = Float.toString(burnTime);
            if (burnTime == Math.round(burnTime))
                s_time = Integer.toString((int) burnTime);

            currenttip.add(translate("recipe.fuel.required", s_time));
        } else if ((gui.isMouseOver(crecipe.getResult(), recipe) || gui.isMouseOver(crecipe.getIngredient(), recipe)) && burnTime > 1) {
            String s_time = Float.toString(burnTime);
            if (burnTime == Math.round(burnTime))
                s_time = Integer.toString((int) burnTime);

            currenttip.add(translate("recipe.fuel." + (gui.isMouseOver(crecipe.getResult(), recipe) ? "produced" : "processed"), s_time));
        }

        return currenttip;
    }
}
