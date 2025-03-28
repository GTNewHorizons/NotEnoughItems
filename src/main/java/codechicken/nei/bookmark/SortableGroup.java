package codechicken.nei.bookmark;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.opengl.GL11;

import codechicken.lib.vec.Rectangle4i;
import codechicken.nei.guihook.GuiContainerManager;

public class SortableGroup {

    public int groupId;
    public BookmarkGrid grid;

    public int shiftX = 0;
    public int shiftY = 0;

    public SortableGroup(BookmarkGrid grid, int groupId) {
        this.grid = grid;
        this.groupId = groupId;
    }

    public void postDraw(int mousex, int mousey) {
        Point startRect = null;

        GuiContainerManager.drawItems.zLevel += 100;

        for (BookmarksGridSlot slot : this.grid.getMask()) {
            if (slot.getGroupId() == this.groupId) {
                final Rectangle4i rect = this.grid.getSlotRect(slot.slotIndex);

                if (startRect == null) {
                    startRect = new Point(rect.x - this.shiftX, rect.y - this.shiftY);
                }

                GuiContainerManager.drawItem(
                        mousex + rect.x - startRect.x + 1,
                        mousey + rect.y - startRect.y + 1,
                        slot.getItemStack(),
                        true,
                        "");

            }
        }

        if (startRect != null) {
            int startRowIndex = -1;
            int endRowIndex = 0;

            for (int rowIndex = 0; rowIndex < this.grid.getRows(); rowIndex++) {
                if (this.grid.getRowGroupId(rowIndex) == this.groupId) {

                    if (startRowIndex == -1) {
                        startRowIndex = rowIndex;
                    }

                    endRowIndex = rowIndex;
                }
            }

            GL11.glPushMatrix();
            GL11.glTranslated(mousex - startRect.x, mousey - startRect.y, 0);
            this.grid.drawGroup(this.groupId, startRowIndex, endRowIndex);
            GL11.glPopMatrix();
        }

        GuiContainerManager.drawItems.zLevel -= 100;
    }

    public List<BookmarkItem> getBookmarkItems() {
        final List<BookmarkItem> items = new ArrayList<>();

        for (BookmarkItem item : this.grid.bookmarkItems) {
            if (item.groupId == this.groupId) {
                items.add(item);
            }
        }

        return items;
    }
}
