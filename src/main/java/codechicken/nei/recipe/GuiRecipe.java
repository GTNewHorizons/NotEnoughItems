package codechicken.nei.recipe;

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
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import codechicken.lib.gui.GuiDraw;
import codechicken.lib.vec.Rectangle4i;
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
import codechicken.nei.Widget;
import codechicken.nei.api.IGuiContainerOverlay;
import codechicken.nei.api.INEIGuiHandler;
import codechicken.nei.api.IRecipeFilter;
import codechicken.nei.api.IRecipeFilter.IRecipeFilterProvider;
import codechicken.nei.api.ItemFilter;
import codechicken.nei.api.TaggedInventoryArea;
import codechicken.nei.drawable.DrawableBuilder;
import codechicken.nei.drawable.DrawableResource;
import codechicken.nei.filter.AllMultiRecipeFilter;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.guihook.IContainerTooltipHandler;
import codechicken.nei.guihook.IGuiClientSide;
import codechicken.nei.guihook.IGuiHandleMouseWheel;
import codechicken.nei.recipe.Recipe.RecipeId;
import codechicken.nei.scroll.ScrollBar;
import codechicken.nei.scroll.ScrollBar.OverflowType;
import codechicken.nei.scroll.ScrollBar.ScrollPlace;
import codechicken.nei.scroll.ScrollContainer;
import codechicken.nei.util.SlotInaccessible;

public abstract class GuiRecipe<H extends IRecipeHandler> extends GuiContainer implements IGuiContainerOverlay,
        IGuiClientSide, IGuiHandleMouseWheel, IContainerTooltipHandler, INEIGuiHandler {

    private static final int BORDER_PADDING = 5;
    private final DrawableResource BG_TEXTURE = new DrawableBuilder("nei:textures/gui/recipebg.png", 0, 0, 176, 166)
            .build();

    private static final int BUTTON_WIDTH = 13;
    private static final int BUTTON_HEIGHT = 12;

    protected static final ScrollBar VERTICAL_SCROLLBAR = new ScrollBar().setScrollbarSize(14).setMarginStart(-6)
            .setOverflowType(OverflowType.AUTO).setScrollPlace(ScrollPlace.END)
            .setTrackTexture(new DrawableBuilder("nei:textures/nei_sprites.png", 42, 90, 13, 21).build(), 9, 9)
            .setThumbTexture(new DrawableBuilder("nei:textures/nei_sprites.png", 21, 96, 8, 9).build(), 3, 2)
            .setThumbPadding(6);

    public static final List<IRecipeFilterProvider> recipeFilterers = new LinkedList<>();

    // some mods reflect this properties
    public ArrayList<H> currenthandlers = new ArrayList<>();
    public int page;
    public int recipetype;

    private RecipeId recipeId;
    public GuiContainer firstGui;
    public GuiScreen firstGuiGeneral;
    public GuiScreen prevGui;
    private GuiButton nextpage;
    private GuiButton prevpage;
    private GuiButton prevtype;
    private GuiButton nexttype;

    protected String recipePageCacheKey = "";

    private final GuiRecipeTabs recipeTabs;
    private final GuiRecipeCatalyst recipeCatalyst;
    private SearchRecipeHandler<H> handler;
    private HandlerInfo handlerInfo;

    protected static final RestartableTask updateFilter = new RestartableTask("NEI Recipe Filtering") {

        @Override
        public void execute() {
            final GuiScreen currentScreen = NEIClientUtils.mc().currentScreen;

            if (currentScreen instanceof GuiRecipe<?>guiRecipe) {
                final SearchRecipeHandler<?> searchHandler = guiRecipe.handler;

                if (searchHandler != null && searchHandler.searchingAvailable()) {

                    if (GuiRecipe.searchField.text().isEmpty()) {
                        searchHandler.setSearchIndices(null);
                        guiRecipe.changePage(0);
                    } else {
                        final IRecipeFilter filter = GuiRecipe.searchField.getRecipeFilter();
                        final List<Integer> filtered = searchHandler.getSearchResult(filter);

                        if (filtered == null) {
                            stop();
                        }

                        if (interrupted()) return;
                        searchHandler.setSearchIndices(filtered);
                        guiRecipe.changePage(0);
                    }

                    guiRecipe.recipePageCacheKey = "";
                }

            }

        }

    };

    protected static final RecipeSearchField searchField = new RecipeSearchField("") {

        @Override
        protected boolean noResults() {
            final GuiScreen currentScreen = NEIClientUtils.mc().currentScreen;
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

            if (GuiRecipe.searchField.isVisible()) {
                GuiRecipe.searchField.setText("");
                GuiRecipe.searchField.setFocus(false);
                GuiRecipe.searchField.setVisible(false);
                state = 0;
            } else {
                GuiRecipe.searchField.setVisible(true);
                GuiRecipe.searchField.setFocus(true);
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
        this.recipeTabs = new GuiRecipeTabs() {

            @Override
            protected void setRecipePage(int recipetype) {
                GuiRecipe.this.setRecipePage(recipetype);
            }

        };
        this.recipeCatalyst = new GuiRecipeCatalyst();
        this.slotcontainer = (ContainerRecipe) this.inventorySlots;

        this.prevGui = prevgui;
        this.firstGuiGeneral = prevgui;

        if (prevgui instanceof GuiContainer firstGui) {
            this.firstGui = firstGui;
        }

        if (prevgui instanceof IGuiContainerOverlay gui) {
            this.firstGui = gui.getFirstScreen();
            this.firstGuiGeneral = gui.getFirstScreenGeneral();
        }
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

        private CompatibilityHacks() {
            trueHeight = height;
            trueGuiTop = guiTop;

            isHeightHackApplied = NEIClientConfig.heightHackHandlerRegex.stream()
                    .map(pattern -> pattern.matcher(handler.original.getHandlerId())).anyMatch(Matcher::matches);
            if (isHeightHackApplied) {
                // guiTop is the top edge of the recipe screen on the y-axis. Old recipe
                // handlers expect a single paging
                // widget at the top and at the bottom of the recipe screen with a height of
                // 16px each, but GTNH NEI
                // moved the bottom paging widget to the top following JEI's design, increasing
                // the "forbidden" zone at
                // the top to 32px. To fix these old recipe handlers we move guiTop down 16px so
                // that they can keep
                // working with the old 16px gap.
                guiTop += 16;

                // The old NEI recipe screen had a fixed width and height and was always
                // centered on the screen. Legacy
                // recipe handlers use the calculation ((height - 166) / 2) to compute the
                // y-value of the top edge of
                // the NEI window. GTNH NEI changes the layout so that the recipe screen length
                // now has a flexible
                // length and is no longer centered vertically. In order for the top-of-screen
                // calculation to return the
                // correct result, we have to hack the height field with an inverse of the
                // calculation using the actual
                // top of the recipe screen stored in guiTop.
                height = (2 * guiTop) + 166;

                // For reference, in case we ever need to modify width as well:
                // the legacy calculation used for width is ((width - 176) / 2), which should
                // evaluate to be equal to
                // guiWidth (the x-value of the left edge of the NEI recipe screen). So if we
                // wanted to override width
                // as well, we'd do this:
                // width = (2 * guiWidth) + 176;
            }
        }

        @Override
        public void close() {
            guiTop = trueGuiTop;
            height = trueHeight;

            isHeightHackApplied = false;
        }
    }

    private ScrollContainer container = new ScrollContainer();

    @Override
    public void initGui() {
        final int tabHeight = GuiRecipeTabs.getTabHeight();
        this.xSize = 176;
        this.ySize = Math
                .min(this.height - 22 - 22 - tabHeight, NEIClientConfig.getIntSetting("inventory.guirecipe.maxHeight"));

        super.initGui();

        this.guiTop = Math.max(22 - 3 + tabHeight, (this.height - this.ySize) / 2);

        if (this.handler == null) {
            setRecipePage(this.recipetype);
        }

        final int rightButtonX = this.guiLeft + this.xSize - BORDER_PADDING - BUTTON_WIDTH + 1;
        final int leftButtonX = this.guiLeft + BORDER_PADDING;

        this.prevtype = new GuiNEIButton(0, leftButtonX, guiTop + 3, BUTTON_WIDTH, BUTTON_HEIGHT, "<") {

            @Override
            public void mouseReleased(int mouseX, int mouseY) {
                prevType();
            }
        };
        this.nexttype = new GuiNEIButton(1, rightButtonX, guiTop + 3, BUTTON_WIDTH, BUTTON_HEIGHT, ">") {

            @Override
            public void mouseReleased(int mouseX, int mouseY) {
                nextType();
            }
        };

        this.prevpage = new GuiNEIButton(2, leftButtonX, guiTop + 17, BUTTON_WIDTH, BUTTON_HEIGHT, "<") {

            @Override
            public void mouseReleased(int mouseX, int mouseY) {
                prevPage();
            }
        };
        this.nextpage = new GuiNEIButton(3, rightButtonX, guiTop + 17, BUTTON_WIDTH, BUTTON_HEIGHT, ">") {

            @Override
            public void mouseReleased(int mouseX, int mouseY) {
                nextPage();
            }
        };

        this.container.x = this.guiLeft + 3;
        this.container.y = this.guiTop + 32;
        this.container.h = this.ySize - 32 - 5;

        GuiRecipe.toggleSearch.icon = new DrawableBuilder("nei:textures/nei_sprites.png", 0, 76, 10, 10).build();
        GuiRecipe.toggleSearch.w = GuiRecipe.toggleSearch.h = 12;
        GuiRecipe.toggleSearch.x = this.guiLeft + BORDER_PADDING + BUTTON_WIDTH;
        GuiRecipe.toggleSearch.y = this.guiTop + 17;

        GuiRecipe.searchField.y = this.guiTop + 16;
        GuiRecipe.searchField.x = this.guiLeft + BORDER_PADDING + BUTTON_WIDTH + GuiRecipe.toggleSearch.w;
        GuiRecipe.searchField.w = this.xSize - (BORDER_PADDING + BUTTON_WIDTH) * 2 + 1 - GuiRecipe.toggleSearch.w - 45;
        GuiRecipe.searchField.h = 14;

        this.buttonList.addAll(Arrays.asList(this.prevtype, this.nexttype, this.prevpage, this.nextpage));
        this.recipePageCacheKey = "";

        if (currenthandlers.size() == 1) {
            this.prevtype.visible = false;
            this.nexttype.visible = false;
        }

        this.recipeTabs.update(this);
    }

    private void updateRecipePage() {
        final List<Integer> indices = getRecipeIndices();
        final String cacheKey = indices.isEmpty() ? "empty"
                : handlerInfo.getHandlerName() + ":" + indices.get(0) + ":" + indices.get(indices.size() - 1);

        if (!cacheKey.equals(this.recipePageCacheKey)) {
            this.recipePageCacheKey = cacheKey;

            final boolean expandVertically = this.handlerInfo.expandVertically();
            final List<Widget> widgets = new ArrayList<>();
            int shiftY = 0;

            for (int recipeIndex : indices) {
                final NEIRecipeWidget widget = RecipeHandlerRef.of(this.handler.original, recipeIndex)
                        .getRecipeWidget();
                widget.setLocation(Math.max(0, (this.container.w - widget.w) / 2), shiftY);

                if (expandVertically) {
                    widget.h = Math.min(
                            this.handlerInfo.getHeight() + this.handlerInfo.getYShift(),
                            this.container.getVisibleHeight());
                }

                widgets.add(widget);
                shiftY += widget.h;
            }

            this.container.setWidgets(widgets);

            if (this.handlerInfo.getUseCustomScroll()) {
                this.container.setVerticalScroll(null);
            } else if (this.container.w == this.xSize - 6) {
                this.container.setVerticalScroll(VERTICAL_SCROLLBAR);
            } else {
                this.container.setVerticalScroll(
                        ScrollBar.defaultVerticalBar().setOverflowType(OverflowType.OVERLAY)
                                .setScrollPlace(ScrollPlace.END));
            }
        }

    }

    public static IRecipeFilter getRecipeListFilter() {
        if (recipeFilterers.isEmpty()) {
            return null;
        }

        final AllMultiRecipeFilter recipeFilter = new AllMultiRecipeFilter();

        synchronized (recipeFilterers) {
            for (IRecipeFilterProvider p : recipeFilterers) {
                IRecipeFilter filter = p.getRecipeFilter();
                if (filter != null) {
                    recipeFilter.filters.add(filter);
                }
            }
        }

        return recipeFilter.filters.size() == 1 ? recipeFilter.filters.get(0) : recipeFilter;
    }

    public static ItemFilter getSearchItemFilter() {
        return GuiRecipe.searchField.getFilter();
    }

    public void setRecipePage(int idx) {
        setRecipePage(idx, 0);
    }

    public void setRecipePage(int idx, int refIndex) {
        this.recipetype = (this.currenthandlers.size() + idx) % this.currenthandlers.size();
        this.handler = new SearchRecipeHandler<>(this.currenthandlers.get(this.recipetype));
        this.handlerInfo = GuiRecipeTab.getHandlerInfo(this.handler.original);

        GuiRecipe.searchField.setText("");
        GuiRecipe.searchField.setVisible(false);
        GuiRecipe.toggleSearch.state = 0;

        this.container.setVerticalScrollOffset(0);
        this.page = Math.min(Math.max(0, refIndex), this.numRecipes() - 1) / getRecipesPerPage();
        changePage(0);

        this.recipeTabs.update(this);
    }

    public int openTargetRecipe(RecipeId recipeId) {
        int refIndex = -1;
        int recipetype = 0;

        this.recipeId = recipeId;

        if (this.recipeId != null) {
            for (int j = 0; j < this.currenthandlers.size(); j++) {
                final H localHandler = this.currenthandlers.get(j);
                final HandlerInfo localHandlerInfo = GuiRecipeTab.getHandlerInfo(localHandler);

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
        final Point mouse = GuiDraw.getMousePosition();
        final Widget activeWidget = this.container.getWidgetUnderMouse(mouse.x, mouse.y);

        if (activeWidget instanceof NEIRecipeWidget recipeWidget && recipeWidget.isFocusedRecipe(mouse.x, mouse.y)) {
            return recipeWidget.getRecipe();
        }

        return null;
    }

    public boolean isMouseOver(PositionedStack stack, int refIndex) {
        final Point mouse = GuiDraw.getMousePosition();
        final Widget activeWidget = this.container.getWidgetUnderMouse(mouse.x, mouse.y);

        if (activeWidget instanceof NEIRecipeWidget recipeWidget) {
            final PositionedStack hovered = recipeWidget.getPositionedStackMouseOver(mouse.x, mouse.y);

            if (hovered != null) {
                return stack.relx == hovered.relx && stack.rely == hovered.rely;
            }
        }

        return false;
    }

    @Override
    public Slot getSlotAtPosition(int mousex, int mousey) {
        final Widget activeWidget = this.container.getWidgetUnderMouse(mousex, mousey);
        final ContainerRecipe slotcontainer = (ContainerRecipe) inventorySlots;
        slotcontainer.setActiveStack(null);

        if (activeWidget instanceof NEIRecipeWidget recipeWidget) {
            final PositionedStack hovered = recipeWidget.getPositionedStackMouseOver(mousex, mousey);

            if (hovered != null) {
                slotcontainer.setActiveStack(hovered.item);
                return new SlotInaccessible(hovered.item, hovered.relx, hovered.rely);
            }
        }

        final PositionedStack activeStack = this.recipeCatalyst.getPositionedStackMouseOver(mousex, mousey);

        if (activeStack != null) {
            slotcontainer.setActiveStack(activeStack.item);
            return new SlotInaccessible(activeStack.item, activeStack.relx, activeStack.rely);
        }

        return null;
    }

    public String getHandlerName() {
        return this.handlerInfo.getHandlerName();
    }

    public H getHandler() {
        return this.handler.original;
    }

    public List<Integer> getRecipeIndices() {
        final int recipesPerPage = getRecipesPerPage();
        final int minIndex = this.page * recipesPerPage;
        final int maxIndex = Math.min(this.numRecipes(), (this.page + 1) * recipesPerPage);
        final List<Integer> range = new ArrayList<>();

        for (int index = minIndex; index < maxIndex; index++) {
            range.add(this.handler.ref(index));
        }

        return range;
    }

    private int numRecipes() {
        return this.handler == null ? 0 : this.handler.numRecipes();
    }

    @Override
    public void keyTyped(char c, int i) {

        if (GuiRecipe.searchField.isVisible() && GuiRecipe.searchField.focused()
                && GuiRecipe.searchField.handleKeyPress(i, c)) {
            return;
        }

        if (i == Keyboard.KEY_ESCAPE) { // esc
            this.mc.displayGuiScreen(getFirstScreenGeneral());
            NEICPH.sendRequestContainer();
            return;
        }

        if (GuiRecipe.searchField.isVisible() && GuiRecipe.searchField.focused()) {
            GuiRecipe.searchField.lastKeyTyped(i, c);
            return;
        }

        if (this.recipeCatalyst.isShowWidget()) {
            if (this.recipeCatalyst.handleKeyPress(i, c)) {
                return;
            }

            this.recipeCatalyst.lastKeyTyped(i, c);
        }

        if (this.container.handleKeyPress(i, c)) {
            return;
        }

        this.container.lastKeyTyped(i, c);

        if (GuiContainerManager.getManager(this).lastKeyTyped(i, c)) {
            return;
        }

        if (i == mc.gameSettings.keyBindInventory.getKeyCode()) {
            this.mc.displayGuiScreen(getFirstScreenGeneral());
            NEICPH.sendRequestContainer();
        } else if (NEIClientConfig.isKeyHashDown("gui.back")) {
            this.mc.displayGuiScreen(this.prevGui);
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

        if (this.handler != null && this.handler.searchingAvailable()) {
            if (GuiRecipe.toggleSearch.contains(mousex, mousey)) {
                GuiRecipe.toggleSearch.handleClick(mousex, mousey, button);
            } else if (GuiRecipe.searchField.contains(mousex, mousey)) {
                GuiRecipe.searchField.handleClick(mousex, mousey, button);
            } else {
                GuiRecipe.searchField.onGuiClick(mousex, mousey);
            }
        }

        if (this.recipeCatalyst.isShowWidget() && this.recipeCatalyst.handleClick(mousex, mousey, button)) {
            return;
        }

        try (CompatibilityHacks compatibilityHacks = new CompatibilityHacks()) {
            if (this.container.handleClick(mousex, mousey, button)) {
                return;
            }
        }

        if (this.recipeTabs.mouseClicked(mousex, mousey, button)) {
            return;
        }

        super.mouseClicked(mousex, mousey, button);
    }

    @Override
    protected void mouseClickMove(int mousex, int mousey, int button, long heldTime) {
        if (this.recipeCatalyst.isShowWidget()) {
            this.recipeCatalyst.mouseDragged(mousex, mousey, button, heldTime);
        }

        this.container.mouseDragged(mousex, mousey, button, heldTime);
        super.mouseClickMove(mousex, mousey, button, heldTime);
    }

    @Override
    protected void mouseMovedOrUp(int mousex, int mousey, int state) {

        if (state != -1) {

            if (this.recipeCatalyst.isShowWidget()) {
                this.recipeCatalyst.mouseUp(mousex, mousey, state);
            }

            this.container.mouseUp(mousex, mousey, state);
        }

        super.mouseMovedOrUp(mousex, mousey, state);
    }

    @Override
    public void mouseScrolled(int scroll) {
        // Height hacking is not necessary here since mouse scrolling is a new feature,
        // added in
        // GTNH NEI. So no old mods will use this. Though not hacking the height here
        // does mean that
        // the value of the height field will be different from in other mouseover
        // methods, which
        // could be confusing...

        // First, invoke scroll handling over recipe handler tabbar. Makes sure it is
        // not overwritten by recipe
        // handler-specific scroll behavior.
        if (this.recipeTabs.mouseScrolled(scroll)) return;

        final Point mouse = GuiDraw.getMousePosition();

        if (this.recipeCatalyst.isShowWidget() && this.recipeCatalyst.onMouseWheel(scroll, mouse.x, mouse.y)) {
            return;
        }

        try (CompatibilityHacks compatibilityHacks = new CompatibilityHacks()) {
            if (this.container.onMouseWheel(scroll, mouse.x, mouse.y)) {
                return;
            }
        }

        // If shift is held, try switching to the next recipe handler. Replicates the
        // GuiRecipeTabs.mouseScrolled()
        // without the checking for the cursor being inside the tabbar.
        if (NEIClientUtils.shiftKey()) {
            if (scroll < 0) {
                nextType();
            } else {
                prevType();
            }

            return;
        }

        // Finally, if nothing else has handled scrolling, try changing to the next
        // recipe page.
        if (new Rectangle(this.guiLeft, this.guiTop, this.xSize, this.ySize).contains(mouse)) {
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

        this.container.w = Math.max(this.xSize - 6, this.handlerInfo.getWidth());

        this.handler.original.onUpdate();
        refreshPage();
        this.container.update();

        this.recipeCatalyst.setAvailableHeight(this.ySize - 5);
        this.recipeCatalyst.y = this.guiTop;
        this.recipeCatalyst.x = this.guiLeft - this.recipeCatalyst.w + 4;
        this.recipeCatalyst.update();
    }

    @Override
    public List<String> handleTooltip(GuiContainer gui, int mousex, int mousey, List<String> currenttip) {

        if (this.recipeCatalyst.isShowWidget()) {
            currenttip = this.recipeCatalyst.handleTooltip(mousex, mousey, currenttip);
        }

        try (CompatibilityHacks compatibilityHacks = new CompatibilityHacks()) {
            currenttip = this.container.handleTooltip(mousex, mousey, currenttip);
        }

        this.recipeTabs.handleTooltip(mousex, mousey, currenttip);

        if (currenttip.isEmpty() && GuiRecipe.searchField.isVisible()
                && new Rectangle(GuiRecipe.searchField.x + GuiRecipe.searchField.w, 15, 44, 16)
                        .contains(mousex - this.guiLeft, mousey - this.guiTop)) {

            final String pageInfo = String
                    .format("%d/%d", page + 1, Math.max(1, this.numRecipes() - 1) / getRecipesPerPage() + 1);

            if (this.fontRendererObj.getStringWidth(pageInfo) >= 45) {
                currenttip.add(pageInfo);
            }
        }

        return currenttip;
    }

    @Override
    public List<String> handleItemTooltip(GuiContainer gui, ItemStack itemstack, int mousex, int mousey,
            List<String> currenttip) {
        final Widget activeWidget = this.container.getWidgetUnderMouse(mousex, mousey);

        if (activeWidget instanceof NEIRecipeWidget recipeWidget) {
            currenttip = recipeWidget.handleItemTooltip(itemstack, mousex, mousey, currenttip);
        }

        return currenttip;
    }

    @Override
    public Map<String, String> handleHotkeys(GuiContainer gui, int mousex, int mousey, Map<String, String> hotkeys) {

        if (this.recipeCatalyst.isShowWidget()) {
            hotkeys = this.recipeCatalyst.handleHotkeys(mousex, mousey, hotkeys);
        }

        try (CompatibilityHacks compatibilityHacks = new CompatibilityHacks()) {
            hotkeys = this.container.handleHotkeys(mousex, mousey, hotkeys);
        }

        return hotkeys;
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
        final int oldPage = this.page;

        if (numRecipes > 0) {
            final int numPages = (int) Math.ceil(numRecipes / (float) recipesPerPage);
            this.page = Math.min(Math.max(0, this.page), numPages);
            this.page = (numPages + this.page + shift) % numPages;
        } else {
            this.page = 0;
        }

        if (this.page != oldPage) {
            this.container.setVerticalScrollOffset(0);
        }

    }

    public GuiRecipeCatalyst getRecipeCatalystWidget() {
        return this.recipeCatalyst;
    }

    public void forceRefreshPage() {
        this.buttonList.removeIf(Arrays.asList(this.overlayButtons)::contains);
        this.overlayButtons = new GuiOverlayButton[0];
        this.currenthandlers.sort(NEIClientConfig.HANDLER_COMPARATOR);
        this.recipePageCacheKey = "";
        refreshPage();
    }

    public void refreshPage() {
        changePage(0);
        final int recipesPerPage = getRecipesPerPage();
        final boolean multiplepages = this.numRecipes() > recipesPerPage;

        this.prevpage.enabled = this.nextpage.enabled = multiplepages;
        this.recipeCatalyst.setCatalysts(RecipeCatalysts.getRecipeCatalysts(this.handler.original));

        updateRecipePage();
    }

    @Override
    public void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        GL11.glTranslatef(-guiLeft, -guiTop, 0);
        this.container.draw(mouseX, mouseY);

        if (NEIClientConfig.getJEIStyleRecipeCatalysts() != 0 && this.recipeCatalyst.isShowWidget()) {
            this.recipeCatalyst.draw(mouseX, mouseY);
        }

        GL11.glTranslatef(guiLeft, guiTop, 0);
    }

    @Override
    public void drawGuiContainerBackgroundLayer(float f, int mouseX, int mouseY) {
        BG_TEXTURE.draw(
                this.guiLeft,
                this.guiTop,
                this.xSize,
                this.ySize,
                BORDER_PADDING,
                BORDER_PADDING,
                BORDER_PADDING,
                BORDER_PADDING);

        drawJEITabs(mouseX, mouseY);
    }

    private void drawJEITabs(int mouseX, int mouseY) {
        final int recipesPerPage = getRecipesPerPage();
        final int textMiddle = (BUTTON_WIDTH - this.fontRendererObj.FONT_HEIGHT) / 2;

        drawRect(
                this.prevtype.xPosition + BUTTON_WIDTH - 1,
                this.prevtype.yPosition,
                this.nexttype.xPosition,
                this.prevtype.yPosition + BUTTON_HEIGHT,
                0x30000000);
        drawRect(
                this.prevpage.xPosition + BUTTON_WIDTH - 1,
                this.prevpage.yPosition,
                this.nextpage.xPosition,
                this.prevpage.yPosition + BUTTON_HEIGHT,
                0x30000000);

        drawCenteredString(
                this.fontRendererObj,
                this.handler.original.getRecipeName().trim(),
                this.guiLeft + this.xSize / 2,
                this.prevtype.yPosition + textMiddle,
                0xffffff);

        if (this.handler.searchingAvailable()) {
            GuiRecipe.toggleSearch.draw(mouseX, mouseY);
        }

        if (GuiRecipe.searchField.isVisible()) {
            GuiRecipe.searchField.draw(mouseX, mouseY);
        }

        if (GuiRecipe.searchField.isVisible()) {
            final String recipePage = NEIClientUtils.cropText(
                    this.fontRendererObj,
                    String.format("%d/%d", this.page + 1, Math.max(0, numRecipes() - 1) / recipesPerPage + 1),
                    45);
            drawCenteredString(
                    this.fontRendererObj,
                    recipePage,
                    GuiRecipe.searchField.x + GuiRecipe.searchField.w + 22,
                    this.prevpage.yPosition + textMiddle,
                    0xffffff);
        } else {
            final String recipePage = NEIClientUtils
                    .translate("recipe.page", this.page + 1, Math.max(0, numRecipes() - 1) / recipesPerPage + 1);
            drawCenteredString(
                    this.fontRendererObj,
                    recipePage,
                    this.guiLeft + this.xSize / 2,
                    this.prevpage.yPosition + textMiddle,
                    0xffffff);
        }

        if (NEIClientConfig.areJEIStyleTabsVisible()) {
            RenderHelper.enableGUIStandardItemLighting();
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
            this.recipeTabs.draw(mouseX, mouseY);
            RenderHelper.disableStandardItemLighting();
        }
    }

    @Override
    public GuiContainer getFirstScreen() {
        return this.firstGui;
    }

    @Override
    public GuiScreen getFirstScreenGeneral() {
        return this.firstGuiGeneral;
    }

    private int getRecipesPerPage() {

        if (this.handlerInfo.getMaxRecipesPerPage() <= 1) {
            return 1;
        }

        if (NEIClientConfig.getBooleanSetting("inventory.guirecipe.infiniteScroll")) {
            return Math.max(1, this.handler.numRecipes());
        }

        return Math.max(this.container.h / (this.handlerInfo.getHeight() + this.handlerInfo.getYShift()), 1);
    }

    public Point getRecipePosition(int recipe) {
        return getRefIndexPosition(getRecipeIndices().indexOf(recipe));
    }

    protected Point getRefIndexPosition(int refIndex) {
        // Legacy recipe handlers using the height hack might use getRefIndexPosition in
        // combination with guiTop/height
        // to position certain elements like tooltips. Since guiTop is moved down by
        // 16px during height hacking, we need
        // to reduce the vertical shift here to 16px instead of 32px.
        final List<Widget> children = this.container.getWidgets();

        if (refIndex >= 0 && refIndex < children.size()
                && children.get(refIndex) instanceof NEIRecipeWidget recipeWidget) {
            return new Point(
                    recipeWidget.x - this.guiLeft,
                    recipeWidget.y - this.guiTop + recipeWidget.getHandlerInfo().getYShift());
        }

        return new Point(0, 0);
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

        if (GuiRecipe.searchField.isVisible() && GuiRecipe.searchField.contains(mousex, mousey)) {
            GuiRecipe.searchField.setText(SearchField.getEscapedSearchText(draggedStack));
            return true;
        }

        return false;
    }

    @Override
    public boolean hideItemPanelSlot(GuiContainer gui, int x, int y, int w, int h) {
        final Rectangle4i rect = new Rectangle4i(x, y, w, h);
        // Because some of the handlers *cough avaritia* are oversized

        if (this.recipeCatalyst.isShowWidget() && this.recipeCatalyst.boundsOutside().intersects(rect)) {
            return true;
        }

        if (getRecipesPerPage() == 1 && !this.container.getWidgets().isEmpty()) {
            return this.container.getWidgets().get(0).bounds().intersects(rect);
        }

        return this.container.boundsOutside().intersects(rect);
    }

    protected static RecipeId getCurrentRecipeId(GuiScreen gui) {

        if (gui instanceof GuiRecipe<?>gRecipe && gRecipe.handler.numRecipes() > 0) {
            final List<Integer> indices = gRecipe.getRecipeIndices();
            final int curRecipe = indices.isEmpty() ? 0 : indices.get(0);
            final Recipe recipe = Recipe.of(gRecipe.handler.original, curRecipe);

            return recipe != null ? recipe.getRecipeId() : null;
        }

        return null;
    }

    // some mods reflect this property
    @Deprecated
    private GuiOverlayButton[] overlayButtons = new GuiOverlayButton[0];

    @Deprecated
    public ContainerRecipe slotcontainer;

    @Deprecated
    public List<GuiButton> getOverlayButtons() {
        return Arrays.asList(this.overlayButtons);
    }

    @Deprecated
    public boolean isLimitedToOneRecipe() {
        return false;
    }
}
