package codechicken.nei.recipe;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.Button;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.NEIClientUtils;

public class GuiRecipeTabs {

    private final List<GuiRecipeTab> tabs = new ArrayList<>();
    private final List<Button> buttons = new ArrayList<>();
    private final Rectangle area = new Rectangle();
    private List<? extends IRecipeHandler> currenthandlers;

    private int pageCount = 1;
    private int pageNumber = 0;
    private int categoriesPerPage = 1;
    private int numHandlers = 1;
    private int recipetype;
    private GuiRecipe<?> currentGui;

    private final Button prevTab = new Button("<") {

        @Override
        public boolean onButtonPress(boolean rightClick) {
            if (!rightClick) {
                NEIClientUtils.playClickSound();
                return previousPage();
            } else return false;
        }

    };

    private final Button nextTab = new Button(">") {

        @Override
        public boolean onButtonPress(boolean rightclick) {
            if (!rightclick) {
                NEIClientUtils.playClickSound();
                return nextPage();
            } else return false;
        }

    };

    public static int getTabWidth() {
        return NEIClientConfig.useCreativeTabStyle() ? GuiRecipeTabCreative.TAB_WIDTH : GuiRecipeTabJEI.TAB_WIDTH;
    }

    public static int getTabHeight() {
        return NEIClientConfig.useCreativeTabStyle() ? GuiRecipeTabCreative.TAB_HEIGHT : GuiRecipeTabJEI.TAB_HEIGHT;
    }

    public void update(GuiRecipe<?> guiRecipe) {
        final int tabWidth = getTabWidth();
        final int tabHeight = getTabHeight();

        this.currentGui = guiRecipe;
        this.recipetype = guiRecipe.recipetype;
        this.currenthandlers = guiRecipe.currenthandlers;
        this.numHandlers = this.currenthandlers.size();
        this.categoriesPerPage = (guiRecipe.xSize - 4) / tabWidth;
        this.pageNumber = this.recipetype / Math.max(this.categoriesPerPage, 1);
        this.pageCount = (int) Math.ceil((float) this.numHandlers / this.categoriesPerPage);

        this.area.width = this.categoriesPerPage * tabWidth;
        this.area.height = tabHeight;
        this.area.x = guiRecipe.guiLeft + 4;
        this.area.y = guiRecipe.guiTop - tabHeight + 3;

        refreshPage();
        updateArrows(tabHeight);
    }

    public void refreshPage() {
        final int startIndex = this.pageNumber * this.categoriesPerPage;
        final int tabWidth = getTabWidth();

        this.tabs.clear();

        for (int i = 0; i < this.categoriesPerPage && i + startIndex < numHandlers; i++) {
            final int index = i + startIndex;
            final IRecipeHandler handler = this.currenthandlers.get(index);
            final int tabX = this.area.x + (i * tabWidth);

            if (NEIClientConfig.useCreativeTabStyle()) {
                tabs.add(new GuiRecipeTabCreative(handler, tabX, this.area.y) {

                    @Override
                    public boolean onButtonPress(boolean rightclick) {
                        setRecipePage(index);
                        return true;
                    }

                });
            } else {
                tabs.add(new GuiRecipeTabJEI(handler, tabX, this.area.y) {

                    @Override
                    public boolean onButtonPress(boolean rightclick) {
                        setRecipePage(index);
                        return true;
                    }

                });
            }
        }
    }

    private void updateArrows(int tabHeight) {
        this.buttons.clear();

        if (this.numHandlers > this.categoriesPerPage) {
            this.prevTab.w = this.nextTab.w = 8;
            this.prevTab.h = this.nextTab.h = tabHeight - 4;
            this.prevTab.y = this.nextTab.y = this.area.y + 2;

            this.prevTab.x = this.area.x - this.prevTab.w + 1;
            this.nextTab.x = this.area.x + this.area.width - 1;

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

        for (GuiRecipeTab tab : this.tabs) {
            if (tab.contains(mousex, mousey)) {
                tab.addTooltips(tooltips);
            }
        }
        for (Button button : this.buttons) {
            if (button.contains(mousex, mousey)) {
                button.addTooltips(tooltips);
            }
        }
    }

    protected boolean mouseClicked(int x, int y, int mouseButton) {
        if (!NEIClientConfig.areJEIStyleTabsVisible()) return false;

        for (Button button : this.buttons) {
            if (button.contains(x, y)) {
                return button.onButtonPress(mouseButton == 1);
            }
        }

        for (GuiRecipeTab tab : this.tabs) {
            if (tab.contains(x, y)) {
                if (mouseButton == 0 && NEIClientUtils.altKey()) {
                    openAllRecipesForHandler(tab);
                    return true;
                }
                return tab.onButtonPress(mouseButton == 1);
            }
        }

        return false;
    }

    private void openAllRecipesForHandler(GuiRecipeTab tab) {
        if (currentGui == null) {
            return;
        }
        if (currentGui instanceof GuiCraftingRecipe) {
            GuiCraftingRecipe.openAllRecipesGuiForHandler(tab.getHandlerName(), tab.getHandlerOverlayId());
        } else if (currentGui instanceof GuiUsageRecipe) {
            GuiUsageRecipe.openAllRecipesGuiForHandler(tab.getHandlerName(), tab.getHandlerOverlayId());
        }
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
