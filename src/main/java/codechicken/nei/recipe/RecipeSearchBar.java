package codechicken.nei.recipe;

import java.util.List;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.RecipeSearchField;
import codechicken.nei.RestartableTask;
import codechicken.nei.SearchField;
import codechicken.nei.SearchTokenParser.ISearchParserProvider;
import codechicken.nei.SearchTokenParser.SearchMode;
import codechicken.nei.api.IRecipeFilter;
import codechicken.nei.api.ItemFilter;
import codechicken.nei.search.SearchExpressionUtils;
import codechicken.nei.theme.UIRecipeTexture;

class RecipeSearchBar extends NavigationBar {

    private static final int COUNTER_WIDTH = 45;
    private static final int PLATE_OVERHANG = 5;

    private final RestartableTask updateFilter = new RestartableTask("NEI Recipe Filtering") {

        @Override
        public void execute() {
            if (!(NEIClientUtils.mc().currentScreen instanceof GuiRecipe<?>guiRecipe)) return;

            final SearchRecipeHandler<?> searchHandler = guiRecipe.getSearchHandler();
            if (searchHandler == null) return;

            final IRecipeFilter filter = getRecipeFilter();
            final List<Integer> found = filter != null ? searchHandler.getSearchResult(filter) : null;

            if (interrupted() || filter != null && found == null) return;
            searchHandler.setSearchIndices(found);
        }

    };

    private final RecipeSearchField field = new RecipeSearchField("") {

        @Override
        protected boolean noResults() {
            final GuiScreen currentScreen = NEIClientUtils.mc().currentScreen;
            return !(currentScreen instanceof GuiRecipe<?>guiRecipe) || guiRecipe.getNumRecipes() > 0;
        }

        @Override
        public void onTextChange(String oldText) {
            if (!text().equals(oldText)) {
                RecipeSearchBar.this.updateFilter.restart();
            }
        }

    };

    private final NavButton toggle = new NavButton(UIRecipeTexture.ICON_SEARCH, () -> true, this::toggleField) {

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
                tooltip.add(
                        NEIClientUtils.translate(
                                "recipe.search.tooltip.text",
                                SearchExpressionUtils.HIGHLIGHTS.RECIPE + "~"));

                final int patternMode = NEIClientConfig.getIntSetting("inventory.search.patternMode");

                if (patternMode != 2) {
                    tooltip.add(
                            NEIClientUtils.translate(
                                    "recipe.search.tooltip.auxiliary",
                                    SearchExpressionUtils.HIGHLIGHTS.RECIPE + "^"));
                }

                if (patternMode == 3) {
                    tooltip.add(
                            NEIClientUtils.translate(
                                    "recipe.search.tooltip.all",
                                    SearchExpressionUtils.HIGHLIGHTS.RECIPE + "<&"));
                }

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

                if (patternMode == 1 || patternMode == 3) {
                    tooltip.add(
                            NEIClientUtils.translate(
                                    "recipe.search.tooltip.regex",
                                    SearchExpressionUtils.HIGHLIGHTS.REGEX + "r/",
                                    SearchExpressionUtils.HIGHLIGHTS.REGEX + "/"));
                }
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

    private boolean available = false;
    private String pageInfo = "";
    private String pageCounter = "";
    private int pageCounterWidth;
    private boolean pageInfoCropped;
    private String pageLabel = "";

    public RecipeSearchBar(Runnable onPrev, Runnable onNext) {
        super(null, onPrev, onNext);
    }

    public void setPages(int current, int total) {
        final FontRenderer font = NEIClientUtils.mc().fontRenderer;
        this.pageInfo = current + "/" + total;
        this.pageCounter = NEIClientUtils.cropText(font, this.pageInfo, COUNTER_WIDTH);
        this.pageCounterWidth = font.getStringWidth(this.pageCounter);
        this.pageInfoCropped = font.getStringWidth(this.pageInfo) >= COUNTER_WIDTH;
        this.pageLabel = NEIClientUtils.translate("recipe.page", current, total);
        updateLabel();
    }

    private void updateLabel() {
        this.label = this.field.isVisible() ? "" : this.pageLabel;
    }

    private void toggleField() {
        if (this.field.isVisible()) {
            this.field.setText("");
            this.field.setFocus(false);
            this.field.setVisible(false);
            this.toggle.state = 0;
        } else {
            this.field.setVisible(true);
            this.field.setFocus(true);
            this.toggle.state = 2;
        }

        updateLabel();
    }

    public void setAvailable(boolean available) {
        this.available = available;
        this.insetLeft = available ? this.toggle.w + GAP : 0;
    }

    public void place(int x, int y, int w) {
        super.place(x, y, w);

        this.toggle.x = plateX();
        this.toggle.y = y;

        this.field.x = this.toggle.x + this.toggle.w + 1;
        this.field.y = y - 1;
        this.field.w = plateWidth() + PLATE_OVERHANG - this.toggle.w - COUNTER_WIDTH;
        this.field.h = 14;
    }

    public ItemFilter getFilter() {
        return this.field.getFilter();
    }

    public IRecipeFilter getRecipeFilter() {
        return this.field.text().isEmpty() ? null : this.field.getRecipeFilter();
    }

    public RecipeSearchField getSearchField() {
        return this.field;
    }

    public void updateFilter() {
        this.updateFilter.restart();
    }

    public void reset() {
        this.field.setText("");
        this.field.setVisible(false);
        this.toggle.state = 0;
        updateLabel();
        updateFilter();
    }

    public boolean overSearch(int px, int py) {
        return this.available && this.toggle.contains(px, py) || this.field.isVisible() && this.field.contains(px, py);
    }

    @Override
    public boolean contains(int px, int py) {
        return super.contains(px, py) || overSearch(px, py);
    }

    @Override
    public void draw(int mx, int my) {
        super.draw(mx, my);

        if (this.available) {
            this.toggle.draw(mx, my);
        }

        if (this.field.isVisible()) {
            this.field.draw(mx, my);

            final FontRenderer font = NEIClientUtils.mc().fontRenderer;
            final int textY = this.y + (this.h - font.FONT_HEIGHT) / 2 + 1;
            font.drawStringWithShadow(
                    this.pageCounter,
                    this.field.x + this.field.w + 22 - this.pageCounterWidth / 2,
                    textY,
                    0xffffff);
        }
    }

    @Override
    public boolean handleClick(int mx, int my, int button) {
        if (this.available) {

            if (this.toggle.contains(mx, my)) {
                return this.toggle.handleClick(mx, my, button);
            } else if (this.field.isVisible() && this.field.contains(mx, my)) {
                return this.field.handleClick(mx, my, button);
            }

            this.field.onGuiClick(mx, my);
        }

        return super.handleClick(mx, my, button);
    }

    @Override
    public boolean handleKeyPress(int keyID, char keyChar) {
        return isFocused() && this.field.handleKeyPress(keyID, keyChar);
    }

    @Override
    public boolean isFocused() {
        return this.field.isVisible() && this.field.focused();
    }

    @Override
    public void loseFocus() {
        this.field.setFocus(false);
    }

    @Override
    public void lastKeyTyped(int keyID, char keyChar) {
        if (isFocused()) {
            this.field.lastKeyTyped(keyID, keyChar);
        }
    }

    @Override
    public boolean onHotkeyPressed() {
        return this.field.isVisible() && this.field.onHotkeyPressed();
    }

    @Override
    public List<String> handleTooltip(int mx, int my, List<String> tooltip) {

        if (this.available) {
            tooltip = this.toggle.handleTooltip(mx, my, tooltip);
        }

        if (tooltip.isEmpty() && this.field.isVisible()
                && mx >= this.field.x + this.field.w
                && mx < plateX() + plateWidth()
                && my >= this.y
                && my < this.y + this.h
                && this.pageInfoCropped) {
            tooltip.add(this.pageInfo);
        }

        return tooltip;
    }

    public boolean handleDragNDrop(int mx, int my, ItemStack draggedStack) {

        if (this.field.isVisible() && this.field.contains(mx, my)) {
            this.field.setText(SearchField.getEscapedSearchText(draggedStack));
            return true;
        }

        return false;
    }
}
