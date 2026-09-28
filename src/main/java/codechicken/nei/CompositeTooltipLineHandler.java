package codechicken.nei;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import codechicken.lib.gui.GuiDraw;
import codechicken.lib.gui.GuiDraw.ITooltipLineHandler;

public abstract class CompositeTooltipLineHandler implements ITooltipLineHandler {

    protected static final int SCREEN_MARGIN = 8 * 2 + 10;
    protected static final int RESERVED_HEIGHT = 40;

    protected final List<ItemsTooltipLineHandler> lines = new ArrayList<>();
    protected final Dimension size = new Dimension();

    protected boolean created = false;
    protected int screenHeight = 0;

    protected abstract void createLines();

    protected boolean needsUpdate() {
        return false;
    }

    @Override
    public Dimension getSize() {
        ensureCreated();
        return this.size;
    }

    @Override
    public void draw(int x, int y) {
        if (this.size.height == 0) return;

        for (ItemsTooltipLineHandler line : this.lines) {
            line.draw(x, y);
            y += line.getSize().height;
        }
    }

    protected void ensureCreated() {
        boolean update = needsUpdate();
        update = this.screenHeight != (this.screenHeight = GuiDraw.displaySize().height) || update;

        if (this.created && !update) {
            return;
        }

        this.created = true;
        this.lines.clear();
        this.size.setSize(0, 0);

        createLines();
        fitToScreen();

        for (ItemsTooltipLineHandler line : this.lines) {
            this.size.width = Math.max(this.size.width, line.getSize().width);
            this.size.height += line.getSize().height;
        }
    }

    protected void fitToScreen() {
        if (this.lines.isEmpty()) {
            return;
        }

        final List<ItemsTooltipLineHandler> ordered = new ArrayList<>(this.lines);
        ordered.sort(Comparator.comparingInt(ItemsTooltipLineHandler::getRows));

        int budget = availableHeight() / ItemsTooltipLineHandler.SLOT_SIZE;
        int rest = ordered.size();

        for (ItemsTooltipLineHandler line : ordered) {
            final int fair = Math.max(1, budget / rest);

            if (line.getRows() > fair) {
                line.setMaxRows(fair);
            }

            budget -= line.getRows();
            rest--;
        }
    }

    protected int availableHeight() {
        return GuiDraw.displaySize().height - SCREEN_MARGIN
                - RESERVED_HEIGHT
                - this.lines.size() * ItemsTooltipLineHandler.headerHeight();
    }

    protected static int maxLineRows() {
        final int height = GuiDraw.displaySize().height - SCREEN_MARGIN
                - RESERVED_HEIGHT
                - ItemsTooltipLineHandler.headerHeight();
        return Math.max(1, height / ItemsTooltipLineHandler.SLOT_SIZE);
    }

}
