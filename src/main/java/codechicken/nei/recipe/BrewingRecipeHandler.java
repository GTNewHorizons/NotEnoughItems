package codechicken.nei.recipe;

import static net.minecraft.init.Items.potionitem;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Collectors;

import net.minecraft.client.gui.inventory.GuiBrewingStand;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.potion.PotionHelper;

import codechicken.nei.ItemStackSet;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.NEIServerUtils;
import codechicken.nei.PositionedStack;
import codechicken.nei.api.API;

public class BrewingRecipeHandler extends TemplateRecipeHandler {

    public static class BrewingRecipe {

        public PositionedStack precursorPotion;
        public PositionedStack result;
        public PositionedStack ingredient;

        public BrewingRecipe(ItemStack ingred, int basePotionID, int resultDamage) {
            precursorPotion = new PositionedStack(new ItemStack(potionitem, 1, basePotionID), 51, 35);
            ingredient = new PositionedStack(ingred, 74, 6);
            result = new PositionedStack(new ItemStack(potionitem, 1, resultDamage), 97, 35);
        }

        // Arbitrary precursor/result stacks (for Lingering)
        public BrewingRecipe(ItemStack ingred, ItemStack precursor, ItemStack resultStack) {
            precursorPotion = new PositionedStack(precursor, 51, 35);
            ingredient = new PositionedStack(ingred, 74, 6);
            result = new PositionedStack(resultStack, 97, 35);
        }
    }

    public class CachedBrewingRecipe extends CachedRecipe {

        public final BrewingRecipe recipe;

        public CachedBrewingRecipe(BrewingRecipe recipe) {
            this.recipe = recipe;
        }

        @Override
        public PositionedStack getResult() {
            return recipe.result;
        }

        @Override
        public ArrayList<PositionedStack> getIngredients() {
            ArrayList<PositionedStack> recipestacks = new ArrayList<>();
            recipestacks.add(recipe.ingredient);
            recipestacks.add(recipe.precursorPotion);
            return recipestacks;
        }
    }

    public static final ItemStackSet ingredients = new ItemStackSet();
    public static final HashSet<BrewingRecipe> apotions = new HashSet<>();

    private static ItemStack DRAGONS_BREATH = null;
    private static Item LINGERING_POTION_ITEM = null;

    @Override
    public void loadTransferRects() {
        transferRects.add(new RecipeTransferRect(new Rectangle(58, 3, 14, 30), "brewing"));
        transferRects.add(new RecipeTransferRect(new Rectangle(92, 3, 14, 30), "brewing"));
        transferRects.add(new RecipeTransferRect(new Rectangle(68, 23, 28, 18), "brewing"));
    }

    @Override
    public Class<? extends GuiContainer> getGuiClass() {
        return GuiBrewingStand.class;
    }

    @Override
    public String getRecipeName() {
        return NEIClientUtils.translate("recipe.brewing");
    }

    @Override
    public void loadCraftingRecipes(String outputId, Object... results) {
        if (outputId.equals("brewing") && getClass() == BrewingRecipeHandler.class) // don't want subclasses getting a
                                                                                    // hold of this
            for (BrewingRecipe recipe : apotions) arecipes.add(new CachedBrewingRecipe(recipe));
        else super.loadCraftingRecipes(outputId, results);
    }

    @Override
    public void loadCraftingRecipes(ItemStack result) {
        if (result == null) return;

        // Allow lingering potion items as output
        Item out = result.getItem();
        boolean isPotion = out == potionitem;
        boolean isLingering = (LINGERING_POTION_ITEM != null && out == LINGERING_POTION_ITEM);
        if (!isPotion && !isLingering) return;

        int damage = result.getItemDamage();

        // Note: Not safe as written for parallelStream
        apotions.stream()
                .filter(recipe -> recipe.result.item.getItem() == out && recipe.result.item.getItemDamage() == damage)
                .map(CachedBrewingRecipe::new).collect(Collectors.toCollection(() -> arecipes));
    }

    @Override
    public void loadUsageRecipes(ItemStack ingredient) {
        if (ingredient == null) return;

        // allow lingering potions to pass the guard & keeps og potion behavior
        boolean passIt = ingredient.getItem() == potionitem
                || (LINGERING_POTION_ITEM != null && ingredient.getItem() == LINGERING_POTION_ITEM)
                || ingredients.contains(ingredient);
        if (!passIt) return;

        // Note: Not safe as written for parallelStream
        apotions.stream()
                .filter(
                        recipe -> NEIServerUtils.areStacksSameType(recipe.ingredient.item, ingredient)
                                || NEIServerUtils.areStacksSameType(recipe.precursorPotion.item, ingredient)
                                || NEIServerUtils.areStacksSameType(recipe.result.item, ingredient))
                .map(CachedBrewingRecipe::new).collect(Collectors.toCollection(() -> arecipes));
    }

    @Override
    public String getGuiTexture() {
        return "textures/gui/container/brewing_stand.png";
    }

    @Override
    public void drawExtras(int recipe) {
        drawProgressBar(92, 5, 176, 0, 8, 30, 120, 1);
        drawProgressBar(60, 1, 185, -2, 12, 30, 35, 3);
    }

    public static void searchPotions() {
        TreeSet<Integer> allPotions = new TreeSet<>();
        HashSet<Integer> searchPotions = new HashSet<>();
        searchPotions.add(0);
        allPotions.add(0);
        do {
            HashSet<Integer> newPotions = new HashSet<>();
            for (Integer basePotion : searchPotions) {
                if (ItemPotion.isSplash(basePotion)) continue;

                for (ItemStack ingred : ingredients.values()) {
                    int result = PotionHelper.applyIngredient(basePotion, ingred.getItem().getPotionEffect(ingred));

                    if (ItemPotion.isSplash(result)) { // splash potions qualify
                        addPotion(ingred, basePotion, result, allPotions, newPotions);
                        continue;
                    }

                    List<?> baseMods = potionitem.getEffects(basePotion);
                    List<?> newMods = potionitem.getEffects(result); // compare ID's
                    if (basePotion > 0 && baseMods == newMods || // same modifiers and not water->empty
                            baseMods != null && (baseMods.equals(newMods) || newMods == null) || // modifiers different
                                                                                                 // and doesn't lose
                                                                                                 // modifiers
                            basePotion == result || // same potion
                            levelModifierChanged(basePotion, result)) // redstone/glowstone cycle
                        continue;

                    addPotion(ingred, basePotion, result, allPotions, newPotions);
                }
            }

            searchPotions = newPotions;
        } while (!searchPotions.isEmpty());

        API.setItemListEntries(
                potionitem,
                allPotions.stream().map(potionID -> new ItemStack(potionitem, 1, potionID))
                        .collect(Collectors.toList()));
        API.addSubset("Items.Potions", new ItemStackSet().with(potionitem));
        API.addSubset(
                "Items.Potions.Splash",
                item -> item.getItem() == potionitem && (item.getItemDamage() & 0x4000) != 0);

        ItemStackSet positivepots = new ItemStackSet();
        ItemStackSet negativepots = new ItemStackSet();
        ItemStackSet neutralpots = new ItemStackSet();

        for (int potionID : allPotions) {
            List<PotionEffect> effectlist = potionitem.getEffects(potionID);
            int type = 0;
            if (effectlist != null && !effectlist.isEmpty()) for (PotionEffect potioneffect : effectlist)
                if (Potion.potionTypes[potioneffect.getPotionID()].isBadEffect()) type--;
                else type++;

            (type == 0 ? neutralpots : type > 0 ? positivepots : negativepots)
                    .add(new ItemStack(potionitem, 1, potionID));
        }

        API.addSubset("Items.Potions.Positive", positivepots);
        API.addSubset("Items.Potions.Negative", negativepots);
        API.addSubset("Items.Potions.Neutral", neutralpots);

        // EFR Potion Recipe Visibility - Will safely skip if EFR isn't loaded
        tryResolveEFRItems();

        if (DRAGONS_BREATH != null && LINGERING_POTION_ITEM != null) {
            ingredients.add(DRAGONS_BREATH.copy());

            // Iterate over snapshot to avoid concurrent modification when adding to apotions
            ArrayList<BrewingRecipe> snapshot = new ArrayList<>(apotions);

            for (BrewingRecipe bgr : snapshot) {
                if (bgr == null || bgr.result == null || bgr.result.item == null) continue;
                ItemStack splashOut = bgr.result.item;
                if (splashOut.getItem() != potionitem) continue;
                int meta = splashOut.getItemDamage();
                if (!ItemPotion.isSplash(meta)) continue;

                // Clear the splash bit (0x4000) and keep meta otherwise
                int lingeringMeta = (meta & ~0x4000);

                ItemStack precursorSplash = splashOut.copy();
                ItemStack lingeringOut = new ItemStack(LINGERING_POTION_ITEM, 1, lingeringMeta);

                apotions.add(new BrewingRecipe(DRAGONS_BREATH, precursorSplash, lingeringOut));
            }

            // Lingering items
            final List<ItemStack> allLingering = apotions.stream()
                    .map(lpr -> lpr.result != null ? lpr.result.item : null)
                    .filter(lps -> lps != null && lps.getItem() == LINGERING_POTION_ITEM).collect(Collectors.toList());

            if (!allLingering.isEmpty()) {
                API.setItemListEntries(LINGERING_POTION_ITEM, allLingering);
                API.addSubset("Items.Potions.Lingering", stack -> stack.getItem() == LINGERING_POTION_ITEM);
            }
        }
    }

    private static boolean levelModifierChanged(int basePotionID, int result) {
        int basemod = basePotionID & 0xE0;
        int resultmod = result & 0xE0;

        return basemod != 0 && basemod != resultmod;
    }

    private static void addPotion(ItemStack ingred, int basePotion, int result, TreeSet<Integer> allPotions,
            HashSet<Integer> newPotions) {
        apotions.add(new BrewingRecipe(ingred, basePotion, result));
        if (allPotions.add(result)) // it's new
            newPotions.add(result);
    }

    private static void tryResolveEFRItems() {
        if (DRAGONS_BREATH == null) {
            Item breath = (Item) Item.itemRegistry.getObject("etfuturum:dragon_breath");
            DRAGONS_BREATH = (breath != null) ? new ItemStack(breath) : null;
        }
        if (LINGERING_POTION_ITEM == null) {
            LINGERING_POTION_ITEM = (Item) Item.itemRegistry.getObject("etfuturum:lingering_potion");
        }
    }

    @Override
    public String getOverlayIdentifier() {
        return "brewing";
    }
}
