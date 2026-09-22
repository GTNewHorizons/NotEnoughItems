package codechicken.nei;

import static codechicken.lib.gui.GuiDraw.fontRenderer;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.guihook.GuiContainerManager;

public class ItemsListTooltipLineHandler extends ItemsTooltipLineHandler {

    protected static final int NAME_MARGIN = 2;

    protected final List<String> names = new ArrayList<>();
    protected int tailCount = 0;

    public ItemsListTooltipLineHandler(String label, List<ItemStack> items, int maxRows) {
        super(label, items, true, maxRows);

        this.rows = Math.min(maxRows, this.length);
        this.count = this.length > this.rows ? this.rows - 1 : this.length;

        int width = fontRenderer.getStringWidth(this.label) + 15;

        for (int index = 0; index < this.count; index++) {
            final String name = this.items.get(index).getDisplayName();
            this.names.add(name);
            width = Math.max(width, SLOT_SIZE + NAME_MARGIN + fontRenderer.getStringWidth(name));
        }

        if (this.count < this.length) {
            final int tail = this.length - this.count;
            width = Math.max(width, Math.min(tail, MAX_COLUMNS) * SLOT_SIZE);

            final int fit = width / SLOT_SIZE;

            if (tail <= fit) {
                this.tailCount = tail;
            } else {
                final String text = "+" + (tail - fit);
                this.tailCount = Math
                        .max(0, fit - (int) Math.ceil((float) (fontRenderer.getStringWidth(text) - 2) / SLOT_SIZE));
            }
        }

        this.size.width = this.length > 0 ? width : 0;
        this.size.height = this.length > 0 ? this.rows * SLOT_SIZE + fontRenderer.FONT_HEIGHT + 2 + MARGIN_TOP : 0;
    }

    @Override
    public void setLabel(String label) {
        this.label = label;
        this.size.width = Math.max(this.size.width, fontRenderer.getStringWidth(this.label) + 15);
    }

    @Override
    public void draw(int x, int y) {
        if (this.length == 0) return;

        y += MARGIN_TOP;

        fontRenderer.drawStringWithShadow(this.labelColor + this.label + ":", x, y, 0);

        GL11.glEnable(GL12.GL_RESCALE_NORMAL);

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_LIGHTING_BIT);
        RenderHelper.enableGUIStandardItemLighting();

        final int xTranslation = x;
        final int yTranslation = y + fontRenderer.FONT_HEIGHT + 2;
        final int zTranslation = GuiContainerManager.TOOLTIP_Z_OFFSET;
        GL11.glTranslatef(xTranslation, yTranslation, zTranslation);

        for (int index = 0; index < this.count; index++) {
            drawSlot(0, index * SLOT_SIZE, index);
        }

        for (int index = 0; index < this.tailCount; index++) {
            drawSlot(index * SLOT_SIZE, this.count * SLOT_SIZE, this.count + index);
        }

        NEIClientUtils.gl2DRenderContext(() -> {
            final int textShift = ICON_OFFSET + Math.round((ICON_SIZE - fontRenderer.FONT_HEIGHT) / 2f);

            for (int index = 0; index < this.count; index++) {
                fontRenderer.drawStringWithShadow(
                        EnumChatFormatting.GRAY + this.names.get(index),
                        SLOT_SIZE + NAME_MARGIN,
                        index * SLOT_SIZE + textShift,
                        0xFFFFFF);
            }

            final int hidden = this.length - this.count - this.tailCount;

            if (hidden > 0) {
                final String text = "+" + hidden;
                fontRenderer.drawStringWithShadow(
                        EnumChatFormatting.GRAY + text,
                        this.size.width - fontRenderer.getStringWidth(text) - 2,
                        this.count * SLOT_SIZE + textShift,
                        0xFFFFFF);
            }
        });

        GL11.glTranslatef(-xTranslation, -yTranslation, -zTranslation);
        GL11.glPopAttrib();
    }

    protected void drawSlot(int x, int y, int index) {
        if (this.activeStackIndex == index) {
            NEIClientUtils.gl2DRenderContext(() -> GuiDraw.drawRect(x, y, SLOT_SIZE, SLOT_SIZE, 0x66555555));
        }

        drawStackWithAmount(x, y, index);
    }

}
