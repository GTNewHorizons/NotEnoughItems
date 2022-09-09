package codechicken.nei.api;

import cpw.mods.fml.common.Loader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidContainerItem;

/**
 * Helper method for getting fluid from item.
 */
@SuppressWarnings("unused")
public class FluidGetter {

    private static final List<IFluidGetter> getters = new ArrayList<>();

    static {
        registerGetter(new GT5uGetter());
        registerGetter(new ForgeGetter());
    }

    public static FluidStack getFluid(ItemStack stack) {
        for (IFluidGetter getter : getters) {
            if (!getter.shouldLoad()) continue;
            FluidStack fluid = getter.get(stack);
            if (fluid != null) {
                return fluid;
            }
        }
        return null;
    }

    public static void registerGetter(IFluidGetter getter) {
        getters.add(getter);
    }

    public interface IFluidGetter {
        boolean shouldLoad();

        FluidStack get(ItemStack stack);
    }

    private static class ForgeGetter implements IFluidGetter {

        @Override
        public boolean shouldLoad() {
            return true;
        }

        @Override
        public FluidStack get(ItemStack stack) {
            if (stack.getItem() instanceof IFluidContainerItem) {
                IFluidContainerItem item = (IFluidContainerItem) stack.getItem();
                if (item.getCapacity(stack) > 0) {
                    ItemStack copy = stack.copy();
                    copy.stackSize = 1;
                    return item.drain(copy, Integer.MAX_VALUE, true);
                }
            }
            return FluidContainerRegistry.getFluidForFilledItem(stack);
        }
    }

    private static class GT5uGetter implements IFluidGetter {

        private static Method getFluidFromContainerOrFluidDisplay;

        static {
            try {
                getFluidFromContainerOrFluidDisplay = Class.forName("gregtech.api.util.GT_Utility")
                        .getDeclaredMethod("getFluidFromContainerOrFluidDisplay", ItemStack.class);
            } catch (Exception ignored) {
            }
        }

        @Override
        public boolean shouldLoad() {
            return Loader.isModLoaded("gregtech") && !Loader.isModLoaded("gregapi_post");
        }

        @Override
        public FluidStack get(ItemStack stack) {
            if (getFluidFromContainerOrFluidDisplay == null) return null;
            try {
                return (FluidStack) getFluidFromContainerOrFluidDisplay.invoke(null, stack);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }
}
