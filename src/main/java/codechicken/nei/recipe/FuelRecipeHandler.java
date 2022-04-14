package codechicken.nei.recipe;

import codechicken.nei.NEIClientUtils;
import codechicken.nei.PositionedStack;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    @Override
    public String specifyTransferRect() {
        return "fuel";
    }

    public String getOverlayIdentifier() {
        return "fuel";
    }

    @Override
    public void drawExtras(int recipe) {
        super.drawExtras(recipe);
        CachedFuelRecipe crecipe = (CachedFuelRecipe) arecipes.get(recipe);
        FuelPair fuel = crecipe.fuel;
        NumberFormat numberInstance = NumberFormat.getNumberInstance();
        numberInstance.setMaximumFractionDigits(2);
        String smeltCount = numberInstance.format(fuel.burnTime / 200f);
        Minecraft.getMinecraft().fontRenderer.drawString(translate("recipe.fuel.smeltCount", smeltCount), 73, 51, 0xFF000000);
    }
}
