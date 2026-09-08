package codechicken.nei.bookmark.tree;

import static codechicken.lib.gui.GuiDraw.drawStringC;

import org.lwjgl.opengl.GL11;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.LayoutManager;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.bookmark.tree.CraftingTreeState.StatsSection;

class StatsSectionsMenu {

    private static final StatsSection[] SECTIONS = StatsSection.values();

    private static final int ROW_HEIGHT = 16;
    private static final int LABEL_PADDING = 10;
    private static final int MIN_WIDTH = 70;
    private static final int TOP_PADDING = 4;
    private static final int Z_OFFSET = 500;

    private static final int COLOR_HOVER = 0xFFFFFFA0;
    private static final int COLOR_ENABLED = 0xFFE0E0E0;
    private static final int COLOR_DISABLED = 0xFFA0A0A0;

    public boolean visible = false;

    private int x;
    private int y;
    private int w;
    private int h;

    public void open(int anchorX, int anchorY) {
        this.visible = true;

        int maxLabelWidth = 0;

        for (StatsSection section : SECTIONS) {
            maxLabelWidth = Math.max(maxLabelWidth, GuiDraw.fontRenderer.getStringWidth(label(section)));
        }

        this.w = Math.max(MIN_WIDTH, maxLabelWidth + LABEL_PADDING * 2);
        this.h = TOP_PADDING + SECTIONS.length * ROW_HEIGHT;
        this.x = anchorX;
        this.y = anchorY;
    }

    public void close() {
        this.visible = false;
    }

    public boolean isInWidget(int mouseX, int mouseY) {
        return this.visible && mouseX >= this.x
                && mouseX < this.x + this.w
                && mouseY >= this.y
                && mouseY < this.y + this.h;
    }

    public boolean handleClick(int mouseX, int mouseY, CraftingTreeState uiState) {

        if (!isInWidget(mouseX, mouseY)) {
            return false;
        }

        final int relativeY = mouseY - this.y - TOP_PADDING;

        if (relativeY >= 0) {
            final int index = relativeY / ROW_HEIGHT;

            if (index < SECTIONS.length) {
                uiState.toggleStatsSection(SECTIONS[index]);
            }
        }

        return true;
    }

    public void draw(int mouseX, int mouseY, CraftingTreeState uiState) {

        if (!this.visible) {
            return;
        }

        GL11.glTranslatef(0, 0, Z_OFFSET);

        for (int i = 0; i < SECTIONS.length; i++) {
            final StatsSection section = SECTIONS[i];
            final int rowY = this.y + TOP_PADDING + i * ROW_HEIGHT;
            final boolean hover = mouseX >= this.x && mouseX < this.x + this.w
                    && mouseY >= rowY
                    && mouseY < rowY + ROW_HEIGHT;
            final boolean checked = uiState.visibleStatsSections.contains(section);
            final int color = hover ? COLOR_HOVER : checked ? COLOR_ENABLED : COLOR_DISABLED;

            if (checked) {
                GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                LayoutManager.drawButtonBackground(this.x, rowY, this.w, ROW_HEIGHT, false, 1);
            } else {
                GL11.glColor4f(0.65F, 0.65F, 0.65F, 1.0F);
                LayoutManager.drawButtonBackground(this.x, rowY, this.w, ROW_HEIGHT, false, 0);
            }

            drawStringC(label(section), this.x, rowY, this.w, ROW_HEIGHT, color);
        }

        GL11.glColor4f(1, 1, 1, 1);
        GL11.glTranslatef(0, 0, -Z_OFFSET);
    }

    private static String label(StatsSection section) {
        return NEIClientUtils.translate(switch (section) {
            case INGREDIENTS_NEEDED -> "bookmark.tree.stats.ingredients_needed";
            case INGREDIENTS_AVAILABLE -> "bookmark.tree.stats.ingredients_available";
            case CRAFTING_NEEDED -> "bookmark.tree.stats.crafting_needed";
            case CRAFTING_AVAILABLE -> "bookmark.tree.stats.crafting_available";
            case RESULTS -> "bookmark.tree.stats.results";
            case REMAINDERS -> "bookmark.tree.stats.remainders";
            case HANDLERS -> "bookmark.tree.stats.handlers";
        });
    }
}
