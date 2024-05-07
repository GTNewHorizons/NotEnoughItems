package mezz.jei.search;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.client.resources.I18n;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;

import codechicken.nei.recipe.StackInfo;

public class ItemStackHelper implements IIngredientHelper<ItemStack> {

    @Override
    public IFocus<?> translateFocus(IFocus<ItemStack> focus, IFocusFactory focusFactory) {
        ItemStack itemStack = focus.getValue();
        Item item = itemStack.getItem();
        // Special case for ItemBlocks containing fluid blocks.
        // Nothing crafts those, the player probably wants to look up fluids.
        if (item instanceof ItemBlock) {
            Block block = ((ItemBlock) item).field_150939_a;
            Fluid fluid = FluidRegistry.lookupFluidForBlock(block);
            if (fluid != null) {
                FluidStack fluidStack = new FluidStack(fluid, 1000);
                return focusFactory.createFocus(focus.getMode(), fluidStack);
            }
        }
        return focus;
    }

    @Nullable
    @Override
    public ItemStack getMatch(Iterable<ItemStack> ingredients, ItemStack ingredientToMatch) {
        return null;
    }

    @Override
    public String getDisplayName(ItemStack ingredient) {
        return ingredient.getDisplayName();
    }

    @Override
    public String getUniqueId(ItemStack ingredient) {
        return StackInfo.getItemStackGUID(ingredient);
    }

    @Override
    public String getWildcardId(ItemStack ingredient) {
        return StackInfo.getItemStackGUID(ingredient);
    }

    @Override
    public String getModId(ItemStack ingredient) {
        String itemName = ingredient.getItem().delegate.name().split(":")[0];

        return itemName;
    }

    @Override
    public String getDisplayModId(ItemStack ingredient) {
        return getModId(ingredient);
    }

    @Override
    public String getResourceId(ItemStack ingredient) {
        return null;
    }

    @Override
    public int getOrdinal(ItemStack ingredient) {
        return ingredient.getItemDamage();
    }

    @Override
    public ItemStack getCheatItemStack(ItemStack ingredient) {
        return ingredient;
    }

    @Override
    public ItemStack copyIngredient(ItemStack ingredient) {
        return ingredient.copy();
    }

    @Override
    public boolean isValidIngredient(ItemStack ingredient) {
        return ingredient != null;
    }

    @Override
    public Collection<String> getOreDictNames(ItemStack ingredient) {
        Collection<String> names = new ArrayList<>();
        for (int oreId : OreDictionary.getOreIDs(ingredient)) {
            String oreNameLowercase = OreDictionary.getOreName(oreId).toLowerCase(Locale.ENGLISH);
            names.add(oreNameLowercase);
        }
        return names;
    }

    @Override
    public Collection<String> getCreativeTabNames(ItemStack ingredient) {
        Collection<String> creativeTabsStrings = new ArrayList<>();
        Item item = ingredient.getItem();
        for (CreativeTabs creativeTab : item.getCreativeTabs()) {
            if (creativeTab != null) {
                String creativeTabName = I18n.format(creativeTab.getTranslatedTabLabel());
                creativeTabsStrings.add(creativeTabName);
            }
        }
        return creativeTabsStrings;
    }

    @Override
    public String getErrorInfo(@Nullable ItemStack ingredient) {
        return ingredient.toString();
    }
}
