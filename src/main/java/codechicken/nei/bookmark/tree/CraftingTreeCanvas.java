package codechicken.nei.bookmark.tree;

import java.awt.Color;
import java.awt.Point;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiPredicate;

import net.minecraft.client.renderer.RenderHelper;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import codechicken.lib.gui.GuiDraw;
import codechicken.lib.vec.Rectangle4i;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.NEIClientUtils.Alignment;
import codechicken.nei.PositionedStack;
import codechicken.nei.bookmark.BookmarkItem;
import codechicken.nei.drawable.DrawableBuilder;
import codechicken.nei.drawable.DrawableResource;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.recipe.Recipe.RecipeId;
import codechicken.nei.scroll.GuiHelper;
import codechicken.nei.util.ReadableNumberConverter;

class CraftingTreeCanvas {

    private static final DrawableResource SCROLLBAR_H = new DrawableBuilder(
            "nei:textures/gui/craftingtree.png",
            0,
            88,
            2,
            24).build();

    private static final DrawableResource SCROLLBAR_W = new DrawableBuilder(
            "nei:textures/gui/craftingtree.png",
            0,
            84,
            24,
            2).build();

    private static final DrawableResource ICON_COLLAPSED = new DrawableBuilder(
            "nei:textures/gui/craftingtree.png",
            4,
            88,
            5,
            5).build();

    private static final DrawableResource ICON_COLLAPSED_HOVER = new DrawableBuilder(
            "nei:textures/gui/craftingtree.png",
            11,
            88,
            5,
            5).build();

    private static final DrawableResource ICON_EXPANDED = new DrawableBuilder(
            "nei:textures/gui/craftingtree.png",
            4,
            95,
            5,
            5).build();

    private static final DrawableResource ICON_EXPANDED_HOVER = new DrawableBuilder(
            "nei:textures/gui/craftingtree.png",
            11,
            95,
            5,
            5).build();

    private static final DrawableResource ICON_REMOVE = new DrawableBuilder(
            "nei:textures/gui/craftingtree.png",
            85,
            48,
            16,
            16).build();

    private static final DrawableResource ICON_CRAFTING = new DrawableBuilder(
            "nei:textures/gui/craftingtree.png",
            67,
            48,
            16,
            16).build();

    private static final DrawableResource SLOT_DEFAULT = new DrawableBuilder(
            "nei:textures/gui/craftingtree.png",
            68,
            2,
            18,
            18).build();

    private static final DrawableResource SLOT_MISSING = new DrawableBuilder(
            "nei:textures/gui/craftingtree.png",
            92,
            2,
            18,
            18).build();

    private static final DrawableResource SLOT_CRAFTING = new DrawableBuilder(
            "nei:textures/gui/craftingtree.png",
            116,
            2,
            18,
            18).build();

    private static final DrawableResource SLOT_DISABLED = new DrawableBuilder(
            "nei:textures/gui/craftingtree.png",
            140,
            2,
            18,
            18).build();

    private static final DrawableResource SLOT_HIGHLIGHTED = new DrawableBuilder(
            "nei:textures/gui/craftingtree.png",
            164,
            2,
            18,
            18).build();

    private static final Color LINE_COLOR = new Color(0xFF888888, true);
    private static final int HOVER_COLOR = 0x66888888;
    private static final float BADGE_SCALE = 0.6f;

    public static final int SLOT_SIZE = 18;
    public static final int X_SPACING = 24;
    public static final int Y_SPACING = 14;
    public static final int ROOT_SPACING = 36;
    private static final float FIT_MARGIN = 8f;

    public final CraftingTreeGraph graph = new CraftingTreeGraph();

    public final Map<ItemTreeSlot, Point> pointByNode = new HashMap<>();
    public final TreeMap<Integer, List<ItemTreeSlot>> rows = new TreeMap<>();
    public final Set<RecipeId> collapsedRecipes = Collections.newSetFromMap(new HashMap<>());
    public int treeWidth = 0;
    public int treeHeight = 0;

    private final Camera camera = new Camera();

    public void setViewport(int x, int y, int w, int h) {
        this.camera.setViewport(x, y, w, h);
    }

    public boolean isInWidget(int x, int y) {
        return this.camera.isInWidget(x, y);
    }

    public void fitToView() {
        final float firstResultCenterX = this.graph.roots.isEmpty() ? this.treeWidth / 2f
                : this.pointByNode.get(this.graph.roots.get(0)).x + SLOT_SIZE / 2f;

        this.camera.zoomLevel = 1.0f;
        this.camera.scrollX = this.treeWidth <= (this.camera.widgetW - FIT_MARGIN)
                ? (this.treeWidth - this.camera.widgetW) / 2f
                : firstResultCenterX - this.camera.widgetW / 2f;
        this.camera.scrollY = this.treeHeight <= (this.camera.widgetH - FIT_MARGIN)
                ? (this.treeHeight - this.camera.widgetH) / 2f
                : -FIT_MARGIN;
    }

    public void startDrag(int mouseX, int mouseY) {
        this.camera.startDrag(mouseX, mouseY);
    }

    public void drag(int mouseX, int mouseY) {
        this.camera.drag(mouseX, mouseY);
    }

    public void endDrag() {
        this.camera.endDrag();
    }

    public boolean isDragging() {
        return this.camera.isDragging();
    }

    public void scroll(int mouseX, int mouseY, int direction, boolean shiftKey, boolean ctrlKey) {
        this.camera.scroll(mouseX, mouseY, direction, shiftKey, ctrlKey);
    }

    public void scrollTo(ItemTreeSlot node) {
        if (node == null || !this.pointByNode.containsKey(node)) {
            return;
        }

        final Point point = this.pointByNode.get(node);
        final float nodeCenterX = point.x + SLOT_SIZE / 2f;
        final float nodeCenterY = point.y + SLOT_SIZE / 2f;

        this.camera.centerOn(nodeCenterX, nodeCenterY);
    }

    public ItemTreeSlot getNodeMouseOver(Point2D mouse) {
        return findVisibleNode(mouse, (node, local) -> {
            final Point point = this.pointByNode.get(node);

            return local.x >= point.x && local.x < point.x + SLOT_SIZE
                    && local.y >= point.y
                    && local.y < point.y + SLOT_SIZE;
        });
    }

    public ItemTreeSlot getHandlerBadgeMouseOver(Point2D mouse) {
        return findVisibleNode(mouse, (node, local) -> {
            if (node.prefItem == null || !this.graph.childrenOf.containsKey(node)) {
                return false;
            }

            final Point point = this.pointByNode.get(node);
            final float[] badge = badgeRect(point.x, point.y);

            return local.x >= badge[0] && local.x < badge[0] + badge[2]
                    && local.y >= badge[1]
                    && local.y < badge[1] + badge[3];
        });
    }

    public ItemTreeSlot getCollapseArrowMouseOver(Point2D mouse) {
        return findVisibleNode(mouse, (node, local) -> {
            if (!this.graph.childrenOf.containsKey(node) || node.prefItem == null
                    || this.collapsedRecipes.contains(node.prefItem.recipeId) && !this.graph.roots.contains(node)) {
                return false;
            }

            final Point point = this.pointByNode.get(node);
            final float iconX = point.x + SLOT_SIZE / 2 - ICON_COLLAPSED.width - 1;
            final float iconY = point.y + SLOT_SIZE + 1;
            final float iconW = ICON_COLLAPSED.width;
            final float iconH = ICON_COLLAPSED.height;

            return local.x >= iconX && local.x < iconX + iconW && local.y >= iconY && local.y < iconY + iconH;
        });
    }

    private ItemTreeSlot findVisibleNode(Point2D mouse, BiPredicate<ItemTreeSlot, Point2D.Double> hitTest) {
        final Point2D.Double local = this.camera.toWorldPoint(mouse);

        for (List<ItemTreeSlot> row : this.rows.subMap(camera.visibleRowMin(), camera.visibleRowMax()).values()) {
            for (ItemTreeSlot node : row) {
                if (hitTest.test(node, local)) {
                    return node;
                }
            }
        }

        return null;
    }

    public void draw(Point2D mouse, Set<BookmarkItem> collapsedItems, SearchResult searchResult,
            boolean useInventorySnapshot, ItemTreeSlot hoveredNode, ItemTreeSlot deleteHoverNode) {
        this.camera.clampToBounds(this.treeWidth, this.treeHeight);

        final float zScrollX = this.camera.scrollX * this.camera.zoomLevel;
        final float zScrollY = this.camera.scrollY * this.camera.zoomLevel;
        final int cropXMin = (int) ((zScrollX - 32) / this.camera.zoomLevel);
        final int cropXMax = (int) ((zScrollX + 32) / this.camera.zoomLevel)
                + (int) (this.camera.widgetW / this.camera.zoomLevel);
        final ItemTreeSlot collapseHoverNode = this.camera.isDragging() ? null : getCollapseArrowMouseOver(mouse);

        GuiHelper.useScissor(this.camera.widgetX, this.camera.widgetY, this.camera.widgetW, this.camera.widgetH, () -> {
            GL11.glPushMatrix();
            GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_LIGHTING_BIT);
            GL11.glColor4f(1, 1, 1, 1);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);
            RenderHelper.enableGUIStandardItemLighting();
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glTranslatef(this.camera.widgetX - zScrollX, this.camera.widgetY - zScrollY, 0.0f);
            GL11.glScalef(this.camera.zoomLevel, this.camera.zoomLevel, 1.0f);

            for (List<ItemTreeSlot> row : this.rows.subMap(this.camera.visibleRowMin(), this.camera.visibleRowMax())
                    .values()) {
                for (ItemTreeSlot node : row) {
                    final Point point = this.pointByNode.get(node);

                    drawParentLine(node);

                    if (point.x >= cropXMin && point.x <= cropXMax) {
                        drawSlot(node, useInventorySnapshot, searchResult, collapsedItems, node == hoveredNode);
                        drawCollapseIcon(node, collapsedItems, node == collapseHoverNode);

                        if (node == hoveredNode) {
                            NEIClientUtils.gl2DRenderContext(
                                    () -> {
                                        GuiDraw.drawRect(
                                                point.x + 1,
                                                point.y + 1,
                                                SLOT_SIZE - 2,
                                                SLOT_SIZE - 2,
                                                HOVER_COLOR);
                                    });
                        }

                        if (node.prefItem != null && this.graph.childrenOf.containsKey(node)) {
                            drawSlotBadge(node, point.x, point.y);

                            if (node == deleteHoverNode) {
                                drawDeleteButton(point);
                            }
                        }

                    }
                }
            }

            GL11.glPopAttrib();
            GL11.glPopMatrix();
        });

        drawScrollbars();
    }

    public void drawFull(Set<BookmarkItem> collapsedItems, SearchResult searchResult, boolean useInventorySnapshot) {
        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_LIGHTING_BIT);
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        RenderHelper.enableGUIStandardItemLighting();
        GL11.glDisable(GL11.GL_LIGHTING);

        for (List<ItemTreeSlot> row : this.rows.values()) {
            for (ItemTreeSlot node : row) {
                final Point point = this.pointByNode.get(node);

                drawParentLine(node);
                drawSlot(node, useInventorySnapshot, searchResult, collapsedItems, false);

                if (node.prefItem != null && this.graph.childrenOf.containsKey(node)) {
                    drawSlotBadge(node, point.x, point.y);
                }
            }
        }

        GL11.glPopAttrib();
        GL11.glPopMatrix();
    }

    private void drawSlot(ItemTreeSlot node, boolean useInventorySnapshot, SearchResult searchResult,
            Set<BookmarkItem> collapsedItems, boolean hovered) {
        final Point point = this.pointByNode.get(node);

        if (searchResult != null) {

            if (node == searchResult.highlightNode) {
                SLOT_HIGHLIGHTED.draw(point.x, point.y);
            } else if (searchResult.searchMatches.contains(node)) {
                SLOT_DEFAULT.draw(point.x, point.y);
            } else {
                SLOT_DISABLED.draw(point.x, point.y);
            }

        } else if (useInventorySnapshot) {

            if (node.leftAmount == 0) {

                if (this.graph.parentOf.get(node) != null && this.graph.parentOf.get(node).leftAmount == 0
                        || node.requestedAmount == 0) {
                    SLOT_DISABLED.draw(point.x, point.y);
                } else {
                    SLOT_DEFAULT.draw(point.x, point.y);
                }

            } else if (!this.graph.childrenOf.containsKey(node)) {
                SLOT_MISSING.draw(point.x, point.y);
            } else if (collapsedItems.contains(node.ingrItem)) {

                if (this.graph.subtreeMissingCache.contains(node)) {
                    SLOT_MISSING.draw(point.x, point.y);
                } else {
                    SLOT_CRAFTING.draw(point.x, point.y);
                }

            } else if (!this.graph.childrenNeedCraftingCache.contains(node)) {
                SLOT_CRAFTING.draw(point.x, point.y);
            } else {
                SLOT_DEFAULT.draw(point.x, point.y);
            }

        } else {
            SLOT_DEFAULT.draw(point.x, point.y);
        }

        GuiContainerManager
                .drawItem(point.x + (SLOT_SIZE - 16) / 2, point.y + (SLOT_SIZE - 16) / 2, node.emptyStack, true);

        if (node.requestedAmount > 0 && node.leftAmount == 0) {
            drawStackSize(node, node.ingrItem.getStackSize(node.requestedAmount), point.x, point.y, true);
        } else if (node.requestedAmount > 0 && node.leftAmount > 0) {
            final long amount = hovered && node.prefItem != null
                    && this.graph.roots.contains(node)
                    && this.collapsedRecipes.contains(node.prefItem.recipeId)
                    && NEIClientUtils.controlKey() ? node.currentRequestedAmount : node.requestedAmount;

            drawStackSize(node, node.ingrItem.getStackSize(amount), point.x, point.y, false);
        }

    }

    protected void drawStackSize(ItemTreeSlot node, long stackSize, int x, int y, boolean resolved) {
        String amountString = stackSize < 10_000 ? String.valueOf(stackSize)
                : ReadableNumberConverter.INSTANCE.toWideReadableForm(stackSize);

        if (node.isFluidDisplay) {
            amountString += "L";
        }

        final boolean hasOutputChance = node.prefItem != null && node.prefItem.chance != PositionedStack.CHANCE_FULL;
        int color = 0xFFFFFF;

        if (node.ingrItem.chance != PositionedStack.CHANCE_FULL || hasOutputChance || node.isContainerItem) {
            amountString = "~" + amountString;
            color = 0xFFAA00;
        }

        if (resolved) {
            color = 0x55FF55;
        }

        NEIClientUtils.drawNEIOverlayText(
                amountString,
                new Rectangle4i(
                        x + (SLOT_SIZE - 16) / 2,
                        y + (SLOT_SIZE - 16) / 2,
                        SLOT_SIZE - (SLOT_SIZE - 16),
                        SLOT_SIZE - (SLOT_SIZE - 16)),
                1,
                color,
                true,
                node.isFluidDisplay ? Alignment.BottomLeft : Alignment.BottomRight);
    }

    protected void drawSlotBadge(ItemTreeSlot node, int x, int y) {
        final float[] badge = badgeRect(x, y);

        GL11.glPushMatrix();
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glTranslatef(badge[0], badge[1], 4.0f);
        GL11.glScalef(BADGE_SCALE, BADGE_SCALE, 1.0f);

        if (node.info.hasImageOrItem()) {
            final DrawableResource image = node.info.getImage();

            if (image != null) {
                image.draw(0, 0);
            } else {
                GuiContainerManager.drawItem(0, 0, node.info.getItemStack(), true);
            }

        } else {
            ICON_CRAFTING.draw(0, 0);
        }

        GL11.glPopMatrix();
    }

    private void drawCollapseIcon(ItemTreeSlot node, Set<BookmarkItem> collapsedItems, boolean hover) {

        if (!this.graph.childrenOf.containsKey(node) || node.prefItem == null
                || this.collapsedRecipes.contains(node.prefItem.recipeId) && !this.graph.roots.contains(node)) {
            return;
        }

        final DrawableResource icon;

        if (collapsedItems.contains(node.ingrItem)) {
            icon = hover ? ICON_COLLAPSED_HOVER : ICON_COLLAPSED;
        } else {
            icon = hover ? ICON_EXPANDED_HOVER : ICON_EXPANDED;
        }

        final Point point = this.pointByNode.get(node);

        GL11.glColor4f(1, 1, 1, 1);
        icon.draw(point.x + SLOT_SIZE / 2 - icon.width - 1, point.y + SLOT_SIZE + 1);
    }

    private void drawDeleteButton(Point point) {
        final float[] badge = badgeRect(point.x, point.y);

        GL11.glPushMatrix();
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glTranslatef(badge[0], badge[1], 350.0f);
        GL11.glScalef(BADGE_SCALE, BADGE_SCALE, 1.0f);
        ICON_REMOVE.draw(0, 0);
        GL11.glPopMatrix();
    }

    private static float[] badgeRect(float x, float y) {
        final float w = ICON_REMOVE.width * BADGE_SCALE;
        final float h = ICON_REMOVE.height * BADGE_SCALE;

        return new float[] { x - w / 2 + 1, y - h / 2 + 1, w, h };
    }

    private void drawParentLine(ItemTreeSlot node) {

        if (!this.graph.parentOf.containsKey(node)) {
            return;
        }

        final Point point = this.pointByNode.get(node);
        final float x0 = point.x + SLOT_SIZE / 2f;
        final float y0 = point.y - 1;

        final ItemTreeSlot parentNode = this.graph.parentOf.get(node);
        final Point parentPoint = this.pointByNode.get(parentNode);
        final float x1 = parentPoint.x + SLOT_SIZE / 2f;
        final float y1 = parentPoint.y + SLOT_SIZE;

        if (x0 != x1) {
            final float midY = (y0 + y1) / 2f;

            NEIClientUtils.drawRect(x0, Math.min(y0, midY), 1, Math.abs(midY - y0) + 1, LINE_COLOR);
            NEIClientUtils.drawRect(Math.min(x0, x1), midY, Math.abs(x1 - x0) + 1, 1, LINE_COLOR);
            NEIClientUtils.drawRect(x1, Math.min(midY, y1), 1, Math.abs(y1 - midY) + 1, LINE_COLOR);
        } else {
            NEIClientUtils.drawRect(x0, Math.min(y0, y1), 1, Math.abs(y1 - y0) + 1, LINE_COLOR);
        }
    }

    private void drawScrollbars() {
        if (this.treeWidth <= 0 || this.treeHeight <= 0) {
            return;
        }

        final float scrollXPct = Camera.clamp(this.camera.scrollX / this.treeWidth, 0.0f, 1.0f);
        final float scrollYPct = Camera.clamp(this.camera.scrollY / this.treeHeight, 0.0f, 1.0f);

        GL11.glTranslatef(0, 0, 500.0f);

        SCROLLBAR_H.draw(
                this.camera.widgetX + this.camera.widgetW - 3,
                (int) (this.camera.widgetY + scrollYPct * (this.camera.widgetH - SCROLLBAR_H.height)));

        SCROLLBAR_W.draw(
                (int) (this.camera.widgetX + scrollXPct * (this.camera.widgetW - SCROLLBAR_W.width)),
                this.camera.widgetY + this.camera.widgetH - 3);

        GL11.glTranslatef(0, 0, -500.0f);
    }

    public void recalculateCoordinates(Set<RecipeId> collapsedRecipes, Set<BookmarkItem> collapsedItems,
            ItemTreeSlot anchor) {
        final Map<ItemTreeSlot, Integer> offsetFromParent = new HashMap<>();
        final Point anchorOldPoint = anchor != null ? resolveAnchorPoint(anchor) : null;

        this.collapsedRecipes.clear();
        this.pointByNode.clear();
        this.rows.clear();
        this.treeWidth = 0;
        this.treeHeight = 0;

        this.collapsedRecipes.addAll(collapsedRecipes);

        for (ItemTreeSlot node : this.graph.items) {
            this.pointByNode.put(node, new Point(0, 0));
        }

        final List<int[]> contour = new ArrayList<>();

        for (ItemTreeSlot root : this.graph.roots) {
            final List<int[]> layout = computeLayout(root, collapsedItems, offsetFromParent);
            final int shift = contour.isEmpty() ? 0 : requiredRootShift(contour, layout);

            mergeContour(contour, layout, shift);
            assignPositions(root, shift, 0, collapsedItems, offsetFromParent);
        }

        int minX = 0;

        for (Point point : this.pointByNode.values()) {
            minX = Math.min(minX, point.x);
        }

        if (minX < 0) {
            for (Point point : this.pointByNode.values()) {
                point.x -= minX;
            }
        }

        for (List<ItemTreeSlot> row : this.rows.values()) {
            for (ItemTreeSlot node : row) {
                this.treeWidth = Math.max(this.treeWidth, this.pointByNode.get(node).x + CraftingTreeCanvas.SLOT_SIZE);
                this.treeHeight = Math
                        .max(this.treeHeight, this.pointByNode.get(node).y + CraftingTreeCanvas.SLOT_SIZE);
            }
        }

        if (anchorOldPoint != null) {
            final Point anchorNewPoint = resolveAnchorPoint(anchor);

            if (anchorNewPoint != null) {
                this.camera.scrollX += anchorNewPoint.x - anchorOldPoint.x;
                this.camera.scrollY += anchorNewPoint.y - anchorOldPoint.y;
            }
        }
    }

    private Point resolveAnchorPoint(ItemTreeSlot anchor) {
        Point result = this.pointByNode.get(anchor);

        if (result == null) {
            for (Map.Entry<ItemTreeSlot, Point> entry : this.pointByNode.entrySet()) {
                if (entry.getKey().ingrItem.equals(anchor.ingrItem)) {
                    return entry.getValue();
                }
            }

        }

        return result;
    }

    private List<int[]> computeLayout(ItemTreeSlot node, Set<BookmarkItem> collapsedItems,
            Map<ItemTreeSlot, Integer> offsetFromParent) {
        List<ItemTreeSlot> children = Collections.emptyList();

        if (!collapsedItems.contains(node.ingrItem)) {
            children = this.graph.childrenOf.getOrDefault(node, Collections.emptyList());
        }

        if (children.isEmpty()) {
            final List<int[]> rows = new ArrayList<>();
            rows.add(new int[] { 0, 0 });
            return rows;
        }

        final int[] offsets = new int[children.size()];
        final List<int[]> combined = new ArrayList<>();

        for (int i = 0; i < children.size(); i++) {
            final List<int[]> childRows = computeLayout(children.get(i), collapsedItems, offsetFromParent);
            final int shift = i == 0 ? 0 : requiredShift(combined, childRows, X_SPACING);

            offsets[i] = shift;
            mergeContour(combined, childRows, shift);
        }

        final int selfX = (offsets[0] + offsets[children.size() - 1]) / 2;

        for (int i = 0; i < children.size(); i++) {
            offsetFromParent.put(children.get(i), offsets[i] - selfX);
        }

        for (int[] row : combined) {
            row[0] -= selfX;
            row[1] -= selfX;
        }

        final List<int[]> rows = new ArrayList<>(combined.size() + 1);
        rows.add(new int[] { 0, 0 });
        rows.addAll(combined);

        return rows;
    }

    private static int requiredShift(List<int[]> existing, List<int[]> incoming, int spacing) {
        int shift = 0;
        final int rows = Math.min(existing.size(), incoming.size());

        for (int r = 0; r < rows; r++) {
            shift = Math.max(shift, existing.get(r)[1] + spacing - incoming.get(r)[0]);
        }

        return shift;
    }

    private static int requiredRootShift(List<int[]> existing, List<int[]> incoming) {
        int existingRight = Integer.MIN_VALUE;

        for (int[] row : existing) {
            existingRight = Math.max(existingRight, row[1]);
        }

        int incomingLeft = Integer.MAX_VALUE;

        for (int[] row : incoming) {
            incomingLeft = Math.min(incomingLeft, row[0]);
        }

        return Math.max(0, existingRight + ROOT_SPACING - incomingLeft);
    }

    private static void mergeContour(List<int[]> target, List<int[]> source, int shift) {
        for (int r = 0; r < source.size(); r++) {
            final int left = source.get(r)[0] + shift;
            final int right = source.get(r)[1] + shift;

            if (r < target.size()) {
                target.get(r)[0] = Math.min(target.get(r)[0], left);
                target.get(r)[1] = Math.max(target.get(r)[1], right);
            } else {
                target.add(new int[] { left, right });
            }
        }
    }

    private void assignPositions(ItemTreeSlot node, int x, int y, Set<BookmarkItem> collapsedItems,
            Map<ItemTreeSlot, Integer> offsetFromParent) {
        this.rows.computeIfAbsent(y, ignored -> new ArrayList<>()).add(node);
        this.pointByNode.put(node, new Point(x, y));

        final int childY = y + CraftingTreeCanvas.SLOT_SIZE + Y_SPACING;

        if (!collapsedItems.contains(node.ingrItem)) {
            for (ItemTreeSlot child : this.graph.childrenOf.getOrDefault(node, Collections.emptyList())) {
                assignPositions(child, x + offsetFromParent.get(child), childY, collapsedItems, offsetFromParent);
            }
        }

    }
}
