package codechicken.nei.search;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;

import codechicken.nei.NEIClientConfig;
import codechicken.nei.api.ItemFilter;
import codechicken.nei.api.ItemInfo;
import codechicken.nei.item.ItemFluidDisplay;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.common.registry.GameRegistry.UniqueIdentifier;

public class ModNameFilter implements ItemFilter {

    private static final Map<String, String> fluidOwners = new HashMap<>();
    private final Pattern pattern;

    public ModNameFilter(Pattern pattern) {
        this.pattern = pattern;
    }

    @Override
    public boolean matches(ItemStack itemStack) {
        return this.pattern.matcher(nameFromStack(itemStack)).find();
    }

    protected static String nameFromStack(ItemStack itemStack) {
        try {
            ModContainer mod = Loader.instance().getIndexedModList().get(getModId(itemStack));
            return mod == null ? "Minecraft" : mod.getName();
        } catch (Throwable e) {
            return "";
        }
    }

    protected static String getModId(ItemStack itemStack) {

        if (itemStack.getItem() instanceof ItemFluidDisplay fluidDisplay) {
            return getFluidModId(fluidDisplay, itemStack);
        }

        if (!ItemInfo.itemOwners.containsKey(itemStack.getItem())) {
            try {
                final UniqueIdentifier identifier = GameRegistry.findUniqueIdentifierFor(itemStack.getItem());
                ItemInfo.itemOwners.put(itemStack.getItem(), identifier.modId);
            } catch (Exception ignored) {
                NEIClientConfig.logger.error("Failed to find identifier for: " + itemStack.getItem());
                ItemInfo.itemOwners.put(itemStack.getItem(), "Unknown");
            }
        }

        return ItemInfo.itemOwners.get(itemStack.getItem());
    }

    private static String getFluidModId(ItemFluidDisplay fluidDisplay, ItemStack itemStack) {
        final Fluid fluid = FluidRegistry.getFluid(itemStack.getItemDamage());
        final String fluidName = FluidRegistry.getDefaultFluidName(fluid);

        if (!fluidOwners.containsKey(fluidName)) {
            try {
                final UniqueIdentifier identifier = new UniqueIdentifier(fluidName);
                fluidOwners.put(fluidName, identifier.modId);
            } catch (Exception ignored) {
                NEIClientConfig.logger.error("Failed to find identifier for fluid: " + fluidName);
                fluidOwners.put(fluidName, "Unknown");
            }
        }

        return fluidOwners.get(fluidName);
    }

}
