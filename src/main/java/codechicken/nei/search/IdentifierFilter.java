package codechicken.nei.search;

import java.util.regex.Pattern;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import codechicken.nei.api.ItemFilter;
import codechicken.nei.recipe.StackInfo;
import cpw.mods.fml.common.registry.GameData;

public class IdentifierFilter implements ItemFilter {

    private final Pattern pattern;

    public IdentifierFilter(Pattern pattern) {
        this.pattern = pattern;
    }

    @Override
    public boolean matches(ItemStack stack) {
        final FluidStack fluidStack = StackInfo.isFluidDisplayItem(stack) ? StackInfo.getFluid(stack) : null;

        if (fluidStack != null) {
            return this.pattern.matcher(getFluidStringIdentifier(fluidStack) + "\n" + getFluidIdentifier(fluidStack))
                    .find();
        }

        return this.pattern.matcher(getStringIdentifier(stack) + "\n" + getIdentifier(stack)).find();
    }

    protected String getFluidIdentifier(FluidStack fluidStack) {
        return String.valueOf(FluidRegistry.getFluidID(fluidStack.getFluid()));
    }

    protected String getFluidStringIdentifier(FluidStack fluidStack) {
        final String name = FluidRegistry.getDefaultFluidName(fluidStack.getFluid());

        return name == null || name.isEmpty() ? "Unknown:Unknown" : name;
    }

    protected String getIdentifier(ItemStack stack) {
        return Item.getIdFromItem(stack.getItem()) + ":" + stack.getItemDamage();
    }

    protected String getStringIdentifier(ItemStack stack) {
        final String name = GameData.getItemRegistry().getNameForObject(stack.getItem());

        return name == null || name.isEmpty() ? "Unknown:Unknown" : name;
    }

}
