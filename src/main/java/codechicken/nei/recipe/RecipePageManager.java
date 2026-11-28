package codechicken.nei.recipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;

import codechicken.nei.NEIClientConfig;
import codechicken.nei.Widget;
import codechicken.nei.recipe.widget.RecipeWidget;
import codechicken.nei.recipe.widget.ViewStates;
import codechicken.nei.scroll.ScrollContainer;

public class RecipePageManager {

    private static final int RECIPE_GAP = 4;

    private static final class Page {

        private final List<Widget> widgets;
        private final int width;
        private final boolean overflowY;
        private int top;
        private int gap;

        private Page(List<Widget> widgets, int width, boolean overflowY, int gap) {
            this.widgets = widgets;
            this.width = width;
            this.overflowY = overflowY;
            this.gap = gap;
        }
    }

    private SearchRecipeHandler<?> handler;
    private ScrollContainer container;
    private HandlerInfo handlerInfo;

    private int numRecipes = -1;
    private int searchVersion = -1;
    private int visibleHeight = -1;
    private int currentPageIndex = -1;
    private final List<RecipeWidget> recipeWidgets = new ArrayList<>();
    private final List<Page> pages = new ArrayList<>();
    private final ViewStates viewStates;
    private int maxRecipeWidth;
    private boolean overflowY;
    private final int recipeGap;

    public RecipePageManager(SearchRecipeHandler<?> handler, HandlerInfo handlerInfo, ScrollContainer container,
            ViewStates viewStates) {
        this.handler = handler;
        this.handlerInfo = handlerInfo;
        this.container = container;
        this.viewStates = viewStates;
        this.recipeGap = handlerInfo.isRecipeSpacing() ? RECIPE_GAP : 0;

        rebuildWidgets();
    }

    public boolean rebuildPages() {
        final boolean widgetsChanged = this.numRecipes != getNumRecipes()
                || this.searchVersion != this.handler.getSearchVersion();
        final int visibleHeight = this.container.getVisibleHeight();

        if (!widgetsChanged && visibleHeight == this.visibleHeight) {
            return false;
        }

        this.visibleHeight = visibleHeight;

        if (widgetsChanged) {
            rebuildWidgets();
        }

        this.pages.clear();
        this.overflowY = false;

        buildPages();
        buildSpreadGaps();
        changePage(0);

        return true;
    }

    private void rebuildWidgets() {
        this.numRecipes = getNumRecipes();
        this.searchVersion = this.handler.getSearchVersion();
        this.recipeWidgets.clear();

        for (List<Integer> group : groupRecipes()) {
            this.recipeWidgets.add(
                    new RecipeWidget(this.handler.original, group, this.viewStates.get(this.handler.original, group)));
        }

        this.maxRecipeWidth = this.recipeWidgets.stream().mapToInt(widget -> widget.w).max()
                .orElse(this.handlerInfo.getWidth());
    }

    private List<List<Integer>> groupRecipes() {
        final List<Integer> searchRecipes = this.handler.getFilteredRecipes();
        final List<List<Integer>> groupedRecipes = new ArrayList<>();
        final Map<Object, Integer> groupEndIndices = new HashMap<>();

        for (int recipeIndex : searchRecipes) {
            final Object groupId = this.handler.original.getRecipeGroupId(recipeIndex);
            final int groupIndex = groupId != null
                    ? groupEndIndices.computeIfAbsent(groupId, k -> groupedRecipes.size())
                    : groupedRecipes.size();

            if (groupedRecipes.size() <= groupIndex) {
                groupedRecipes.add(new ArrayList<>());
            }

            groupedRecipes.get(groupIndex).add(recipeIndex);
        }

        return groupedRecipes;
    }

    private void buildPages() {
        final boolean multipleWidgetsAllowed = isMultipleWidgetsAllowed();
        final boolean infiniteScroll = isInfiniteScroll();
        final int heightAvailable = this.container.getVisibleHeight();
        List<Widget> widgets = new ArrayList<>();
        int shiftY = 0;

        for (RecipeWidget widget : this.recipeWidgets) {
            if (this.handlerInfo.isAllowOverflowY()) {
                widget.setHeight(Math.min(widget.getContentHeight(), heightAvailable));
            }

            if (!widgets.isEmpty() && (!multipleWidgetsAllowed
                    || !infiniteScroll && shiftY + this.recipeGap + widget.h > heightAvailable)) {
                addPage(widgets);
                widgets = new ArrayList<>();
                shiftY = 0;
            }

            if (!widgets.isEmpty()) {
                shiftY += this.recipeGap;
            }

            widgets.add(widget);
            shiftY += widget.h;
        }

        if (!widgets.isEmpty()) {
            addPage(widgets);
        }
    }

    private void addPage(List<Widget> widgets) {
        final int pageWidth = widgets.stream().mapToInt(widget -> widget.w).max().orElse(0);
        final int pageHeight = widgets.stream().mapToInt(widget -> widget.h).sum()
                + this.recipeGap * (widgets.size() - 1);

        final boolean pageOverflowsY = pageHeight > this.container.getVisibleHeight();

        this.overflowY |= pageOverflowsY;
        this.pages.add(new Page(widgets, pageWidth, pageOverflowsY, this.recipeGap));
    }

    public boolean needsVerticalScroll() {
        return this.overflowY;
    }

    private Page getCurrentPage() {
        return this.currentPageIndex >= 0 && this.currentPageIndex < this.pages.size()
                ? this.pages.get(this.currentPageIndex)
                : null;
    }

    public boolean currentPageNeedsVerticalScroll() {
        final Page page = getCurrentPage();
        return page != null && page.overflowY;
    }

    private void buildSpreadGaps() {
        if (!isMultipleWidgetsAllowed() || isInfiniteScroll()
                || !NEIClientConfig.getBooleanSetting("inventory.guirecipe.evenRecipeSpacing")) {
            return;
        }

        final int heightAvailable = this.container.getVisibleHeight();
        int maxHeight = 0;

        for (Page page : this.pages) {
            for (Widget widget : page.widgets) {
                maxHeight = Math.max(maxHeight, widget.h);
            }
        }

        final int slots = maxHeight > 0 ? (heightAvailable + this.recipeGap) / (maxHeight + this.recipeGap) : 0;

        // a page that fits a single recipe has nothing to spread
        if (slots <= 1) {
            return;
        }

        for (int index = 0; index < this.pages.size(); index++) {
            final Page page = this.pages.get(index);
            int totalHeight = 0;

            for (Widget widget : page.widgets) {
                totalHeight += widget.h;
            }

            int spreadGap = (heightAvailable - totalHeight) / (page.widgets.size() + 1);

            if (index == this.pages.size() - 1) {
                final int fullPageGap;

                if (index > 0) {
                    fullPageGap = this.pages.get(index - 1).top;
                } else {
                    fullPageGap = (heightAvailable - slots * maxHeight) / (slots + 1);
                }

                spreadGap = Math.min(spreadGap, fullPageGap);
            }

            if (spreadGap >= this.recipeGap) {
                page.top = spreadGap;
                page.gap = spreadGap;
            }
        }
    }

    private boolean isInfiniteScroll() {
        return !this.handlerInfo.isAllowOverflowY()
                && NEIClientConfig.getBooleanSetting("inventory.guirecipe.infiniteScroll");
    }

    public boolean isMultipleWidgetsAllowed() {
        return !this.handlerInfo.isAllowOverflowY() && this.handlerInfo.isMultipleWidgetsAllowed();
    }

    public int getNumRecipes() {
        return this.handler.numRecipes();
    }

    public int getCurrentPageIndex() {
        return this.currentPageIndex;
    }

    public int getNumPages() {
        return this.pages.size();
    }

    public int getMaxRecipeWidth() {
        return this.maxRecipeWidth;
    }

    public int getCurrentPageWidth() {
        final Page page = getCurrentPage();
        return page != null ? page.width : 0;
    }

    public void changePage(int delta) {
        final int numPages = getNumPages();
        final int page = numPages > 0 ? (numPages + Math.max(0, this.currentPageIndex) + delta) % numPages : 0;

        if (this.currentPageIndex == page) {
            return;
        }

        this.currentPageIndex = page;
        this.container.setVerticalScrollOffset(0);
    }

    public boolean gotoRecipeIndex(int recipeIndex) {

        for (int page = 0; page < this.pages.size(); page++) {
            for (Widget widget : this.pages.get(page).widgets) {
                if (widget instanceof RecipeWidget recipeWidget && recipeWidget.containsRecipeIndex(recipeIndex)) {
                    changePage(page - this.currentPageIndex);
                    return true;
                }
            }
        }

        return false;
    }

    public boolean pinRecipe(int recipeIndex, List<ItemStack> ingredients) {

        for (Page page : this.pages) {
            for (Widget widget : page.widgets) {
                if (widget instanceof RecipeWidget recipeWidget && recipeWidget.containsRecipeIndex(recipeIndex)) {
                    return recipeWidget.pinRecipe(recipeIndex, ingredients);
                }
            }
        }

        return false;
    }

    public List<Widget> getCurrentPageWidgets() {

        if (this.pages.isEmpty()) {
            return Collections.emptyList();
        }

        final Page page = this.pages.get(this.currentPageIndex);
        final int placeWidth = Math.max(this.container.w, page.width);
        int shiftY = page.top;

        for (Widget widget : page.widgets) {
            if (widget instanceof RecipeWidget recipeWidget) {
                final int offsetX = (placeWidth - widget.w) / 2;
                recipeWidget.setLocation(offsetX, shiftY, placeWidth - widget.w - offsetX);
            }
            shiftY += widget.h + page.gap;
        }

        return page.widgets;
    }

    public List<Integer> getRecipeIndices() {

        if (this.pages.isEmpty()) {
            return Collections.emptyList();
        }

        final List<Integer> recipeIndices = new ArrayList<>();

        for (Widget widget : this.pages.get(this.currentPageIndex).widgets) {
            if (widget instanceof RecipeWidget recipeWidget) {
                recipeIndices.add(recipeWidget.getRecipeHandlerRef().recipeIndex);
            }
        }

        return recipeIndices;
    }

}
