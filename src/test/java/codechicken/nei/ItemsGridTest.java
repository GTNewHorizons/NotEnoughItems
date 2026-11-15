package codechicken.nei;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Collections;
import java.util.List;

import net.minecraft.client.gui.inventory.GuiContainer;

import org.junit.jupiter.api.Test;

public class ItemsGridTest {

    @Test
    void refreshesCaptureOnlyWhenGridChanged() {
        TestGrid grid = new TestGrid();
        ItemsGrid.ScreenCapture capture = mock(ItemsGrid.ScreenCapture.class);
        grid.screenCapture = capture;

        grid.setGridSize(1, 2, 18, 18);
        grid.setGridSize(1, 2, 18, 18);
        verify(capture, times(1)).refreshBuffer();
    }

    @Test
    void refreshesOverlapOnlyOnGridChangeOrScanFrame() {
        TestGrid grid = new TestGrid();

        GridOverlapScanner.requestScan();
        GridOverlapScanner.beginFrame(null, true);
        grid.setGridSize(1, 2, 18, 18);
        grid.refresh(null);
        assertEquals(1, grid.overlapRefreshes);

        GridOverlapScanner.beginFrame(null, true);
        grid.refresh(null);
        assertEquals(1, grid.overlapRefreshes);

        grid.setGridSize(1, 2, 36, 18);
        grid.refresh(null);
        assertEquals(2, grid.overlapRefreshes);

        GridOverlapScanner.requestScan();
        GridOverlapScanner.beginFrame(null, true);
        grid.refresh(null);
        assertEquals(3, grid.overlapRefreshes);

        GridOverlapScanner.beginFrame(null, false);
        grid.refresh(null);
        assertEquals(4, grid.overlapRefreshes);

        GridOverlapScanner.beginFrame(null, false);
        grid.refresh(null);
        assertEquals(4, grid.overlapRefreshes);

        GridOverlapScanner.beginFrame(null, true);
        grid.refresh(null);
        assertEquals(5, grid.overlapRefreshes);
    }

    private static final class TestGrid extends ItemsGrid<ItemsGrid.ItemsGridSlot, ItemsGrid.MouseContext> {

        private int overlapRefreshes;

        @Override
        protected void updateGuiOverlapSlots(GuiContainer gui) {
            overlapRefreshes++;
        }

        @Override
        public List<ItemsGrid.ItemsGridSlot> getMask() {
            return Collections.emptyList();
        }

        @Override
        protected ItemsGrid.MouseContext getMouseContext(int mousex, int mousey) {
            return null;
        }
    }
}
