package codechicken.nei.api;

import net.minecraftforge.fluids.Fluid;

/**
 * Custom renderer for a fluid, used by NEI fluid display slots and fluid tanks in recipes.
 * <p>
 * Register with {@link API#registerFluidRenderer(Fluid, IFluidRenderer)}.
 */
public interface IFluidRenderer {

    /**
     * Renders one 16x16 tile of the fluid. Partial tiles of a tank are clipped by NEI. The amount overlay is drawn by
     * NEI.
     */
    void renderFluid(Fluid fluid, int x, int y);
}
