package codechicken.nei.item;

import java.util.List;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidContainerItem;

import codechicken.nei.NEIClientConfig;
import codechicken.nei.NEIClientUtils;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class ItemFluidDisplay extends Item implements IFluidContainerItem {

    public static ItemFluidDisplay INSTANCE;

    public final static CreativeTabs tabFluids = new CreativeTabs("neiFluids") {

        @Override
        public Item getTabIconItem() {
            return INSTANCE;
        }

        @Override
        @SideOnly(Side.CLIENT)
        public ItemStack getIconItemStack() {
            return createStack(new FluidStack(FluidRegistry.WATER, 0));
        }
    };

    public ItemFluidDisplay() {
        setMaxStackSize(1);
        setUnlocalizedName("nei.fluidDisplay");
        INSTANCE = this;

        setCreativeTab(tabFluids);
        MinecraftForgeClient.registerItemRenderer(this, new FluidDisplayRenderer());
    }

    public static ItemStack createStack(FluidStack fluidStack) {
        return fluidStack == null ? null : createStack(fluidStack.getFluid(), fluidStack.amount);
    }

    public static ItemStack createStack(Fluid fluid, long amount) {
        if (fluid == null) {
            return null;
        }

        int fluidId;

        try {
            fluidId = FluidRegistry.getFluidID(fluid);
        } catch (Exception e) {
            NEIClientConfig.logger.error("Failed to get fluid id for: " + fluid.getName(), e);
            return null;
        }

        final ItemStack stack = new ItemStack(INSTANCE, 1, fluidId);
        final NBTTagCompound nbtTag = new NBTTagCompound();
        nbtTag.setLong("neiFluidDisplayAmount", amount);
        stack.setTagCompound(nbtTag);
        return stack;
    }

    @Override
    public FluidStack getFluid(ItemStack stack) {

        if (stack == null || !(stack.getItem() instanceof ItemFluidDisplay) || !stack.hasTagCompound()) {
            return null;
        }

        final Fluid fluid = FluidRegistry.getFluid(stack.getItemDamage());

        if (fluid == null) {
            return null;
        }

        final NBTTagCompound nbTag = stack.getTagCompound();
        final FluidStack fluidStack = new FluidStack(
                fluid,
                (int) Math.min(nbTag.getLong("neiFluidDisplayAmount"), Integer.MAX_VALUE));

        return fluidStack;
    }

    public long getAmountLong(ItemStack stack) {

        if (stack == null || !(stack.getItem() instanceof ItemFluidDisplay) || !stack.hasTagCompound()) {
            return 0;
        }

        return stack.getTagCompound().getLong("neiFluidDisplayAmount");
    }

    @Override
    public int getCapacity(ItemStack stack) {
        return (int) Math.min(getAmountLong(stack), Integer.MAX_VALUE);
    }

    @Override
    public int fill(ItemStack container, FluidStack resource, boolean doFill) {
        return 0;
    }

    @Override
    public FluidStack drain(ItemStack container, int maxDrain, boolean doDrain) {
        return null;
    }

    @Override
    public IIcon getIconFromDamage(int metadata) {
        final Fluid fluid = FluidRegistry.getFluid(metadata);
        final IIcon icon = fluid != null ? fluid.getStillIcon() : null;
        return icon != null ? icon : FluidRegistry.WATER.getStillIcon();
    }

    @Override
    public int getSpriteNumber() {
        return 0;
    }

    @Override
    public int getColorFromItemStack(ItemStack stack, int renderPass) {
        final Fluid fluid = stack != null ? FluidRegistry.getFluid(stack.getItemDamage()) : null;
        return fluid != null ? fluid.getColor() : 0xFFFFFF;
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        final Fluid fluid = stack != null ? FluidRegistry.getFluid(stack.getItemDamage()) : null;
        return fluid != null ? fluid.getLocalizedName() : super.getItemStackDisplayName(stack);
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        final Fluid fluid = stack != null ? FluidRegistry.getFluid(stack.getItemDamage()) : null;
        return fluid != null ? fluid.getUnlocalizedName() : super.getUnlocalizedName(stack);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> tooltip, boolean advanced) {
        final Fluid fluid = FluidRegistry.getFluid(stack.getItemDamage());

        if (fluid == null) {
            return;
        }

        if (NEIClientConfig.getBooleanSetting("inventory.tooltip.showFluidTemperature")) {
            tooltip.add(
                    EnumChatFormatting.RED + NEIClientUtils.translate(
                            "inventory.tooltip.temperature.fluid",
                            NEIClientUtils.formatNumber(fluid.getTemperature())));
        }

        if (NEIClientConfig.getBooleanSetting("inventory.tooltip.showFluidState")) {
            tooltip.add(
                    EnumChatFormatting.GREEN + NEIClientUtils.translate(
                            "inventory.tooltip.state.fluid",
                            NEIClientUtils.translate(
                                    fluid.isGaseous() ? "inventory.tooltip.state.fluid.gas"
                                            : "inventory.tooltip.state.fluid.liquid")));
        }

    }

    @Override
    public void registerIcons(IIconRegister register) {}

    @Override
    @SideOnly(Side.CLIENT)
    public void getSubItems(Item aItem, CreativeTabs aTab, List<ItemStack> aList) {
        for (int i = 0, j = FluidRegistry.getMaxID(); i < j; i++) {
            final Fluid fluid = FluidRegistry.getFluid(i);
            if (fluid == null) {
                continue;
            }

            final ItemStack stack = ItemFluidDisplay.createStack(new FluidStack(fluid, 0));
            if (stack != null) {
                aList.add(stack);
            }
        }
    }

}
