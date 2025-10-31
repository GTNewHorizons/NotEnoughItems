package codechicken.nei.recipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import codechicken.nei.NEIClientConfig;
import codechicken.nei.Widget;
import codechicken.nei.scroll.ScrollContainer;

public class RecipePageManager {

    private SearchRecipeHandler<?> handler;
    private ScrollContainer container;
    private HandlerInfo handlerInfo;

    private String cacheKey = "";
    private int currentPageIndex = -1;
    private List<Integer> recipeIndices = new ArrayList<>();
    private List<List<Widget>> pageWidgets = new ArrayList<>();

    public RecipePageManager(SearchRecipeHandler<?> handler, HandlerInfo handlerInfo, ScrollContainer container) {
        this.handler = handler;
        this.handlerInfo = handlerInfo;
        this.container = container;

        rebuildPages();
    }

    public boolean rebuildPages() {
        final String cacheKey = handlerInfo.getHandlerName() + ":"
                + getNumRecipes()
                + ":"
                + this.container.getVisibleHeight()
                + ":"
                + GuiRecipe.searchField.text();

        if (cacheKey.equals(this.cacheKey)) {
            return false;
        }

        this.cacheKey = cacheKey;
        this.pageWidgets.clear();

        final boolean useCustomScroll = this.handlerInfo.getUseCustomScroll();
        final int handlerHeight = this.handlerInfo.getHeight() + this.handlerInfo.getYShift();
        final boolean multipleWidgetsAllowed = !useCustomScroll && handlerInfo.isMultipleWidgetsAllowed();
        final boolean infiniteScroll = !useCustomScroll
                && NEIClientConfig.getBooleanSetting("inventory.guirecipe.infiniteScroll");

        final int heightAvailable = this.container.getVisibleHeight();
        final int width = Math.max(166, this.handlerInfo.getWidth());
        final int numRecipes = getNumRecipes();
        List<Widget> widgets = new ArrayList<>();
        int shiftY = 0;

        for (int i = 0; i < numRecipes; i++) {
            final int recipeIndex = this.handler.ref(i);
            final NEIRecipeWidget widget = RecipeHandlerRef.of(this.handler.original, recipeIndex).getRecipeWidget();
            widget.w = width;

            if (useCustomScroll) {
                widget.h = Math.min(handlerHeight, heightAvailable);
            }

            if (!widgets.isEmpty()
                    && (!multipleWidgetsAllowed || !infiniteScroll && shiftY + widget.h > heightAvailable)) {
                this.pageWidgets.add(widgets);
                widgets = new ArrayList<>();
                shiftY = 0;
            }

            widgets.add(widget);
            shiftY += widget.h;
        }

        if (!widgets.isEmpty()) {
            this.pageWidgets.add(widgets);
        }

        changePage(0);

        return true;
    }

    public boolean isMultipleWidgetsAllowed() {
        return !this.handlerInfo.getUseCustomScroll() && this.handlerInfo.isMultipleWidgetsAllowed();
    }

    public int getNumRecipes() {
        return this.handler.numRecipes();
    }

    public int getCurrentPageIndex() {
        return this.currentPageIndex;
    }

    public int getNumPages() {
        return this.pageWidgets.size();
    }

    public void changePage(int delta) {
        final int numPages = getNumPages();
        final int page = numPages > 0 ? (numPages + Math.max(0, this.currentPageIndex) + delta) % numPages : 0;

        if (this.currentPageIndex == page) {
            return;
        }

        this.currentPageIndex = page;
        this.recipeIndices = new ArrayList<>();

        for (Widget widget : getCurrentPageWidgets()) {
            if (widget instanceof NEIRecipeWidget recipeWidget) {
                this.recipeIndices.add(recipeWidget.getRecipeHandlerRef().recipeIndex);
            }
        }

        this.container.setVerticalScrollOffset(0);
    }

    public void gotoRefIndex(int refIndex) {
        int page = 0;

        for (int i = 0; i < this.pageWidgets.size(); i++) {
            refIndex -= this.pageWidgets.get(i).size();
            if (refIndex < 0) {
                page = i;
                break;
            }
        }

        changePage(page);
    }

    public List<Widget> getCurrentPageWidgets() {
        if (this.pageWidgets.isEmpty()) {
            return Collections.emptyList();
        }

        List<Widget> currentWidgets = this.pageWidgets.get(this.currentPageIndex);
        int shiftY = 0;

        for (Widget widget : currentWidgets) {
            if (widget instanceof NEIRecipeWidget recipeWidget) {
                recipeWidget.setLocation(0, shiftY);
            }
            shiftY += widget.h;
        }

        return currentWidgets;
    }

    public List<Integer> getRecipeIndices() {
        return this.recipeIndices;
    }

}
