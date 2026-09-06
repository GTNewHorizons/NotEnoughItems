package codechicken.nei;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;

import org.lwjgl.opengl.GL11;

import codechicken.nei.api.ItemFilter;
import codechicken.nei.api.ItemInfo;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.recipe.Badge;
import codechicken.nei.recipe.GuiRecipe;
import codechicken.nei.recipe.StackInfo;

/**
 * Simply an {@link ItemStack} with position. Mainly used in the recipe handlers.
 */
public class PositionedStack implements Cloneable {

    /** Maximum chance value representing 100% probability (1 unit = 0.01%). */
    public static final int CHANCE_FULL = 10_000;

    public int relx;
    public int rely;
    public int width = 16;
    public int height = 16;
    public ItemStack[] items;
    // compatibility dummy
    public ItemStack item;

    protected int chance = CHANCE_FULL;
    protected boolean permutated = false;

    protected String acceptsLabel;
    protected List<String> tooltip;
    protected List<Badge> badges;

    public PositionedStack(Object object, int x, int y, boolean genPerms) {
        items = NEIServerUtils.extractRecipeItems(object);
        relx = x;
        rely = y;

        if (genPerms) {
            generatePermutations();
        } else {
            setPermutationToRender(0);
        }
    }

    public PositionedStack(Object object, int x, int y) {
        this(object, x, y, true);
    }

    public void generatePermutations() {
        if (permutated) return;

        List<ItemStack> stacks = new ArrayList<>();
        for (ItemStack item : items) {
            if (item == null || item.getItem() == null) continue;

            if (item.getItemDamage() == Short.MAX_VALUE) {
                List<ItemStack> permutations = ItemList.itemMap.get(item.getItem());
                if (!permutations.isEmpty()) {
                    for (ItemStack stack : permutations) {
                        ItemStack toAdd = stack.copy();
                        toAdd.stackSize = item.stackSize;
                        stacks.add(toAdd);
                    }
                } else {
                    ItemStack base = new ItemStack(item.getItem(), item.stackSize);
                    base.stackTagCompound = item.stackTagCompound;
                    stacks.add(base);
                }
                continue;
            }

            stacks.add(item.copy());
        }
        items = stacks.toArray(new ItemStack[0]);

        if (items.length == 0) items = new ItemStack[] { new ItemStack(Blocks.fire) };

        permutated = true;
        setPermutationToRender(0);
    }

    public void setMaxSize(int i) {
        for (ItemStack item : items) if (item.stackSize > i) item.stackSize = i;
    }

    public void setAcceptsLabel(String acceptsLabel) {
        this.acceptsLabel = acceptsLabel;
    }

    public String getAcceptsLabel() {
        return this.acceptsLabel;
    }

    public void setBadges(List<Badge> badges) {
        this.badges = badges;
    }

    public List<Badge> getBadges() {
        return this.badges;
    }

    public void setTooltip(List<String> tooltip) {
        this.tooltip = tooltip;
    }

    public List<String> getTooltip() {
        return this.tooltip;
    }

    public int getChance() {
        return this.chance;
    }

    public void setChance(int chance) {
        this.chance = chance;
    }

    public PositionedStack copy() {
        try {
            PositionedStack pStack = (PositionedStack) super.clone();
            pStack.items = Arrays.stream(this.items).map(ItemStack::copy).toArray(ItemStack[]::new);
            pStack.item = this.item == null ? null : this.item.copy();
            pStack.tooltip = this.tooltip == null ? null : new ArrayList<>(this.tooltip);
            pStack.badges = this.badges == null ? null : new ArrayList<>(this.badges);
            return pStack;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    public void draw(int mousex, int mousey) {
        final int x = this.relx + (this.width - 16) / 2;
        final int y = this.rely + (this.height - 16) / 2;
        GuiContainerManager.drawItem(x, y, this.item);
    }

    public List<ItemStack> getFilteredPermutations() {
        return getFilteredPermutations(null);
    }

    public List<ItemStack> getFilteredPermutations(ItemFilter additionalFilter) {
        List<ItemStack> items = Arrays.asList(this.items);

        items = filteringPermutations(items, item -> !ItemInfo.isHidden(item));
        items = filteringPermutations(items, PresetsList.getItemFilter());
        items = filteringPermutations(items, GuiRecipe.getSearchItemFilter());
        items = filteringPermutations(items, additionalFilter);

        items.sort(Comparator.comparing(FavoriteRecipes::containsManual).reversed());
        return items;
    }

    private List<ItemStack> filteringPermutations(List<ItemStack> items, ItemFilter filter) {
        if (filter == null) return items;
        final List<ItemStack> filteredItems = items.stream().filter(filter::matches).collect(Collectors.toList());
        return filteredItems.isEmpty() ? items : filteredItems;
    }

    public int getPermutationIndex(ItemStack stack) {

        for (int index = 0; index < this.items.length; index++) {
            if (NEIServerUtils.areStacksSameType(items[index], stack)) {
                return index;
            }
        }

        return -1;
    }

    public boolean setPermutationToRender(ItemStack ingredient) {
        final int stackIndex = getPermutationIndex(ingredient);

        if (stackIndex >= 0) {
            setPermutationToRender(stackIndex);
        }

        return stackIndex >= 0;
    }

    public void setPermutationToRender(int index) {
        this.item = this.items[index].copy();

        if (this.item.getItem() == null) {
            this.item = new ItemStack(Blocks.fire);
        } else if (this.item.getItemDamage() == OreDictionary.WILDCARD_VALUE && this.item.getItem().isRepairable()) {
            this.item.setItemDamage(0);
        }
    }

    public boolean contains(int mx, int my) {
        return mx >= this.relx - 1 && mx < this.relx + this.width + 1
                && my >= this.rely - 1
                && my < this.rely + this.height + 1;
    }

    public boolean contains(ItemStack ingredient) {
        for (ItemStack item : items) if (NEIServerUtils.areStacksSameTypeCrafting(item, ingredient)) return true;

        return false;
    }

    /**
     * NBT-friendly version of {@link #contains(ItemStack)}
     */
    public boolean containsWithNBT(ItemStack ingredient) {
        for (ItemStack item : items) if (StackInfo.equalItemAndNBT(item, ingredient, true)) return true;

        return false;
    }

    public boolean contains(Item ingred) {
        for (ItemStack item : items) if (item.getItem() == ingred) return true;

        return false;
    }

    @Override
    public String toString() {
        return "PositionedStack(output='" + item.toString() + "')";
    }

    public static class Placeholder extends PositionedStack {

        public Placeholder(Object object, int x, int y, boolean genPerms) {
            super(object, x, y, genPerms);
        }

        public Placeholder(Object object, int x, int y) {
            this(object, x, y, true);
        }

        @Override
        public void draw(int mousex, int mousey) {}
    }

    public static class Fluid extends PositionedStack {

        /** The tank size the fill level is measured against, in mB. 0 means "always render full". */
        public int capacity = 0;

        private ItemStack cachedFluidItem;
        private FluidStack cachedFluidStack;

        public Fluid(Object object, int x, int y, boolean genPerms) {
            super(object, x, y, genPerms);
        }

        public Fluid(Object object, int x, int y) {
            this(object, x, y, true);
        }

        protected FluidStack getFluidStack() {

            if (this.item != this.cachedFluidItem) {
                this.cachedFluidItem = this.item;
                this.cachedFluidStack = StackInfo.isFluidDisplayItem(this.item) ? StackInfo.getFluid(this.item) : null;
            }

            return this.cachedFluidStack;
        }

        @Override
        public List<String> getTooltip() {
            final List<String> tooltip = new ArrayList<>();
            final List<String> customTooltip = super.getTooltip();
            final FluidStack fluidStack = getFluidStack();

            if (fluidStack != null) {
                tooltip.add(
                        NEIClientUtils
                                .translate("recipe.fluid.tank.amount", NEIClientUtils.formatFluid(fluidStack.amount)));
            }

            if (customTooltip != null) {
                tooltip.addAll(customTooltip);
            }

            return tooltip;
        }

        @Override
        public void draw(int mousex, int mousey) {
            final FluidStack fluidStack = getFluidStack();

            if (fluidStack == null) {
                super.draw(mousex, mousey);
                return;
            }

            final int tankCapacity = this.capacity > 0 ? this.capacity : fluidStack.amount;
            int fillHeight = tankCapacity > 0
                    ? (int) ((long) this.height * Math.min(fluidStack.amount, tankCapacity) / tankCapacity)
                    : 0;

            if (fluidStack.amount > 0 && fillHeight <= 0) {
                fillHeight = 1;
            }

            if (fillHeight > 0) {
                drawFluid(this.relx, this.rely, this.width, this.height, fillHeight, fluidStack);
            }
        }

        private static void drawFluid(int x, int y, int width, int height, int fillHeight, FluidStack fluidStack) {
            final IIcon icon = fluidStack.getFluid().getIcon(fluidStack);

            if (icon == null) {
                return;
            }

            final int color = fluidStack.getFluid().getColor(fluidStack);
            final float red = (color >> 16 & 0xFF) / 255F;
            final float green = (color >> 8 & 0xFF) / 255F;
            final float blue = (color & 0xFF) / 255F;

            Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
            GL11.glColor4f(red, green, blue, 1F);
            GL11.glDisable(GL11.GL_LIGHTING);

            final Tessellator tessellator = Tessellator.instance;

            tessellator.startDrawingQuads();
            for (int tx = 0; tx < width; tx += 16) {
                final int tileWidth = Math.min(16, width - tx);
                final double u2 = icon.getMinU() + (icon.getMaxU() - icon.getMinU()) * tileWidth / 16D;

                for (int ty = 0; ty < fillHeight; ty += 16) {
                    final int tileHeight = Math.min(16, fillHeight - ty);
                    final double v2 = icon.getMinV() + (icon.getMaxV() - icon.getMinV()) * tileHeight / 16D;
                    final int bottomY = y + height - ty;
                    final int topY = bottomY - tileHeight;

                    tessellator.addVertexWithUV(x + tx, bottomY, 0, icon.getMinU(), v2);
                    tessellator.addVertexWithUV(x + tx + tileWidth, bottomY, 0, u2, v2);
                    tessellator.addVertexWithUV(x + tx + tileWidth, topY, 0, u2, icon.getMinV());
                    tessellator.addVertexWithUV(x + tx, topY, 0, icon.getMinU(), icon.getMinV());
                }
            }
            tessellator.draw();

            GL11.glEnable(GL11.GL_LIGHTING);
            GL11.glColor4f(1F, 1F, 1F, 1F);
        }
    }
}
