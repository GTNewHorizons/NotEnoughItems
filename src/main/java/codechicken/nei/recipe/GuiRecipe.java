package codechicken.nei.recipe;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

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
import codechicken.nei.ColorUtils;
import codechicken.nei.GuiNEIButton;
import codechicken.nei.KeyManager;
import codechicken.nei.NEICPH;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.PositionedStack;
import codechicken.nei.RecipeSearchField;
import codechicken.nei.RestartableTask;
import codechicken.nei.SearchField;
import codechicken.nei.SearchTokenParser.ISearchParserProvider;
import codechicken.nei.SearchTokenParser.SearchMode;
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
import codechicken.nei.recipe.widget.RecipeWidget;
import codechicken.nei.scroll.ScrollBar;
import codechicken.nei.scroll.ScrollBar.OverflowType;
import codechicken.nei.scroll.ScrollBar.ScrollPlace;
import codechicken.nei.scroll.ScrollContainer;
import codechicken.nei.search.SearchExpressionUtils;
import codechicken.nei.util.EmptyContainer;
import codechicken.nei.util.SlotInaccessible;

public abstract class GuiRecipe<H extends IRecipeHandler> extends GuiContainer implements IGuiContainerOverlay,
        IGuiClientSide, IGuiHandleMouseWheel, IContainerTooltipHandler, INEIGuiHandler {

    private static final int BORDER_PADDING = 5;
    private static final int TRANSPARENCY_BORDER = 4;
    private final DrawableResource BG_TEXTURE = new DrawableBuilder(
            "nei:textures/gui/recipebg.png",
            0,
            0,
            176 + TRANSPARENCY_BORDER * 2,
            166 + TRANSPARENCY_BORDER * 2).build();

    private static final int BUTTON_WIDTH = 13;
    private static final int BUTTON_HEIGHT = 12;

    protected static final ScrollBar VERTICAL_SCROLLBAR = new ScrollBar().setTrackWidth(14)
            .setOverflowType(OverflowType.AUTO).setScrollPlace(ScrollPlace.END)
            .setTrackTexture(new DrawableBuilder("nei:textures/nei_sprites.png", 42, 90, 13, 21).build(), 9, 9)
            .setThumbTexture(new DrawableBuilder("nei:textures/nei_sprites.png", 22, 96, 6, 9).build(), 3, 2)
            .setThumbPadding(1, 6, 0, 6);

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

    private Rectangle typeArea = new Rectangle();
    private Rectangle pageArea = new Rectangle();

    private int lastPage = -1;
    private final GuiRecipeTabs recipeTabs;
    private final GuiRecipeCatalyst recipeCatalyst;
    private SearchRecipeHandler<H> handler;
    private HandlerInfo handlerInfo;
    private RecipePageManager handlerPages;

    protected static final RestartableTask updateFilter = new RestartableTask("NEI Recipe Filtering") {

        @Override
        public void execute() {
            final GuiScreen currentScreen = NEIClientUtils.mc().currentScreen;

            if (currentScreen instanceof GuiRecipe<?>guiRecipe) {
                final SearchRecipeHandler<?> searchHandler = guiRecipe.handler;

                if (searchHandler != null && searchHandler.searchingAvailable()) {

                    if (GuiRecipe.searchField.text().isEmpty()) {
                        searchHandler.setSearchIndices(null);
                    } else {
                        final IRecipeFilter filter = GuiRecipe.searchField.getRecipeFilter();
                        final List<Integer> filtered = searchHandler.getSearchResult(filter);

                        if (filtered == null) {
                            stop();
                        }

                        if (interrupted()) return;
                        searchHandler.setSearchIndices(filtered);
                    }

                }

            }

        }

    };

    protected static final RecipeSearchField searchField = new RecipeSearchField("") {

        @Override
        protected boolean noResults() {
            final GuiScreen currentScreen = NEIClientUtils.mc().currentScreen;
            return !(currentScreen instanceof GuiRecipe)
                    || ((GuiRecipe<?>) currentScreen).handlerPages.getNumRecipes() > 0;
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

        @Override
        public void addTooltips(List<String> tooltip) {
            tooltip.add(NEIClientUtils.translate("recipe.search.tooltip.name") + GuiDraw.TOOLTIP_LINESPACE);

            if (NEIClientUtils.shiftKey()) {
                tooltip.add(
                        NEIClientUtils.translate(
                                "recipe.search.tooltip.input",
                                SearchExpressionUtils.HIGHLIGHTS.RECIPE + "<"));
                tooltip.add(
                        NEIClientUtils.translate(
                                "recipe.search.tooltip.output",
                                SearchExpressionUtils.HIGHLIGHTS.RECIPE + ">"));

                for (ISearchParserProvider provider : SearchField.searchParser.getProviders()) {
                    if (provider.getSearchMode() == SearchMode.PREFIX) {
                        addPrefixTooltip(tooltip, provider);
                    }
                }

                tooltip.add(
                        NEIClientUtils.translate(
                                "recipe.search.tooltip.exclude",
                                SearchExpressionUtils.HIGHLIGHTS.NEGATE + "-"));
                tooltip.add(
                        NEIClientUtils.translate(
                                "recipe.search.tooltip.exclude_recipe",
                                SearchExpressionUtils.HIGHLIGHTS.NEGATE + "!"));
                tooltip.add(
                        NEIClientUtils
                                .translate("recipe.search.tooltip.or", SearchExpressionUtils.HIGHLIGHTS.OR.f + "|"));
                tooltip.add(
                        NEIClientUtils.translate(
                                "recipe.search.tooltip.exact",
                                SearchExpressionUtils.HIGHLIGHTS.QUOTED + "\"",
                                SearchExpressionUtils.HIGHLIGHTS.QUOTED + "\""));
            } else {
                tooltip.add(NEIClientUtils.translate("recipe.search.tooltip.shift"));
            }

        }

        private void addPrefixTooltip(List<String> tooltip, ISearchParserProvider provider) {
            final char prefix = SearchField.searchParser.getRedefinedPrefix(provider.getPrefix());
            final String modeName = NEIClientUtils.translate("recipe.search.tooltip.prefix." + provider.getName());

            tooltip.add(
                    NEIClientUtils.translate(
                            "recipe.search.tooltip.prefix",
                            provider.getHighlightedColor() + String.valueOf(prefix),
                            modeName));
        }

    };

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
                GuiRecipe.this.setRecipePage(recipetype);
            }

        };
        this.recipeCatalyst = new GuiRecipeCatalyst();
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

        this.prevtype = new GuiNEIButton(0, 0, 0, BUTTON_WIDTH, BUTTON_HEIGHT, "<") {

            @Override
            public void mouseReleased(int mouseX, int mouseY) {
                prevType();
            }
        };
        this.nexttype = new GuiNEIButton(1, 0, 0, BUTTON_WIDTH, BUTTON_HEIGHT, ">") {

            @Override
            public void mouseReleased(int mouseX, int mouseY) {
                nextType();
            }
        };

        this.prevpage = new GuiNEIButton(2, 0, 0, BUTTON_WIDTH, BUTTON_HEIGHT, "<") {

            @Override
            public void mouseReleased(int mouseX, int mouseY) {
                prevPage();
            }
        };
        this.nextpage = new GuiNEIButton(3, 0, 0, BUTTON_WIDTH, BUTTON_HEIGHT, ">") {

            @Override
            public void mouseReleased(int mouseX, int mouseY) {
                nextPage();
            }
        };
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

        // layoutWindow() places the recipe container at (guiLeft + 3, guiTop + 32); RecipePageManager then places
        // each left-aligned recipe widget another 2px to the right of that. This is the inverse of both, undoing the
        // container's own inset so a legacy handler sees its recipe flush with guiLeft as it expects.
        private static final int CONTAINER_LEFT_INSET = 3 + 2;

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

        @Override
        public Widget getWidgetUnderMouse(int mousex, int mousey) {

            if (handlerInfo.isAllowOverflowY() && !this.widgets.isEmpty()) {
                return this.widgets.get(0);
            }

            return super.getWidgetUnderMouse(mousex, mousey);
        }

    };

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

        this.prevtype.xPosition = leftButtonX;
        this.prevtype.yPosition = guiTop + 3;
        this.nexttype.xPosition = rightButtonX;
        this.nexttype.yPosition = guiTop + 3;
        this.prevpage.xPosition = leftButtonX;
        this.prevpage.yPosition = guiTop + 17;
        this.nextpage.xPosition = rightButtonX;
        this.nextpage.yPosition = guiTop + 17;

        this.container.x = this.guiLeft + 3;
        this.container.y = this.guiTop + 32;
        this.container.h = this.ySize - 32 - 4;

        GuiRecipe.toggleSearch.icon = new DrawableBuilder("nei:textures/nei_sprites.png", 0, 76, 10, 10).build();
        GuiRecipe.toggleSearch.w = GuiRecipe.toggleSearch.h = 12;
        GuiRecipe.toggleSearch.x = this.guiLeft + BORDER_PADDING + BUTTON_WIDTH;
        GuiRecipe.toggleSearch.y = this.guiTop + 17;

        GuiRecipe.searchField.y = this.guiTop + 16;
        GuiRecipe.searchField.x = this.guiLeft + BORDER_PADDING + BUTTON_WIDTH + GuiRecipe.toggleSearch.w;
        GuiRecipe.searchField.w = this.xSize - (BORDER_PADDING + BUTTON_WIDTH) * 2 + 1 - GuiRecipe.toggleSearch.w - 45;
        GuiRecipe.searchField.h = 14;

        this.typeArea.setBounds(
                this.prevtype.xPosition + BUTTON_WIDTH,
                this.prevtype.yPosition,
                this.nexttype.xPosition - this.prevtype.xPosition - BUTTON_WIDTH - 1,
                BUTTON_HEIGHT);
        this.pageArea.setBounds(
                this.prevpage.xPosition + BUTTON_WIDTH,
                this.prevpage.yPosition,
                this.nextpage.xPosition - this.prevpage.xPosition - BUTTON_WIDTH - 1,
                BUTTON_HEIGHT);

        this.buttonList.addAll(Arrays.asList(this.prevtype, this.nexttype, this.prevpage, this.nextpage));
        if (this.currenthandlers.size() == 1) {
            this.prevtype.visible = false;
            this.nexttype.visible = false;
        }

        this.recipeTabs.update(this);
    }

    protected void refreshContainer() {

        if (this.handlerPages.rebuildPages() || this.lastPage != this.handlerPages.getCurrentPageIndex()) {
            this.lastPage = this.handlerPages.getCurrentPageIndex();
            updateContainerSize();

            this.container.setWidgets(this.handlerPages.getCurrentPageWidgets());

            if (!this.handlerInfo.isAllowOverflowX() && this.handlerInfo.getWidth() > this.xSize - 6) {
                this.container.setPaddingInline(0, 2);
                this.container.setHorizontalScroll(ScrollBar.defaultHorizontalBar().setTrackPadding(1, 0, 1, 0));
            } else {
                this.container.setPaddingInline(0, 0);
                this.container.setHorizontalScroll(null);
            }

            if (this.handlerInfo.isAllowOverflowY()) {
                this.container.setVerticalScroll(null);
            } else if (this.container.w == this.xSize - 6) {
                this.container.setVerticalScroll(VERTICAL_SCROLLBAR);
            } else {
                this.container.setVerticalScroll(
                        ScrollBar.defaultVerticalBar().setOverflowType(OverflowType.OVERLAY)
                                .setTrackPadding(-8, 0, 0, 0));
            }
        }

        this.recipeCatalyst.setCatalysts(RecipeCatalysts.getRecipeCatalysts(this.handler.original));
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

    public static ItemFilter getSearchItemFilter() {
        return GuiRecipe.searchField.getFilter();
    }

    protected void setRecipePage(int idx) {
        this.recipetype = (this.currenthandlers.size() + idx) % this.currenthandlers.size();
        this.handler = new SearchRecipeHandler<>(this.currenthandlers.get(this.recipetype));
        this.handlerInfo = GuiRecipeTab.getHandlerInfo(this.handler.original);
        updateContainerSize();
        this.handlerPages = new RecipePageManager(this.handler, this.handlerInfo, this.container);
        this.prevpage.enabled = this.nextpage.enabled = this.handlerPages.getNumPages() > 1;
        this.lastPage = -1;

        GuiRecipe.searchField.setText("");
        GuiRecipe.searchField.setVisible(false);
        GuiRecipe.toggleSearch.state = 0;

        this.container.setVerticalScrollOffset(0);
        this.container.setHorizontalScrollOffset(0);
        this.recipeTabs.update(this);
    }

    private void updateContainerSize() {
        final int width = this.xSize - 6;
        this.container.w = Math.max(width, this.handlerInfo.getWidth());
        this.container.h = this.ySize - 32 - 4;

        if (!this.handlerInfo.isAllowOverflowX() && this.handlerInfo.getWidth() > width) {
            this.container.w = width;
            this.container.h -= 8;
        }
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
        return this.handlerPages.getRecipeIndices();
    }

    @Override
    public void keyTyped(char c, int i) {
        handleKeyTyped(c, i);
    }

    /** Dispatch a mouse binding without invoking a subclass's keyboard-event handler. */
    public final void handleMouseKeybind(int keyCode) {
        handleKeyTyped('\0', keyCode);
    }

    private void handleKeyTyped(char c, int i) {

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
        } else if (KeyManager.isKeyDown("recipe.back")) {
            this.mc.displayGuiScreen(this.prevGui);
        } else if (KeyManager.isKeyDown("recipe.prev_machine")) {
            prevType();
        } else if (KeyManager.isKeyDown("recipe.next_machine")) {
            nextType();
        } else if (KeyManager.isKeyDown("recipe.prev_recipe")) {
            prevPage();
        } else if (KeyManager.isKeyDown("recipe.next_recipe")) {
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

        if (this.container.handleClick(mousex, mousey, button)) {
            return;
        }

        if (this.recipeTabs.mouseClicked(mousex, mousey, button)) {
            return;
        }

        if (button == 0 && isHandlerTitleHovered(mousex, mousey)) {
            if (this instanceof GuiCraftingRecipe) {
                GuiCraftingRecipe.openRecipeGui("all");
            } else if (this instanceof GuiUsageRecipe) {
                GuiUsageRecipe.openRecipeGui("all");
            }
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
        // GTNH NEI. So no old mods will use this.

        // First, invoke scroll handling over recipe handler tabbar. Makes sure it is
        // not overwritten by recipe
        // handler-specific scroll behavior.
        if (this.recipeTabs.mouseScrolled(scroll)) return;

        final Point mouse = GuiDraw.getMousePosition();

        if (this.recipeCatalyst.isShowWidget() && this.recipeCatalyst.onMouseWheel(scroll, mouse.x, mouse.y)) {
            return;
        }

        if (this.container.onMouseWheel(scroll, mouse.x, mouse.y)) {
            return;
        }

        // If shift is held, try switching to the next recipe handler. Replicates the
        // GuiRecipeTabs.mouseScrolled()
        // without the checking for the cursor being inside the tabbar.
        if (NEIClientUtils.shiftKey() || this.typeArea.contains(mouse)) {
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
                || this.pageArea.contains(mouse)
                        && (!this.handler.searchingAvailable() || !GuiRecipe.toggleSearch.contains(mouse.x, mouse.y))
                        && (!GuiRecipe.searchField.isVisible() || !GuiRecipe.searchField.contains(mouse.x, mouse.y))) {

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

        currenttip = this.container.handleTooltip(mousex, mousey, currenttip);

        this.recipeTabs.handleTooltip(mousex, mousey, currenttip);

        if (this.handler != null && this.handler.searchingAvailable()) {
            currenttip = GuiRecipe.toggleSearch.handleTooltip(mousex, mousey, currenttip);
        }

        if (currenttip.isEmpty() && isHandlerTitleHovered(mousex, mousey)) {
            currenttip.add(NEIClientUtils.translate("recipe.tab.view_all.tooltip"));
        }

        if (currenttip.isEmpty() && GuiRecipe.searchField.isVisible()
                && new Rectangle(GuiRecipe.searchField.x + GuiRecipe.searchField.w, 15, 44, 16)
                        .contains(mousex - this.guiLeft, mousey - this.guiTop)) {

            final String pageInfo = String
                    .format("%d/%d", this.handlerPages.getCurrentPageIndex() + 1, this.handlerPages.getNumPages());

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

        if (activeWidget instanceof RecipeWidget recipeWidget) {
            currenttip = recipeWidget.handleItemTooltip(itemstack, mousex, mousey, currenttip);
        }

        return currenttip;
    }

    @Override
    public Map<String, String> handleHotkeys(GuiContainer gui, int mousex, int mousey, Map<String, String> hotkeys) {

        if (this.recipeCatalyst.isShowWidget()) {
            hotkeys = this.recipeCatalyst.handleHotkeys(mousex, mousey, hotkeys);
        }

        hotkeys = this.container.handleHotkeys(mousex, mousey, hotkeys);

        return hotkeys;
    }

    private void nextPage() {
        this.handlerPages.changePage(1);
    }

    private void prevPage() {
        this.handlerPages.changePage(-1);
    }

    protected void nextType() {
        setRecipePage(++recipetype);
    }

    protected void prevType() {
        setRecipePage(--recipetype);
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
        this.container.draw(mouseX, mouseY);

        if (NEIClientConfig.getJEIStyleRecipeCatalysts() != 0 && this.recipeCatalyst.isShowWidget()) {
            this.recipeCatalyst.draw(mouseX, mouseY);
        }

        GL11.glTranslatef(guiLeft, guiTop, 0);
    }

    @Override
    public void drawGuiContainerBackgroundLayer(float f, int mouseX, int mouseY) {
        BG_TEXTURE.draw(
                this.guiLeft - TRANSPARENCY_BORDER,
                this.guiTop - TRANSPARENCY_BORDER,
                this.xSize + TRANSPARENCY_BORDER * 2,
                this.ySize + TRANSPARENCY_BORDER * 2,
                BORDER_PADDING + TRANSPARENCY_BORDER,
                BORDER_PADDING + TRANSPARENCY_BORDER,
                BORDER_PADDING + TRANSPARENCY_BORDER,
                BORDER_PADDING + TRANSPARENCY_BORDER);

        drawJEITabs(mouseX, mouseY);
    }

    private void drawJEITabs(int mouseX, int mouseY) {
        final int textMiddle = (BUTTON_WIDTH - this.fontRendererObj.FONT_HEIGHT) / 2;

        drawRect(
                this.typeArea.x,
                this.typeArea.y,
                this.typeArea.x + this.typeArea.width,
                this.typeArea.y + this.typeArea.height,
                0x30000000);
        drawRect(
                this.pageArea.x,
                this.pageArea.y,
                this.pageArea.x + this.pageArea.width,
                this.pageArea.y + this.pageArea.height,
                0x30000000);

        final String handlerTitle = this.handler.original.getRecipeName().trim();
        final int titleColor = getHandlerTitleColor(isHandlerTitleHovered(mouseX, mouseY));
        drawCenteredString(
                this.fontRendererObj,
                handlerTitle,
                this.guiLeft + this.xSize / 2,
                this.typeArea.y + textMiddle,
                titleColor);

        if (this.handler.searchingAvailable()) {
            GuiRecipe.toggleSearch.draw(mouseX, mouseY);
        }

        if (GuiRecipe.searchField.isVisible()) {
            GuiRecipe.searchField.draw(mouseX, mouseY);
        }

        if (GuiRecipe.searchField.isVisible()) {
            final String recipePage = NEIClientUtils.cropText(
                    this.fontRendererObj,
                    String.format(
                            "%d/%d",
                            this.handlerPages.getCurrentPageIndex() + 1,
                            this.handlerPages.getNumPages()),
                    45);
            drawCenteredString(
                    this.fontRendererObj,
                    recipePage,
                    GuiRecipe.searchField.x + GuiRecipe.searchField.w + 22,
                    this.pageArea.y + textMiddle,
                    0xffffff);
        } else {
            final String recipePage = NEIClientUtils.translate(
                    "recipe.page",
                    this.handlerPages.getCurrentPageIndex() + 1,
                    this.handlerPages.getNumPages());
            drawCenteredString(
                    this.fontRendererObj,
                    recipePage,
                    this.guiLeft + this.xSize / 2,
                    this.pageArea.y + textMiddle,
                    0xffffff);
        }

        if (NEIClientConfig.areJEIStyleTabsVisible()) {
            RenderHelper.enableGUIStandardItemLighting();
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
            this.recipeTabs.draw(mouseX, mouseY);
            RenderHelper.disableStandardItemLighting();
        }
    }

    private boolean isHandlerTitleHovered(int mousex, int mousey) {
        final String handlerTitle = this.handler.original.getRecipeName().trim();
        final int titleWidth = this.fontRendererObj.getStringWidth(handlerTitle);
        final int textMiddle = (BUTTON_WIDTH - this.fontRendererObj.FONT_HEIGHT) / 2;
        final int titleX = this.guiLeft + (this.xSize - titleWidth) / 2;
        final int titleY = this.prevtype.yPosition + textMiddle;
        return new Rectangle(titleX, titleY, titleWidth, this.fontRendererObj.FONT_HEIGHT).contains(mousex, mousey);
    }

    private int getHandlerTitleColor(boolean hovered) {
        return hovered ? ColorUtils.recipeTitleHover.getColor() : ColorUtils.recipeTitle.getColor();
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
