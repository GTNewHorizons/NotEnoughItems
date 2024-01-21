package codechicken.nei.recipe;

import static codechicken.lib.gui.GuiDraw.fontRenderer;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.fluids.FluidStack;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.Button;
import codechicken.nei.GuiNEIButton;
import codechicken.nei.LayoutManager;
import codechicken.nei.NEICPH;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.PositionedStack;
import codechicken.nei.RecipeSearchField;
import codechicken.nei.RestartableTask;
import codechicken.nei.VisiblityData;
import codechicken.nei.api.IGuiContainerOverlay;
import codechicken.nei.api.INEIGuiHandler;
import codechicken.nei.api.IOverlayHandler;
import codechicken.nei.api.IRecipeFilter;
import codechicken.nei.api.IRecipeFilter.IRecipeFilterProvider;
import codechicken.nei.api.IRecipeOverlayRenderer;
import codechicken.nei.api.ItemFilter;
import codechicken.nei.api.TaggedInventoryArea;
import codechicken.nei.drawable.DrawableBuilder;
import codechicken.nei.drawable.DrawableResource;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.guihook.IContainerTooltipHandler;
import codechicken.nei.guihook.IGuiClientSide;
import codechicken.nei.guihook.IGuiHandleMouseWheel;

public abstract class GuiRecipe<H extends IRecipeHandler> extends GuiContainer implements IGuiContainerOverlay,
        IGuiClientSide, IGuiHandleMouseWheel, IContainerTooltipHandler, INEIGuiHandler {

    // Background image calculations
    private static final int BG_TOP_HEIGHT = 6;
    private static final int BG_MIDDLE_HEIGHT = 154;
    private static final int BG_BOTTOM_HEIGHT = 6;

    private static final int BG_TOP_Y = 0;
    private static final int BG_MIDDLE_Y = BG_TOP_Y + BG_TOP_HEIGHT;
    private static final int BG_BOTTOM_Y = BG_MIDDLE_Y + BG_MIDDLE_HEIGHT;

    // Button sizes
    private static final int borderPadding = 6;
    private static final int buttonWidth = 13;
    private static final int buttonHeight = 12;

    public static final List<IRecipeFilterProvider> recipeFilterers = new LinkedList<>();

    protected boolean limitToOneRecipe = false;

    // Background image
    final DrawableResource bgTop = new DrawableBuilder("nei:textures/gui/recipebg.png", 0, BG_TOP_Y, 176, BG_TOP_HEIGHT)
            .build();
    final DrawableResource bgMiddle = new DrawableBuilder(
            "nei:textures/gui/recipebg.png",
            0,
            BG_MIDDLE_Y,
            176,
            BG_MIDDLE_HEIGHT).build();
    final DrawableResource bgBottom = new DrawableBuilder(
            "nei:textures/gui/recipebg.png",
            0,
            BG_BOTTOM_Y,
            176,
            BG_BOTTOM_HEIGHT).build();

    public ArrayList<H> currenthandlers = new ArrayList<>();
    public int page;
    public int recipetype;

    public BookmarkRecipeId recipeId;
    public ContainerRecipe slotcontainer;
    public GuiContainer firstGui;
    public GuiScreen firstGuiGeneral;
    public GuiScreen prevGui;
    public GuiButton nextpage;
    public GuiButton prevpage;
    private GuiButton nexttype;
    private GuiButton prevtype;

    private final int OVERLAY_BUTTON_ID_START = 4;
    private GuiButton[] overlayButtons;

    private final Rectangle area = new Rectangle();
    private final GuiRecipeTabs recipeTabs;
    private final GuiRecipeCatalyst guiRecipeCatalyst;
    private H handler;
    private HandlerInfo handlerInfo;

    private int yShift = 0;

    public static class ItemRecipeFilter implements IRecipeFilter {

        public ItemFilter filter;

        public ItemRecipeFilter(ItemFilter filter) {
            this.filter = filter;
        }

        @Override
        public boolean matches(IRecipeHandler handler, List<PositionedStack> ingredients, PositionedStack result,
                List<PositionedStack> others) {

            if (matchPositionedStack(ingredients)) {
                return true;
            }

            if (matchPositionedStack(result)) {
                return true;
            }

            if (matchPositionedStack(others)) {
                return true;
            }

            return false;
        }

        private boolean matchPositionedStack(List<PositionedStack> items) {
            for (PositionedStack pStack : items) {
                if (matchPositionedStack(pStack)) {
                    return true;
                }
            }
            return false;
        }

        private boolean matchPositionedStack(PositionedStack pStack) {
            if (pStack == null) return false;

            for (ItemStack stack : pStack.items) {
                if (filter.matches(stack)) {
                    return true;
                }
            }

            return false;
        }

    }

    public static class AllMultiRecipeFilter implements IRecipeFilter {

        public List<IRecipeFilter> filters;

        public AllMultiRecipeFilter(List<IRecipeFilter> filters) {
            this.filters = filters;
        }

        public AllMultiRecipeFilter(IRecipeFilter a, IRecipeFilter b) {
            this.filters = new ArrayList<>();
            this.filters.add(a);
            this.filters.add(b);
        }

        public AllMultiRecipeFilter() {
            this(new ArrayList<>());
        }

        @Override
        public boolean matches(IRecipeHandler handler, List<PositionedStack> ingredients, PositionedStack result,
                List<PositionedStack> others) {
            for (IRecipeFilter filter : filters) {
                try {
                    if (filter != null && !filter.matches(handler, ingredients, result, others)) return false;
                } catch (Exception e) {
                    NEIClientConfig.logger.error("Exception filtering " + handler + " with " + filter, e);
                }
            }
            return true;
        }
    }

    protected static final RestartableTask updateFilter = new RestartableTask("NEI Recipe Filtering") {

        @Override
        public void execute() {
            final GuiScreen currentScreen = NEIClientUtils.mc().currentScreen;

            if (currentScreen instanceof GuiRecipe) {
                final GuiRecipe<?> guiRecipe = (GuiRecipe<?>) currentScreen;
                final IRecipeHandler handler = guiRecipe.handler;

                if (handler != null && IRecipeFilterProvider.filteringAvailable(handler)) {

                    if (GuiRecipe.searchField.text().isEmpty()) {
                        guiRecipe.updateRecipeList(handler, null);
                    } else {
                        final IRecipeFilter filter = new ItemRecipeFilter(GuiRecipe.searchField.getFilter());
                        ArrayList<Integer> filtered = ((TemplateRecipeHandler) handler).getSearchResult(filter);

                        if (filtered == null) {
                            stop();
                        }

                        if (interrupted()) return;
                        guiRecipe.updateRecipeList(handler, filtered);
                    }

                }

            }

        }

    };

    public static final RecipeSearchField searchField = new RecipeSearchField("") {

        @Override
        protected boolean noResults() {
            GuiScreen currentScreen = NEIClientUtils.mc().currentScreen;
            return !(currentScreen instanceof GuiRecipe) || ((GuiRecipe<?>) currentScreen).handler.numRecipes() > 0;
        }

        @Override
        public void onTextChange(String oldText) {
            updateFilter.restart();
        }

    };

    protected static Button toggleSearch = new Button() {

        @Override
        public boolean onButtonPress(boolean rightclick) {
            if (rightclick) return false;

            if (searchField.isVisible()) {
                searchField.setText("");
                searchField.setFocus(false);
                searchField.setVisible(false);
                state = 0;
            } else {
                searchField.setVisible(true);
                searchField.setFocus(true);
                state = 2;
            }

            return true;
        }

    };

    /**
     * This will only be true iff height hacking has been configured for the current recipe handler AND we are currently
     * within the scope of an active {@link CompatibilityHacks} instance.
     */
    private boolean isHeightHackApplied = false;

    protected GuiRecipe(GuiScreen prevgui) {
        super(new ContainerRecipe());
        recipeTabs = new GuiRecipeTabs(this);
        guiRecipeCatalyst = new GuiRecipeCatalyst(this);
        slotcontainer = (ContainerRecipe) inventorySlots;

        this.prevGui = prevgui;
        this.firstGuiGeneral = prevgui;

        if (prevgui instanceof GuiContainer) {
            this.firstGui = (GuiContainer) prevgui;
        }

        if (prevgui instanceof IGuiContainerOverlay) {
            this.firstGui = ((IGuiContainerOverlay) prevgui).getFirstScreen();
            this.firstGuiGeneral = ((IGuiContainerOverlay) prevgui).getFirstScreenGeneral();
        }
    }

    /** Changes the GUI mode to only display one recipe at a time, e.g. for tooltip usage */
    public void limitToOneRecipe() {
        limitToOneRecipe = true;
    }

    /** Checks if the gui only displays one recipe at a time, e.g. for tooltip usage */
    public boolean isLimitedToOneRecipe() {
        return limitToOneRecipe;
    }

    /**
     * Many old mods assumed a fixed NEI window height of {@code 166} pixels. Now that this is no longer the case, their
     * tooltip and click zone handling is broken. This helper class fixes these old mods by hacking the {@link #height}
     * property's value so that these old mods' calculations return the correct value for the new height.
     *
     * <p>
     * New and old mods often assume that the handler is only drawn when {@link GuiRecipe} is the currently active
     * screen in {@link net.minecraft.client.Minecraft}. This is no longer true since the addition of recipe tooltips
     * which need to render the recipe in other containers on mouse hover, this class temporarily switches the active
     * screen to {@code this} while rendering them to avoid ClassCastExceptions and other similar crashes.
     *
     * <p>
     * This class is an {@link AutoCloseable} so that it can be used with try-with-resources, which will ensure that
     * {@link #height} and {@link net.minecraft.client.Minecraft#currentScreen} is returned to the correct value
     * afterwards.
     */
    private class CompatibilityHacks implements AutoCloseable {

        private final int trueHeight;
        private final int trueGuiTop;
        private final GuiScreen trueGui;

        private CompatibilityHacks() {
            trueHeight = height;
            trueGuiTop = guiTop;

            isHeightHackApplied = NEIClientConfig.heightHackHandlerRegex.stream()
                    .map(pattern -> pattern.matcher(handler.getHandlerId())).anyMatch(Matcher::matches);
            if (isHeightHackApplied) {
                // guiTop is the top edge of the recipe screen on the y-axis. Old recipe handlers expect a single paging
                // widget at the top and at the bottom of the recipe screen with a height of 16px each, but GTNH NEI
                // moved the bottom paging widget to the top following JEI's design, increasing the "forbidden" zone at
                // the top to 32px. To fix these old recipe handlers we move guiTop down 16px so that they can keep
                // working with the old 16px gap.
                guiTop += 16;

                // The old NEI recipe screen had a fixed width and height and was always centered on the screen. Legacy
                // recipe handlers use the calculation ((height - 166) / 2) to compute the y-value of the top edge of
                // the NEI window. GTNH NEI changes the layout so that the recipe screen length now has a flexible
                // length and is no longer centered vertically. In order for the top-of-screen calculation to return the
                // correct result, we have to hack the height field with an inverse of the calculation using the actual
                // top of the recipe screen stored in guiTop.
                height = (2 * guiTop) + 166;

                // For reference, in case we ever need to modify width as well:
                // the legacy calculation used for width is ((width - 176) / 2), which should evaluate to be equal to
                // guiWidth (the x-value of the left edge of the NEI recipe screen). So if we wanted to override width
                // as well, we'd do this:
                // width = (2 * guiWidth) + 176;
            }

            // Recipe handlers may assume the current screen is the GuiRecipe object, which is not the case in
            // recipe tooltips drawn on the bookmarks panel with the main inventory open.
            trueGui = NEIClientUtils.mc().currentScreen;
            if (limitToOneRecipe) {
                NEIClientUtils.mc().currentScreen = GuiRecipe.this;
            }
        }

        @Override
        public void close() {
            guiTop = trueGuiTop;
            height = trueHeight;

            // Only restore currentScreen if it hasn't been altered by a recipe handler (for example through
            // Minecraft#displayGuiScreen).
            if (limitToOneRecipe && NEIClientUtils.mc().currentScreen == GuiRecipe.this) {
                NEIClientUtils.mc().currentScreen = trueGui;
            }

            isHeightHackApplied = false;
        }
    }

    @SuppressWarnings("unchecked")
    public void initGui() {
        xSize = 176;
        ySize = Math.min(Math.max(height - 68, 166), 370);
        if (!this.limitToOneRecipe) {
            super.initGui();
        } else {
            this.guiLeft = (this.width - this.xSize) / 2;
            this.ySize = this.getHeightAsWidget();
            this.mc = NEIClientUtils.mc();
            this.fontRendererObj = mc.fontRenderer;
            ScaledResolution scaledresolution = new ScaledResolution(
                    this.mc,
                    this.mc.displayWidth,
                    this.mc.displayHeight);
            this.width = scaledresolution.getScaledWidth();
            this.height = scaledresolution.getScaledHeight();
        }

        guiTop = (height - ySize) / 2 + 10;

        if (handler == null) {
            setRecipePage(recipetype);
        } else {
            initOverlayButtons();
        }

        checkYShift();

        final int rightButtonX = guiLeft + xSize - borderPadding - buttonWidth;
        final int leftButtonX = guiLeft + borderPadding;

        nexttype = new GuiNEIButton(0, leftButtonX, guiTop + 3, buttonWidth, buttonHeight, "<");
        prevtype = new GuiNEIButton(1, rightButtonX, guiTop + 3, buttonWidth, buttonHeight, ">");

        nextpage = new GuiNEIButton(2, leftButtonX, guiTop + 17, buttonWidth, buttonHeight, "<");
        prevpage = new GuiNEIButton(3, rightButtonX, guiTop + 17, buttonWidth, buttonHeight, ">");

        toggleSearch.icon = new DrawableBuilder("nei:textures/nei_sprites.png", 0, 38, 8, 8).setTextureSize(128, 128)
                .build();

        buttonList.addAll(Arrays.asList(nexttype, prevtype, nextpage, prevpage));

        if (currenthandlers.size() == 1) {
            nexttype.visible = false;
            prevtype.visible = false;
        }

        recipeTabs.initLayout();
        refreshPage();
    }

    @SuppressWarnings("unchecked")
    private void initOverlayButtons() {
        if (overlayButtons != null) {
            buttonList.removeIf(Arrays.asList(overlayButtons)::contains);
        }

        final int recipesPerPage = getRecipesPerPage();
        overlayButtons = new GuiButton[recipesPerPage];
        final int xOffset = limitToOneRecipe ? ((xSize / 2) - 2) : (width / 2);
        final int yOffset = limitToOneRecipe ? 0 : guiTop - 18;
        final String overlayKeyName = NEIClientConfig
                .getKeyName(NEIClientConfig.getKeyBinding("gui.overlay_use"), true);
        for (int i = 0; i < recipesPerPage; i++) {
            overlayButtons[i] = new GuiNEIButton(
                    OVERLAY_BUTTON_ID_START + i,
                    xOffset + 65,
                    yOffset + getRecipePosition(i).y + handlerInfo.getHeight(),
                    buttonWidth,
                    buttonHeight,
                    "?");
            if (overlayKeyName != null && limitToOneRecipe) {
                overlayButtons[i].visible = true;
                overlayButtons[i].displayString = overlayKeyName;
                overlayButtons[i].width = fontRenderer.getStringWidth(overlayKeyName) + 6;
                overlayButtons[i].xPosition = xOffset + 85 - overlayButtons[i].width;
            }
        }
        Collections.addAll(buttonList, overlayButtons);
        itemPresenceCacheRecipe = -1;
    }

    public static IRecipeFilter getRecipeListFilter(IRecipeHandler handler) {
        if (IRecipeFilterProvider.filteringAvailable(handler)) {
            LinkedList<IRecipeFilter> filters = new LinkedList<>();

            synchronized (recipeFilterers) {
                for (IRecipeFilterProvider p : recipeFilterers) {
                    IRecipeFilter filter = p.getFilter();
                    if (filter != null) {
                        filters.add(filter);
                    }
                }
            }

            if (!filters.isEmpty()) {
                return filters.size() == 1 ? filters.get(0) : new AllMultiRecipeFilter(filters);
            }
        }

        return null;
    }

    protected void updateRecipeList(IRecipeHandler h, ArrayList<Integer> filtered) {
        synchronized (handler) {
            if (handler != null && handler == h && IRecipeFilterProvider.filteringAvailable(handler)) {
                ((TemplateRecipeHandler) handler).applySearch(filtered);
                changePage(0);
            }
        }
    }

    private void checkYShift() {
        yShift = handlerInfo == null ? 0 : handlerInfo.getYShift();
    }

    public void setRecipePage(int idx) {
        setRecipePage(idx, 0);
    }

    public void setRecipePage(int idx, int recipe) {
        recipetype = (currenthandlers.size() + idx) % currenthandlers.size();

        handler = currenthandlers.get(recipetype);
        handlerInfo = GuiRecipeTab.getHandlerInfo(handler);

        if (!limitToOneRecipe) {
            searchField.setText("");
            searchField.setVisible(false);
            toggleSearch.state = 0;
        }

        page = Math.min(Math.max(0, recipe), handler.numRecipes() - 1) / getRecipesPerPage();
        changePage(0);
        recipeTabs.calcPageNumber();
        checkYShift();
        initOverlayButtons();
    }

    public List<GuiButton> getOverlayButtons() {
        return Collections.unmodifiableList(Arrays.asList(overlayButtons));
    }

    public int openTargetRecipe(BookmarkRecipeId recipeId) {
        int recipe = -1;
        int recipetype = 0;

        this.recipeId = recipeId;

        if (this.recipeId != null) {
            for (int j = 0; j < currenthandlers.size(); j++) {
                IRecipeHandler localHandler = currenthandlers.get(j);
                HandlerInfo localHandlerInfo = GuiRecipeTab.getHandlerInfo(localHandler);

                if (localHandlerInfo.getHandlerName().equals(this.recipeId.handlerName)) {

                    if (!this.recipeId.ingredients.isEmpty()) {
                        for (int i = 0; i < localHandler.numRecipes(); i++) {
                            if (this.recipeId.equalsIngredients(localHandler.getIngredientStacks(i))) {
                                recipetype = j;
                                recipe = i;
                                break;
                            }
                        }
                    }

                    break;
                }
            }
        }

        setRecipePage(recipetype, Math.max(0, recipe));

        return recipe;
    }

    public List<PositionedStack> getFocusedRecipeIngredients() {

        for (int idx : getRecipeIndices()) {
            if (recipeInFocus(idx)) {
                return handler.getIngredientStacks(idx);
            }
        }

        return null;
    }

    public int prepareFocusedRecipeResultStackSize(ItemStack stackover) {

        for (int idx : getRecipeIndices()) {
            if (recipeInFocus(idx)) {
                final PositionedStack result = handler.getResultStack(idx);
                int stackSize = 0;

                if (result != null && StackInfo.equalItemAndNBT(result.item, stackover, true)) {
                    stackSize += result.item.stackSize;
                }

                final List<PositionedStack> stacks = handler.getOtherStacks(idx);
                for (PositionedStack pStack : stacks) {
                    if (StackInfo.equalItemAndNBT(pStack.item, stackover, true)) {
                        stackSize += pStack.item.stackSize;
                    }
                }

                return stackSize;
            }
        }

        return stackover.stackSize;
    }

    protected boolean recipeInFocus(int idx) {
        final PositionedStack result = handler.getResultStack(idx);
        if (result != null && isMouseOver(result, idx)) {
            return true;
        }

        final List<PositionedStack> stacks = handler.getOtherStacks(idx);
        for (PositionedStack stack : stacks) {
            if (isMouseOver(stack, idx)) {
                return true;
            }
        }

        return false;
    }

    public String getHandlerName() {
        return handlerInfo.getHandlerName();
    }

    public H getHandler() {
        return handler;
    }

    public List<Integer> getRecipeIndices() {
        final int recipesPerPage = getRecipesPerPage();
        int minIndex = page * recipesPerPage;
        int maxIndex = Math.min(handler.numRecipes(), (page + 1) * recipesPerPage);

        return IntStream.range(minIndex, maxIndex).boxed().collect(Collectors.toList());
    }

    @Override
    public void keyTyped(char c, int i) {

        if (searchField.isVisible() && searchField.focused() && searchField.handleKeyPress(i, c)) {
            return;
        }

        if (i == Keyboard.KEY_ESCAPE) { // esc
            mc.displayGuiScreen(firstGuiGeneral);
            NEICPH.sendRequestContainer();
            return;
        }

        if (searchField.isVisible() && searchField.focused()) {
            searchField.lastKeyTyped(i, c);
            return;
        }

        if (GuiContainerManager.getManager(this).lastKeyTyped(i, c)) {
            return;
        }

        try (CompatibilityHacks compatibilityHacks = new CompatibilityHacks()) {
            for (int recipe : getRecipeIndices()) {
                if (handler.keyTyped(this, c, i, recipe)) {
                    return;
                }
            }
        }

        if (i == mc.gameSettings.keyBindInventory.getKeyCode()) {
            mc.displayGuiScreen(firstGuiGeneral);
            NEICPH.sendRequestContainer();
        } else if (NEIClientConfig.isKeyHashDown("gui.back")) {
            mc.displayGuiScreen(prevGui);
        } else if (NEIClientConfig.isKeyHashDown("gui.prev_machine")) {
            prevType();
        } else if (NEIClientConfig.isKeyHashDown("gui.next_machine")) {
            nextType();
        } else if (NEIClientConfig.isKeyHashDown("gui.prev_recipe")) {
            prevPage();
        } else if (NEIClientConfig.isKeyHashDown("gui.next_recipe")) {
            nextPage();
        }
    }

    @Override
    protected void mouseClicked(int mousex, int mousey, int button) {

        if (!limitToOneRecipe && handler != null && IRecipeFilterProvider.filteringAvailable(handler)) {
            if (toggleSearch.contains(mousex - guiLeft, mousey - guiTop)) {
                toggleSearch.handleClick(mousex - guiLeft, mousey - guiTop, button);
            } else if (searchField.contains(mousex - guiLeft, mousey - guiTop)) {
                searchField.handleClick(mousex - guiLeft, mousey - guiTop, button);
            } else {
                searchField.onGuiClick(mousex - guiLeft, mousey - guiTop);
            }

        }

        try (CompatibilityHacks compatibilityHacks = new CompatibilityHacks()) {
            for (int recipe : getRecipeIndices()) if (handler.mouseClicked(this, button, recipe)) return;
        }

        if (recipeTabs.mouseClicked(mousex, mousey, button)) return;

        super.mouseClicked(mousex, mousey, button);
    }

    @Override
    public void mouseScrolled(int i) {
        // Height hacking is not necessary here since mouse scrolling is a new feature, added in
        // GTNH NEI. So no old mods will use this. Though not hacking the height here does mean that
        // the value of the height field will be different from in other mouseover methods, which
        // could be confusing...

        // First, invoke scroll handling over recipe handler tabbar. Makes sure it is not overwritten by recipe
        // handler-specific scroll behavior.
        if (recipeTabs.mouseScrolled(i)) return;

        for (int recipe : getRecipeIndices()) if (handler.mouseScrolled(this, i, recipe)) return;

        // If shift is held, try switching to the next recipe handler. Replicates the GuiRecipeTabs.mouseScrolled()
        // without the checking for the cursor being inside the tabbar.
        if (NEIClientUtils.shiftKey()) {
            if (i < 0) {
                nextType();
            } else {
                prevType();
            }

            return;
        }

        // Finally, if nothing else has handled scrolling, try changing to the next recipe page.
        if (new Rectangle(guiLeft, guiTop, xSize, ySize).contains(GuiDraw.getMousePosition())) {
            if (i > 0) {
                prevPage();
            } else {
                nextPage();
            }
        }
    }

    @Override
    protected void actionPerformed(GuiButton guibutton) {
        super.actionPerformed(guibutton);
        switch (guibutton.id) {
            case 0:
                prevType();
                return;
            case 1:
                nextType();
                return;
            case 2:
                prevPage();
                return;
            case 3:
                nextPage();
                return;
        }

        if (overlayButtons != null && guibutton.id >= OVERLAY_BUTTON_ID_START
                && guibutton.id < OVERLAY_BUTTON_ID_START + overlayButtons.length) {
            mc.displayGuiScreen(firstGui);
            overlayRecipe(
                    page * getRecipesPerPage() + guibutton.id - OVERLAY_BUTTON_ID_START,
                    NEIClientUtils.shiftKey());
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        updateAsTooltip();
    }

    /** updateScreen() that's safe to call when this isn't the currently open GUI */
    public void updateAsTooltip() {
        handler.onUpdate();
        refreshPage();
    }

    @Override
    public List<String> handleTooltip(GuiContainer gui, int mousex, int mousey, List<String> currenttip) {
        try (CompatibilityHacks compatibilityHacks = new CompatibilityHacks()) {
            for (int i : getRecipeIndices()) currenttip = handler.handleTooltip(this, currenttip, i);
        }
        recipeTabs.handleTooltip(mousex, mousey, currenttip);

        if (currenttip.isEmpty() && searchField.isVisible()
                && new Rectangle(searchField.x + searchField.w, 15, 44, 16)
                        .contains(mousex - guiLeft, mousey - guiTop)) {

            final String s = String.format("%d/%d", page + 1, (handler.numRecipes() - 1) / getRecipesPerPage() + 1);

            if (fontRendererObj.getStringWidth(s) >= 45) {
                currenttip.add(s);
            }
        }

        return currenttip;
    }

    @Override
    public List<String> handleItemTooltip(GuiContainer gui, ItemStack stack, int mousex, int mousey,
            List<String> currenttip) {
        try (CompatibilityHacks compatibilityHacks = new CompatibilityHacks()) {
            for (int i : getRecipeIndices()) currenttip = handler.handleItemTooltip(this, stack, currenttip, i);
        }

        return currenttip;
    }

    @Override
    public List<String> handleItemDisplayName(GuiContainer gui, ItemStack itemstack, List<String> currenttip) {
        return currenttip;
    }

    private void nextPage() {
        changePage(1);
    }

    private void prevPage() {
        changePage(-1);
    }

    protected void nextType() {
        setRecipePage(++recipetype);
    }

    protected void prevType() {
        setRecipePage(--recipetype);
    }

    protected void overlayRecipe(int recipe, final boolean shift) {

        if (handler == null || !handler.hasOverlay(firstGui, firstGui.inventorySlots, recipe)) {
            return;
        }

        final boolean moveItems = shift || !NEIClientConfig.requireShiftForOverlayRecipe();
        final IRecipeOverlayRenderer renderer = handler.getOverlayRenderer(firstGui, recipe);
        final IOverlayHandler overlayHandler = handler.getOverlayHandler(firstGui, recipe);

        if (renderer == null || moveItems) {
            if (overlayHandler != null) {
                overlayHandler.overlayRecipe(firstGui, handler, recipe, moveItems);
            }
        } else {
            LayoutManager.overlayRenderer = renderer;
        }
    }

    protected void changePage(int shift) {
        final int recipesPerPage = getRecipesPerPage();
        final int numRecipes = handler != null ? handler.numRecipes() : 0;

        if (numRecipes > 0) {
            final int numPages = (int) Math.ceil(numRecipes / (float) recipesPerPage);
            this.page = Math.min(Math.max(0, this.page), numPages);
            this.page = (numPages + this.page + shift) % numPages;
        } else {
            this.page = 0;
        }

    }

    public void refreshPage() {
        RecipeCatalysts.updatePosition(ySize - BG_TOP_HEIGHT - (GuiRecipeCatalyst.fullBorder * 2));
        changePage(0);
        refreshSlots();
        final int recipesPerPage = getRecipesPerPage();
        final boolean multiplepages = handler.numRecipes() > recipesPerPage;
        final int numRecipes = Math.min(handler.numRecipes() - (page * recipesPerPage), recipesPerPage);

        area.width = handlerInfo.getWidth();
        area.height = handlerInfo.getHeight() * numRecipes;
        area.x = guiLeft - 2;
        area.y = guiTop - 4 + yShift;
        checkYShift();

        if (!limitToOneRecipe) {
            toggleSearch.w = toggleSearch.h = 12;
            toggleSearch.x = borderPadding + buttonWidth;
            toggleSearch.y = 17;

            searchField.y = 16;
            searchField.x = borderPadding + buttonWidth + toggleSearch.w - 1;
            searchField.w = xSize - (borderPadding + buttonWidth) * 2 + 1 - toggleSearch.w - 45;
            searchField.h = 14;
        }

        nextpage.enabled = prevpage.enabled = multiplepages;

        if (firstGui == null) {
            for (GuiButton overlay : overlayButtons) {
                overlay.visible = false;
            }
        } else {
            for (int i = 0; i < overlayButtons.length; i++) {
                if (i >= numRecipes) {
                    overlayButtons[i].visible = false;
                } else {
                    final int curRecipe = page * recipesPerPage + i;
                    overlayButtons[i].visible = handler.hasOverlay(firstGui, firstGui.inventorySlots, curRecipe);
                }
            }
        }

        recipeTabs.refreshPage();
    }

    private void refreshSlots() {
        slotcontainer.inventorySlots.clear();
        for (int i : getRecipeIndices()) {
            Point p = getRecipePosition(i);
            // Legacy recipe handlers only expect a single paging widget at the top of the recipe screen, in contrast,
            // GTNH NEI moves the recipe paging widget from the bottom to the top, which means said legacy handlers will
            // position item slots 16px too high in the screen.
            if (isHeightHackApplied) {
                p.translate(0, 16);
            }

            List<PositionedStack> stacks = handler.getIngredientStacks(i);
            for (PositionedStack stack : stacks) slotcontainer.addSlot(stack, p.x, p.y);

            stacks = handler.getOtherStacks(i);
            for (PositionedStack stack : stacks) slotcontainer.addSlot(stack, p.x, p.y);

            PositionedStack result = handler.getResultStack(i);
            if (result != null) slotcontainer.addSlot(result, p.x, p.y);

            if (!limitToOneRecipe) {
                List<PositionedStack> catalysts = RecipeCatalysts.getRecipeCatalysts(handler);
                for (PositionedStack catalyst : catalysts) {
                    int xOffset = -GuiRecipeCatalyst.ingredientSize + 1;
                    int yOffset = BG_TOP_Y + GuiRecipeCatalyst.fullBorder;
                    slotcontainer.addSlot(catalyst, xOffset, yOffset);
                }
            }
        }
    }

    private int itemPresenceCacheRecipe = -1;
    private ArrayList<Boolean> itemPresenceCacheSlots;

    private void updateItemPresenceCache(int recipe) {
        if (itemPresenceCacheSlots == null) {
            itemPresenceCacheSlots = new ArrayList<>();
        }
        itemPresenceCacheRecipe = recipe;
        itemPresenceCacheSlots.clear();
        List<PositionedStack> ingredients = handler.getIngredientStacks(recipe);
        ArrayList<ItemStack> invStacks = ((List<Slot>) firstGui.inventorySlots.inventorySlots).stream()
                .filter(
                        s -> s != null && s.getStack() != null
                                && s.getStack().stackSize > 0
                                && s.isItemValid(s.getStack())
                                && s.canTakeStack(mc.thePlayer))
                .map(s -> s.getStack().copy()).collect(Collectors.toCollection(ArrayList::new));
        for (PositionedStack stack : ingredients) {
            Optional<ItemStack> used = invStacks.stream().filter(is -> is.stackSize > 0 && stack.contains(is))
                    .findFirst();
            itemPresenceCacheSlots.add(used.isPresent());
            if (used.isPresent()) {
                ItemStack is = used.get();
                is.stackSize -= 1;
            }
        }
    }

    @Override
    public void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        GuiContainerManager.enable2DRender();
        final int recipesPerPage = getRecipesPerPage();
        final int ySkip = limitToOneRecipe ? 25 : 0;

        if (!limitToOneRecipe) {
            String s = handler.getRecipeName().trim();
            fontRendererObj.drawStringWithShadow(s, (xSize - fontRendererObj.getStringWidth(s)) / 2, 5, 0xffffff);

            if (IRecipeFilterProvider.filteringAvailable(handler)) {
                toggleSearch.draw(mouseX - guiLeft, mouseY - guiTop);
            }

            if (searchField.isVisible()) {
                searchField.draw(mouseX - guiLeft, mouseY - guiTop);
                s = NEIClientUtils.cropText(
                        fontRendererObj,
                        String.format("%d/%d", page + 1, (handler.numRecipes() - 1) / recipesPerPage + 1),
                        45);
                fontRendererObj.drawStringWithShadow(
                        s,
                        searchField.x + searchField.w + (44 - fontRendererObj.getStringWidth(s)) / 2,
                        19,
                        0xffffff);
            } else {
                s = NEIClientUtils.translate("recipe.page", page + 1, (handler.numRecipes() - 1) / recipesPerPage + 1);
                fontRendererObj.drawStringWithShadow(s, (xSize - fontRendererObj.getStringWidth(s)) / 2, 19, 0xffffff);
            }

        }

        final boolean drawItemPresence = NEIClientConfig.isJEIStyleItemPresenceOverlayVisible();

        GL11.glPushMatrix();
        GL11.glTranslatef(5, 32 - ySkip + yShift, 0);
        try (CompatibilityHacks compatibilityHacks = new CompatibilityHacks()) {

            for (int i : getRecipeIndices()) {
                handler.drawForeground(i);

                if (drawItemPresence && firstGui != null
                        && firstGui.inventorySlots != null
                        && (isMouseOverOverlayButton(i - page * recipesPerPage) || limitToOneRecipe)) {
                    List<PositionedStack> ingredients = handler.getIngredientStacks(i);
                    if (itemPresenceCacheRecipe != i || itemPresenceCacheSlots == null
                            || itemPresenceCacheSlots.size() != ingredients.size()) {
                        updateItemPresenceCache(i);
                    }
                    for (int j = 0; j < ingredients.size(); j++) {
                        PositionedStack stack = ingredients.get(j);
                        boolean isPresent = itemPresenceCacheSlots.get(j);
                        LayoutManager.drawItemPresenceOverlay(stack.relx, stack.rely, isPresent);
                    }
                }

                GL11.glTranslatef(0, handlerInfo.getHeight(), 0);
            }

        }
        GL11.glPopMatrix();
    }

    @Override
    public void drawGuiContainerBackgroundLayer(float f, int mouseX, int mouseY) {
        final int ySkip = limitToOneRecipe ? 25 : 0;
        GL11.glColor4f(1, 1, 1, 1);

        drawBackgroundTiled(guiLeft, guiTop);

        if (NEIClientConfig.areJEIStyleTabsVisible() && !limitToOneRecipe) {
            drawRect(
                    guiLeft + borderPadding + buttonWidth - 1,
                    nexttype.yPosition,
                    guiLeft + xSize - borderPadding - buttonWidth,
                    nexttype.yPosition + buttonHeight,
                    0x30000000);
            drawRect(
                    guiLeft + borderPadding + buttonWidth - 1,
                    nextpage.yPosition,
                    guiLeft + xSize - borderPadding - buttonWidth,
                    nextpage.yPosition + buttonHeight,
                    0x30000000);

            RenderHelper.enableGUIStandardItemLighting();
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
            recipeTabs.draw(mouseX, mouseY);
            if (NEIClientConfig.areJEIStyleRecipeCatalystsVisible()) {
                guiRecipeCatalyst.draw();
            }
            RenderHelper.disableStandardItemLighting();
        }

        GL11.glPushMatrix();
        GL11.glTranslatef(guiLeft + 5, guiTop - ySkip + 32 + yShift, 0);
        try (CompatibilityHacks compatibilityHacks = new CompatibilityHacks()) {

            for (int i : getRecipeIndices()) {
                handler.drawBackground(i);
                GL11.glTranslatef(0, handlerInfo.getHeight(), 0);
            }

        }
        GL11.glPopMatrix();
    }

    private void drawBackgroundTiled(int j, int k) {
        // Draw the top
        final int ySkip = limitToOneRecipe ? 25 : 0;
        final int handlerHeight = limitToOneRecipe ? getHeightAsWidget() : ySize;
        bgTop.draw(j, k + BG_TOP_Y);

        // Maybe Tile the middle
        final int tiledHeight = handlerHeight - ySkip - BG_TOP_HEIGHT - BG_BOTTOM_HEIGHT;

        if (tiledHeight > 0) {
            final int yTileCount = tiledHeight / BG_MIDDLE_HEIGHT;
            final int yRemainder = tiledHeight - (yTileCount * BG_MIDDLE_HEIGHT);
            final int yStart = k + BG_TOP_HEIGHT + tiledHeight;

            for (int yTile = 0; yTile <= yTileCount; yTile++) {
                final int tileHeight = (yTile == yTileCount) ? yRemainder : BG_MIDDLE_HEIGHT;
                final int y = yStart - ((yTile + 1) * BG_MIDDLE_HEIGHT);
                if (tileHeight > 0) {
                    bgMiddle.draw(j, y, BG_MIDDLE_HEIGHT - tileHeight, 0, 0, 0);
                }
            }
        }

        // Draw the bottom
        bgBottom.draw(j, k + handlerHeight - ySkip - BG_BOTTOM_HEIGHT);
    }

    public int getHeightAsWidget() {
        return (handlerInfo == null) ? ySize : (handlerInfo.getHeight() + 56);
    }

    @Override
    public GuiContainer getFirstScreen() {
        return firstGui;
    }

    @Override
    public GuiScreen getFirstScreenGeneral() {
        return firstGuiGeneral;
    }

    public boolean isMouseOver(PositionedStack stack, int recipe) {
        Slot stackSlot = slotcontainer
                .getSlotWithStack(stack, getRecipePosition(recipe).x, getRecipePosition(recipe).y);
        Point mousepos = GuiDraw.getMousePosition();
        Slot mouseoverSlot = getSlotAtPosition(mousepos.x, mousepos.y);

        return stackSlot == mouseoverSlot;
    }

    private boolean isMouseOverOverlayButton(int buttonId) {
        if (buttonId >= 0 && buttonId < overlayButtons.length) {
            GuiButton button = overlayButtons[buttonId];
            Point mousePos = GuiDraw.getMousePosition();
            return button.visible && mousePos.x >= button.xPosition
                    && mousePos.y >= button.yPosition
                    && mousePos.x < button.xPosition + button.width
                    && mousePos.y < button.yPosition + button.height;
        } else {
            return false;
        }
    }

    private int getRecipesPerPage() {
        return limitToOneRecipe ? 1 : getRecipesPerPage(handlerInfo);
    }

    private int getRecipesPerPage(HandlerInfo handlerInfo) {
        if (handlerInfo != null) {
            return Math.max(
                    Math.min(
                            ((ySize - (buttonHeight * 3)) / handlerInfo.getHeight()),
                            handlerInfo.getMaxRecipesPerPage()),
                    1);
        } else if (handler != null) {
            return handler.recipiesPerPage();
        }
        return 1;
    }

    public Point getRecipePosition(int recipe) {
        // Legacy recipe handlers using the height hack might use getRecipePosition in combination with guiTop/height to
        // position certain elements like tooltips. Since guiTop is moved down by 16px during height hacking, we need to
        // reduce the vertical shift here to 16px instead of 32px.
        return new Point(
                5,
                (isHeightHackApplied ? 16 : 32) - (limitToOneRecipe ? 25 : 0)
                        + yShift
                        + ((recipe % getRecipesPerPage()) * handlerInfo.getHeight()));
    }

    public abstract ArrayList<H> getCurrentRecipeHandlers();

    @Override
    public VisiblityData modifyVisiblity(GuiContainer gui, VisiblityData currentVisibility) {
        return currentVisibility;
    }

    @Override
    public Iterable<Integer> getItemSpawnSlots(GuiContainer gui, ItemStack item) {
        return Collections.emptyList();
    }

    @Override
    public List<TaggedInventoryArea> getInventoryAreas(GuiContainer gui) {
        return null;
    }

    @Override
    public boolean handleDragNDrop(GuiContainer gui, int mousex, int mousey, ItemStack draggedStack, int button) {

        if (searchField.isVisible() && searchField.contains(mousex - guiLeft, mousey - guiTop)) {
            final FluidStack fluidStack = StackInfo.getFluid(draggedStack);

            if (fluidStack != null) {
                searchField.setText(formattingText(fluidStack.getLocalizedName()));
            } else {
                searchField.setText(formattingText(draggedStack.getDisplayName()));
            }

            return true;
        }

        return false;
    }

    protected String formattingText(String displayName) {
        return Pattern.compile("[{}()\\[\\].+*?^$\\\\|]")
                .matcher(EnumChatFormatting.getTextWithoutFormattingCodes(displayName)).replaceAll("\\\\$0");
    }

    @Override
    public boolean hideItemPanelSlot(GuiContainer gui, int x, int y, int w, int h) {
        // Because some of the handlers *cough avaritia* are oversized
        return area.intersects(x, y, w, h);
    }

    protected static BookmarkRecipeId getCurrentRecipe(GuiScreen gui) {

        if (gui instanceof GuiRecipe && !((GuiRecipe<?>) gui).handler.isEmpty()) {
            GuiRecipe<?> gRecipe = (GuiRecipe<?>) gui;
            return new BookmarkRecipeId(
                    gRecipe.handlerInfo.getHandlerName(),
                    gRecipe.handler.getIngredientStacks(gRecipe.page * gRecipe.getRecipesPerPage()));
        }

        return null;
    }
}
