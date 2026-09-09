package codechicken.nei.bookmark.tree;

import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.IntBuffer;
import java.nio.file.FileAlreadyExistsException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.event.ClickEvent;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.BufferUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.KeyManager;
import codechicken.nei.LRUCache;
import codechicken.nei.NEICPH;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.PositionedStack;
import codechicken.nei.SearchField.GuiSearchField;
import codechicken.nei.VisiblityData;
import codechicken.nei.Widget;
import codechicken.nei.api.IGuiContainerOverlay;
import codechicken.nei.api.INEIGuiHandler;
import codechicken.nei.api.ItemFilter;
import codechicken.nei.bookmark.BookmarkGrid;
import codechicken.nei.bookmark.BookmarkGroup;
import codechicken.nei.bookmark.BookmarkItem;
import codechicken.nei.bookmark.BookmarkItem.BookmarkItemType;
import codechicken.nei.bookmark.BookmarkPayload;
import codechicken.nei.drawable.DrawableBuilder;
import codechicken.nei.drawable.DrawableResource;
import codechicken.nei.guihook.IContainerTooltipHandler;
import codechicken.nei.guihook.IGuiClientSide;
import codechicken.nei.guihook.IGuiHandleMouseWheel;
import codechicken.nei.recipe.AcceptsFollowingTooltipLineHandler;
import codechicken.nei.recipe.AutoCraftingManager;
import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.GuiOverlayButton;
import codechicken.nei.recipe.GuiRecipe;
import codechicken.nei.recipe.GuiRecipeButton.UpdateRecipeButtonsEvent;
import codechicken.nei.recipe.GuiRecipeTab;
import codechicken.nei.recipe.Recipe;
import codechicken.nei.recipe.Recipe.RecipeId;
import codechicken.nei.recipe.RecipeTooltipLineHandler;
import codechicken.nei.recipe.StackInfo;
import codechicken.nei.util.EmptyContainer;
import codechicken.nei.util.NEIMouseUtils;
import codechicken.nei.util.ReadableNumberConverter;
import codechicken.nei.util.SlotInaccessible;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class GuiCraftingTree extends GuiContainer implements INEIGuiHandler, IGuiClientSide, IGuiHandleMouseWheel,
        IContainerTooltipHandler, IGuiContainerOverlay {

    public static class EventHandler {

        @SubscribeEvent
        public void updateRecipeButtons(UpdateRecipeButtonsEvent.Post event) {
            GuiScreen screen = event.gui;

            while (screen instanceof GuiRecipe<?>recipeGui) {
                screen = recipeGui.prevGui;
            }

            if (screen instanceof GuiCraftingTree treeGui) {
                for (int index = 0; index < event.buttonList.size(); index++) {
                    if (event.buttonList.get(index) instanceof GuiOverlayButton button) {
                        event.buttonList.set(
                                index,
                                new TreeOverlayButton(treeGui, button.handlerRef, button.xPosition, button.yPosition));
                    }
                }
            }

        }
    }

    private static final DrawableResource BG_TEXTURE = new DrawableBuilder(
            "nei:textures/gui/craftingtree.png",
            0,
            0,
            64,
            64).build();

    private static final DateTimeFormatter SCREENSHOT_DATE_FORMAT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd_HH.mm.ss", Locale.ROOT);

    private static final int MARGIN = 4;
    private static final int PADDING = 9;
    private static final int PADDING_TOP = 22;

    private static LRUCache<String, String> handlerTitleCache = new LRUCache<>(100);

    private GuiScreen prevGui;
    private boolean isFirstScreen = true;

    private final BookmarkGrid grid;
    private final int groupId;
    private CraftingTreeMath math;
    private Set<RecipeId> collapsedRecipes;

    private final StatsPanel statsPanel = new StatsPanel();
    private final StatsSectionsMenu statsSectionsMenu = new StatsSectionsMenu();
    private final CraftingTreeCanvas canvas = new CraftingTreeCanvas();
    private boolean statsPanelVisible;

    private GuiSearchField searchField;

    private RecipeTooltipLineHandler recipeTooltipLineHandler;
    private AcceptsFollowingTooltipLineHandler acceptsFollowingTooltipLineHandler;

    private final CraftingTreeState uiState = new CraftingTreeState();
    private final CraftingTreeToolbar toolbar;

    private ItemTreeSlot hoveredNode;
    private ItemTreeSlot hoveredBadgeHandler;

    protected GuiCraftingTree(GuiScreen prevGui, BookmarkGrid grid, int groupId) {
        super(new EmptyContainer());
        this.prevGui = prevGui;
        this.grid = grid;
        this.groupId = groupId;

        this.toolbar = new CraftingTreeToolbar(this.uiState);
    }

    public static void openCraftingTreeGui(BookmarkGrid grid, int groupId) {
        final Minecraft mc = NEIClientUtils.mc();
        mc.displayGuiScreen(new GuiCraftingTree(mc.currentScreen, grid, groupId));
    }

    protected void addRecipe(Recipe recipe) {
        final ItemTreeSlot replaceNode = this.uiState.replaceRecipeNode;
        this.uiState.replaceRecipeNode = null;

        if (recipe != null) {
            final RecipeId recipeId = recipe.getRecipeId();
            boolean changed = false;

            if (replaceNode != null) {
                final Set<RecipeId> unusedRecipes = this.canvas.graph.collectUnusedRecipes(replaceNode);
                unusedRecipes.remove(recipeId);

                for (RecipeId unusedId : unusedRecipes) {
                    changed |= this.grid.removeRecipe(unusedId, this.groupId);
                }
            }

            if (!this.grid.existsRecipe(recipeId, this.groupId)) {
                this.grid.addRecipe(recipe, 1, this.groupId);
                changed = true;
            }

            if (changed) {
                rebuildTree(null);
            }
        }

        NEIClientUtils.mc().displayGuiScreen(this);
    }

    @Override
    public void initGui() {
        this.xSize = this.width;
        this.ySize = this.height;

        super.initGui();

        this.canvas.setViewport(
                MARGIN + PADDING,
                MARGIN + PADDING_TOP,
                this.width - MARGIN * 2 - PADDING * 2,
                this.height - MARGIN * 2 - PADDING_TOP - PADDING);

        this.searchField = new GuiSearchField() {

            @Override
            public void mouseClicked(int x, int y, int button) {

                if (button == 1 && x >= this.xPosition
                        && x < this.xPosition + this.width
                        && y >= this.yPosition
                        && y < this.yPosition + this.height) {
                    setText("");
                    onSearchTextChanged();
                } else {
                    super.mouseClicked(x, y, button);
                }

            }

        };
        this.searchField.width = Math.min(140, (this.width - MARGIN * 2 - PADDING * 2) / 3);
        this.searchField.height = 14;
        this.searchField.xPosition = this.width - MARGIN - this.searchField.width - PADDING;
        this.searchField.yPosition = MARGIN + 5;
        this.searchField.setMaxStringLength(256);

        this.toolbar.layout(MARGIN + PADDING, MARGIN + 4);

        this.buttonList.clear();
        this.toolbar.addTo(this.buttonList);

        this.uiState.load();

        rebuildTree(null);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public GuiContainer getFirstScreen() {

        if (this.prevGui instanceof IGuiContainerOverlay overlay) {
            return overlay.getFirstScreen();
        }

        return this.prevGui instanceof GuiContainer gui ? gui : null;
    }

    @Override
    public GuiScreen getFirstScreenGeneral() {
        return this;
    }

    @Override
    public void updateScreen() {
        this.statsPanel.update();

        super.updateScreen();
    }

    private void rebuildTree(ItemTreeSlot anchor) {
        final BookmarkGroup group = this.grid.getGroup(this.groupId);
        final List<BookmarkItem> chainItems = group.crafting != null
                ? new ArrayList<>(this.grid.createChainItems(this.groupId).values())
                : Collections.emptyList();

        this.collapsedRecipes = group.collapsedRecipes;
        this.math = new CraftingTreeMath(chainItems, this.collapsedRecipes);
        this.math.initialItems.clear();

        if (this.uiState.useInventorySnapshot) {
            final GuiContainer firstGui = getFirstScreen();

            if (firstGui != null) {
                for (ItemStack stack : AutoCraftingManager.getInventoryItems(firstGui).values()) {
                    if (stack != null) {
                        this.math.initialItems.add(BookmarkItem.of(-1, stack));
                    }
                }
            }
        }

        this.canvas.graph.rebuild(this.math);

        if (this.isFirstScreen) {
            updateCollapsedItems();
        } else {
            this.canvas.recalculateCoordinates(this.collapsedRecipes, this.uiState.collapsedItems, anchor);
        }

        rebuildStatsPanel();

        if (this.isFirstScreen) {
            this.isFirstScreen = false;
            this.canvas.fitToView();
        }
    }

    private void rebuildStatsPanel() {
        this.statsPanel.rebuild(
                this.math,
                this.canvas.graph,
                this.uiState.useInventorySnapshot,
                this.uiState.visibleStatsSections,
                MARGIN + PADDING,
                MARGIN + PADDING_TOP,
                this.width - MARGIN * 2 - PADDING * 2,
                this.height - MARGIN * 2 - PADDING_TOP - PADDING);

        updateSearchItemMatches();
        updateSearchStatsMatches();
        this.statsPanel.setHighlight(this.uiState.searchStatsResult);
        this.statsPanelVisible = this.uiState.statsPanelVisible && !this.statsPanel.getWidgets().isEmpty();

        this.canvas.setViewport(
                MARGIN + PADDING,
                MARGIN + PADDING_TOP,
                this.width - MARGIN * 2 - PADDING * 2,
                this.height - MARGIN * 2 - PADDING_TOP - PADDING - (this.statsPanelVisible ? this.statsPanel.h : 0));
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        final boolean canHover = !this.canvas.isDragging() && this.canvas.isInWidget(mouseX, mouseY)
                && this.statsPanel.getStackMouseOver(mouseX, mouseY) == null;
        this.hoveredBadgeHandler = canHover ? this.canvas.getHandlerBadgeMouseOver(getMousePosition()) : null;
        this.hoveredNode = canHover && hoveredBadgeHandler == null ? this.canvas.getNodeMouseOver(getMousePosition())
                : null;

        BG_TEXTURE.draw(
                MARGIN,
                MARGIN,
                this.width - MARGIN * 2,
                this.height - MARGIN * 2,
                PADDING,
                PADDING,
                PADDING_TOP,
                PADDING);

        if (this.math.outputRecipes.isEmpty()) {
            drawCenteredString(
                    this.fontRendererObj,
                    NEIClientUtils.translate("bookmark.tree.empty"),
                    this.width / 2,
                    MARGIN + PADDING_TOP + (this.height - MARGIN * 2 - PADDING_TOP) / 2,
                    0xFFAAAAAA);
        } else {
            this.canvas.draw(
                    getMousePosition(),
                    this.uiState.collapsedItems,
                    this.uiState.activeSearchResult(),
                    this.uiState.useInventorySnapshot,
                    this.hoveredNode,
                    this.hoveredBadgeHandler);

            if (this.statsPanelVisible) {
                this.statsPanel.draw(mouseX, mouseY);
            }
        }

        drawCenteredString(
                this.fontRendererObj,
                NEIClientUtils.translate("bookmark.tree.title"),
                this.width / 2,
                MARGIN + PADDING_TOP / 2 - this.fontRendererObj.FONT_HEIGHT / 2 + 1,
                0xFFFFFFFF);

        this.searchField.drawTextBox();

        this.statsSectionsMenu.draw(mouseX, mouseY, this.uiState);
    }

    @Override
    protected void actionPerformed(GuiButton button) {

        switch (button.id) {
            case CraftingTreeToolbar.INV_BUTTON_ID:
                this.uiState.toggleUseInventorySnapshot();
                rebuildTree(null);
                break;
            case CraftingTreeToolbar.FIT_BUTTON_ID:
                this.canvas.fitToView();
                break;
            case CraftingTreeToolbar.TOGGLE_STATS_BUTTON_ID:
                this.uiState.toggleStatsPanelVisible();
                rebuildStatsPanel();
                break;
            case CraftingTreeToolbar.STATS_SECTIONS_BUTTON_ID:

                if (this.statsSectionsMenu.visible) {
                    this.statsSectionsMenu.close();
                } else {
                    this.statsSectionsMenu.open(button.xPosition, button.yPosition + button.height + 2);
                }

                break;
            case CraftingTreeToolbar.LINK_BUTTON_ID:
                NEIClientUtils.sendChatItemLink(BookmarkPayload.of(this.groupId).toNBT());
                break;
            case CraftingTreeToolbar.COLLAPSE_ALL_BUTTON_ID:
                this.uiState.cycleCollapseMode();
                updateCollapsedItems();
                this.canvas.fitToView();
                break;
            case CraftingTreeToolbar.SCREENSHOT_BUTTON_ID:
                saveScreenshot();
                break;
            default:
                return;
        }

    }

    private void saveScreenshot() {

        if (this.math.outputRecipes.isEmpty()) {
            return;
        }

        if (!OpenGlHelper.isFramebufferEnabled()) {
            NEIClientUtils.printChatMessage(
                    new ChatComponentText(NEIClientUtils.translate("bookmark.tree.screenshot.fbo_unsupported")));
            return;
        }

        final Minecraft mc = NEIClientUtils.mc();

        try {
            final int screenshotZoom = 2;
            final File screenshotsDir = new File(mc.mcDataDir, "screenshots");
            screenshotsDir.mkdirs();

            final StatsPanel screenshotStats = this.statsPanelVisible ? new StatsPanel() : null;
            final int treeWidth = this.canvas.treeWidth;
            final int treeHeight = this.canvas.treeHeight + 16;
            int statsWidth = 0;
            int statsHeight = 0;

            if (screenshotStats != null) {
                screenshotStats.rebuild(
                        this.math,
                        this.canvas.graph,
                        this.uiState.useInventorySnapshot,
                        this.uiState.visibleStatsSections,
                        0,
                        0,
                        treeWidth,
                        Short.MAX_VALUE);

                screenshotStats.x = 0;
                screenshotStats.y = treeHeight;
                screenshotStats.update();
                statsWidth = screenshotStats.getActualWidth();
                statsHeight = screenshotStats.h = screenshotStats.getActualHeight() + 8;
            }

            final int contentWidth = Math.max(treeWidth, statsWidth);
            final float treeShiftX = (contentWidth - treeWidth) / 2f;

            if (screenshotStats != null && statsWidth > treeWidth) {
                screenshotStats.w = statsWidth;
                screenshotStats.x = 0;
                screenshotStats.update();
            }

            final int maxGlTexSize = GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE) / 2;
            int imgHeight = screenshotZoom * (treeHeight + statsHeight + 16);
            int imgWidth = screenshotZoom * (contentWidth + 16);

            // Make sure the image can be actually allocated, worst case it'll be cropped
            while ((long) imgWidth * (long) imgHeight >= (long) Integer.MAX_VALUE / 4) {
                if (imgWidth > imgHeight) {
                    imgWidth /= 2;
                } else {
                    imgHeight /= 2;
                }
            }

            final int xChunks = (imgWidth + maxGlTexSize - 1) / maxGlTexSize;
            final int yChunks = (imgHeight + maxGlTexSize - 1) / maxGlTexSize;
            final int fbWidth = Math.min(imgWidth, maxGlTexSize);
            final int fbHeight = Math.min(imgHeight, maxGlTexSize);

            final BufferedImage outputImg = new BufferedImage(imgWidth, imgHeight, BufferedImage.TYPE_INT_ARGB);
            final IntBuffer downloadBuffer = BufferUtils.createIntBuffer(fbWidth * fbHeight);

            GL11.glPushMatrix();
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);

            final Framebuffer fb = new Framebuffer(fbWidth, fbHeight, true);

            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glLoadIdentity();
            GL11.glOrtho(0, fbWidth / (float) screenshotZoom, fbHeight / (float) screenshotZoom, 0, 1000, 3000);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glLoadIdentity();
            GL11.glDisable(GL11.GL_DEPTH_TEST);

            try {
                fb.bindFramebuffer(true);

                for (int xChunk = 0; xChunk < xChunks; xChunk++) {
                    final int xStart = xChunk * maxGlTexSize;
                    final int xChunkSize = Math.min((xChunk + 1) * maxGlTexSize, imgWidth) - xStart;

                    for (int yChunk = 0; yChunk < yChunks; yChunk++) {
                        final int yStart = yChunk * maxGlTexSize;
                        final int yChunkSize = Math.min((yChunk + 1) * maxGlTexSize, imgHeight) - yStart;

                        GL11.glPushMatrix();
                        GL11.glTranslatef(
                                8 - xStart / (float) screenshotZoom,
                                8 - yStart / (float) screenshotZoom,
                                -2000.0f);
                        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

                        GL11.glPushMatrix();
                        GL11.glTranslatef(treeShiftX, 0, 0);
                        this.canvas.drawFull(
                                this.uiState.collapsedItems,
                                this.uiState.activeSearchResult(),
                                this.uiState.useInventorySnapshot);
                        GL11.glPopMatrix();

                        if (screenshotStats != null) {
                            GL11.glPushMatrix();
                            screenshotStats.drawFull();
                            GL11.glPopMatrix();
                        }

                        GL11.glPopMatrix();

                        GL11.glBindTexture(GL11.GL_TEXTURE_2D, fb.framebufferTexture);
                        GL11.glGetTexImage(
                                GL11.GL_TEXTURE_2D,
                                0,
                                GL12.GL_BGRA,
                                GL12.GL_UNSIGNED_INT_8_8_8_8_REV,
                                downloadBuffer);

                        for (int y = 0; y < yChunkSize; y++) {
                            for (int x = 0; x < xChunkSize; x++) {
                                outputImg.setRGB(
                                        x + xStart,
                                        y + yStart,
                                        downloadBuffer.get((fbHeight - 1 - y) * fbWidth + x));
                            }
                        }
                    }
                }
            } finally {
                fb.deleteFramebuffer();
                GL11.glViewport(0, 0, mc.displayWidth, mc.displayHeight);
            }

            GL11.glPopAttrib();
            GL11.glPopMatrix();

            final String date = SCREENSHOT_DATE_FORMAT.format(LocalDateTime.now());
            String filename = String.format("%s-nei.png", date);
            File outFile = new File(screenshotsDir, filename);
            for (int i = 1; outFile.exists() && i < 99; i++) {
                filename = String.format("%s-nei-%d.png", date, i);
                outFile = new File(screenshotsDir, filename);
            }
            if (outFile.exists()) {
                throw new FileAlreadyExistsException(filename);
            }
            ImageIO.write(outputImg, "png", outFile);

            final ChatComponentText chatLink = new ChatComponentText(filename);
            chatLink.getChatStyle()
                    .setChatClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, outFile.getAbsolutePath()));
            chatLink.getChatStyle().setUnderlined(true);
            NEIClientUtils.printChatMessage(new ChatComponentTranslation("screenshot.success", chatLink));
        } catch (Exception e) {
            NEIClientUtils.printChatMessage(new ChatComponentTranslation("screenshot.failure", e.getMessage()));
        }
    }

    private void updateCollapsedItems() {
        this.uiState.collapsedItems.clear();

        for (ItemTreeSlot node : this.canvas.graph.childrenOf.keySet()) {

            if (this.canvas.graph.roots.contains(node)) {
                continue;
            }

            final boolean collapse = switch (this.uiState.collapseMode) {
                case EXPANDED -> false;
                case SMART -> !this.canvas.graph.childrenNeedCraftingCache.contains(node);
                case ALL -> true;
            };

            if (collapse) {
                this.uiState.collapsedItems.add(node.ingrItem);
            }
        }

        this.canvas.recalculateCoordinates(this.collapsedRecipes, this.uiState.collapsedItems, null);
    }

    protected ItemStack getItemStackMouseOver(int mousex, int mousey) {

        if (this.statsPanelVisible) {
            final ItemStack stack = this.statsPanel.getStackMouseOver(mousex, mousey);

            if (stack != null) {
                return stack;
            }
        }

        if (this.hoveredNode != null) {
            return this.hoveredNode.itemStack;
        }

        return null;
    }

    @Override
    public Slot getSlotAtPosition(int mousex, int mousey) {
        final ItemStack hoveredStack = getItemStackMouseOver(mousex, mousey);
        final EmptyContainer slotcontainer = (EmptyContainer) this.inventorySlots;
        slotcontainer.setActiveStack(hoveredStack);

        return hoveredStack != null ? new SlotInaccessible(hoveredStack, 0, 0) : null;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        this.searchField.mouseClicked(mouseX, mouseY, button);

        if (this.statsSectionsMenu.visible) {

            if (this.statsSectionsMenu.handleClick(mouseX, mouseY, this.uiState)) {
                rebuildStatsPanel();
                return;
            }

            if (!isOverButton(this.toolbar.getButton(CraftingTreeToolbar.STATS_SECTIONS_BUTTON_ID), mouseX, mouseY)) {
                this.statsSectionsMenu.close();
            }
        }

        if (this.statsPanelVisible) {

            if (handleWidgetClick(mouseX, mouseY, button)) {
                return;
            }

            if (this.statsPanel.handleClick(mouseX, mouseY, button)) {
                return;
            }
        }

        if (button == 0 && handleCanvasClick(mouseX, mouseY)) {
            return;
        }

        super.mouseClicked(mouseX, mouseY, button);
    }

    private static boolean isOverButton(GuiButton button, int mouseX, int mouseY) {
        return mouseX >= button.xPosition && mouseX < button.xPosition + button.width
                && mouseY >= button.yPosition
                && mouseY < button.yPosition + button.height;
    }

    private boolean handleWidgetClick(int mouseX, int mouseY, int button) {
        final Object widgetGUID = this.statsPanel.getWidgetGUIDUnderMouse(mouseX, mouseY);

        if (widgetGUID == null) {
            return false;
        }

        if (button == 1 && this.uiState.searchStatsResult != null
                && this.uiState.searchStatsResult.matchesGUID(widgetGUID)) {
            NEIClientUtils.playClickSound();

            this.uiState.searchStatsResult = null;
            this.statsPanel.setHighlight(null);
            onSearchTextChanged();
            return true;
        }

        if (button == 0) {
            NEIClientUtils.playClickSound();

            if (this.uiState.searchStatsResult == null || !this.uiState.searchStatsResult.matchesGUID(widgetGUID)) {
                this.uiState.searchItemResult = null;
                this.uiState.searchStatsResult = new SearchResult(widgetGUID);
                updateSearchStatsMatches();
                this.statsPanel.setHighlight(this.uiState.searchStatsResult);
            }

            searchGoTo(this.uiState.searchStatsResult, !NEIClientUtils.shiftKey());
            return true;
        }

        return false;
    }

    private boolean handleCanvasClick(int mouseX, int mouseY) {

        if (!this.canvas.isInWidget(mouseX, mouseY)) {
            return false;
        }

        final Point2D mouse = getMousePosition();
        final ItemTreeSlot deleteButtonTarget = this.canvas.getHandlerBadgeMouseOver(mouse);
        final ItemTreeSlot hoveredNode = deleteButtonTarget != null ? null : this.canvas.getNodeMouseOver(mouse);

        if (hoveredNode != null) {

            if (NEIClientUtils.altKey() && hoveredNode.prefItem != null) {
                NEIClientUtils.playClickSound();
                this.grid.toggleCollapsedRecipe(this.groupId, hoveredNode.prefItem.recipeId);
                rebuildTree(hoveredNode);
                return true;
            } else if (this.canvas.graph.childrenOf.containsKey(hoveredNode)) {
                this.uiState.replaceRecipeNode = hoveredNode;
            }

        } else if (deleteButtonTarget != null) {
            NEIClientUtils.playClickSound();
            final RecipeId recipeId = deleteButtonTarget.prefItem.recipeId;
            final int itemIndex = this.grid
                    .indexOf(this.groupId, deleteButtonTarget.prefItem.itemStack, recipeId, false);

            this.grid.removeRecipe(itemIndex, false);

            if (!this.grid.existsRecipe(recipeId, this.groupId)) {
                for (RecipeId unusedId : this.canvas.graph.collectUnusedRecipes(deleteButtonTarget)) {
                    this.grid.removeRecipe(unusedId, this.groupId);
                }
            }

            rebuildTree(null);
            return true;
        } else {
            final ItemTreeSlot collapseTarget = this.canvas.getCollapseArrowMouseOver(mouse);

            if (collapseTarget != null) {
                NEIClientUtils.playClickSound();
                final BookmarkItem item = collapseTarget.ingrItem;

                if (!this.uiState.collapsedItems.remove(item)) {
                    this.uiState.collapsedItems.add(item);
                }

                this.canvas.recalculateCoordinates(this.collapsedRecipes, this.uiState.collapsedItems, collapseTarget);

                return true;
            }

            this.canvas.startDrag(mouseX, mouseY);
        }

        return false;
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long timeSinceLastClick) {
        if (button != 0) {
            return;
        }

        if (this.statsPanelVisible) {

            if (this.statsPanel.isDraggingScrollbar()) {
                this.statsPanel.mouseDragged(mouseX, mouseY, button, timeSinceLastClick);
                return;
            }

        }

        this.canvas.drag(mouseX, mouseY);
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int state) {
        super.mouseMovedOrUp(mouseX, mouseY, state);

        if (this.statsPanelVisible) {
            this.statsPanel.mouseUp(mouseX, mouseY, state);
        }

        this.canvas.endDrag();
    }

    @Override
    public void mouseScrolled(int wheel) {
        final Point2D mouse = getMousePosition();

        if (handleItemWheel(wheel, mouse)) {
            return;
        }

        final int direction = wheel > 0 ? 1 : -1;

        if (NEIClientUtils.shiftKey() && this.acceptsFollowingTooltipLineHandler != null) {
            final BookmarkItem item = (BookmarkItem) this.acceptsFollowingTooltipLineHandler.tooltipGUID;
            final int itemIndex = this.grid
                    .indexOf(this.groupId, item.itemStack, item.recipeId, item.type == BookmarkItemType.INGREDIENT);

            if (item.permutations.size() > 1) {
                final List<ItemStack> items = acceptsFollowingTooltipLineHandler.getItems();
                ItemStack activeStack = this.acceptsFollowingTooltipLineHandler.getActiveStack();
                int stackIndex = 0;

                for (int i = 0; i < items.size(); i++) {
                    if (StackInfo.equalItemAndNBT(activeStack, items.get(i), true)) {
                        stackIndex = i;
                        break;
                    }
                }

                activeStack = items.get((items.size() - direction + stackIndex) % items.size());

                this.acceptsFollowingTooltipLineHandler.setActiveStack(activeStack);

                if (itemIndex != -1) {
                    final BookmarkItem oldItem = this.grid.getBookmarkItem(itemIndex);
                    final BookmarkItem newItem = oldItem.copyWithPerm(activeStack);
                    this.grid.replaceBookmarkItem(itemIndex, newItem);
                } else {
                    this.grid.onItemsChanged();
                }

                item.itemStack = activeStack;
                rebuildTree(null);
            }

            return;
        }

        this.canvas.scroll(
                (int) mouse.getX(),
                (int) mouse.getY(),
                direction,
                NEIClientUtils.shiftKey(),
                NEIClientUtils.controlKey());
    }

    private boolean handleItemWheel(int wheel, Point2D mouse) {
        final int direction = wheel > 0 ? 1 : -1;
        final int x = (int) mouse.getX();
        final int y = (int) mouse.getY();

        if (this.statsPanelVisible && this.statsPanel.contains(x, y) && this.statsPanel.onMouseWheel(direction, x, y)
                || !this.canvas.isInWidget(x, y)) {
            return true;
        }

        if (NEIClientUtils.controlKey()) {
            final ItemTreeSlot node = this.canvas.getNodeMouseOver(mouse);

            if (this.canvas.graph.roots.contains(node)) {
                final BookmarkItem item = node.ingrItem;
                final RecipeId recipeId = item.recipeId;

                if (recipeId != null) {
                    final long multiplier = NEIClientUtils.altKey() ? NEIClientUtils.getScrollMultiplier(item.itemStack)
                            : 1;

                    this.grid.shiftRecipeAmount(this.groupId, recipeId, multiplier * direction);
                    rebuildTree(null);

                    return true;
                }
            }

        }

        return false;
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {

        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.mc.displayGuiScreen(this.prevGui);
            NEICPH.sendRequestContainer();
            return;
        }

        if (this.searchField.isFocused()) {

            if (keyCode == Keyboard.KEY_RETURN) {
                if (this.uiState.searchItemResult == null) {
                    onSearchTextChanged();
                } else {
                    searchGoTo(this.uiState.searchItemResult, !NEIClientUtils.shiftKey());
                }
                return;
            }

            if (this.searchField.textboxKeyTyped(typedChar, keyCode)) {
                onSearchTextChanged();
                return;
            }
        }

        if (this.statsPanelVisible && KeyManager.isKeyDown("recipe.recipe")) {
            final Point2D mouse = getMousePosition();
            final Widget widget = this.statsPanel.getWidgetUnderMouse((int) mouse.getX(), (int) mouse.getY());

            if (widget != null && widget instanceof StatsPanel.ItemStatsSlot itemStatsSlot
                    && itemStatsSlot.recipeId != null) {
                GuiCraftingRecipe.openRecipeGui("recipeId", itemStatsSlot.recipeId.getResult(), itemStatsSlot.recipeId);
                return;
            }
        }

        super.keyTyped(typedChar, keyCode);
    }

    private static String getHandlerTitle(String handlerName) {
        return handlerTitleCache.computeIfAbsent(
                handlerName,
                hname -> Stream
                        .concat(
                                GuiCraftingRecipe.craftinghandlers.stream(),
                                GuiCraftingRecipe.serialCraftingHandlers.stream())
                        .filter(handler -> hname.equals(GuiRecipeTab.getHandlerInfo(handler).getHandlerName()))
                        .findFirst().map(handler -> handler.getRecipeName().trim()).orElse(hname));
    }

    @Override
    public List<String> handleTooltip(GuiContainer gui, int mousex, int mousey, List<String> currenttip) {
        final String toolbarTooltip = this.toolbar.getTooltip(gui.mc, mousex, mousey);

        if (this.recipeTooltipLineHandler != null && getSlotAtPosition(mousex, mousey) == null) {
            this.recipeTooltipLineHandler = null;
        }

        if (toolbarTooltip != null) {
            currenttip.add(toolbarTooltip);
        } else if (this.hoveredBadgeHandler != null) {
            final RecipeId recipeId = this.hoveredBadgeHandler.prefItem != null
                    ? this.hoveredBadgeHandler.prefItem.recipeId
                    : null;
            final long multiplier = this.hoveredBadgeHandler.multiplier;

            addRecipeTooltipLine(recipeId, multiplier, currenttip);
        } else {

            if (this.statsPanelVisible) {
                final Widget widget = this.statsPanel.getWidgetUnderMouse(mousex, mousey);

                if (widget instanceof StatsPanel.HandlerStatsSlot handlerStatsSlot) {
                    currenttip.add(getHandlerTitle(handlerStatsSlot.handlerName) + GuiDraw.TOOLTIP_LINESPACE);
                    currenttip.add(
                            EnumChatFormatting.GRAY + NEIClientUtils.translate(
                                    "bookmark.tree.stats.handler_recipes",
                                    handlerStatsSlot.stats.recipeCount));
                    currenttip.add(
                            EnumChatFormatting.GRAY + NEIClientUtils.translate(
                                    "bookmark.tree.stats.handler_iterations",
                                    handlerStatsSlot.stats.iterations));
                }
            }

        }

        return currenttip;
    }

    private void addRecipeTooltipLine(RecipeId recipeId, long multiplier, List<String> currenttip) {

        if (recipeId == null) {
            this.recipeTooltipLineHandler = null;
        } else
            if (this.recipeTooltipLineHandler == null || !this.recipeTooltipLineHandler.getRecipeId().equals(recipeId)
                    || this.recipeTooltipLineHandler.getMultiplier() != multiplier) {
                        this.recipeTooltipLineHandler = new RecipeTooltipLineHandler(recipeId, multiplier);
                    }

        if (this.recipeTooltipLineHandler != null) {
            currenttip.add(GuiDraw.TOOLTIP_HANDLER + GuiDraw.getTipLineId(this.recipeTooltipLineHandler));
        }
    }

    @Override
    public List<String> handleItemTooltip(GuiContainer gui, ItemStack itemstack, int mousex, int mousey,
            List<String> currenttip) {

        final ItemTreeSlot hoveredItemNode = this.hoveredNode;

        if (hoveredItemNode != null) {
            final BookmarkItem item = hoveredItemNode.ingrItem;

            if (item.chance != PositionedStack.CHANCE_FULL) {
                final String chanceText = NEIClientUtils
                        .formatChance(item.chance / (float) PositionedStack.CHANCE_FULL);

                currenttip.add(
                        1,
                        EnumChatFormatting.GRAY + NEIClientUtils.translate(
                                item.type == BookmarkItemType.INGREDIENT ? "recipe.badge.chance.consume"
                                        : "recipe.badge.chance.output",
                                chanceText));

            }

            if (this.uiState.useInventorySnapshot && hoveredItemNode.requestedAmount > 0) {

                if (hoveredItemNode.leftAmount == 0) {
                    final ItemStack resolvedStack = (hoveredItemNode.prefItem != null ? hoveredItemNode.prefItem
                            : hoveredItemNode.ingrItem).itemStack;
                    final String resolvedName = resolvedStack.getDisplayName();

                    currenttip.add(
                            1,
                            EnumChatFormatting.GRAY
                                    + NEIClientUtils.translate("bookmark.tree.item.state.resolved", resolvedName));
                } else if (hoveredItemNode.leftAmount != hoveredItemNode.requestedAmount) {
                    final long freshStackSize = item.getStackSize(hoveredItemNode.leftAmount);
                    String freshAmountText = freshStackSize < 10_000 ? String.valueOf(freshStackSize)
                            : ReadableNumberConverter.INSTANCE.toWideReadableForm(freshStackSize);

                    if (hoveredItemNode.isFluidDisplay) {
                        freshAmountText += "L";
                    }

                    currenttip.add(
                            1,
                            EnumChatFormatting.GRAY
                                    + NEIClientUtils.translate("bookmark.tree.item.state.partial", freshAmountText));
                }

                if (hoveredItemNode.leftAmount > 0 && this.canvas.graph.childrenOf.containsKey(hoveredItemNode)
                        && !this.canvas.graph.childrenNeedCraftingCache.contains(hoveredItemNode)) {
                    currenttip.add(
                            1,
                            EnumChatFormatting.GRAY + NEIClientUtils.translate("bookmark.tree.item.state.craftable"));
                }

            }

            if (hoveredItemNode.prefItem != null && this.canvas.graph.childrenOf.containsKey(hoveredItemNode)) {
                final BookmarkItem output = hoveredItemNode.prefItem;
                final long producedAmount = output.getAmount(hoveredItemNode.multiplier);
                final long surplus = producedAmount - hoveredItemNode.requestedAmount;

                if (surplus > 0) {
                    String surplusText = surplus < 10_000 ? String.valueOf(surplus)
                            : ReadableNumberConverter.INSTANCE.toWideReadableForm(surplus);

                    if (hoveredItemNode.isFluidDisplay) {
                        surplusText += "L";
                    }

                    currenttip.add(
                            1,
                            EnumChatFormatting.GRAY
                                    + NEIClientUtils.translate("bookmark.tree.item.state.surplus", surplusText));
                }
            }

            if (item.recipeId == null || hoveredItemNode.prefItem != null
                    || hoveredItemNode.leftAmount == 0 && hoveredItemNode.requestedAmount > 0) {
                this.acceptsFollowingTooltipLineHandler = null;
            } else if (this.acceptsFollowingTooltipLineHandler == null
                    || !item.equals(this.acceptsFollowingTooltipLineHandler.tooltipGUID)) {
                        this.acceptsFollowingTooltipLineHandler = AcceptsFollowingTooltipLineHandler.of(
                                item,
                                new ArrayList<>(item.permutations.values()),
                                hoveredItemNode.ingrItem.getItemStack(),
                                2);
                    }

            if (this.acceptsFollowingTooltipLineHandler != null) {
                currenttip.add(GuiDraw.TOOLTIP_HANDLER + GuiDraw.getTipLineId(this.acceptsFollowingTooltipLineHandler));
            }

        } else {
            this.acceptsFollowingTooltipLineHandler = null;

            if (this.statsPanelVisible) {
                final Widget widget = this.statsPanel.getWidgetUnderMouse(mousex, mousey);
                if (widget instanceof StatsPanel.ItemStatsSlot itemStatsSlot) {
                    addRecipeTooltipLine(itemStatsSlot.recipeId, itemStatsSlot.multiplier, currenttip);
                }
            }

        }

        return currenttip;
    }

    @Override
    public Map<String, String> handleHotkeys(GuiContainer gui, int mousex, int mousey, Map<String, String> hotkeys) {

        if (this.hoveredNode != null) {
            if (this.hoveredNode.prefItem != null) {
                hotkeys.put(
                        NEIClientUtils.getKeyName(NEIClientUtils.ALT_HASH, NEIMouseUtils.MOUSE_BTN_LMB),
                        NEIClientUtils.translate("bookmark.tree.item.toggle_recipe"));
            }

            if (this.canvas.graph.roots.contains(this.hoveredNode)) {
                hotkeys.put(
                        NEIClientUtils.getKeyName(
                                NEIClientUtils.CTRL_HASH,
                                NEIMouseUtils.MOUSE_BTN_NONE + NEIMouseUtils.MOUSE_SCROLL),
                        NEIClientUtils.translate("bookmark.change_quantity"));

                hotkeys.put(
                        NEIClientUtils.getKeyName(
                                NEIClientUtils.CTRL_HASH + NEIClientUtils.ALT_HASH,
                                NEIMouseUtils.MOUSE_BTN_NONE + NEIMouseUtils.MOUSE_SCROLL),
                        NEIClientUtils.translate("bookmark.change_quantity_step"));
            }
        } else if (this.statsPanelVisible) {
            final Object widgetGUID = this.statsPanel.getWidgetGUIDUnderMouse(mousex, mousey);

            if (widgetGUID != null) {

                if (this.uiState.searchStatsResult != null && this.uiState.searchStatsResult.matchesGUID(widgetGUID)) {
                    hotkeys.put(
                            NEIMouseUtils.getKeyName(NEIMouseUtils.MOUSE_BTN_LMB),
                            NEIClientUtils.translate("bookmark.tree.stats.next_highlight"));

                    hotkeys.put(
                            NEIClientUtils.getKeyName(NEIClientUtils.SHIFT_HASH, NEIMouseUtils.MOUSE_BTN_LMB),
                            NEIClientUtils.translate("bookmark.tree.stats.prev_highlight"));

                    hotkeys.put(
                            NEIMouseUtils.getKeyName(NEIMouseUtils.MOUSE_BTN_RMB),
                            NEIClientUtils.translate("bookmark.tree.stats.clear_highlight"));
                } else {
                    hotkeys.put(
                            NEIMouseUtils.getKeyName(NEIMouseUtils.MOUSE_BTN_LMB),
                            NEIClientUtils.translate("bookmark.tree.stats.highlight_handler"));
                }

            }

        }

        if (this.acceptsFollowingTooltipLineHandler != null) {
            hotkeys.put(
                    NEIClientUtils.getKeyName(
                            NEIClientUtils.SHIFT_HASH,
                            NEIMouseUtils.MOUSE_BTN_NONE + NEIMouseUtils.MOUSE_SCROLL),
                    NEIClientUtils.translate("recipe.accepts.scroll"));
        }

        return hotkeys;
    }

    @Override
    public VisiblityData modifyVisiblity(GuiContainer gui, VisiblityData currentVisibility) {
        currentVisibility.showNEI = false;
        return currentVisibility;
    }

    private Point2D getMousePosition() {
        final float preciseMouseX = Mouse.getX() * (float) this.width / this.mc.displayWidth;
        final float preciseMouseY = this.height - Mouse.getY() * (float) this.height / this.mc.displayHeight;
        return new Point2D.Float(preciseMouseX, preciseMouseY);
    }

    private void onSearchTextChanged() {
        final String searchText = this.searchField.getText();

        if (this.uiState.searchStatsResult != null) {
            this.uiState.searchStatsResult = null;
            this.statsPanel.setHighlight(null);
        }

        if (!searchText.isEmpty()) {
            this.uiState.searchItemResult = new SearchResult(searchText);
            updateSearchItemMatches();
            searchGoTo(this.uiState.searchItemResult, true);
        } else {
            this.uiState.searchItemResult = null;
        }

    }

    private void searchGoTo(SearchResult searchResult, boolean forward) {

        if (searchResult == null || searchResult.searchMatches.isEmpty()) {
            return;
        }

        final List<ItemTreeSlot> searchList = new ArrayList<>(searchResult.searchMatches);
        int searchIndex = searchList.indexOf(searchResult.highlightNode);

        if (forward) {

            if (searchIndex >= searchList.size() - 1) {
                searchIndex = -1;
            }

            searchIndex++;
        } else {

            if (searchIndex <= 0) {
                searchIndex = searchList.size();
            }

            searchIndex--;
        }

        ItemTreeSlot target = searchIndex == -1 ? null : searchList.get(searchIndex);

        if (target != null) {
            ItemTreeSlot current = this.canvas.graph.parentOf.get(target);

            while (current != null) {
                this.uiState.collapsedItems.remove(current.ingrItem);
                current = this.canvas.graph.parentOf.get(current);
            }
        }

        this.canvas.recalculateCoordinates(this.collapsedRecipes, this.uiState.collapsedItems, null);

        searchResult.highlightNode = target;
        this.canvas.scrollTo(target);
    }

    private void updateSearchItemMatches() {

        if (this.uiState.searchItemResult != null) {
            final ItemFilter filter = this.searchField.getFilter((String) this.uiState.searchItemResult.searchGUID);
            this.uiState.searchItemResult.searchMatches.clear();
            this.uiState.searchItemResult.highlightNode = null;

            for (ItemTreeSlot node : this.canvas.graph.items) {
                if (filter.matches(node.emptyStack)) {
                    this.uiState.searchItemResult.searchMatches.add(node);
                }
            }
        }

    }

    private void updateSearchStatsMatches() {

        if (this.uiState.searchStatsResult != null) {
            this.uiState.searchStatsResult.searchMatches.clear();
            this.uiState.searchStatsResult.highlightNode = null;

            final boolean byHandler = this.uiState.searchStatsResult.searchGUID instanceof String;

            for (ItemTreeSlot node : this.canvas.graph.items) {
                final boolean matches = byHandler
                        ? node.prefItem != null
                                && this.uiState.searchStatsResult.matchesGUID(node.prefItem.recipeId.getHandlerName())
                        : this.uiState.searchStatsResult.matchesGUID(node.emptyStack);

                if (matches) {
                    this.uiState.searchStatsResult.searchMatches.add(node);
                }
            }
        }

    }

}
