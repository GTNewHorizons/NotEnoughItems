package codechicken.nei.recipe;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemDye;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.RecipeFireworks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import codechicken.nei.InventoryCraftingDummy;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.guihook.GuiContainerManager;

public class FireworkRecipeHandler extends ShapelessRecipeHandler {

    public class CachedFireworkRecipe extends CachedShapelessRecipe {

        public final int recipeType;
        public final Object groupId;

        public CachedFireworkRecipe(List<ItemStack> ingredients, ItemStack result, int type, Object groupId) {
            super(ingredients, result);
            this.recipeType = type;
            this.groupId = groupId;
        }
    }

    // indexed by the "Type" explosion tag
    private static final ItemStack[] SHAPES = { null, new ItemStack(Items.fire_charge),
            new ItemStack(Items.gold_nugget), new ItemStack(Items.skull, 1, Short.MAX_VALUE),
            new ItemStack(Items.feather) };

    private final InventoryCrafting inventoryCrafting = new InventoryCraftingDummy();
    private final RecipeFireworks recipeFireworks = new RecipeFireworks();
    private ItemStack usageIngredient = null;

    public FireworkRecipeHandler() {
        super();
        stackorder = new int[][] { { 0, 0 }, { 1, 0 }, { 2, 0 }, { 0, 1 }, { 1, 1 }, { 2, 1 }, { 0, 2 }, { 1, 2 },
                { 2, 2 } };
    }

    @Override
    public Object getRecipeGroupId(int recipe) {
        return this.arecipes.get(recipe) instanceof CachedFireworkRecipe firework ? firework.groupId : null;
    }

    private void loadChargeRecipes() {
        final List<List<ItemStack>> dyeSets = new ArrayList<>();

        for (int dye = 0; dye < ItemDye.field_150922_c.length; dye++) {
            dyeSets.add(Collections.singletonList(new ItemStack(Items.dye, 1, dye)));
        }

        for (ItemStack shape : SHAPES) {
            for (int effect = 0; effect < 4; effect++) {
                addRecipes(getChargeBase(shape, (effect & 1) != 0, (effect & 2) != 0), dyeSets, 0);
            }
        }

        final ItemStack charge = craft(Arrays.asList(new ItemStack(Items.gunpowder), new ItemStack(Items.dye)));
        addRecipes(Collections.singletonList(charge), dyeSets, 1);
    }

    private void loadRocketRecipes() {
        final ItemStack charge = craft(Arrays.asList(new ItemStack(Items.gunpowder), new ItemStack(Items.dye)));
        final List<List<ItemStack>> chargeSets = new ArrayList<>();

        for (int count = 0; count < 9; count++) {
            chargeSets.add(Collections.nCopies(count, charge));
        }

        for (int flight = 1; flight <= 3; flight++) {
            addRecipes(getRocketBase(flight), chargeSets, 2);
        }
    }

    private void loadExactChargeRecipes(NBTTagCompound explosion) {
        final byte type = explosion.getByte("Type");

        if (explosion.hasKey("FadeColors")) {
            final NBTTagCompound baseTag = new NBTTagCompound();
            final ItemStack charge = new ItemStack(Items.firework_charge);

            baseTag.setTag("Explosion", explosion.copy());
            baseTag.getCompoundTag("Explosion").removeTag("FadeColors");
            charge.setTagCompound(baseTag);

            addRecipes(Collections.singletonList(charge), getDyeSets(explosion.getIntArray("FadeColors")), 1);
        } else if (type >= 0 && type < SHAPES.length) {
            addRecipes(
                    getChargeBase(SHAPES[type], explosion.getBoolean("Flicker"), explosion.getBoolean("Trail")),
                    getDyeSets(explosion.getIntArray("Colors")),
                    0);
        }
    }

    private void loadExactRocketRecipes(NBTTagCompound fireworks) {
        final NBTTagList explosions = fireworks.getTagList("Explosions", 10);
        final List<ItemStack> charges = new ArrayList<>();

        for (int i = 0; i < explosions.tagCount(); i++) {
            final ItemStack charge = new ItemStack(Items.firework_charge);
            charge.setTagCompound(new NBTTagCompound());
            charge.getTagCompound().setTag("Explosion", explosions.getCompoundTagAt(i));
            charges.add(charge);
        }

        addRecipes(getRocketBase(fireworks.getByte("Flight")), Collections.singletonList(charges), 2);
    }

    private static List<ItemStack> getChargeBase(ItemStack shape, boolean flicker, boolean trail) {
        final List<ItemStack> base = new ArrayList<>();
        base.add(new ItemStack(Items.gunpowder));

        if (shape != null) {
            base.add(shape);
        }

        if (flicker) {
            base.add(new ItemStack(Items.glowstone_dust));
        }

        if (trail) {
            base.add(new ItemStack(Items.diamond));
        }

        return base;
    }

    private static List<ItemStack> getRocketBase(int flight) {
        final List<ItemStack> base = new ArrayList<>();

        for (int i = 0; i < flight; i++) {
            base.add(new ItemStack(Items.gunpowder));
        }

        base.add(new ItemStack(Items.paper));

        return base;
    }

    private static List<List<ItemStack>> getDyeSets(int[] colors) {
        final List<ItemStack> dyes = new ArrayList<>();

        for (int color : colors) {
            for (int dye = 0; dye < ItemDye.field_150922_c.length; dye++) {
                if (ItemDye.field_150922_c[dye] == color) {
                    dyes.add(new ItemStack(Items.dye, 1, dye));
                    break;
                }
            }
        }

        if (dyes.isEmpty() || dyes.size() != colors.length) {
            return Collections.emptyList();
        }

        if (Arrays.stream(colors).distinct().count() > 1) {
            return Collections.singletonList(dyes);
        }

        final List<List<ItemStack>> dyeSets = new ArrayList<>();

        for (int count = 1; count < 9; count++) {
            dyeSets.add(Collections.nCopies(count, dyes.get(0)));
        }

        return dyeSets;
    }

    private void addRecipes(List<ItemStack> base, List<List<ItemStack>> extraSets, int type) {
        final Object groupId = new Object();

        for (List<ItemStack> extras : extraSets) {
            final List<ItemStack> ingredients = new ArrayList<>(base);
            ingredients.addAll(extras);

            final ItemStack result = craft(ingredients);

            if (result != null) {
                final CachedFireworkRecipe recipe = new CachedFireworkRecipe(ingredients, result, type, groupId);

                if (usageIngredient == null || recipe.contains(recipe.ingredients, usageIngredient)) {
                    arecipes.add(recipe);
                }
            }
        }
    }

    private ItemStack craft(List<ItemStack> ingredients) {

        if (ingredients.size() > 9) {
            return null;
        }

        for (int i = 0; i < 9; i++) {
            inventoryCrafting.setInventorySlotContents(i, i < ingredients.size() ? ingredients.get(i) : null);
        }

        return recipeFireworks.matches(inventoryCrafting, null) ? recipeFireworks.getCraftingResult(inventoryCrafting)
                : null;
    }

    @Override
    public void loadCraftingRecipes(ItemStack result) {
        final NBTTagCompound tag = result.getTagCompound();

        if (result.getItem() == Items.firework_charge) {
            if (tag != null) {
                loadExactChargeRecipes(tag.getCompoundTag("Explosion"));
            }

            if (arecipes.isEmpty()) {
                loadChargeRecipes();
            }
        } else if (result.getItem() == Items.fireworks) {
            if (tag != null) {
                loadExactRocketRecipes(tag.getCompoundTag("Fireworks"));
            }

            if (arecipes.isEmpty()) {
                loadRocketRecipes();
            }
        }
    }

    @Override
    public void loadCraftingRecipes(String outputId, Object... results) {
        if (outputId.equals("crafting") && getClass() == FireworkRecipeHandler.class) {
            loadChargeRecipes();
            loadRocketRecipes();
        } else {
            super.loadCraftingRecipes(outputId, results);
        }
    }

    @Override
    public void loadUsageRecipes(ItemStack ingredient) {
        usageIngredient = ingredient;
        loadChargeRecipes();
        loadRocketRecipes();
    }

    @Override
    public String getRecipeName() {
        return NEIClientUtils.translate("recipe.firework");
    }

    @Override
    public List<String> handleTooltip(GuiRecipe<?> gui, List<String> currenttip, int recipe) {
        currenttip = super.handleTooltip(gui, currenttip, recipe);
        if (currenttip.isEmpty() && GuiContainerManager.getStackMouseOver(gui) == null
                && new Rectangle(0, 0, 166, 55).contains(gui.getRecipeMousePosition(recipe)))
            currenttip.add(
                    NEIClientUtils.translate(
                            "recipe.firework.tooltip" + ((CachedFireworkRecipe) arecipes.get(recipe)).recipeType));
        return currenttip;
    }
}
