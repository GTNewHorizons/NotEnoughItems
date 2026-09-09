package codechicken.nei.bookmark.tree;

import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

import org.lwjgl.opengl.GL11;

import codechicken.nei.GuiNEIButton;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.drawable.DrawableBuilder;
import codechicken.nei.drawable.DrawableResource;

class CraftingTreeToolbar {

    private static class GuiStatsButton extends GuiNEIButton {

        private final Supplier<DrawableResource> icon;
        private final BooleanSupplier isEnabled;
        private final Supplier<String> tooltip;

        public GuiStatsButton(int buttonId, Supplier<DrawableResource> icon, Supplier<String> tooltip,
                BooleanSupplier isEnabled) {
            super(buttonId, 0, 0, 16, 16, "");
            this.tooltip = tooltip;
            this.icon = icon;
            this.isEnabled = isEnabled;
        }

        public String getTooltip() {
            return this.tooltip.get();
        }

        @Override
        public int getHoverState(boolean mouseOver) {
            return this.isEnabled.getAsBoolean() ? 0 : super.getHoverState(mouseOver);
        }

        @Override
        protected void drawContent(Minecraft minecraft, int y, int x, boolean mouseOver) {
            final DrawableResource icon = this.icon.get();
            final int iconX = this.xPosition + (this.width - icon.width / 2) / 2;
            final int iconY = this.yPosition + (this.height - icon.height / 2) / 2;

            GL11.glPushMatrix();
            GL11.glColor4f(1, 1, 1, 1);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glTranslated(iconX, iconY, 0);
            GL11.glScalef(0.5f, 0.5f, 1);

            icon.bindTexture();
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            icon.draw(0, 0);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);

            GL11.glDisable(GL11.GL_BLEND);
            GL11.glPopMatrix();
        }

    }

    private static final DrawableResource STATS_TEXTURE = new DrawableBuilder(
            "nei:textures/gui/craftingtree.png",
            2,
            66,
            16,
            16).build();

    private static final DrawableResource COMPACT_STATS_TEXTURE = new DrawableBuilder(
            "nei:textures/gui/craftingtree.png",
            20,
            66,
            16,
            16).build();

    private static final DrawableResource FIT_TEXTURE = new DrawableBuilder(
            "nei:textures/gui/craftingtree.png",
            38,
            66,
            16,
            16).build();

    private static final DrawableResource INV_TEXTURE = new DrawableBuilder(
            "nei:textures/gui/craftingtree.png",
            56,
            66,
            16,
            16).build();

    private static final DrawableResource LINK_TEXTURE = new DrawableBuilder(
            "nei:textures/gui/craftingtree.png",
            74,
            66,
            16,
            16).build();

    private static final DrawableResource ICON_COLLAPSED = new DrawableBuilder(
            "nei:textures/gui/craftingtree.png",
            92,
            66,
            16,
            16).build();

    private static final DrawableResource ICON_INDETERMINATE = new DrawableBuilder(
            "nei:textures/gui/craftingtree.png",
            110,
            66,
            16,
            16).build();

    private static final DrawableResource ICON_EXPANDED = new DrawableBuilder(
            "nei:textures/gui/craftingtree.png",
            128,
            66,
            16,
            16).build();

    private static final DrawableResource ICON_SCREENSHOT = new DrawableBuilder(
            "nei:textures/gui/craftingtree.png",
            146,
            66,
            16,
            16).build();

    private static final DrawableResource ICON_SETTINGS = new DrawableBuilder(
            "nei:textures/gui/craftingtree.png",
            164,
            66,
            16,
            16).build();

    private static final int BUTTON_SPACING = 20;

    public static final int INV_BUTTON_ID = 0;
    public static final int FIT_BUTTON_ID = 1;
    public static final int COLLAPSE_ALL_BUTTON_ID = 2;
    public static final int TOGGLE_STATS_BUTTON_ID = 3;
    public static final int STATS_SECTIONS_BUTTON_ID = 4;
    public static final int LINK_BUTTON_ID = 5;
    public static final int SCREENSHOT_BUTTON_ID = 6;

    private final CraftingTreeState uiState;
    private final GuiStatsButton[] buttons;

    public CraftingTreeToolbar(CraftingTreeState uiState) {
        this.uiState = uiState;

        this.buttons = new GuiStatsButton[] {
                new GuiStatsButton(
                        INV_BUTTON_ID,
                        () -> INV_TEXTURE,
                        () -> NEIClientUtils.translate(
                                this.uiState.useInventorySnapshot ? "bookmark.tree.toolbar.inventory_on"
                                        : "bookmark.tree.toolbar.inventory_off"),
                        () -> this.uiState.useInventorySnapshot),

                new GuiStatsButton(
                        FIT_BUTTON_ID,
                        () -> FIT_TEXTURE,
                        () -> NEIClientUtils.translate("bookmark.tree.toolbar.fit_to_view"),
                        () -> false),

                new GuiStatsButton(COLLAPSE_ALL_BUTTON_ID, () -> switch (this.uiState.collapseMode) {
                case EXPANDED -> ICON_EXPANDED;
                case SMART -> ICON_INDETERMINATE;
                case ALL -> ICON_COLLAPSED;
                }, () -> NEIClientUtils.translate(switch (this.uiState.collapseMode) {
                case EXPANDED -> "bookmark.tree.toolbar.collapse_resolved";
                case SMART -> "bookmark.tree.toolbar.collapse_all";
                case ALL -> "bookmark.tree.toolbar.expand_all";
                }), () -> this.uiState.collapseMode != CraftingTreeState.CollapseMode.EXPANDED),

                new GuiStatsButton(
                        TOGGLE_STATS_BUTTON_ID,
                        () -> this.uiState.visibleStatsSections.size() == CraftingTreeState.StatsSection.values().length
                                ? STATS_TEXTURE
                                : COMPACT_STATS_TEXTURE,
                        () -> NEIClientUtils.translate(
                                this.uiState.statsPanelVisible ? "bookmark.tree.toolbar.stats_on"
                                        : "bookmark.tree.toolbar.stats_off"),
                        () -> this.uiState.statsPanelVisible),

                new GuiStatsButton(
                        STATS_SECTIONS_BUTTON_ID,
                        () -> ICON_SETTINGS,
                        () -> NEIClientUtils.translate("bookmark.tree.toolbar.stats_sections"),
                        () -> false),
                new GuiStatsButton(
                        LINK_BUTTON_ID,
                        () -> LINK_TEXTURE,
                        () -> NEIClientUtils.translate("bookmark.tree.toolbar.link"),
                        () -> false),
                new GuiStatsButton(
                        SCREENSHOT_BUTTON_ID,
                        () -> ICON_SCREENSHOT,
                        () -> NEIClientUtils.translate("bookmark.tree.toolbar.screenshot"),
                        () -> false)

        };
    }

    public void layout(int x, int y) {
        for (int i = 0; i < this.buttons.length; i++) {
            this.buttons[i].xPosition = x + BUTTON_SPACING * i;
            this.buttons[i].yPosition = y;
        }
    }

    public void addTo(List<GuiButton> buttonList) {
        buttonList.addAll(Arrays.asList(this.buttons));
    }

    public GuiButton getButton(int buttonId) {

        for (int i = 0; i < this.buttons.length; i++) {
            if (this.buttons[i].id == buttonId) {
                return this.buttons[i];
            }
        }

        return null;
    }

    public String getTooltip(Minecraft mc, int mousex, int mousey) {
        for (GuiStatsButton button : this.buttons) {
            if (button.mousePressed(mc, mousex, mousey)) {
                return button.getTooltip();
            }
        }

        return null;
    }

}
