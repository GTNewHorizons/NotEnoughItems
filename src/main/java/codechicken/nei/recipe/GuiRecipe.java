package codechicken.nei.recipe;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

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
import codechicken.nei.ColorUtils;
import codechicken.nei.KeyManager;
import codechicken.nei.LayoutStyleMinecraft;
import codechicken.nei.NEICPH;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.PositionedStack;
import codechicken.nei.RecipeSearchField;
import codechicken.nei.VisiblityData;
import codechicken.nei.Widget;
import codechicken.nei.WidgetContainer;
import codechicken.nei.api.IGuiContainerOverlay;
import codechicken.nei.api.INEIGuiHandler;
import codechicken.nei.api.IRecipeFilter;
import codechicken.nei.api.IRecipeFilter.IRecipeFilterProvider;
import codechicken.nei.api.ItemFilter;
import codechicken.nei.api.TaggedInventoryArea;
import codechicken.nei.filter.AllMultiRecipeFilter;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.guihook.IContainerTooltipHandler;
import codechicken.nei.guihook.IGuiClientSide;
import codechicken.nei.guihook.IGuiHandleMouseWheel;
import codechicken.nei.guihook.IGuiHotkeyListener;
import codechicken.nei.recipe.Recipe.RecipeId;
import codechicken.nei.recipe.widget.RecipeWidget;
import codechicken.nei.recipe.widget.ViewStates;
import codechicken.nei.scroll.ScrollContainer;
import codechicken.nei.theme.UIRecipeTexture;
import codechicken.nei.theme.UIWidgetTexture;
import codechicken.nei.util.EmptyContainer;
import codechicken.nei.util.SlotInaccessible;

public abstract class GuiRecipe<H extends IRecipeHandler> extends GuiContainer implements IGuiContainerOverlay,
        IGuiClientSide, IGuiHandleMouseWheel, IGuiHotkeyListener, IContainerTooltipHandler, INEIGuiHandler {

    private static final int BORDER_PADDING = 5;
    private static final int SCROLL_CONTENT_GAP = 2;
    private static final int SCROLL_BORDER_SHIFT = 1;
    private static final int HEADER_HEIGHT = 33;
    private static final int MIN_HEIGHT = 166;

    public static final List<IRecipeFilterProvider> recipeFilterers = new LinkedList<>();

    // some mods reflect this properties
    public ArrayList<H> currenthandlers = new ArrayList<>();
    public int page;
    public int recipetype;

    private RecipeId recipeId;
    public GuiContainer firstGui;
    public GuiScreen firstGuiGeneral;
    public GuiScreen prevGui;
    private static final NavigationBar typeBar = new NavigationBar(
            () -> GuiRecipe.onCurrentScreen(GuiRecipe::openAllHandlers),
            () -> GuiRecipe.onCurrentScreen(GuiRecipe::prevType),
            () -> GuiRecipe.onCurrentScreen(GuiRecipe::nextType));

    private static final RecipeSearchBar search = new RecipeSearchBar(
            () -> GuiRecipe.onCurrentScreen(GuiRecipe::prevPage),
            () -> GuiRecipe.onCurrentScreen(GuiRecipe::nextPage));

    private int lastPage = -1;
    private int catalystsVersion = -1;
    private int scrollInset;
    private final GuiRecipeTabs recipeTabs;
    private final GuiRecipeCatalyst recipeCatalyst;
    private SearchRecipeHandler<H> handler;
    private HandlerInfo handlerInfo;
    private RecipePageManager handlerPages;
    private final Map<IRecipeHandler, ViewStates> viewStates = new IdentityHashMap<>();

    /**
     * This will only be true iff height hacking has been configured for the current recipe handler AND we are currently
     * within the scope of an active {@link CompatibilityHacks} instance.
     */
    private boolean isHeightHackApplied = false;

    protected GuiRecipe(GuiScreen prevgui) {
        super(new EmptyContainer());
        this.recipeTabs = new GuiRecipeTabs() {

            @Override
            protected void setRecipePage(int recipetype) {
                final int size = GuiRecipe.this.currenthandlers.size();
                if ((size + recipetype) % size != GuiRecipe.this.recipetype) {
                    GuiRecipe.this.setRecipePage(recipetype);
                }
            }

        };
        this.recipeCatalyst = new GuiRecipeCatalyst();

        this.screen.addWidget(this.recipeCatalyst);
        this.screen.addWidget(this.container);
        // oversized recipes must not cover the navigation
        this.screen.addWidget(GuiRecipe.typeBar);
        this.screen.addWidget(GuiRecipe.search);

        this.slotcontainer = (EmptyContainer) this.inventorySlots;

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
     * Many old mods assumed a fixed NEI window size of {@code 176x166} pixels, with exactly one recipe shown at a time,
     * flush against the top-left corner of the window. Now that none of these assumptions hold (the window has a
     * flexible size, several recipes can be stacked in the same scrollable list, and the recipe area is inset from the
     * window's edges), these old mods' tooltip and click zone handling is broken. This helper class fixes these old
     * mods by hacking the {@link #width}, {@link #height}, {@link #guiLeft} and {@link #guiTop} properties' values so
     * that these old mods' calculations return the correct value for whichever recipe they are currently being asked
     * about.
     *
     * <p>
     * Since each recipe handled by a {@link RecipeWidget} can sit at a different real position, this must be
     * constructed with that widget's top-left edge every time a legacy entry point ({@code mouseClicked},
     * {@code handleTooltip}, ...) is invoked for it, rather than once per {@link GuiRecipe}.
     *
     * <p>
     * This class is an {@link AutoCloseable} so that it can be used with try-with-resources, which will ensure that
     * {@link #width}, {@link #height}, {@link #guiLeft} and {@link #guiTop} are returned to the correct value
     * afterwards.
     */
    public static final class CompatibilityHacks implements AutoCloseable {

        // The historical NEI recipe screen's fixed size: a single top paging widget 16px tall, no horizontal inset,
        // and a 176x166 box. Legacy handlers either read guiLeft/guiTop directly (expecting them to point 16px above
        // their recipe, flush on the left) or recompute them from width/height with these same two constants.
        private static final int LEGACY_TOP_GAP = 16;
        private static final int LEGACY_BOX_WIDTH = 176;
        private static final int LEGACY_BOX_HEIGHT = 166;

        // Legacy handlers draw their recipe 5px from the left edge of the 176 wide box, and that is where the widget
        // is centered in the default window, so this is the inverse of that offset.
        private static final int CONTAINER_LEFT_INSET = 5;

        private final GuiRecipe<?> gui;
        private final int trueWidth;
        private final int trueHeight;
        private final int trueGuiLeft;
        private final int trueGuiTop;

        /**
         * @param widget The recipe widget a legacy entry point ({@code mouseClicked}, {@code handleTooltip}, ...) is
         *               about to be invoked for.
         */
        public CompatibilityHacks(GuiRecipe<?> gui, RecipeWidget widget) {
            this.gui = gui;
            this.trueWidth = gui.width;
            this.trueHeight = gui.height;
            this.trueGuiLeft = gui.guiLeft;
            this.trueGuiTop = gui.guiTop;

            final String handlerId = gui.handler.original.getHandlerId();
            gui.isHeightHackApplied = NEIClientConfig.heightHackHandlerRegex.stream()
                    .anyMatch(pattern -> pattern.matcher(handlerId).matches());

            if (gui.isHeightHackApplied) {
                gui.guiTop = recipeTop(widget) - LEGACY_TOP_GAP;
                gui.guiLeft = widget.x - CONTAINER_LEFT_INSET;
                gui.height = (2 * gui.guiTop) + LEGACY_BOX_HEIGHT;
                gui.width = (2 * gui.guiLeft) + LEGACY_BOX_WIDTH;
            }
        }

        // recipiesPerPage() == 2 is the IRecipeHandler default: a handler only ends up with this value if it never
        // overrode the method, or if it overrode it and chose 2 on purpose. We only care about the latter case, so
        // we require the override to actually come from the handler's own class rather than being inherited as-is.
        private static final Map<Class<?>, Boolean> overridesRecipiesPerPage = new WeakHashMap<>();

        /**
         * The real top edge of this widget's recipe, as a legacy handler following the {@link #LEGACY_TOP_GAP}
         * convention would expect to find it at guiTop. A few handlers (e.g. ImmersiveEngineering) are left over from
         * the old {@code recipiesPerPage() == 2} convention, where two recipes shared one physical page: their own
         * {@code mouseClicked}/{@code handleTooltip} still add a hardcoded "recipe height * (recipe % 2)" on top of
         * guiTop to find the odd one of the pair. Since every recipe is its own independent widget now, we pre-subtract
         * that same offset here so their own addition cancels out and lands back on this widget's real top.
         *
         * <p>
         * This only applies when the handler itself overrides {@link IRecipeHandler#recipiesPerPage()} to return
         * {@code 2}: that method defaults to {@code 2} on the interface, so most height-hacked handlers return it
         * without ever having implemented this pairing (e.g. BuildCraftCompat never overrides it at all, and only reads
         * it elsewhere to pick a cosmetic sub-name), and applying this compensation to them would break them. A handler
         * that went out of its way to declare {@code return 2;} itself is making a deliberate statement about this
         * legacy convention that a handler which simply never mentions the method isn't.
         */
        private static int recipeTop(RecipeWidget widget) {
            final RecipeHandlerRef ref = widget.getRecipeHandlerRef();
            final HandlerInfo handlerInfo = widget.getHandlerInfo();
            final int top = widget.y + handlerInfo.getYShift();

            if (ref.handler.recipiesPerPage() == 2 && declaresRecipiesPerPage(ref.handler)) {
                return top - handlerInfo.getHeight() * (ref.recipeIndex % 2);
            }

            return top;
        }

        private static boolean declaresRecipiesPerPage(IRecipeHandler handler) {
            return overridesRecipiesPerPage.computeIfAbsent(handler.getClass(), clazz -> {
                try {
                    return clazz.getMethod("recipiesPerPage").getDeclaringClass() != IRecipeHandler.class;
                } catch (NoSuchMethodException e) {
                    return false;
                }
            });
        }

        @Override
        public void close() {
            this.gui.guiLeft = this.trueGuiLeft;
            this.gui.guiTop = this.trueGuiTop;
            this.gui.width = this.trueWidth;
            this.gui.height = this.trueHeight;

            this.gui.isHeightHackApplied = false;
        }
    }

    private ScrollContainer container = new ScrollContainer() {

        // the scrollbar track lies outside the container
        @Override
        public boolean contains(int px, int py) {
            return boundsOutside().contains(px, py);
        }

        @Override
        public Widget getWidgetUnderMouse(int mousex, int mousey) {

            if (handlerInfo.isAllowOverflowY() && !this.widgets.isEmpty()) {
                return this.widgets.get(0);
            }

            return super.getWidgetUnderMouse(mousex, mousey);
        }

    };

    // all widgets of the screen, later ones are drawn over and get the mouse first
    private final WidgetContainer screen = new WidgetContainer() {

        @Override
        public Widget getWidgetUnderMouse(int mousex, int mousey) {
            final Widget widget = super.getWidgetUnderMouse(mousex, mousey);

            // recipes that overflow the container still get the mouse outside of it
            if (widget == null && GuiRecipe.this.handlerInfo.isAllowOverflowY()) {
                return GuiRecipe.this.container;
            }

            return widget;
        }

    };

    @Override
    public void initGui() {
        final int tabHeight = GuiRecipeTabs.getTabHeight();
        final int bottomInset = NEIClientConfig.isSearchWidgetCentered() ? LayoutStyleMinecraft.BUTTON_SIZE : 0;
        int topInset = 0;
        if (NEIClientConfig.showSubsetWidget() && NEIClientConfig.subsetWidgetOnTop()) {
            topInset += LayoutStyleMinecraft.MARGIN + LayoutStyleMinecraft.SUBSET_BAR_HEIGHT;
        }

        final int freeHeight = this.height - topInset - bottomInset - tabHeight;
        topInset += Math.min(
                NEIClientConfig.getIntSetting("inventory.guirecipe.topMargin"),
                Math.max(0, freeHeight - MIN_HEIGHT));

        final int availableHeight = this.height - topInset - bottomInset - tabHeight;
        this.ySize = Math.min(availableHeight, NEIClientConfig.getIntSetting("inventory.guirecipe.maxHeight"));
        this.xSize = fitWindowContentWidth(this.scrollInset) + windowChrome();

        super.initGui();

        this.guiTop = topInset + tabHeight - 3 + (availableHeight - this.ySize) / 2;

        if (this.handler == null) {
            setRecipePage(this.recipetype);
        } else {
            GuiRecipe.search.updateFilter();
        }

        layoutWindow();
        fitWindow();
        this.lastPage = -1;

        GuiRecipe.typeBar.enabled = this.currenthandlers.size() > 1;
        GuiRecipe.typeBar.labelTooltip = NEIClientUtils.translate("recipe.tab.view_all.tooltip");
        GuiRecipe.typeBar.color = ColorUtils.recipeTitle.getColor();
        GuiRecipe.typeBar.hoverColor = ColorUtils.recipeTitleHover.getColor();
        updateNavigation();
    }

    private void updateNavigation() {
        GuiRecipe.typeBar.label = this.handler.original.getRecipeName().trim();
        GuiRecipe.search.setAvailable(this.handler.searchingAvailable());
        GuiRecipe.search.enabled = this.handlerPages.getNumPages() > 1;
        GuiRecipe.search.setPages(this.handlerPages.getCurrentPageIndex() + 1, this.handlerPages.getNumPages());
    }

    private void layoutWindow() {
        this.guiLeft = (this.width - this.xSize) / 2;

        this.screen.w = this.width;
        this.screen.h = this.height;

        this.container.x = this.guiLeft + BORDER_PADDING;
        this.container.y = this.guiTop + HEADER_HEIGHT;

        placeNavigation();
        placeSidePanels();
    }

    private int fitWindowContentWidth(int scrollInset) {
        final int minWidth = NEIClientConfig.getIntSetting("inventory.guirecipe.minWidth");
        final int maxWidth = this.width - BORDER_PADDING * 2 - scrollInset;

        if (this.handlerInfo == null) {
            return Math.min(minWidth, maxWidth);
        }

        this.recipeCatalyst.setAvailableHeight(this.ySize);

        final int desiredWidth = this.handlerInfo.isAllowOverflowX()
                ? Math.max(this.handlerInfo.getWindowWidth(), this.handlerPages.getMaxRecipeWidth())
                : this.handlerInfo.getWindowWidth();
        final int sideWidth = this.recipeCatalyst.isVisible() ? this.recipeCatalyst.w - 5 : 0;

        return Math.min(maxWidth - sideWidth * 2, Math.max(minWidth - scrollInset, desiredWidth));
    }

    private int windowChrome() {
        return BORDER_PADDING * 2 + this.scrollInset;
    }

    private void fitWindow() {
        final int trackInset = UIWidgetTexture.SCROLL_TRACK_WIDTH + SCROLL_CONTENT_GAP - SCROLL_BORDER_SHIFT;
        // the height splits recipes into pages, so it must not depend on the current page's scroll track
        final boolean horizontalScroll = this.handlerPages.getMaxRecipeWidth()
                > fitWindowContentWidth(this.handlerPages.needsVerticalScroll() ? trackInset : 0);

        this.scrollInset = this.handlerPages.currentPageNeedsVerticalScroll() ? trackInset : 0;
        final int windowWidth = fitWindowContentWidth(this.scrollInset) + windowChrome();

        if (windowWidth != this.xSize) {
            this.xSize = windowWidth;
            layoutWindow();
        }

        updateContainerSize(horizontalScroll);
    }

    protected void refreshContainer() {

        if (this.catalystsVersion != RecipeCatalysts.getVersion()) {
            this.catalystsVersion = RecipeCatalysts.getVersion();
            this.recipeCatalyst.setCatalysts(RecipeCatalysts.getRecipeCatalysts(this.handler.original));
            // the catalyst width changes the window width, so the page has to be placed again
            this.lastPage = -1;
        }

        if (this.handlerPages.rebuildPages() || this.lastPage != this.handlerPages.getCurrentPageIndex()) {
            this.lastPage = this.handlerPages.getCurrentPageIndex();
            updateNavigation();
            fitWindow();

            this.container.setWidgets(this.handlerPages.getCurrentPageWidgets());

            final boolean verticalScroll = !this.handlerInfo.isAllowOverflowY()
                    && this.handlerPages.currentPageNeedsVerticalScroll();

            if (isWiderThanWindow(this.handlerPages.getCurrentPageWidth())) {
                this.container.setHorizontalScroll(
                        UIWidgetTexture.horizontalScrollBar()
                                .setTrackPadding(0, 0, verticalScroll ? -SCROLL_CONTENT_GAP : 0, 0));
            } else {
                this.container.setPaddingInline(0, 0);
                this.container.setHorizontalScroll(null);
            }

            if (!verticalScroll) {
                this.container.setPaddingBlock(0, 0);
                this.container.setVerticalScroll(null);
            } else {
                this.container.setVerticalScroll(
                        UIWidgetTexture.verticalScrollBar().setTrackPadding(SCROLL_CONTENT_GAP, 0, 0, 0));
            }
        }

        this.container.update();
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

    private static void onCurrentScreen(Consumer<GuiRecipe<?>> action) {
        if (NEIClientUtils.mc().currentScreen instanceof GuiRecipe<?>guiRecipe) {
            action.accept(guiRecipe);
        }
    }

    public static ItemFilter getSearchItemFilter() {
        return GuiRecipe.search.getFilter();
    }

    protected void setRecipePage(int idx) {
        this.recipetype = (this.currenthandlers.size() + idx) % this.currenthandlers.size();
        this.handler = new SearchRecipeHandler<>(
                this.currenthandlers.get(this.recipetype),
                GuiRecipe.getRecipeListFilter());
        this.handlerInfo = GuiRecipeTab.getHandlerInfo(this.handler.original);
        this.catalystsVersion = RecipeCatalysts.getVersion();
        this.recipeCatalyst.setCatalysts(RecipeCatalysts.getRecipeCatalysts(this.handler.original));
        GuiRecipe.search.reset();
        this.handlerPages = new RecipePageManager(
                this.handler,
                this.handlerInfo,
                this.container,
                this.viewStates.computeIfAbsent(this.handler.original, k -> new ViewStates()));
        fitWindow();
        this.handlerPages.rebuildPages();
        // the pages decide whether the window needs room for the scroll track
        fitWindow();
        this.handlerPages.rebuildPages();
        this.lastPage = -1;

        updateNavigation();

        this.container.setVerticalScrollOffset(0);
        this.container.setHorizontalScrollOffset(0);
        this.recipeTabs.update(this.currenthandlers, this.recipetype);
    }

    private void placeNavigation() {
        final int navWidth = Math
                .min(this.xSize, NEIClientConfig.getIntSetting("inventory.guirecipe.minWidth") + BORDER_PADDING * 2);
        final int navLeft = this.guiLeft + (this.xSize - navWidth) / 2;

        GuiRecipe.typeBar.place(navLeft + BORDER_PADDING, this.guiTop + 5, navWidth - BORDER_PADDING * 2);

        GuiRecipe.search.place(
                GuiRecipe.typeBar.x,
                GuiRecipe.typeBar.y + GuiRecipe.typeBar.h + NavigationBar.GAP,
                GuiRecipe.typeBar.w);

        this.recipeTabs.place(navLeft, navWidth, this.guiTop);
    }

    private void updateContainerSize(boolean horizontalScroll) {
        this.container.w = this.xSize - windowChrome();
        this.container.h = this.ySize - HEADER_HEIGHT - BORDER_PADDING;

        if (horizontalScroll) {
            this.container.h -= UIWidgetTexture.SCROLL_TRACK_WIDTH - SCROLL_BORDER_SHIFT;
        }
    }

    private boolean isWiderThanWindow(int recipesWidth) {
        return recipesWidth > this.xSize - windowChrome();
    }

    public int openTargetRecipe(RecipeId recipeId) {
        return openTargetRecipe(recipeId, null);
    }

    /**
     * @param pinnedIngredients when not null, the found recipe is paused with these ingredients shown
     */
    public int openTargetRecipe(RecipeId recipeId, List<ItemStack> pinnedIngredients) {
        int recipeIndex = -1;
        int recipetype = 0;

        this.recipeId = recipeId;

        if (this.recipeId != null) {
            for (int j = 0; j < this.currenthandlers.size(); j++) {
                final H localHandler = this.currenthandlers.get(j);
                final HandlerInfo localHandlerInfo = GuiRecipeTab.getHandlerInfo(localHandler);

                if (localHandlerInfo.getHandlerName().equals(this.recipeId.getHandlerName())) {
                    recipetype = j;

                    if (!this.recipeId.getIngredients().isEmpty()) {
                        recipeIndex = SearchRecipeHandler.findFirst(
                                localHandler,
                                ri -> this.recipeId.equalsIngredients(localHandler.getIngredientStacks(ri)));
                    }

                    break;
                }
            }
        }

        setRecipePage(recipetype);
        this.handlerPages.gotoRecipeIndex(Math.max(0, recipeIndex));

        if (pinnedIngredients != null && recipeIndex >= 0) {
            this.handlerPages.pinRecipe(recipeIndex, pinnedIngredients);
        }

        return recipeIndex;
    }

    public Recipe getFocusedRecipe() {
        final Point mouse = GuiDraw.getMousePosition();
        final Widget activeWidget = this.container.getWidgetUnderMouse(mouse.x, mouse.y);

        if (activeWidget instanceof RecipeWidget recipeWidget && recipeWidget.isFocusedRecipe(mouse.x, mouse.y)) {
            return Recipe.of(recipeWidget.getRecipeHandlerRef());
        }

        return null;
    }

    public boolean isMouseOver(PositionedStack stack, int refIndex) {
        final Point mouse = GuiDraw.getMousePosition();
        final Widget activeWidget = this.container.getWidgetUnderMouse(mouse.x, mouse.y);

        if (activeWidget instanceof RecipeWidget recipeWidget) {
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
        final EmptyContainer slotcontainer = (EmptyContainer) inventorySlots;
        slotcontainer.setActiveStack(null);

        if (activeWidget instanceof RecipeWidget recipeWidget) {
            final PositionedStack hovered = recipeWidget.getPositionedStackMouseOver(mousex, mousey);

            if (hovered != null) {
                slotcontainer.setActiveStack(hovered.item);
                return new SlotInaccessible(hovered.item, hovered.relx, hovered.rely);
            }
        }

        final PositionedStack activeStack = this.recipeCatalyst.isVisible()
                ? this.recipeCatalyst.getPositionedStackMouseOver(mousex, mousey)
                : null;

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

    SearchRecipeHandler<H> getSearchHandler() {
        return this.handler;
    }

    int getNumRecipes() {
        return this.handlerPages.getNumRecipes();
    }

    public List<Integer> getRecipeIndices() {
        return this.handlerPages.getRecipeIndices();
    }

    public List<Integer> getFilteredRecipes() {
        return this.handler.getFilteredRecipes();
    }

    @Override
    public void keyTyped(char c, int i) {

        if (GuiRecipe.search.handleKeyPress(i, c)) {
            return;
        }

        if (i == Keyboard.KEY_ESCAPE) { // esc
            this.mc.displayGuiScreen(getFirstScreenGeneral());
            NEICPH.sendRequestContainer();
            return;
        }

        if (GuiRecipe.search.isFocused()) {
            GuiRecipe.search.lastKeyTyped(i, c);
            GuiRecipe.search.onHotkeyPressed();
            return;
        }

        if (this.screen.handleKeyPress(i, c)) {
            return;
        }

        this.screen.lastKeyTyped(i, c);

        if (GuiContainerManager.getManager(this).lastKeyTyped(i, c)) {
            return;
        }

        if (i == mc.gameSettings.keyBindInventory.getKeyCode()) {
            this.mc.displayGuiScreen(getFirstScreenGeneral());
            NEICPH.sendRequestContainer();
        }
    }

    @Override
    public boolean onHotkeyPressed() {
        if (GuiRecipe.search.onHotkeyPressed()
                || this.recipeCatalyst.isVisible() && this.recipeCatalyst.onHotkeyPressed()
                || this.container.onHotkeyPressed()) {
            return true;
        }

        return false;
    }

    @Override
    public boolean onScreenHotkeyPressed() {

        if (KeyManager.isKeyDown("recipe.back")) {
            this.mc.displayGuiScreen(this.prevGui);
        } else if (KeyManager.isKeyDown("recipe.prev_machine")) {
            prevType();
        } else if (KeyManager.isKeyDown("recipe.next_machine")) {
            nextType();
        } else if (KeyManager.isKeyDown("recipe.prev_recipe")) {
            prevPage();
        } else if (KeyManager.isKeyDown("recipe.next_recipe")) {
            nextPage();
        } else {
            return false;
        }

        return true;
    }

    @Override
    protected void mouseClicked(int mousex, int mousey, int button) {
        if (this.screen.handleClick(mousex, mousey, button)) {
            return;
        }

        if (this.recipeTabs.mouseClicked(mousex, mousey, button)) {
            return;
        }

        super.mouseClicked(mousex, mousey, button);
    }

    @Override
    protected void mouseClickMove(int mousex, int mousey, int button, long heldTime) {
        this.screen.mouseDragged(mousex, mousey, button, heldTime);
        super.mouseClickMove(mousex, mousey, button, heldTime);
    }

    @Override
    protected void mouseMovedOrUp(int mousex, int mousey, int state) {

        if (state != -1) {
            this.screen.mouseUp(mousex, mousey, state);
        }

        super.mouseMovedOrUp(mousex, mousey, state);
    }

    @Override
    public void mouseScrolled(int scroll) {
        // Height hacking is not necessary here since mouse scrolling is a new feature,
        // added in
        // GTNH NEI. So no old mods will use this.

        // First, invoke scroll handling over recipe handler tabbar. Makes sure it is
        // not overwritten by recipe
        // handler-specific scroll behavior.
        if (this.recipeTabs.mouseScrolled(scroll)) return;

        final Point mouse = GuiDraw.getMousePosition();

        if (this.screen.onMouseWheel(scroll, mouse.x, mouse.y)) {
            return;
        }

        // If shift is held, try switching to the next recipe handler. Replicates the
        // GuiRecipeTabs.mouseScrolled()
        // without the checking for the cursor being inside the tabbar.
        if (NEIClientUtils.shiftKey() || GuiRecipe.typeBar.contains(mouse.x, mouse.y)) {
            if (scroll < 0) {
                nextType();
            } else {
                prevType();
            }

            return;
        }

        // Finally, if nothing else has handled scrolling, try changing to the next
        // recipe page.
        if (NEIClientConfig.getBooleanSetting("inventory.guirecipe.scrollPages")
                && this.container.boundsInside().contains(mouse.x, mouse.y)
                || GuiRecipe.search.contains(mouse.x, mouse.y) && !GuiRecipe.search.overSearch(mouse.x, mouse.y)) {

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

        this.handler.original.onUpdate();
        refreshContainer();
        placeSidePanels();
        this.recipeCatalyst.update();
    }

    private void placeSidePanels() {
        this.recipeCatalyst.y = this.guiTop;
        this.recipeCatalyst.x = this.guiLeft - this.recipeCatalyst.w + 5;
    }

    @Override
    public List<String> handleTooltip(GuiContainer gui, int mousex, int mousey, List<String> currenttip) {

        currenttip = this.screen.handleTooltip(mousex, mousey, currenttip);

        this.recipeTabs.handleTooltip(mousex, mousey, currenttip);

        return currenttip;
    }

    @Override
    public List<String> handleItemTooltip(GuiContainer gui, ItemStack itemstack, int mousex, int mousey,
            List<String> currenttip) {
        final Widget activeWidget = this.container.getWidgetUnderMouse(mousex, mousey);

        if (activeWidget instanceof RecipeWidget recipeWidget) {
            currenttip = recipeWidget.handleItemTooltip(itemstack, mousex, mousey, currenttip);
        }

        return currenttip;
    }

    @Override
    public Map<String, String> handleHotkeys(GuiContainer gui, int mousex, int mousey, Map<String, String> hotkeys) {
        return this.screen.handleHotkeys(mousex, mousey, hotkeys);
    }

    void nextPage() {
        this.handlerPages.changePage(1);
    }

    void prevPage() {
        this.handlerPages.changePage(-1);
    }

    protected void nextType() {
        if (this.currenthandlers.size() <= 1) return;
        setRecipePage(++this.recipetype);
    }

    protected void prevType() {
        if (this.currenthandlers.size() <= 1) return;
        setRecipePage(--this.recipetype);
    }

    public GuiRecipeCatalyst getRecipeCatalystWidget() {
        return this.recipeCatalyst;
    }

    public void forceRefreshPage() {
        final int currentPage = this.handlerPages.getCurrentPageIndex();
        this.currenthandlers.sort(NEIClientConfig.HANDLER_COMPARATOR);
        this.recipetype = this.currenthandlers.indexOf(this.handler.original);

        setRecipePage(this.recipetype);
        this.handlerPages.changePage(currentPage - this.handlerPages.getCurrentPageIndex());

        refreshContainer();
    }

    @Override
    public void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        GL11.glTranslatef(-guiLeft, -guiTop, 0);
        this.screen.draw(mouseX, mouseY);
        GL11.glTranslatef(guiLeft, guiTop, 0);
    }

    @Override
    public void drawGuiContainerBackgroundLayer(float f, int mouseX, int mouseY) {
        GL11.glColor4f(1, 1, 1, 1);
        UIRecipeTexture.WINDOW.draw(this.guiLeft, this.guiTop, this.xSize, this.ySize);
        drawJEITabs(mouseX, mouseY);
    }

    private void drawJEITabs(int mouseX, int mouseY) {
        if (NEIClientConfig.areJEIStyleTabsVisible()) {
            RenderHelper.enableGUIStandardItemLighting();
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
            this.recipeTabs.draw(mouseX, mouseY);
            RenderHelper.disableStandardItemLighting();
        }
    }

    private void openAllHandlers() {
        if (this instanceof GuiCraftingRecipe) {
            GuiCraftingRecipe.openRecipeGui("all");
        } else if (this instanceof GuiUsageRecipe) {
            GuiUsageRecipe.openRecipeGui("all");
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

    public Point getRecipePosition(int recipeIndex) {

        for (Widget widget : this.container.getWidgets()) {
            if (widget instanceof RecipeWidget recipeWidget && recipeWidget.containsRecipeIndex(recipeIndex)) {
                return new Point(
                        recipeWidget.x - this.guiLeft,
                        recipeWidget.y - this.guiTop + recipeWidget.getHandlerInfo().getYShift());
            }
        }

        return new Point(0, 0);
    }

    public Point getRecipeMousePosition(int recipeIndex) {
        final Point mouse = GuiDraw.getMousePosition();
        final Point recipePosition = getRecipePosition(recipeIndex);
        return new Point(mouse.x - this.guiLeft - recipePosition.x, mouse.y - this.guiTop - recipePosition.y);
    }

    protected Point getRefIndexPosition(int refIndex) {
        // Legacy recipe handlers using the height hack might use getRefIndexPosition in
        // combination with guiTop/height
        // to position certain elements like tooltips. Since guiTop is moved down by
        // 16px during height hacking, we need
        // to reduce the vertical shift here to 16px instead of 32px.
        final List<Widget> children = this.container.getWidgets();

        if (refIndex >= 0 && refIndex < children.size()
                && children.get(refIndex) instanceof RecipeWidget recipeWidget) {
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
        return GuiRecipe.search.handleDragNDrop(mousex, mousey, draggedStack);
    }

    @Override
    public boolean hideItemPanelSlot(GuiContainer gui, int x, int y, int w, int h) {
        final Rectangle4i rect = new Rectangle4i(x, y, w, h);
        // Because some of the handlers *cough avaritia* are oversized

        if (this.recipeCatalyst.isVisible() && this.recipeCatalyst.boundsOutside().intersects(rect)) {
            return true;
        }

        return this.container.boundsOutside().intersects(rect);
    }

    protected static RecipeId getCurrentRecipeId(GuiScreen gui) {

        if (gui instanceof GuiRecipe<?>gRecipe && gRecipe.handlerPages.getNumRecipes() > 0) {
            final List<Integer> indices = gRecipe.getRecipeIndices();
            final int curRecipe = indices.isEmpty() ? 0 : indices.get(0);
            final Recipe recipe = Recipe.of(gRecipe.handler.original, curRecipe);

            return recipe.getRecipeId();
        }

        return null;
    }

    // some mods reflect this property
    @Deprecated
    private GuiOverlayButton[] overlayButtons = new GuiOverlayButton[0];

    @Deprecated
    protected static final RecipeSearchField searchField = search.getSearchField();

    @Deprecated
    public EmptyContainer slotcontainer;

    @Deprecated
    public List<GuiButton> getOverlayButtons() {
        return Arrays.asList(this.overlayButtons);
    }

    @Deprecated
    public boolean isLimitedToOneRecipe() {
        return false;
    }
}
