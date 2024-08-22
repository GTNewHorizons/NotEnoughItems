package codechicken.nei.config.preset;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import codechicken.core.gui.GuiWidget;
import codechicken.lib.gui.GuiDraw;
import codechicken.lib.vec.Rectangle4i;
import codechicken.nei.ItemList;
import codechicken.nei.ItemList.AllMultiItemFilter;
import codechicken.nei.ItemList.AnyMultiItemFilter;
import codechicken.nei.ItemList.NegatedItemFilter;
import codechicken.nei.ItemPanel.ItemPanelSlot;
import codechicken.nei.ItemsGrid;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.PresetsList;
import codechicken.nei.PresetsList.Preset;
import codechicken.nei.SearchField.GuiSearchField;
import codechicken.nei.TextField;
import codechicken.nei.api.ItemFilter;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.guihook.IContainerTooltipHandler;
import codechicken.nei.util.TextHistory;
import cpw.mods.fml.common.registry.GameRegistry;

public class RightPanel extends GuiWidget {

    protected static class MouseSelection {

        public int startX = -1;
        public int startY = -1;
        public int startIndex = -1;
        public int endIndex = -1;

        public Set<ItemStack> items = new HashSet<>();
        public boolean append = true;

        public MouseSelection(int slotIndex, Rectangle4i rec, boolean ppnd) {
            append = ppnd;
            endIndex = slotIndex;
            startIndex = slotIndex;
            startX = rec.x;
            startY = rec.y;
        }
    }

    protected abstract static class PresetSearchField extends TextField {

        private static final TextHistory history = new TextHistory();

        protected PresetSearchField(String ident) {
            super(ident);
        }

        @Override
        protected void initInternalTextField() {
            field = new GuiSearchField();
            field.setMaxStringLength(maxSearchLength);
            field.setCursorPositionZero();
        }

        @Override
        public int getTextColour() {
            if (!text().isEmpty()) {
                return focused() ? 0xFFcc3300 : 0xFF993300;
            } else {
                return focused() ? 0xFFE0E0E0 : 0xFF909090;
            }
        }

        @Override
        public void lastKeyTyped(int keyID, char keyChar) {

            if (!focused() && NEIClientConfig.isKeyHashDown("gui.search")) {
                setFocus(true);
            }

            if (focused() && NEIClientConfig.isKeyHashDown("gui.getprevioussearch")) {
                handleNavigateHistory(TextHistory.Direction.PREVIOUS);
            }

            if (focused() && NEIClientConfig.isKeyHashDown("gui.getnextsearch")) {
                handleNavigateHistory(TextHistory.Direction.NEXT);
            }
        }

        @Override
        public String filterText(String s) {
            return EnumChatFormatting.getTextWithoutFormattingCodes(s);
        }

        public ItemFilter getFilter() {
            return ((GuiSearchField) field).getFilter();
        }

        @Override
        public void setFocus(boolean focus) {
            final boolean previousFocus = field.isFocused();

            if (previousFocus != focus) {
                history.add(text());
            }

            super.setFocus(focus);
        }

        private boolean handleNavigateHistory(TextHistory.Direction direction) {
            if (focused()) {
                return history.get(direction, text()).map(newText -> {
                    setText(newText);
                    return true;
                }).orElse(false);
            }
            return false;
        }

    }

    protected static final int INPUT_HEIGHT = 20;
    protected static final int BUTTON_SIZE = 16;

    protected final PresetItemsGrid grid = new PresetItemsGrid() {

        @Override
        protected ItemFilter getFilter() {
            AllMultiItemFilter filter = new AllMultiItemFilter(searchField.getFilter());

            if (enabledPresets.isChecked()) {
                AllMultiItemFilter andFilter = new AllMultiItemFilter();
                AnyMultiItemFilter orFilter = new AnyMultiItemFilter();
                Set<String> identifiers = PresetsList.presets.stream().flatMap(p -> p.items.stream())
                        .collect(Collectors.toSet());

                andFilter.filters.add(item -> !identifiers.contains(Preset.getIdentifier(item)));
                andFilter.filters.add(new NegatedItemFilter(ItemList.collapsibleItems.getItemFilter()));

                if (slotIndex != -1) {
                    orFilter.filters.add(PresetsList.presets.get(slotIndex));
                }

                orFilter.filters.add(andFilter);
                filter.filters.add(orFilter);
            }

            return filter;
        }

        @Override
        protected boolean isSelected(ItemStack stack) {

            if (mouseSelection != null && mouseSelection.items.contains(stack)) {
                return mouseSelection.append;
            }

            return preset.items.contains(Preset.getIdentifier(stack));
        }
    };

    protected CheckboxButton enabledPresets;

    protected final Preset preset;
    protected final int slotIndex;
    protected MouseSelection mouseSelection;

    protected final PresetSearchField searchField = new PresetSearchField("preset-search") {

        @Override
        public void onTextChange(String oldText) {
            final String newText = text();
            if (!newText.equals(oldText)) {
                grid.restartFilter();
            }
        }

    };

    public RightPanel(Preset preset, int slotIndex) {
        super(1, 0, 2, 2);

        this.slotIndex = slotIndex;
        this.preset = preset;

        enabledPresets = new CheckboxButton(NEIClientUtils.translate("presets.filter")) {

            @Override
            protected void onChange() {
                grid.restartFilter();
            }
        };

        enabledPresets.setChecked(true);

        grid.restartFilter();
    }

    @Override
    public void mouseClicked(int x, int y, int button) {
        grid.mouseClicked(x, y, button);

        if (enabledPresets.contains(x, y)) {
            enabledPresets.handleClick(x, y, button);
        }

        if (searchField.contains(x, y)) {
            searchField.handleClick(x, y, button);
        } else {
            searchField.onGuiClick(x, y);
        }

        if (mouseSelection == null && (button == 0 || button == 1)) {
            ItemPanelSlot slot = grid.getSlotMouseOver(x, y);

            if (slot != null) {
                mouseSelection = new MouseSelection(slot.slotIndex, grid.getItemRect(slot.slotIndex), button == 0);
            }
        }

    }

    protected void onItemsChanges() {}

    @Override
    public void mouseMovedOrUp(int x, int y, int button) {
        searchField.mouseUp(x, y, button);

        if (mouseSelection != null && (button == 0 || button == 1)) {
            ItemPanelSlot hoverSlot = grid.getSlotMouseOver(x, y);

            if (hoverSlot != null && hoverSlot.slotIndex == mouseSelection.startIndex) {
                setHidden(hoverSlot.item, button == 0);
            } else if (!mouseSelection.items.isEmpty()) {

                for (ItemStack stack : mouseSelection.items) {
                    hideItem(stack, mouseSelection.append);
                }
            }

            mouseSelection = null;
            onItemsChanges();
        }

    }

    protected void setHidden(final ItemStack stack, final boolean append) {

        if (NEIClientUtils.shiftKey()) {
            final List<ItemStack> items = grid.getItems();

            for (int i = 0; i < items.size(); i++) {
                if (stack.getItem().equals(items.get(i).getItem())) {
                    hideItem(items.get(i), append);
                }
            }

        } else if (NEIClientUtils.controlKey()) {
            final String modId = getModId(stack);

            if (modId == null) {
                hideItem(stack, append);
            } else {
                final List<ItemStack> items = grid.getItems();

                for (int i = 0; i < items.size(); i++) {
                    final String mod = getModId(items.get(i));
                    if (mod != null && mod.equals(modId)) {
                        hideItem(items.get(i), append);
                    }
                }
            }

        } else {
            hideItem(stack, append);
        }
    }

    protected void hideItem(final ItemStack stack, boolean append) {
        if (append) {
            preset.items.add(Preset.getIdentifier(stack));
        } else {
            preset.items.remove(Preset.getIdentifier(stack));
        }
    }

    protected static String getModId(final ItemStack stack) {
        try {
            return GameRegistry.findUniqueIdentifierFor(stack.getItem()).modId;
        } catch (Exception ignored) {}

        return null;
    }

    @Override
    public void mouseDragged(int x, int y, int button, long time) {
        searchField.mouseDragged(x, y, button, time);

        if (mouseSelection != null && (button == 0 || button == 1)) {
            final ItemPanelSlot slot = grid.getSlotMouseOver(x, y);

            if (slot != null && slot.slotIndex != mouseSelection.endIndex) {
                mouseSelection.endIndex = slot.slotIndex;
                mouseSelection.items.clear();

                final Rectangle4i rec = grid.getItemRect(slot.slotIndex);
                final Rectangle4i sel = new Rectangle4i(
                        Math.min(rec.x, mouseSelection.startX),
                        Math.min(rec.y, mouseSelection.startY),
                        Math.max(rec.x, mouseSelection.startX) - Math.min(rec.x, mouseSelection.startX),
                        Math.max(rec.y, mouseSelection.startY) - Math.min(rec.y, mouseSelection.startY));

                for (int ix = sel.x; ix <= sel.x + sel.w; ix += ItemsGrid.SLOT_SIZE) {
                    for (int iy = sel.y; iy <= sel.y + sel.h; iy += ItemsGrid.SLOT_SIZE) {
                        ItemPanelSlot over = grid.getSlotMouseOver(ix, iy);

                        if (over != null) {
                            mouseSelection.items.add(over.item);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void update() {
        enabledPresets.w = 20;
        enabledPresets.h = INPUT_HEIGHT;
        enabledPresets.x = this.x;
        enabledPresets.y = this.y + this.height - 2 - INPUT_HEIGHT;

        searchField.w = this.width - enabledPresets.w - 3;
        searchField.h = INPUT_HEIGHT;
        searchField.x = enabledPresets.x + enabledPresets.w + 3;
        searchField.y = enabledPresets.y;

        grid.setGridSize(this.x, this.y, this.width, searchField.y - this.y);
        grid.refresh(null);
    }

    @Override
    public void draw(int mousex, int mousey, float frame) {
        enabledPresets.draw(mousex, mousey);
        searchField.draw(mousex, mousey);

        RenderHelper.enableGUIStandardItemLighting();
        GL11.glPushMatrix();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);

        grid.draw(mousex, mousey);

        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        GL11.glPopMatrix();
        RenderHelper.enableStandardItemLighting();
    }

    public List<String> handleTooltip(int mousex, int mousey, List<String> tooltip) {
        ItemPanelSlot over = grid.getSlotMouseOver(mousex, mousey);

        if (over != null) {
            tooltip = GuiContainerManager.itemDisplayNameMultiline(over.item, null, true);

            synchronized (GuiContainerManager.tooltipHandlers) {
                for (IContainerTooltipHandler handler : GuiContainerManager.tooltipHandlers) {
                    tooltip = handler.handleItemTooltip(null, over.item, mousex, mousey, tooltip);
                }
            }

            if (!tooltip.isEmpty()) {
                tooltip.set(0, tooltip.get(0) + GuiDraw.TOOLTIP_LINESPACE); // add space after 'title'
            }

        } else if (enabledPresets.contains(mousex, mousey)) {
            tooltip.add(NEIClientUtils.translate("presets.filter.tip"));
        }

        return tooltip;
    }

    @Override
    public void keyTyped(char c, int keycode) {
        searchField.handleKeyPress(keycode, c);
    }

    @Override
    public void mouseScrolled(int x, int y, int scroll) {
        if (grid.contains(x, y)) {
            grid.shiftPage(-scroll);
        }
    }

}
