package codechicken.nei;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.function.Predicate;

import net.minecraft.client.gui.inventory.GuiContainer;

import codechicken.lib.vec.Rectangle4i;

final class GridOverlapScanner {

    private static boolean scanRequested = true;
    private static boolean scanFrame = true;
    private static boolean wasShowTooltip = true;
    private static WeakReference<GuiContainer> lastGui = new WeakReference<>(null);

    private boolean invalidated = true;

    private final Rectangle4i rect = new Rectangle4i();
    private boolean[] checked = new boolean[0];
    private int[] queue = new int[0];
    private int tail;

    private Predicate<Rectangle4i> isHidden;
    private boolean[] hidden;
    private int hiddenCount;
    private int left;
    private int top;
    private int slotSize;
    private int columns;

    public static void requestScan() {
        scanRequested = true;
    }

    public static void beginFrame(GuiContainer gui, boolean shouldShowTooltip) {

        if (wasShowTooltip != shouldShowTooltip) {
            scanRequested = true;
        }

        wasShowTooltip = shouldShowTooltip;

        if (lastGui.get() != gui) {
            lastGui = new WeakReference<>(gui);
            scanRequested = true;
        }

        scanFrame = scanRequested;
        scanRequested = false;
    }

    public static boolean isScanFrame() {
        return scanFrame;
    }

    public void invalidate() {
        this.invalidated = true;
    }

    public boolean shouldRefresh() {
        final boolean refresh = this.invalidated || scanFrame;
        this.invalidated = false;
        return refresh;
    }

    public int scan(Predicate<Rectangle4i> isHidden, int left, int top, int slotSize, int rows, int columns,
            boolean seedLeft, boolean seedRight, boolean[] hidden) {
        final int size = rows * columns;

        if (this.checked.length < size) {
            this.checked = new boolean[size];
            this.queue = new int[size];
        } else {
            Arrays.fill(this.checked, 0, size, false);
        }

        this.isHidden = isHidden;
        this.hidden = hidden;
        this.hiddenCount = 0;
        this.tail = 0;
        this.left = left;
        this.top = top;
        this.slotSize = slotSize;
        this.columns = columns;

        for (int r = 0; r < rows; r++) {
            if (seedLeft) {
                probe(r, 0);
            }
            if (seedRight) {
                probe(r, columns - 1);
            }
        }

        for (int head = 0; head < this.tail; head++) {
            final int row = this.queue[head] / columns;
            final int column = this.queue[head] % columns;

            for (int r = Math.max(0, row - 1); r <= Math.min(rows - 1, row + 1); r++) {
                for (int c = Math.max(0, column - 1); c <= Math.min(columns - 1, column + 1); c++) {
                    probe(r, c);
                }
            }
        }

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < columns; c++) {
                if (this.checked[r * columns + c]) {
                    continue;
                }

                int c1 = c;
                while (c1 + 1 < columns && !this.checked[r * columns + c1 + 1]) {
                    c1++;
                }

                int r1 = r;
                while (r1 + 1 < rows && isUnchecked(r1 + 1, c, c1)) {
                    r1++;
                }

                scanArea(r, r1, c, c1);
                c = c1;
            }
        }

        this.isHidden = null;
        this.hidden = null;

        return this.hiddenCount;
    }

    private void probe(int row, int column) {
        final int idx = row * this.columns + column;

        if (!this.checked[idx]) {
            this.checked[idx] = true;

            if (test(row, row, column, column)) {
                this.hidden[idx] = true;
                this.queue[this.tail++] = idx;
                this.hiddenCount++;
            }
        }
    }

    private void scanArea(int r0, int r1, int c0, int c1) {

        if (!test(r0, r1, c0, c1)) {
            for (int r = r0; r <= r1; r++) {
                Arrays.fill(this.checked, r * this.columns + c0, r * this.columns + c1 + 1, true);
            }
        } else if (r0 == r1 && c0 == c1) {
            this.checked[r0 * this.columns + c0] = true;
            this.hidden[r0 * this.columns + c0] = true;
            this.hiddenCount++;
        } else if (r1 - r0 >= c1 - c0) {
            final int mid = (r0 + r1) / 2;
            scanArea(r0, mid, c0, c1);
            scanArea(mid + 1, r1, c0, c1);
        } else {
            final int mid = (c0 + c1) / 2;
            scanArea(r0, r1, c0, mid);
            scanArea(r0, r1, mid + 1, c1);
        }
    }

    private boolean isUnchecked(int row, int c0, int c1) {
        for (int c = c0; c <= c1; c++) {
            if (this.checked[row * this.columns + c]) {
                return false;
            }
        }
        return true;
    }

    private boolean test(int r0, int r1, int c0, int c1) {
        this.rect.set(
                this.left + c0 * this.slotSize,
                this.top + r0 * this.slotSize,
                (c1 - c0 + 1) * this.slotSize,
                (r1 - r0 + 1) * this.slotSize);
        return this.isHidden.test(this.rect);
    }

}
