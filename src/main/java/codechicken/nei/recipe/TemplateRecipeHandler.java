package codechicken.nei.recipe;

import static codechicken.lib.gui.GuiDraw.changeTexture;
import static codechicken.lib.gui.GuiDraw.drawTexturedModalRect;
import static codechicken.lib.gui.GuiDraw.getMousePosition;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntityFurnace;

import org.lwjgl.opengl.GL11;

import com.google.common.base.Stopwatch;

import codechicken.lib.vec.Rectangle4i;
import codechicken.nei.ItemList;
import codechicken.nei.ItemList.AllMultiItemFilter;
import codechicken.nei.LayoutManager;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.NEIServerUtils;
import codechicken.nei.PositionedStack;
import codechicken.nei.api.DefaultOverlayRenderer;
import codechicken.nei.api.IOverlayHandler;
import codechicken.nei.api.IRecipeOverlayRenderer;
import codechicken.nei.api.IStackPositioner;
import codechicken.nei.api.ItemFilter;
import codechicken.nei.api.ItemInfo;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.guihook.IContainerInputHandler;
import codechicken.nei.guihook.IContainerTooltipHandler;

/**
 * A Template Recipe Handler! How about that. Because it was sooo hard, and more seriously required lots of copied code
 * to make a handler in the past. you can now extend this class to make your custom recipe handlers much easier to
 * create. Just look at the 5 handlers included by default to work out how to do stuff if you are still stuck.
 */
public abstract class TemplateRecipeHandler implements ICraftingHandler, IUsageHandler {

    protected static ReentrantLock lock = new ReentrantLock();

    public static void findFuelsOnce() {
        // Ensure we only find fuels once, even if threaded
        lock.lock();
        try {
            if (FurnaceRecipeHandler.afuels == null || FurnaceRecipeHandler.afuels.isEmpty()) findFuels(false);
        } finally {
            lock.unlock();
        }
    }

    public static void findFuelsOnceParallel() {
        // Ensure we only find fuels once, even if threaded
        lock.lock();
        try {
            if (FurnaceRecipeHandler.afuels == null || FurnaceRecipeHandler.afuels.isEmpty()) findFuels(true);
        } finally {
            lock.unlock();
        }
    }

    private static Set<Item> efuels;

    @SuppressWarnings("UnstableApiUsage")
    private static void findFuels(boolean parallel) {
        efuels = new HashSet<>();
        efuels.add(Item.getItemFromBlock(Blocks.brown_mushroom));
        efuels.add(Item.getItemFromBlock(Blocks.red_mushroom));
        efuels.add(Item.getItemFromBlock(Blocks.standing_sign));
        efuels.add(Item.getItemFromBlock(Blocks.wall_sign));
        efuels.add(Item.getItemFromBlock(Blocks.wooden_door));
        efuels.add(Item.getItemFromBlock(Blocks.trapped_chest));

        Stopwatch stopwatch = Stopwatch.createStarted();
        if (parallel) {
            try {
                FurnaceRecipeHandler.afuels = ItemList.forkJoinPool
                        .submit(
                                () -> ItemList.items.parallelStream().map(TemplateRecipeHandler::identifyFuel)
                                        .filter(Objects::nonNull).collect(Collectors.toCollection(ArrayList::new)))
                        .get();
            } catch (InterruptedException | ExecutionException e) {
                FurnaceRecipeHandler.afuels = new ArrayList<>();
                e.printStackTrace();
            }
        } else {
            FurnaceRecipeHandler.afuels = ItemList.items.stream().map(TemplateRecipeHandler::identifyFuel)
                    .filter(Objects::nonNull).collect(Collectors.toCollection(ArrayList::new));
        }

        NEIClientConfig.logger.info("FindFuels took " + stopwatch.stop());
    }

    private static FurnaceRecipeHandler.FuelPair identifyFuel(final ItemStack itemStack) {
        if (efuels.contains(itemStack.getItem())) return null;
        final int burnTime = TileEntityFurnace.getItemBurnTime(itemStack);
        if (burnTime <= 0) return null;
        return new FurnaceRecipeHandler.FuelPair(itemStack.copy(), burnTime);
    }

    /**
     * This Recipe Handler runs on this internal class Fill the recipe array with subclasses of this to make
     * transforming the different types of recipes out there into a nice format for NEI a much easier job.
     */
    public abstract class CachedRecipe {

        final long offset = System.currentTimeMillis();

        /**
         * @return The item produced by this recipe, with position
         */
        public abstract PositionedStack getResult();

        /**
         * The ingredients required to produce the result Use this if you have more than one ingredient
         *
         * @return A list of positioned ingredient items.
         */
        public List<PositionedStack> getIngredients() {
            ArrayList<PositionedStack> stacks = new ArrayList<>();
            PositionedStack stack = getIngredient();
            if (stack != null) stacks.add(stack);
            return stacks;
        }

        /**
         * @return The ingredient required to produce the result
         */
        public PositionedStack getIngredient() {
            return null;
        }

        /**
         * Return extra items that are not directly involved in the ingredient->result relationship. Eg fuels. Use this
         * if you have more than one other stack
         *
         * @return A list of positioned items.
         */
        public List<PositionedStack> getOtherStacks() {
            ArrayList<PositionedStack> stacks = new ArrayList<>();
            try {
                PositionedStack stack = getOtherStack();
                if (stack != null) stacks.add(stack);
            } catch (ArithmeticException e) {
                NEIClientConfig.logger.error("Error in getOtherStacks: " + e);
            }
            return stacks;
        }

        /**
         * Simple utility
         *
         * @return The another positioned stack
         */
        public PositionedStack getOtherStack() {
            return null;
        }

        /**
         * This will perform default cycling of ingredients, mulitItem capable
         *
         * @param cycle       Current cycle step
         * @param ingredients List of ItemStacks to cycle
         * @return The provided list of ingredients, with their permutations cycled to a different permutation, if one
         *         is available
         */
        public List<PositionedStack> getCycledIngredients(int cycle, List<PositionedStack> ingredients) {

            if (!NEIClientUtils.shiftKey() && !disableCycledIngredients) {
                if (NEIClientConfig.useJEIStyledCycledIngredients()) {
                    jeiStyledRenderPermutation(ingredients, cycle);
                } else {
                    randomRenderPermutation(ingredients, cycle);
                }
            }

            return ingredients;
        }

        public void randomRenderPermutation(List<PositionedStack> stacks, long cycle) {
            final ItemFilter filter = getItemFilter();
            final int randCycle = Math.abs(new Random(cycle + offset).nextInt());

            for (PositionedStack stack : stacks) {
                if (stack.items.length <= 1) continue;
                ArrayList<Integer> filtered = getFilteredIngredientAlternatives(stack, filter);
                if (filtered.isEmpty()) {
                    stack.setPermutationToRender((int) (randCycle % stack.items.length));
                } else {
                    stack.setPermutationToRender(filtered.get((int) (randCycle % filtered.size())));
                }
            }

        }

        protected void jeiStyledRenderPermutation(List<PositionedStack> stacks, long cycle) {
            final ItemFilter filter = getItemFilter();

            for (PositionedStack stack : stacks) {
                if (stack.items.length <= 1) continue;
                ArrayList<Integer> filtered = getFilteredIngredientAlternatives(stack, filter);
                if (filtered.isEmpty()) {
                    stack.setPermutationToRender((int) (cycle % stack.items.length));
                } else {
                    stack.setPermutationToRender(filtered.get((int) (cycle % filtered.size())));
                }
            }
        }

        @Deprecated
        public void randomRenderPermutation(PositionedStack stack, long cycle) {
            Random rand = new Random(cycle + offset);
            stack.setPermutationToRender((int) (Math.abs(rand.nextInt()) % stack.items.length));
        }

        public void setIngredientPermutation(Collection<PositionedStack> ingredients, ItemStack ingredient) {
            for (PositionedStack stack : ingredients) {
                for (int i = 0; i < stack.items.length; i++) {
                    if (NEIServerUtils.areStacksSameTypeCrafting(ingredient, stack.items[i])) {
                        stack.item = stack.items[i];
                        Items.feather.setDamage(stack.item, Items.feather.getDamage(ingredient));
                        if (ingredient.hasTagCompound()) {
                            stack.item.setTagCompound((NBTTagCompound) ingredient.getTagCompound().copy());
                        }
                        stack.items = new ItemStack[] { stack.item };
                        stack.setPermutationToRender(0);
                        break;
                    }
                }
            }
        }

        /**
         * @param ingredients Collection of ItemStacks
         * @param ingredient  ItemStack we're looking for
         * @return true if any of the permutations of the ingredients contain this ItemStack
         */
        public boolean contains(Collection<PositionedStack> ingredients, ItemStack ingredient) {
            for (PositionedStack stack : ingredients) if (stack.contains(ingredient)) return true;

            return false;
        }

        /**
         * NBT-friendly version of {@link #contains(Collection, ItemStack)}
         */
        public boolean containsWithNBT(Collection<PositionedStack> ingredients, ItemStack ingredient) {
            for (PositionedStack stack : ingredients) if (stack.containsWithNBT(ingredient)) return true;

            return false;
        }

        /**
         * @param ingredients Collection of ItemStacks
         * @param ingredient  Item we're looking for
         * @return true if any of the permutations of the ingredients contain this Item
         */
        public boolean contains(Collection<PositionedStack> ingredients, Item ingredient) {
            for (PositionedStack stack : ingredients) if (stack.contains(ingredient)) return true;
            return false;
        }
    }

    /**
     * The Rectangle is an region of the gui relative to the corner of the recipe that will activate the recipe with the
     * corresponding outputId apon being clicked. Apply this over fuel icons or arrows that the user may click to see
     * all recipes pertaining to that action.
     */
    public static class RecipeTransferRect {

        public RecipeTransferRect(Rectangle rectangle, String outputId, Object... results) {
            rect = rectangle;
            this.outputId = outputId;
            this.results = results;
        }

        public boolean equals(Object obj) {
            if (!(obj instanceof RecipeTransferRect)) return false;

            return rect.equals(((RecipeTransferRect) obj).rect);
        }

        public int hashCode() {
            return rect.hashCode();
        }

        Rectangle rect;
        String outputId;
        Object[] results;
    }

    public static class RecipeTransferRectHandler implements IContainerInputHandler, IContainerTooltipHandler {

        private static final HashMap<Class<? extends GuiContainer>, HashSet<RecipeTransferRect>> guiMap = new HashMap<>();

        public static void registerRectsToGuis(List<Class<? extends GuiContainer>> classes,
                List<RecipeTransferRect> rects) {
            if (classes == null) return;

            for (Class<? extends GuiContainer> clazz : classes) {
                HashSet<RecipeTransferRect> set = guiMap.computeIfAbsent(clazz, k -> new HashSet<>());
                set.addAll(rects);
            }
        }

        public boolean canHandle(GuiContainer gui) {
            return guiMap.containsKey(gui.getClass());
        }

        @Override
        public boolean lastKeyTyped(GuiContainer gui, char keyChar, int keyCode) {
            if (!canHandle(gui)) return false;

            if (NEIClientConfig.isKeyHashDown("gui.recipe")) {
                return transferRect(gui, false);
            } else if (NEIClientConfig.isKeyHashDown("gui.usage")) {
                return transferRect(gui, true);
            }

            return false;
        }

        @Override
        public boolean mouseClicked(GuiContainer gui, int mousex, int mousey, int button) {
            if (!canHandle(gui)) return false;

            if (button == 0) return transferRect(gui, false);
            else if (button == 1) return transferRect(gui, true);

            return false;
        }

        private boolean transferRect(GuiContainer gui, boolean usage) {
            int[] offset = RecipeInfo.getGuiOffset(gui);
            return TemplateRecipeHandler.transferRect(gui, guiMap.get(gui.getClass()), offset[0], offset[1], usage);
        }

        @Override
        public void onKeyTyped(GuiContainer gui, char keyChar, int keyID) {}

        @Override
        public void onMouseClicked(GuiContainer gui, int mousex, int mousey, int button) {}

        @Override
        public void onMouseUp(GuiContainer gui, int mousex, int mousey, int button) {}

        @Override
        public boolean keyTyped(GuiContainer gui, char keyChar, int keyID) {
            return false;
        }

        @Override
        public boolean mouseScrolled(GuiContainer gui, int mousex, int mousey, int scrolled) {
            return false;
        }

        @Override
        public void onMouseScrolled(GuiContainer gui, int mousex, int mousey, int scrolled) {}

        @Override
        public List<String> handleTooltip(GuiContainer gui, int mousex, int mousey, List<String> currenttip) {
            if (!canHandle(gui)) return currenttip;

            if (GuiContainerManager.shouldShowTooltip(gui) && currenttip.size() == 0) {
                int[] offset = RecipeInfo.getGuiOffset(gui);
                currenttip = TemplateRecipeHandler
                        .transferRectTooltip(gui, guiMap.get(gui.getClass()), offset[0], offset[1], currenttip);
            }
            return currenttip;
        }

        @Override
        public List<String> handleItemDisplayName(GuiContainer gui, ItemStack itemstack, List<String> currenttip) {
            return currenttip;
        }

        @Override
        public List<String> handleItemTooltip(GuiContainer gui, ItemStack itemstack, int mousex, int mousey,
                List<String> currenttip) {
            return currenttip;
        }

        @Override
        public void onMouseDragged(GuiContainer gui, int mousex, int mousey, int button, long heldTime) {}
    }

    static {
        GuiContainerManager.addInputHandler(new RecipeTransferRectHandler());
        GuiContainerManager.addTooltipHandler(new RecipeTransferRectHandler());
    }

    /**
     * Internal tick counter, initialised to random value and incremented every tick. Used for cycling similar
     * ingredients and progress bars.
     */
    public int cycleticks = Math.abs((int) System.currentTimeMillis());
    /**
     * The list of matching recipes
     */
    public ArrayList<CachedRecipe> arecipes = new ArrayList<>();
    /**
     * A list of transferRects that when clicked or R is pressed will open a new recipe.
     */
    public LinkedList<RecipeTransferRect> transferRects = new LinkedList<>();

    public static boolean disableCycledIngredients = true;

    public TemplateRecipeHandler() {
        loadTransferRects();
        RecipeTransferRectHandler.registerRectsToGuis(getRecipeTransferRectGuis(), transferRects);
    }

    protected static ItemFilter getItemFilter() {
        return new AllMultiItemFilter(
                item -> !ItemInfo.hiddenItems.contains(item),
                LayoutManager.presetsPanel != null ? LayoutManager.presetsPanel.getFilter() : null,
                GuiRecipe.searchField != null ? GuiRecipe.searchField.getFilter() : null);
    }

    protected static ArrayList<Integer> getFilteredIngredientAlternatives(PositionedStack stack, ItemFilter filter) {
        final ArrayList<Integer> filtered = new ArrayList<>();

        for (int i = 0; i < stack.items.length; i++) {
            if (filter.matches(stack.items[i])) {
                filtered.add(i);
            }
        }

        return filtered;
    }

    /**
     * Add all RecipeTransferRects to the transferRects list during this call. Afterward they may be added to the input
     * handler for the corresponding guis from getRecipeTransferRectGuis
     */
    public void loadTransferRects() {}

    /**
     * In this function you need to fill up the empty recipe array with recipes. The default passes it to a cleaner
     * handler if outputId is an item
     *
     * @param outputId A String identifier representing the type of output produced. Eg. {"item", "fuel"}
     * @param results  Objects representing the results that matching recipes must produce.
     */
    public void loadCraftingRecipes(String outputId, Object... results) {
        if (outputId.equals("item")) loadCraftingRecipes((ItemStack) results[0]);
    }

    /**
     * Simplified wrapper, implement this and fill the empty recipe array with recipes
     *
     * @param result The result the recipes must output.
     */
    public void loadCraftingRecipes(ItemStack result) {}

    /**
     * In this function you need to fill up the empty recipe array with recipes The default passes it to a cleaner
     * handler if inputId is an item
     *
     * @param inputId     A String identifier representing the type of ingredients used. Eg. {"item", "fuel"}
     * @param ingredients Objects representing the ingredients that matching recipes must contain.
     */
    public void loadUsageRecipes(String inputId, Object... ingredients) {
        if (inputId.equals("item")) loadUsageRecipes((ItemStack) ingredients[0]);
    }

    /**
     * Simplified wrapper, implement this and fill the empty recipe array with recipes
     *
     * @param ingredient The ingredient the recipes must contain.
     */
    public void loadUsageRecipes(ItemStack ingredient) {}

    /**
     * @return The filepath to the texture to use when drawing this recipe
     */
    public abstract String getGuiTexture();

    /**
     * Simply works with the {@link DefaultOverlayRenderer} If the current container has been registered with this
     * identifier, the question mark appears and an overlay guide can be drawn.
     *
     * @return The overlay identifier of this recipe type.
     */
    @Override
    public String getOverlayIdentifier() {
        return null;
    }

    /**
     * Extension point for drawing progress bars and other overlays
     *
     * @param recipe The recipeIndex being drawn
     */
    public void drawExtras(int recipe) {}

    /**
     * Draws a texture rectangle that changes size with time. Commonly used for progress bars.
     *
     * @param x         X position on screen
     * @param y         Y position on screen
     * @param tx        Texture X position
     * @param ty        Texture Y position
     * @param w         Texture width
     * @param h         Texture height
     * @param ticks     The amount of ticks for the bar to complete
     * @param direction 0 right, 1 down, 2 left, 3 up. If bit 3 is set the bar will shrink rather extend
     */
    public void drawProgressBar(int x, int y, int tx, int ty, int w, int h, int ticks, int direction) {
        drawProgressBar(x, y, tx, ty, w, h, cycleticks % ticks / (float) ticks, direction);
    }

    /**
     * Draws a texture rectangle that changes size with time. Commonly used for progress bars. If for some reason you
     * don't like the default counter use this and specify the progress percentage.
     *
     * @param x          X position on screen
     * @param y          Y position on screen
     * @param tx         Texture X position
     * @param ty         Texture Y position
     * @param w          Texture width
     * @param h          Texture height
     * @param completion the percentage of progress bar completion, 0-1
     * @param direction  0 right, 1 down, 2 left, 3 up. If bit 3 is set the bar will shrink rather extend
     */
    public void drawProgressBar(int x, int y, int tx, int ty, int w, int h, float completion, int direction) {
        if (direction > 3) {
            completion = 1 - completion;
            direction %= 4;
        }
        int var = (int) (completion * (direction % 2 == 0 ? w : h));

        switch (direction) {
            case 0: // right
                drawTexturedModalRect(x, y, tx, ty, var, h);
                break;
            case 1: // down
                drawTexturedModalRect(x, y, tx, ty, w, var);
                break;
            case 2: // left
                drawTexturedModalRect(x + w - var, y, tx + w - var, ty, var, h);
                break;
            case 3: // up
                drawTexturedModalRect(x, y + h - var, tx, ty + h - var, w, var);
                break;
        }
    }

    /**
     * @return The gui classes to which the transfer rects added in the constructor are to be located over. null if
     *         none.
     */
    public List<Class<? extends GuiContainer>> getRecipeTransferRectGuis() {
        Class<? extends GuiContainer> clazz = getGuiClass();
        if (clazz != null) {
            LinkedList<Class<? extends GuiContainer>> list = new LinkedList<>();
            list.add(clazz);
            return list;
        }
        return null;
    }

    /**
     * @return The class of the GuiContainer that this recipe would be crafted in.
     */
    public Class<? extends GuiContainer> getGuiClass() {
        return null;
    }

    public TemplateRecipeHandler newInstance() {
        try {
            // Use the non parallel version since we might already be using the forkJoinPool
            // and might have no threads
            // available
            // This will ideally have been pre-cached elsewhere and will be a NOOP
            findFuelsOnce();
            return getClass().newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public ICraftingHandler getRecipeHandler(String outputId, Object... results) {
        TemplateRecipeHandler handler = newInstance();
        handler.loadCraftingRecipes(outputId, results);
        return handler;
    }

    public IUsageHandler getUsageHandler(String inputId, Object... ingredients) {
        TemplateRecipeHandler handler = newInstance();
        handler.loadUsageRecipes(inputId, ingredients);
        return handler;
    }

    /**
     * First look up {@link RecipeCatalysts} whether target ingredient is registered for it. If so, load all recipes for
     * this handler. Fallback to {@link TemplateRecipeHandler#getUsageHandler}.
     */
    public IUsageHandler getUsageAndCatalystHandler(String inputId, Object... ingredients) {
        TemplateRecipeHandler handler = newInstance();
        if (inputId.equals("item")) {
            ItemStack candidate = (ItemStack) ingredients[0];
            if (RecipeCatalysts.containsCatalyst(handler, candidate)) {
                for (RecipeTransferRect rect : transferRects) {
                    if (specifyTransferRect() == null || Objects.equals(rect.outputId, specifyTransferRect())) {
                        handler.loadCraftingRecipes(rect.outputId, rect.results);
                        return handler;
                    }
                }
                NEIClientConfig.logger.info(
                        "failed to load catalyst handler, implement `loadTransferRects` for your handler "
                                + handler.getClass().getName());
            }
        }
        return this.getUsageHandler(inputId, ingredients);
    }

    /**
     * Override this if you have multiple {@link RecipeTransferRect}s. Used for
     * {@link TemplateRecipeHandler#getUsageAndCatalystHandler}.
     */
    public String specifyTransferRect() {
        return null;
    }

    public int numRecipes() {
        return arecipes.size();
    }

    public void drawBackground(int recipe) {
        GL11.glColor4f(1, 1, 1, 1);
        changeTexture(getGuiTexture());
        drawTexturedModalRect(0, 0, 5, 11, 166, 65);
    }

    public void drawForeground(int recipe) {
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glDisable(GL11.GL_LIGHTING);
        changeTexture(getGuiTexture());
        drawExtras(recipe);
    }

    public List<PositionedStack> getIngredientStacks(int recipe) {
        return arecipes.get(recipe).getIngredients();
    }

    public PositionedStack getResultStack(int recipe) {
        try {
            return arecipes.get(recipe).getResult();
        } catch (ArrayIndexOutOfBoundsException ignored) {
            return null;
        }
    }

    public List<PositionedStack> getOtherStacks(int recipe) {
        return arecipes.get(recipe).getOtherStacks();
    }

    public void onUpdate() {
        if (!NEIClientUtils.shiftKey()) cycleticks++;
    }

    public boolean hasOverlay(GuiContainer gui, Container container, int recipe) {
        return RecipeInfo.hasDefaultOverlay(gui, getOverlayIdentifier())
                || RecipeInfo.hasOverlayHandler(gui, getOverlayIdentifier());
    }

    public IRecipeOverlayRenderer getOverlayRenderer(GuiContainer gui, int recipe) {
        IStackPositioner positioner = RecipeInfo.getStackPositioner(gui, getOverlayIdentifier());
        if (positioner == null) return null;
        return new DefaultOverlayRenderer(getIngredientStacks(recipe), positioner);
    }

    @Override
    public IOverlayHandler getOverlayHandler(GuiContainer gui, int recipe) {
        return RecipeInfo.getOverlayHandler(gui, getOverlayIdentifier());
    }

    @Override
    public int recipiesPerPage() {
        return 2;
    }

    @Override
    public List<String> handleTooltip(GuiRecipe<?> gui, List<String> currenttip, int recipe) {
        if (GuiContainerManager.shouldShowTooltip(gui) && currenttip.size() == 0) {
            Point offset = gui.getRecipePosition(recipe);
            currenttip = transferRectTooltip(gui, transferRects, offset.x, offset.y, currenttip);
        }
        return currenttip;
    }

    @Override
    public List<String> handleItemTooltip(GuiRecipe<?> gui, ItemStack stack, List<String> currenttip, int recipe) {
        return currenttip;
    }

    @Override
    public boolean keyTyped(GuiRecipe<?> gui, char keyChar, int keyCode, int recipe) {

        if (NEIClientConfig.isKeyHashDown("gui.recipe")) {
            return transferRect(gui, recipe, false);
        } else if (NEIClientConfig.isKeyHashDown("gui.usage")) {
            return transferRect(gui, recipe, true);
        }

        return false;
    }

    @Override
    public boolean mouseClicked(GuiRecipe<?> gui, int button, int recipe) {

        if (button == 0) {
            return transferRect(gui, recipe, false);
        } else if (button == 1) {
            return transferRect(gui, recipe, true);
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(GuiRecipe<?> gui, int scroll, int recipe) {
        if (!NEIClientUtils.shiftKey()) return false;

        final Point pos = getMousePosition();
        final Point offset = gui.getRecipePosition(recipe);
        final Point relMouse = new Point(pos.x - gui.guiLeft - offset.x, pos.y - gui.guiTop - offset.y);
        final PositionedStack overStack = getIngredientMouseOver(relMouse.x, relMouse.y, recipe);

        if (overStack != null && overStack.items.length > 1) {
            final ItemFilter filter = getItemFilter();

            if (NEIClientConfig.useJEIStyledCycledIngredients()) {
                ItemStack stack = overStack.item;
                for (PositionedStack pStack : getIngredientStacks(recipe)) {
                    shiftPermutationToRender(pStack, stack, scroll, filter);
                }
            } else {
                shiftPermutationToRender(overStack, overStack.item, scroll, filter);
            }

            return true;
        }

        return false;
    }

    private void shiftPermutationToRender(PositionedStack pStack, ItemStack stack, int scroll, ItemFilter filter) {

        for (int index = 0; index < pStack.items.length; index++) {
            if (NEIServerUtils.areStacksSameTypeCraftingWithNBT(pStack.items[index], stack)) {
                ArrayList<Integer> filtered = getFilteredIngredientAlternatives(pStack, filter);

                if (filtered.isEmpty()) {
                    pStack.setPermutationToRender((int) ((pStack.items.length + scroll + index) % pStack.items.length));
                } else {
                    pStack.setPermutationToRender(
                            filtered.get((int) ((filtered.size() + scroll + index) % filtered.size())));
                }

                break;
            }
        }
    }

    private PositionedStack getIngredientMouseOver(int mousex, int mousey, int recipe) {

        for (PositionedStack pStack : getIngredientStacks(recipe)) {
            if ((new Rectangle4i(pStack.relx, pStack.rely, 18, 18)).contains(mousex, mousey)) {
                return pStack;
            }
        }

        return null;
    }

    private boolean transferRect(GuiRecipe<?> gui, int recipe, boolean usage) {
        Point offset = gui.getRecipePosition(recipe);
        return transferRect(gui, transferRects, offset.x, offset.y, usage);
    }

    private static boolean transferRect(GuiContainer gui, Collection<RecipeTransferRect> transferRects, int offsetx,
            int offsety, boolean usage) {
        Point pos = getMousePosition();
        Point relMouse = new Point(pos.x - gui.guiLeft - offsetx, pos.y - gui.guiTop - offsety);
        for (RecipeTransferRect rect : transferRects) {
            if (rect.rect.contains(relMouse) && (usage ? GuiUsageRecipe.openRecipeGui(rect.outputId, rect.results)
                    : GuiCraftingRecipe.openRecipeGui(rect.outputId, rect.results)))
                return true;
        }

        return false;
    }

    private static List<String> transferRectTooltip(GuiContainer gui, Collection<RecipeTransferRect> transferRects,
            int offsetx, int offsety, List<String> currenttip) {
        Point pos = getMousePosition();
        Point relMouse = new Point(pos.x - gui.guiLeft - offsetx, pos.y - gui.guiTop - offsety);
        for (RecipeTransferRect rect : transferRects) {
            if (rect.rect.contains(relMouse)) {
                currenttip.add(NEIClientUtils.translate("recipe.tooltip"));
                break;
            }
        }

        return currenttip;
    }
}
