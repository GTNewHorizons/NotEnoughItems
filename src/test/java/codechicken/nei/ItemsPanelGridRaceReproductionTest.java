package codechicken.nei;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.Test;

public class ItemsPanelGridRaceReproductionTest {

    // JUnit thread represents the client/render thread. Explicit worker threads represent NEI filtering
    // this test controls the ordering that caused the original crash
    @Test
    void backgroundFilterUpdateDoesNotInvalidateMaskDuringConstruction() throws InterruptedException {
        // save the global panel state because updateItemList() always targets ItemPanels.itemPanel
        final ItemsPanelGrid previous = ItemPanels.itemPanel.getGrid();
        final ArrayList<ItemStack> previousItems = ItemPanels.itemPanel.realItems;
        final ItemsPanelGrid grid = new ItemsPanelGrid() {

            private boolean published;

            // getMask() calls this after allocating gridMask, but before adding the next visible slot
            // publishing a filter result here reproduces the vulnerable interleaving deterministically
            @Override
            public boolean isInvalidSlot(int index) {
                if (!published) {
                    published = true;
                    // build the result on the test thread, then publish it from the filtering thread
                    // its size differs from rawItems, so the old implementation would call setPage(0)
                    // on that worker thread and clear gridMask while getMask() was still building it
                    final ArrayList<ItemStack> result = new ArrayList<>(this.realItems);
                    final Thread filter = new Thread(() -> ItemPanel.updateItemList(result));
                    filter.start();
                    try {
                        // pause mask construction until publication finishes; this avoids timing-dependent sleeps
                        // The timeout prevents a broken worker from hanging the test indefinitely
                        filter.join(5000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(e);
                    }
                    assertEquals(Thread.State.TERMINATED, filter.getState());
                }
                return super.isInvalidSlot(index);
            }
        };
        // one valid slot is enough to reach the vulnerable code. A null item is intentional:
        // mask construction stores the reference without rendering it or querying Minecraft item data
        grid.rows = 1;
        grid.columns = 1;
        grid.perPage = 1;
        grid.invalidSlotMap = new boolean[1];
        grid.realItems.add(null);
        ItemPanels.itemPanel.grid = grid;

        try {
            // drain any pending result before starting the controlled interleaving
            ItemPanel.applyPendingItems();
            // this builds the mask on the simulated client thread and triggers the worker above
            final List<ItemsPanelGrid.ItemsPanelGridSlot> mask = grid.getMask();
            assertEquals(1, mask.size());
            assertSame(mask, grid.gridMask);
            // publication alone must leave the current GUI state and the in-progress mask untouched
            assertNull(grid.newItems);
            assertSame(previousItems, ItemPanels.itemPanel.realItems);

            // simulate the next client-side refresh: only now may the result invalidate the old mask
            // and become the input for the grid's normal refresh processing
            ItemPanel.applyPendingItems();
            assertNull(grid.gridMask);
            assertNotNull(grid.newItems);
            assertEquals(1, grid.newItems.size());
            assertSame(grid.newItems, ItemPanels.itemPanel.realItems);

            // consuming a result clears the pending slot. A second consume must have no effect
            final ArrayList<ItemStack> applied = grid.newItems;
            ItemPanel.applyPendingItems();
            assertSame(applied, grid.newItems);

            // simulate two filtering results arriving before the next client refresh
            // only the most recent result should be applied; neither may update the GUI on the worker
            final ArrayList<ItemStack> latest = new ArrayList<>();
            final Thread filter = new Thread(() -> {
                ItemPanel.updateItemList(new ArrayList<>());
                ItemPanel.updateItemList(latest);
            });
            filter.start();
            filter.join(5000);
            assertEquals(Thread.State.TERMINATED, filter.getState());
            assertSame(applied, grid.newItems);

            // the simulated client thread consumes the latest result after the worker has finished
            ItemPanel.applyPendingItems();
            assertSame(latest, grid.newItems);
            assertSame(latest, ItemPanels.itemPanel.realItems);
        } finally {
            // drain pending work before restoring globals so later tests cannot consume our fake results
            ItemPanel.applyPendingItems();
            ItemPanels.itemPanel.grid = previous;
            ItemPanels.itemPanel.realItems = previousItems;
        }
    }
}
