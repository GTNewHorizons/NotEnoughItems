package codechicken.nei.recipe;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.Button;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.drawable.DrawableResource;
import codechicken.nei.theme.UIRecipeTexture;
import codechicken.nei.theme.UIWidgetTexture;

public class GuiRecipeTabs {

    private final List<GuiRecipeTab> tabs = new ArrayList<>();
    private final List<Button> buttons = new ArrayList<>();
    private final Rectangle area = new Rectangle();
    private List<? extends IRecipeHandler> currenthandlers = Collections.emptyList();

    private int pageCount = 1;
    private int pageNumber = 0;
    private int categoriesPerPage = 1;
    private int numHandlers = 1;
    private int recipetype;

    private static class TabArrowButton extends Button {

        private final DrawableResource icon;
        private final BooleanSupplier action;

        private TabArrowButton(DrawableResource icon, BooleanSupplier action) {
            this.icon = icon;
            this.action = action;
            this.w = 8;
            this.h = 20;
        }

        @Override
        public boolean onButtonPress(boolean rightClick) {
            return !rightClick && this.action.getAsBoolean();
        }

        @Override
        public void draw(int mouseX, int mouseY) {
            UIWidgetTexture.BUTTON
                    .draw(this.x, this.y, this.w, this.h, this.icon, true, false, contains(mouseX, mouseY));
        }
    }

    private final Button prevTab = new TabArrowButton(UIRecipeTexture.ICON_PREV, this::previousPage);
    private final Button nextTab = new TabArrowButton(UIRecipeTexture.ICON_NEXT, this::nextPage);

    public static int getTabWidth() {
        return UIRecipeTexture.TAB_UNSELECTED.getWidth() - 1;
    }

    public static int getTabHeight() {
        return UIRecipeTexture.TAB_UNSELECTED.getHeight();
    }

    public void place(int navLeft, int navWidth, int guiTop) {
        final int tabWidth = getTabWidth();
        final int tabHeight = getTabHeight();
        final int perPage = (navWidth - 4) / tabWidth;

        this.area.width = perPage * tabWidth;
        this.area.height = tabHeight;

        this.area.x = navLeft + (navWidth - this.area.width) / 2;
        this.area.y = guiTop - tabHeight + 4;

        this.prevTab.y = this.nextTab.y = guiTop - this.prevTab.h + 1;
        this.prevTab.x = this.area.x - this.prevTab.w + 1;
        this.nextTab.x = this.area.x + this.area.width - 1;

        if (this.categoriesPerPage != perPage) {
            this.categoriesPerPage = perPage;
            rebuild();
        } else {
            refreshPage();
        }
    }

    public void update(List<? extends IRecipeHandler> handlers, int recipetype) {
        this.currenthandlers = handlers;
        this.recipetype = recipetype;

        rebuild();
    }

    private void rebuild() {
        this.numHandlers = this.currenthandlers.size();
        this.pageNumber = this.recipetype / Math.max(this.categoriesPerPage, 1);
        this.pageCount = (int) Math.ceil((float) this.numHandlers / this.categoriesPerPage);

        refreshPage();
        refreshArrows();
    }

    public void refreshPage() {
        final int startIndex = this.pageNumber * this.categoriesPerPage;
        final int tabWidth = getTabWidth();

        this.tabs.clear();

        for (int i = 0; i < this.categoriesPerPage && i + startIndex < numHandlers; i++) {
            final int index = i + startIndex;
            final IRecipeHandler handler = this.currenthandlers.get(index);
            final int tabX = this.area.x + (i * tabWidth);

            this.tabs.add(new GuiRecipeTab(handler, tabX, this.area.y) {

                @Override
                public boolean onButtonPress(boolean rightclick) {
                    setRecipePage(index);
                    return true;
                }

            });
        }
    }

    private void refreshArrows() {
        this.buttons.clear();

        if (this.numHandlers > this.categoriesPerPage) {
            this.buttons.add(this.prevTab);
            this.buttons.add(this.nextTab);
        }
    }

    protected void setRecipePage(int recipetype) {}

    public boolean nextPage() {
        this.pageNumber = (this.pageNumber + 1) % this.pageCount;
        refreshPage();
        return true;
    }

    public boolean previousPage() {
        this.pageNumber = (this.pageCount + this.pageNumber - 1) % this.pageCount;
        refreshPage();
        return true;
    }

    public void draw(int mouseX, int mouseY) {
        final int indexShift = this.pageNumber * this.categoriesPerPage;

        for (int index = 0; index < this.tabs.size(); index++) {
            final GuiRecipeTab tab = this.tabs.get(index);
            tab.setSelected(indexShift + index == this.recipetype);
            tab.draw(mouseX, mouseY);
        }

        for (Button button : this.buttons) {
            button.draw(mouseX, mouseY);
        }
    }

    public void handleTooltip(int mousex, int mousey, List<String> tooltips) {
        if (!NEIClientConfig.areJEIStyleTabsVisible()) return;

        for (Button button : this.buttons) {
            if (button.contains(mousex, mousey)) {
                button.addTooltips(tooltips);
                return;
            }
        }

        for (GuiRecipeTab tab : this.tabs) {
            if (tab.contains(mousex, mousey)) {
                tab.addTooltips(tooltips);
                return;
            }
        }
    }

    protected boolean mouseClicked(int x, int y, int mouseButton) {
        if (!NEIClientConfig.areJEIStyleTabsVisible()) return false;

        for (Button button : this.buttons) {
            if (button.contains(x, y)) {
                return button.handleClick(x, y, mouseButton);
            }
        }

        for (GuiRecipeTab tab : this.tabs) {
            if (tab.contains(x, y)) {
                return tab.onButtonPress(mouseButton == 1);
            }
        }

        return false;
    }

    protected boolean mouseScrolled(int scroll) {

        // Switch between recipe handlers if the cursor is over the tabbar.
        if (NEIClientConfig.areJEIStyleTabsVisible() && this.area.contains(GuiDraw.getMousePosition())) {
            setRecipePage(this.recipetype - scroll);
            return true;
        }

        return false;
    }
}
