package codechicken.nei.config.preset;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import codechicken.core.gui.GuiWidget;
import codechicken.lib.gui.GuiDraw;
import codechicken.lib.vec.Rectangle4i;
import codechicken.nei.ItemsGrid;
import codechicken.nei.ItemsGrid.ItemsGridSlot;
import codechicken.nei.Label;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.PresetsList.Preset;
import codechicken.nei.PresetsList.PresetMode;
import codechicken.nei.TextField;
import codechicken.nei.api.ItemFilter;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.guihook.IContainerTooltipHandler;

public class LeftPanel extends GuiWidget {

    protected static class MouseSelection {

        public int startX = -1;
        public int startY = -1;
        public int startIndex = -1;
        public int endIndex = -1;

        public Set<ItemStack> items = new HashSet<>();

        public MouseSelection(int slotIndex, Rectangle4i rec) {
            endIndex = slotIndex;
            startIndex = slotIndex;
            startX = rec.x;
            startY = rec.y;
        }
    }

    protected static final int INPUT_HEIGHT = 20;

    public final PresetItemsGrid grid = new PresetItemsGrid() {

        @Override
        protected ItemFilter getFilter() {
            final Set<String> identifiers = preset.items;
            return item -> identifiers.contains(Preset.getIdentifier(item));
        }

        @Override
        protected boolean isSelected(ItemStack stack) {
            return mouseSelection != null && mouseSelection.items.contains(stack);
        }

        @Override
        protected MouseContext getMouseContext(int mousex, int mousey) {
            final ItemsGridSlot hovered = getSlotMouseOver(mousex, mousey);

            if (hovered != null) {
                return new MouseContext(
                        hovered.slotIndex,
                        hovered.slotIndex / this.columns,
                        hovered.slotIndex % this.columns);
            }

            return null;
        }
    };

    protected Label nameLabel = new Label(NEIClientUtils.translate("presets.name"), false);

    protected TextField nameField = new TextField("name") {

        @Override
        public void onTextChange(String oldText) {
            preset.name = text();
        }
    };

    protected Label modeLabel = new Label(NEIClientUtils.translate("presets.mode"), false);

    protected CheckboxButton modeHide;
    protected CheckboxButton modeRemove;
    protected CheckboxButton modeGroup;

    protected CheckboxButton enabledButton;

    protected final Preset preset;
    protected MouseSelection mouseSelection;

    public LeftPanel(Preset preset) {
        super(1, 0, 2, 2);

        this.preset = preset;

        nameField.setText(preset.name);

        modeHide = new CheckboxButton(NEIClientUtils.translate("presets.mode.hide")) {

            @Override
            public boolean isChecked() {
                return preset.mode == PresetMode.HIDE;
            }

            @Override
            protected void onChange() {
                preset.mode = this.checked ? PresetMode.HIDE : null;
            }
        };

        modeRemove = new CheckboxButton(NEIClientUtils.translate("presets.mode.remove")) {

            @Override
            public boolean isChecked() {
                return preset.mode == PresetMode.REMOVE;
            }

            @Override
            protected void onChange() {
                preset.mode = this.checked ? PresetMode.REMOVE : null;
            }
        };

        modeGroup = new CheckboxButton(NEIClientUtils.translate("presets.mode.group")) {

            @Override
            public boolean isChecked() {
                return preset.mode == PresetMode.GROUP;
            }

            @Override
            protected void onChange() {
                preset.mode = this.checked ? PresetMode.GROUP : null;
            }
        };

        enabledButton = new CheckboxButton(NEIClientUtils.translate("presets.enabled")) {

            @Override
            public boolean isChecked() {
                return preset.enabled;
            }

            @Override
            protected void onChange() {
                preset.enabled = this.checked;
            }
        };

        grid.restartFilter();
    }

    @Override
    public void mouseClicked(int x, int y, int button) {
        grid.mouseClicked(x, y, button);

        if (modeHide.contains(x, y)) {
            modeHide.handleClick(x, y, button);
        }

        if (modeRemove.contains(x, y)) {
            modeRemove.handleClick(x, y, button);
        }

        if (modeGroup.contains(x, y)) {
            modeGroup.handleClick(x, y, button);
        }

        if (enabledButton.contains(x, y)) {
            enabledButton.handleClick(x, y, button);
        }

        if (nameField.contains(x, y)) {
            nameField.handleClick(x, y, button);
        } else {
            nameField.onGuiClick(x, y);
        }

        if (mouseSelection == null && button == 0) {
            final ItemsGridSlot slot = grid.getSlotMouseOver(x, y);

            if (slot != null) {
                mouseSelection = new MouseSelection(slot.itemIndex, grid.getItemRect(slot.itemIndex));
            }
        }

    }

    protected void onItemsChanges() {}

    @Override
    public void mouseMovedOrUp(int x, int y, int button) {
        nameField.mouseUp(x, y, button);

        if (mouseSelection != null && button == 0) {
            final ItemsGridSlot hoverSlot = grid.getSlotMouseOver(x, y);

            if (hoverSlot != null && hoverSlot.itemIndex == mouseSelection.startIndex) {
                preset.items.remove(Preset.getIdentifier(hoverSlot.getItemStack()));
            } else if (!mouseSelection.items.isEmpty()) {

                for (ItemStack stack : mouseSelection.items) {
                    preset.items.remove(Preset.getIdentifier(stack));
                }
            }

            mouseSelection = null;
            onItemsChanges();
        }
    }

    @Override
    public void mouseDragged(int x, int y, int button, long time) {
        nameField.mouseDragged(x, y, button, time);

        if (mouseSelection != null && button == 0) {
            final ItemsGridSlot slot = grid.getSlotMouseOver(x, y);

            if (slot != null && slot.itemIndex != mouseSelection.endIndex) {
                mouseSelection.endIndex = slot.itemIndex;
                mouseSelection.items.clear();

                final Rectangle4i rec = grid.getItemRect(slot.itemIndex);
                final Rectangle4i sel = new Rectangle4i(
                        Math.min(rec.x, mouseSelection.startX),
                        Math.min(rec.y, mouseSelection.startY),
                        Math.max(rec.x, mouseSelection.startX) - Math.min(rec.x, mouseSelection.startX),
                        Math.max(rec.y, mouseSelection.startY) - Math.min(rec.y, mouseSelection.startY));

                for (int ix = sel.x; ix <= sel.x + sel.w; ix += ItemsGrid.SLOT_SIZE) {
                    for (int iy = sel.y; iy <= sel.y + sel.h; iy += ItemsGrid.SLOT_SIZE) {
                        final ItemsGridSlot over = grid.getSlotMouseOver(ix, iy);

                        if (over != null) {
                            mouseSelection.items.add(over.getItemStack());
                        }
                    }
                }
            }
        }
    }

    @Override
    public void update() {
        int CHECKBOX_WIDTH = (this.width - 12) / 3;
        nameLabel.x = this.x + 2;
        nameLabel.y = this.y + 4;
        nameLabel.w = this.width;
        nameLabel.h = 10;

        nameField.x = this.x;
        nameField.y = nameLabel.y + nameLabel.h;
        nameField.w = this.width - CHECKBOX_WIDTH - 6;
        nameField.h = INPUT_HEIGHT;

        enabledButton.x = nameField.x + nameField.w + 6;
        enabledButton.y = nameField.y;
        enabledButton.w = CHECKBOX_WIDTH;
        enabledButton.h = INPUT_HEIGHT;

        modeLabel.x = this.x + 2;
        modeLabel.y = nameField.y + nameField.h + 6;
        modeLabel.w = this.width;
        modeLabel.h = 10;

        modeHide.w = modeRemove.w = modeGroup.w = CHECKBOX_WIDTH;
        modeHide.h = modeRemove.h = modeGroup.h = INPUT_HEIGHT;
        modeHide.y = modeRemove.y = modeGroup.y = modeLabel.y + modeLabel.h;

        modeHide.x = this.x + 1;
        modeRemove.x = modeHide.x + modeHide.w + 6;
        modeGroup.x = modeRemove.x + modeRemove.w + 6;

        grid.setGridSize(
                this.x + 1,
                modeHide.y + modeHide.h + 2,
                this.width - 2,
                this.height - (modeHide.y + modeHide.h + 2));
        grid.refresh(null);
    }

    @Override
    public void draw(int mousex, int mousey, float frame) {
        nameLabel.draw(mousex, mousey);
        nameField.draw(mousex, mousey);

        modeLabel.draw(mousex, mousey);
        modeHide.draw(mousex, mousey);
        modeRemove.draw(mousex, mousey);
        modeGroup.draw(mousex, mousey);

        enabledButton.draw(mousex, mousey);

        RenderHelper.enableGUIStandardItemLighting();
        GL11.glPushMatrix();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);

        grid.draw(mousex, mousey);

        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        GL11.glPopMatrix();
        RenderHelper.enableStandardItemLighting();
    }

    public List<String> handleTooltip(int mousex, int mousey, List<String> tooltip) {
        final ItemsGridSlot over = grid.getSlotMouseOver(mousex, mousey);

        if (over != null) {
            tooltip = GuiContainerManager.itemDisplayNameMultiline(over.getItemStack(), null, true);

            synchronized (GuiContainerManager.tooltipHandlers) {
                for (IContainerTooltipHandler handler : GuiContainerManager.tooltipHandlers) {
                    tooltip = handler.handleItemTooltip(null, over.getItemStack(), mousex, mousey, tooltip);
                }
            }

            if (!tooltip.isEmpty()) {
                tooltip.set(0, tooltip.get(0) + GuiDraw.TOOLTIP_LINESPACE); // add space after 'title'
            }

        } else if (modeHide.contains(mousex, mousey)) {
            tooltip.add(NEIClientUtils.translate("presets.mode.hide.tip"));
        } else if (modeRemove.contains(mousex, mousey)) {
            tooltip.add(NEIClientUtils.translate("presets.mode.remove.tip"));
        } else if (modeGroup.contains(mousex, mousey)) {
            tooltip.add(NEIClientUtils.translate("presets.mode.group.tip"));
        }

        return tooltip;
    }

    @Override
    public void keyTyped(char c, int keycode) {
        nameField.handleKeyPress(keycode, c);
    }

    @Override
    public void mouseScrolled(int x, int y, int scroll) {
        if (grid.contains(x, y)) {
            grid.shiftPage(-scroll);
        }
    }

}
