package codechicken.nei.recipe;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.Button;
import codechicken.nei.GuiNEIButton;
import codechicken.nei.NEICPH;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.PositionedStack;
import codechicken.nei.RecipeSearchField;
import codechicken.nei.RestartableTask;
import codechicken.nei.SearchField;
import codechicken.nei.VisiblityData;
import codechicken.nei.api.IGuiContainerOverlay;
import codechicken.nei.api.INEIGuiHandler;
import codechicken.nei.api.IRecipeFilter;
import codechicken.nei.api.IRecipeFilter.IRecipeFilterProvider;
import codechicken.nei.api.ItemFilter;
import codechicken.nei.api.TaggedInventoryArea;
import codechicken.nei.drawable.DrawableBuilder;
import codechicken.nei.drawable.DrawableResource;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.guihook.IContainerTooltipHandler;
import codechicken.nei.guihook.IGuiClientSide;
import codechicken.nei.guihook.IGuiHandleMouseWheel;
import codechicken.nei.recipe.GuiRecipeButton.UpdateRecipeButtonsEvent;
import codechicken.nei.recipe.Recipe.RecipeId;
import codechicken.nei.util.NEIMouseUtils;

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
    private static final int BORDER_PADDING = 6;
    private static final int BUTTON_WIDTH = 13;
    private static final int BUTTON_HEIGHT = 12;

    public static final List<IRecipeFilterProvider> recipeFilterers = new LinkedList<>();

    protected boolean limitToOneRecipe = false;
    protected AcceptsFollowingTooltipLineHandler acceptsFollowingTooltipLineHandler;

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

    private RecipeId recipeId;
    public ContainerRecipe slotcontainer;
    public GuiContainer firstGui;
    public GuiScreen firstGuiGeneral;
    public GuiScreen prevGui;
    public GuiButton nextpage;
    public GuiButton prevpage;
    private GuiButton nexttype;
    private GuiButton prevtype;

    // some mods reflect this property
    @Deprecated
    private GuiOverlayButton[] overlayButtons = new GuiOverlayButton[0];

    private final List<GuiRecipeButton> recipeButtons = new ArrayList<>();
    private String recipePageCacheKey = "";

    private final Rectangle area = new Rectangle();
    private final GuiRecipeTabs recipeTabs;
    private final GuiRecipeCatalyst guiRecipeCatalyst;
    private SearchRecipeHandler<H> handler;
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

        public AllMultiRecipeFilter(IRecipeFilter filters) {
            this(Arrays.asList(filters));
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
                final SearchRecipeHandler<?> searchHandler = guiRecipe.handler;

                if (searchHandler != null && searchHandler.searchingAvailable()) {

                    if (GuiRecipe.searchField.text().isEmpty()) {
                        searchHandler.setSearchIndices(null);
                        guiRecipe.changePage(0);
                    } else {
                        final IRecipeFilter filter = new ItemRecipeFilter(GuiRecipe.searchField.getFilter());
                        final List<Integer> filtered = searchHandler.getSearchResult(filter);

                        if (filtered == null) {
                            stop();
                        }

                        if (interrupted()) return;
                        searchHandler.setSearchIndices(filtered);
                        guiRecipe.changePage(0);
                    }

                }

            }

        }

    };

    protected static final RecipeSearchField searchField = new RecipeSearchField("") {

        @Override
        protected boolean noResults() {
            GuiScreen currentScreen = NEIClientUtils.mc().currentScreen;
            return !(currentScreen instanceof GuiRecipe) || ((GuiRecipe<?>) currentScreen).numRecipes() > 0;
        }

        @Override
        public void onTextChange(String oldText) {
            updateFilter.restart();
        }

    };

    protected static final Button toggleSearch = new Button() {

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

        if (prevgui instanceof IGuiContainerOverlay gui) {
            this.firstGui = gui.getFirstScreen();
            this.firstGuiGeneral = gui.getFirstScreenGeneral();
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
                    .map(pattern -> pattern.matcher(handler.original.getHandlerId())).anyMatch(Matcher::matches);
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

    public void initGui() {
        xSize = 176;
        ySize = Math.min(Math.max(height - 68, 166), 370);
        if (!this.limitToOneRecipe) {
            super.initGui();
        } else {
            this.guiLeft = (this.width - this.xSize) / 2;
            this.ySize = this.getWidgetSize().height;
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
        }

        checkYShift();

        final int rightButtonX = guiLeft + xSize - BORDER_PADDING - BUTTON_WIDTH;
        final int leftButtonX = guiLeft + BORDER_PADDING;

        nexttype = new GuiNEIButton(0, leftButtonX, guiTop + 3, BUTTON_WIDTH, BUTTON_HEIGHT, "<") {

            @Override
            public void mouseReleased(int mouseX, int mouseY) {
                prevType();
            }
        };
        prevtype = new GuiNEIButton(1, rightButtonX, guiTop + 3, BUTTON_WIDTH, BUTTON_HEIGHT, ">") {

            @Override
            public void mouseReleased(int mouseX, int mouseY) {
                nextType();
            }
        };

        nextpage = new GuiNEIButton(2, leftButtonX, guiTop + 17, BUTTON_WIDTH, BUTTON_HEIGHT, "<") {

            @Override
            public void mouseReleased(int mouseX, int mouseY) {
                prevPage();
            }
        };
        prevpage = new GuiNEIButton(3, rightButtonX, guiTop + 17, BUTTON_WIDTH, BUTTON_HEIGHT, ">") {

            @Override
            public void mouseReleased(int mouseX, int mouseY) {
                nextPage();
            }
        };

        toggleSearch.icon = new DrawableBuilder("nei:textures/nei_sprites.png", 0, 76, 10, 10).build();

        this.buttonList.addAll(Arrays.asList(nexttype, prevtype, nextpage, prevpage));
        this.recipeButtons.clear();
        this.recipePageCacheKey = "";

        if (currenthandlers.size() == 1) {
            nexttype.visible = false;
            prevtype.visible = false;
        }

        recipeTabs.initLayout();
        refreshPage();
    }

    private void updateRecipePage() {
        final List<Integer> indices = getRecipeIndices();
        final String cacheKey = indices.isEmpty() ? ""
                : handlerInfo.getHandlerName() + ":" + indices.get(0) + ":" + indices.get(indices.size() - 1);

        if (!cacheKey.equals(this.recipePageCacheKey)) {
            this.recipePageCacheKey = cacheKey;

            for (int refIndex = 0; refIndex < indices.size(); refIndex++) {
                final int recipeIndex = indices.get(refIndex);
                for (PositionedStack pStack : handler.original.getIngredientStacks(recipeIndex)) {
                    pStack.setPermutationToRender(pStack.getFilteredPermutations().get(0));
                }
            }

            final int xOffset = (limitToOneRecipe ? 0 : guiLeft) + xSize - BORDER_PADDING;
            final int yOffset = (limitToOneRecipe ? -BUTTON_HEIGHT : guiTop - 18) + getRefIndexPosition(0).y;
            final int height = handlerInfo.getHeight() - yShift;
            final boolean showFavorites = NEIClientConfig.favoritesEnabled();
            final UpdateRecipeButtonsEvent.Pre preEvent = new UpdateRecipeButtonsEvent.Pre(
                    this,
                    xOffset,
                    yOffset,
                    height,
                    handlerInfo);
            List<GuiRecipeButton> buttons = new ArrayList<>();

            if (MinecraftForge.EVENT_BUS.post(preEvent)) {
                buttons = preEvent.buttonList;
            } else {

                for (int refIndex = 0; refIndex < indices.size(); refIndex++) {
                    final int recipeIndex = indices.get(refIndex);

                    if (handler.original.getResultStack(recipeIndex) == null
                            && handler.original.getOtherStacks(recipeIndex).isEmpty()) {
                        continue;
                    }

                    GuiRecipeButton button = new GuiOverlayButton(
                            firstGui,
                            RecipeHandlerRef.of(handler.original, recipeIndex),
                            xOffset - GuiRecipeButton.BUTTON_WIDTH,
                            yOffset + height * (refIndex + 1));

                    buttons.add(button);

                    if (showFavorites) {
                        buttons.add(
                                new GuiFavoriteButton(
                                        RecipeHandlerRef.of(handler.original, recipeIndex),
                                        button.xPosition,
                                        button.yPosition - button.height - 1));
                    }

                }

                if (this.equals(this.mc.currentScreen)) {
                    UpdateRecipeButtonsEvent.Post postEvent = new UpdateRecipeButtonsEvent.Post(this, buttons);
                    MinecraftForge.EVENT_BUS.post(postEvent);
                    buttons = postEvent.buttonList;
                }
            }

            if (!showFavorites) {
                buttons.removeIf(GuiFavoriteButton.class::isInstance);
            }

            /** compatibility with old format */
            this.overlayButtons = buttons.stream().filter(GuiOverlayButton.class::isInstance)
                    .toArray(GuiOverlayButton[]::new);

            this.buttonList.removeIf(this.recipeButtons::contains);
            this.recipeButtons.clear();

            this.recipeButtons.addAll(buttons);
            this.buttonList.addAll(buttons);
        }

    }

    public static IRecipeFilter getRecipeListFilter() {
        if (recipeFilterers.isEmpty()) {
            return null;
        }

        final AllMultiRecipeFilter recipeFilter = new AllMultiRecipeFilter();

        synchronized (recipeFilterers) {
            for (IRecipeFilterProvider p : recipeFilterers) {
                IRecipeFilter filter = p.getFilter();
                if (filter != null) {
                    recipeFilter.filters.add(filter);
                }
            }
        }

        return recipeFilter.filters.size() == 1 ? recipeFilter.filters.get(0) : recipeFilter;
    }

    public static ItemFilter getSearchItemFilter() {
        return searchField.getFilter();
    }

    private void checkYShift() {
        yShift = handlerInfo == null ? 0 : handlerInfo.getYShift();
    }

    public void setRecipePage(int idx) {
        setRecipePage(idx, 0);
    }

    public void setRecipePage(int idx, int refIndex) {
        recipetype = (currenthandlers.size() + idx) % currenthandlers.size();

        handler = new SearchRecipeHandler<>(currenthandlers.get(recipetype));
        handlerInfo = GuiRecipeTab.getHandlerInfo(handler.original);

        if (!limitToOneRecipe) {
            searchField.setText("");
            searchField.setVisible(false);
            toggleSearch.state = 0;
        }

        page = Math.min(Math.max(0, refIndex), this.numRecipes() - 1) / getRecipesPerPage();
        changePage(0);
        recipeTabs.calcPageNumber();
        checkYShift();
    }

    public List<GuiRecipeButton> getRecipeButtons() {
        return Collections.unmodifiableList(this.recipeButtons);
    }

    public List<GuiButton> getOverlayButtons() {
        return Collections.unmodifiableList(
                Arrays.asList(
                        this.recipeButtons.stream().filter(GuiOverlayButton.class::isInstance)
                                .toArray(GuiButton[]::new)));
    }

    public int openTargetRecipe(RecipeId recipeId) {
        int refIndex = -1;
        int recipetype = 0;

        this.recipeId = recipeId;

        if (this.recipeId != null) {
            for (int j = 0; j < currenthandlers.size(); j++) {
                H localHandler = currenthandlers.get(j);
                HandlerInfo localHandlerInfo = GuiRecipeTab.getHandlerInfo(localHandler);

                if (localHandlerInfo.getHandlerName().equals(this.recipeId.getHandleName())) {
                    recipetype = j;

                    if (!this.recipeId.getIngredients().isEmpty()) {
                        refIndex = SearchRecipeHandler.findFirst(
                                localHandler,
                                recipeIndex -> this.recipeId
                                        .equalsIngredients(localHandler.getIngredientStacks(recipeIndex)));
                    }

                    break;
                }
            }
        }

        setRecipePage(recipetype, Math.max(0, refIndex));

        return refIndex;
    }

    public Recipe getFocusedRecipe() {
        final List<Integer> indices = getRecipeIndices();

        for (int refIndex = 0; refIndex < indices.size(); refIndex++) {
            final int recipeIndex = indices.get(refIndex);
            if (recipeInFocus(refIndex, recipeIndex)) {
                return Recipe.of(handler.original, recipeIndex);
            }
        }

        return null;
    }

    protected boolean recipeInFocus(int refIndex, int recipeIndex) {
        final PositionedStack result = handler.original.getResultStack(recipeIndex);
        if (result != null && isMouseOver(result, refIndex)) {
            return true;
        }

        final List<PositionedStack> stacks = handler.original.getOtherStacks(recipeIndex);
        for (PositionedStack stack : stacks) {
            if (isMouseOver(stack, refIndex)) {
                return true;
            }
        }

        return false;
    }

    private boolean isMouseOver(PositionedStack stack, int refIndex) {
        Point p = getRefIndexPosition(refIndex);
        Point mousepos = GuiDraw.getMousePosition();
        Slot stackSlot = slotcontainer.getSlotWithStack(stack, p.x, p.y);
        Slot mouseoverSlot = getSlotAtPosition(mousepos.x, mousepos.y);

        return stackSlot == mouseoverSlot;
    }

    public String getHandlerName() {
        return handlerInfo.getHandlerName();
    }

    public H getHandler() {
        return handler.original;
    }

    public List<Integer> getRecipeIndices() {
        final int recipesPerPage = getRecipesPerPage();
        final int minIndex = page * recipesPerPage;
        final int maxIndex = Math.min(this.numRecipes(), (page + 1) * recipesPerPage);
        final List<Integer> range = new ArrayList<>();

        for (int index = minIndex; index < maxIndex; index++) {
            range.add(handler.ref(index));
        }

        return range;
    }

    private int numRecipes() {
        return handler == null ? 0 : handler.numRecipes();
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
            for (int recipeIndex : getRecipeIndices()) {
                if (handler.original.keyTyped(this, c, i, recipeIndex)) {
                    return;
                }
            }

            Point mouse = GuiDraw.getMousePosition();

            for (GuiRecipeButton button : this.recipeButtons) {
                if (button.contains(mouse.x, mouse.y)) {
                    button.lastKeyTyped(this, c, i);
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

        if (!limitToOneRecipe && handler != null && handler.searchingAvailable()) {
            if (toggleSearch.contains(mousex - guiLeft, mousey - guiTop)) {
                toggleSearch.handleClick(mousex - guiLeft, mousey - guiTop, button);
            } else if (searchField.contains(mousex - guiLeft, mousey - guiTop)) {
                searchField.handleClick(mousex - guiLeft, mousey - guiTop, button);
            } else {
                searchField.onGuiClick(mousex - guiLeft, mousey - guiTop);
            }
        }

        try (CompatibilityHacks compatibilityHacks = new CompatibilityHacks()) {
            for (int recipeIndex : getRecipeIndices()) {
                if (handler.original.mouseClicked(this, button, recipeIndex)) {
                    return;
                }
            }
        }

        if (recipeTabs.mouseClicked(mousex, mousey, button)) {
            return;
        }

        super.mouseClicked(mousex, mousey, button);
    }

    @Override
    public void mouseScrolled(int scroll) {
        // Height hacking is not necessary here since mouse scrolling is a new feature, added in
        // GTNH NEI. So no old mods will use this. Though not hacking the height here does mean that
        // the value of the height field will be different from in other mouseover methods, which
        // could be confusing...

        // First, invoke scroll handling over recipe handler tabbar. Makes sure it is not overwritten by recipe
        // handler-specific scroll behavior.
        if (recipeTabs.mouseScrolled(scroll)) return;

        try (CompatibilityHacks compatibilityHacks = new CompatibilityHacks()) {
            Point mouse = GuiDraw.getMousePosition();

            for (GuiRecipeButton button : this.recipeButtons) {
                if (button.contains(mouse.x, mouse.y) && button.mouseScrolled(this, scroll)) {
                    return;
                }
            }

            for (int recipeIndex : getRecipeIndices()) {
                if (handler.original.mouseScrolled(this, scroll, recipeIndex)) {
                    return;
                }
            }
        }

        // If shift is held, try switching to the next recipe handler. Replicates the GuiRecipeTabs.mouseScrolled()
        // without the checking for the cursor being inside the tabbar.
        if (NEIClientUtils.shiftKey()) {
            if (scroll < 0) {
                nextType();
            } else {
                prevType();
            }

            return;
        }

        // Finally, if nothing else has handled scrolling, try changing to the next recipe page.
        if (new Rectangle(guiLeft, guiTop, xSize, ySize).contains(GuiDraw.getMousePosition())) {
            if (scroll > 0) {
                prevPage();
            } else {
                nextPage();
            }
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        onUpdate();
    }

    public void onUpdate() {
        handler.original.onUpdate();

        if (limitToOneRecipe) {
            refreshSlots();
        } else {
            refreshPage();
        }
    }

    @Override
    public List<String> handleTooltip(GuiContainer gui, int mousex, int mousey, List<String> currenttip) {

        try (CompatibilityHacks compatibilityHacks = new CompatibilityHacks()) {
            for (int recipeIndex : getRecipeIndices()) {
                currenttip = handler.original.handleTooltip(this, currenttip, recipeIndex);
            }

            for (GuiRecipeButton button : this.recipeButtons) {
                if (button.contains(mousex, mousey)) {
                    currenttip = button.handleTooltip(this, currenttip);
                }
            }
        }

        recipeTabs.handleTooltip(mousex, mousey, currenttip);

        if (currenttip.isEmpty() && searchField.isVisible()
                && new Rectangle(searchField.x + searchField.w, 15, 44, 16)
                        .contains(mousex - guiLeft, mousey - guiTop)) {

            final String s = String.format("%d/%d", page + 1, (this.numRecipes() - 1) / getRecipesPerPage() + 1);

            if (fontRendererObj.getStringWidth(s) >= 45) {
                currenttip.add(s);
            }
        }

        return currenttip;
    }

    @Override
    public List<String> handleItemTooltip(GuiContainer gui, ItemStack itemstack, int mousex, int mousey,
            List<String> currenttip) {

        List<Integer> indices = getRecipeIndices();

        try (CompatibilityHacks compatibilityHacks = new CompatibilityHacks()) {
            for (int recipeIndex : indices) {
                currenttip = handler.original.handleItemTooltip(this, itemstack, currenttip, recipeIndex);
            }
        }

        if (NEIClientConfig.showCycledIngredientsTooltip() && itemstack != null) {
            PositionedStack hovered = null;

            for (int refIndex = 0; refIndex < indices.size() && hovered == null; refIndex++) {
                final int recipeIndex = indices.get(refIndex);
                final List<PositionedStack> stacks = handler.original.getIngredientStacks(recipeIndex);

                for (PositionedStack pStack : stacks) {
                    if (isMouseOver(pStack, refIndex)) {
                        hovered = pStack;
                        break;
                    }
                }
            }

            if (hovered == null || hovered.items.length <= 1) {
                this.acceptsFollowingTooltipLineHandler = null;
            } else if (this.acceptsFollowingTooltipLineHandler == null
                    || this.acceptsFollowingTooltipLineHandler.tooltipGUID != hovered) {
                        this.acceptsFollowingTooltipLineHandler = AcceptsFollowingTooltipLineHandler
                                .of(hovered, hovered.getFilteredPermutations(), hovered.item);
                    }
        } else if (this.acceptsFollowingTooltipLineHandler != null) {
            this.acceptsFollowingTooltipLineHandler = null;
        }

        if (this.acceptsFollowingTooltipLineHandler != null) {
            this.acceptsFollowingTooltipLineHandler
                    .setActiveStack(((PositionedStack) this.acceptsFollowingTooltipLineHandler.tooltipGUID).item);
            currenttip.add(GuiDraw.TOOLTIP_HANDLER + GuiDraw.getTipLineId(this.acceptsFollowingTooltipLineHandler));
        }

        return currenttip;
    }

    @Override
    public Map<String, String> handleHotkeys(GuiContainer gui, int mousex, int mousey, Map<String, String> hotkeys) {

        try (CompatibilityHacks compatibilityHacks = new CompatibilityHacks()) {
            for (GuiRecipeButton button : this.recipeButtons) {
                if (button.contains(mousex, mousey)) {
                    hotkeys = button.handleHotkeys(gui, mousex, mousey, hotkeys);
                }
            }
        }

        if (this.acceptsFollowingTooltipLineHandler != null) {
            hotkeys.put(
                    NEIClientUtils.getKeyName(
                            NEIClientUtils.SHIFT_HASH,
                            NEIMouseUtils.MOUSE_BTN_NONE + NEIMouseUtils.MOUSE_SCROLL),
                    NEIClientUtils.translate("recipe.accepts.scroll"));
        }

        return hotkeys;
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

    protected void changePage(int shift) {
        final int recipesPerPage = getRecipesPerPage();
        final int numRecipes = this.numRecipes();

        if (numRecipes > 0) {
            final int numPages = (int) Math.ceil(numRecipes / (float) recipesPerPage);
            this.page = Math.min(Math.max(0, this.page), numPages);
            this.page = (numPages + this.page + shift) % numPages;
        } else {
            this.page = 0;
        }
    }

    public void refreshPage() {
        changePage(0);
        final int recipesPerPage = getRecipesPerPage();
        final boolean multiplepages = this.numRecipes() > recipesPerPage;
        final int numRecipes = Math.min(this.numRecipes() - (page * recipesPerPage), recipesPerPage);

        area.width = handlerInfo.getWidth();
        area.height = handlerInfo.getHeight() * numRecipes;
        area.x = guiLeft - 2;
        area.y = guiTop - 4 + yShift;
        checkYShift();

        if (!limitToOneRecipe) {
            RecipeCatalysts.updatePosition(ySize - BG_TOP_HEIGHT - (GuiRecipeCatalyst.fullBorder * 2));
            toggleSearch.w = toggleSearch.h = 12;
            toggleSearch.x = BORDER_PADDING + BUTTON_WIDTH;
            toggleSearch.y = 17;

            searchField.y = 16;
            searchField.x = BORDER_PADDING + BUTTON_WIDTH + toggleSearch.w - 1;
            searchField.w = xSize - (BORDER_PADDING + BUTTON_WIDTH) * 2 + 1 - toggleSearch.w - 45;
            searchField.h = 14;
        }

        nextpage.enabled = prevpage.enabled = multiplepages;

        updateRecipePage();
        refreshSlots();
        recipeTabs.refreshPage();
    }

    private void refreshSlots() {
        slotcontainer.inventorySlots.clear();
        final List<Integer> indices = getRecipeIndices();

        for (int refIndex = 0; refIndex < indices.size(); refIndex++) {
            int recipeIndex = indices.get(refIndex);
            Point p = getRefIndexPosition(refIndex);
            // Legacy recipe handlers only expect a single paging widget at the top of the recipe screen, in contrast,
            // GTNH NEI moves the recipe paging widget from the bottom to the top, which means said legacy handlers will
            // position item slots 16px too high in the screen.
            if (isHeightHackApplied) {
                p.translate(0, 16);
            }

            TemplateRecipeHandler.disableCycledIngredients = false;

            for (PositionedStack stack : handler.original.getIngredientStacks(recipeIndex)) {
                slotcontainer.addSlot(stack, p.x, p.y);
            }

            for (PositionedStack stack : handler.original.getOtherStacks(recipeIndex)) {
                slotcontainer.addSlot(stack, p.x, p.y);
            }

            PositionedStack result = handler.original.getResultStack(recipeIndex);
            if (result != null) {
                slotcontainer.addSlot(result, p.x, p.y);
            }

            if (!limitToOneRecipe) {
                for (PositionedStack catalyst : RecipeCatalysts.getRecipeCatalysts(handler.original)) {
                    int xOffset = -GuiRecipeCatalyst.ingredientSize + 1;
                    int yOffset = BG_TOP_Y + GuiRecipeCatalyst.fullBorder;
                    slotcontainer.addSlot(catalyst, xOffset, yOffset);
                }
            }

            TemplateRecipeHandler.disableCycledIngredients = true;
        }
    }

    @Override
    public void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        GuiContainerManager.enable2DRender();
        final int recipesPerPage = getRecipesPerPage();
        final int ySkip = limitToOneRecipe ? BG_TOP_HEIGHT + 1 : 32;

        if (!limitToOneRecipe) {
            String s = handler.original.getRecipeName().trim();
            fontRendererObj.drawStringWithShadow(s, (xSize - fontRendererObj.getStringWidth(s)) / 2, 5, 0xffffff);

            if (handler.searchingAvailable()) {
                toggleSearch.draw(mouseX - guiLeft, mouseY - guiTop);
            }

            if (searchField.isVisible()) {
                searchField.draw(mouseX - guiLeft, mouseY - guiTop);
                s = NEIClientUtils.cropText(
                        fontRendererObj,
                        String.format("%d/%d", page + 1, (this.numRecipes() - 1) / recipesPerPage + 1),
                        45);
                fontRendererObj.drawStringWithShadow(
                        s,
                        searchField.x + searchField.w + (44 - fontRendererObj.getStringWidth(s)) / 2,
                        19,
                        0xffffff);
            } else {
                s = NEIClientUtils.translate("recipe.page", page + 1, (this.numRecipes() - 1) / recipesPerPage + 1);
                fontRendererObj.drawStringWithShadow(s, (xSize - fontRendererObj.getStringWidth(s)) / 2, 19, 0xffffff);
            }

        }

        try (CompatibilityHacks compatibilityHacks = new CompatibilityHacks()) {
            List<Integer> indices = getRecipeIndices();

            GL11.glPushMatrix();
            GL11.glTranslatef(5, ySkip + yShift, 0);

            for (int refIndex = 0; refIndex < indices.size(); refIndex++) {
                int recipeIndex = indices.get(refIndex);

                handler.original.drawForeground(recipeIndex);

                if (limitToOneRecipe) {
                    GuiOverlayButton overlayButton = (GuiOverlayButton) this.recipeButtons.stream()
                            .filter(b -> b instanceof GuiOverlayButton && b.handlerRef.recipeIndex == recipeIndex)
                            .findAny().orElse(null);

                    if (overlayButton != null && overlayButton.enabled) {
                        overlayButton.drawItemOverlay();
                    }
                }

                GL11.glTranslatef(0, handlerInfo.getHeight(), 0);
            }

            GL11.glPopMatrix();

            GL11.glPushMatrix();
            GL11.glTranslatef(5, ySkip + yShift, 0);

            if (!limitToOneRecipe) {
                for (GuiRecipeButton button : this.recipeButtons) {
                    if (button.contains(mouseX, mouseY)) {
                        GL11.glTranslatef(
                                0,
                                handlerInfo.getHeight() * indices.indexOf(button.handlerRef.recipeIndex),
                                0);
                        button.drawItemOverlay();
                        break;
                    }
                }
            }

            GL11.glPopMatrix();
        }

    }

    @Override
    public void drawGuiContainerBackgroundLayer(float f, int mouseX, int mouseY) {
        final int ySkip = limitToOneRecipe ? BG_TOP_HEIGHT + 1 : 32;
        GL11.glColor4f(1, 1, 1, 1);

        drawBackgroundTiled(guiLeft, guiTop);

        if (NEIClientConfig.areJEIStyleTabsVisible() && !limitToOneRecipe) {
            drawRect(
                    guiLeft + BORDER_PADDING + BUTTON_WIDTH - 1,
                    nexttype.yPosition,
                    guiLeft + xSize - BORDER_PADDING - BUTTON_WIDTH,
                    nexttype.yPosition + BUTTON_HEIGHT,
                    0x30000000);
            drawRect(
                    guiLeft + BORDER_PADDING + BUTTON_WIDTH - 1,
                    nextpage.yPosition,
                    guiLeft + xSize - BORDER_PADDING - BUTTON_WIDTH,
                    nextpage.yPosition + BUTTON_HEIGHT,
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
        GL11.glTranslatef(guiLeft + 5, guiTop + ySkip + yShift, 0);
        try (CompatibilityHacks compatibilityHacks = new CompatibilityHacks()) {

            for (int recipeIndex : getRecipeIndices()) {
                handler.original.drawBackground(recipeIndex);
                GL11.glTranslatef(0, handlerInfo.getHeight(), 0);
            }

        }
        GL11.glPopMatrix();
    }

    private void drawBackgroundTiled(int j, int k) {
        final int handlerHeight = getWidgetSize().height;

        // Draw the top
        bgTop.draw(j, k + BG_TOP_Y);

        // Maybe Tile the middle
        final int tiledHeight = handlerHeight - BG_TOP_HEIGHT - BG_BOTTOM_HEIGHT;

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
        bgBottom.draw(j, k + handlerHeight - BG_BOTTOM_HEIGHT);
    }

    public Dimension getWidgetSize() {

        if (handlerInfo == null) {
            return new Dimension(xSize, ySize);
        }

        final int handlerHeight = limitToOneRecipe ? handlerInfo.getHeight() + yShift + BG_TOP_HEIGHT + BG_BOTTOM_HEIGHT
                : ySize;
        return new Dimension(xSize, handlerHeight);
    }

    @Override
    public GuiContainer getFirstScreen() {
        return firstGui;
    }

    @Override
    public GuiScreen getFirstScreenGeneral() {
        return firstGuiGeneral;
    }

    private int getRecipesPerPage() {
        return limitToOneRecipe ? 1 : getRecipesPerPage(handlerInfo);
    }

    private int getRecipesPerPage(HandlerInfo handlerInfo) {
        if (handlerInfo != null) {
            return Math.max(
                    Math.min(
                            ((ySize - (BUTTON_HEIGHT * 3)) / handlerInfo.getHeight()),
                            handlerInfo.getMaxRecipesPerPage()),
                    1);
        } else if (handler != null) {
            return handler.original.recipiesPerPage();
        }
        return 1;
    }

    public Point getRecipePosition(int recipe) {
        return getRefIndexPosition(getRecipeIndices().indexOf(recipe));
    }

    protected Point getRefIndexPosition(int refIndex) {
        // Legacy recipe handlers using the height hack might use getRefIndexPosition in combination with guiTop/height
        // to position certain elements like tooltips. Since guiTop is moved down by 16px during height hacking, we need
        // to reduce the vertical shift here to 16px instead of 32px.
        return new Point(
                5,
                (isHeightHackApplied ? 16 : 32) - (limitToOneRecipe ? 25 : 0)
                        + yShift
                        + ((refIndex % getRecipesPerPage()) * handlerInfo.getHeight()));
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
            searchField.setText(SearchField.getEscapedSearchText(draggedStack));
            return true;
        }

        return false;
    }

    @Override
    public boolean hideItemPanelSlot(GuiContainer gui, int x, int y, int w, int h) {
        // Because some of the handlers *cough avaritia* are oversized
        return area.intersects(x, y, w, h);
    }

    protected static RecipeId getCurrentRecipeId(GuiScreen gui) {

        if ((gui instanceof GuiRecipe<?>gRecipe) && gRecipe.handler.numRecipes() > 0) {
            final List<Integer> indices = gRecipe.getRecipeIndices();
            final int curRecipe = indices.isEmpty() ? 0 : indices.get(0);
            final Recipe recipe = Recipe.of(gRecipe.handler.original, curRecipe);

            return recipe != null ? recipe.getRecipeId() : null;
        }

        return null;
    }
}
