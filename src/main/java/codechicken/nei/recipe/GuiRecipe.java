package codechicken.nei.recipe;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.GuiNEIButton;
import codechicken.nei.LayoutManager;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.PositionedStack;
import codechicken.nei.VisiblityData;
import codechicken.nei.api.IGuiContainerOverlay;
import codechicken.nei.api.INEIGuiHandler;
import codechicken.nei.api.IOverlayHandler;
import codechicken.nei.api.IRecipeOverlayRenderer;
import codechicken.nei.api.TaggedInventoryArea;
import codechicken.nei.drawable.DrawableBuilder;
import codechicken.nei.drawable.DrawableResource;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.guihook.IContainerTooltipHandler;
import codechicken.nei.guihook.IGuiClientSide;
import codechicken.nei.guihook.IGuiHandleMouseWheel;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

public abstract class GuiRecipe extends GuiContainer
        implements IGuiContainerOverlay,
                IGuiClientSide,
                IGuiHandleMouseWheel,
                IContainerTooltipHandler,
                INEIGuiHandler {
    // Background image calculations
    private static final int BG_TOP_HEIGHT = 6;
    private static final int BG_MIDDLE_HEIGHT = 154;
    private static final int BG_BOTTOM_HEIGHT = 6;

    private static final int BG_TOP_Y = 0;
    private static final int BG_MIDDLE_Y = BG_TOP_Y + BG_TOP_HEIGHT;
    private static final int BG_BOTTOM_Y = BG_MIDDLE_Y + BG_MIDDLE_HEIGHT;

    // Button sizes
    private static final int borderPadding = 6;
    private static final int buttonWidth = 13;
    private static final int buttonHeight = 12;

    // Background image
    final DrawableResource bgTop =
            new DrawableBuilder("nei:textures/gui/recipebg.png", 0, BG_TOP_Y, 176, BG_TOP_HEIGHT).build();
    final DrawableResource bgMiddle =
            new DrawableBuilder("nei:textures/gui/recipebg.png", 0, BG_MIDDLE_Y, 176, BG_MIDDLE_HEIGHT).build();
    final DrawableResource bgBottom =
            new DrawableBuilder("nei:textures/gui/recipebg.png", 0, BG_BOTTOM_Y, 176, BG_BOTTOM_HEIGHT).build();

    public ArrayList<? extends IRecipeHandler> currenthandlers = new ArrayList<>();

    public int page;
    public int recipetype;
    public BookmarkRecipeId recipeId;
    public ContainerRecipe slotcontainer;
    public GuiContainer firstGui;
    public GuiScreen firstGuiGeneral;
    public GuiScreen prevGui;
    public GuiButton nextpage;
    public GuiButton prevpage;
    private GuiButton nexttype;
    private GuiButton prevtype;

    private int OVERLAY_BUTTON_ID_START = 4;
    private GuiButton[] overlayButtons;

    private final Rectangle area = new Rectangle();
    private final GuiRecipeTabs recipeTabs;
    private final GuiRecipeCatalyst guiRecipeCatalyst;
    private IRecipeHandler handler;
    private HandlerInfo handlerInfo;

    private int yShift = 0;

    protected GuiRecipe(GuiScreen prevgui) {
        super(new ContainerRecipe());
        recipeTabs = new GuiRecipeTabs(this);
        guiRecipeCatalyst = new GuiRecipeCatalyst(this);
        slotcontainer = (ContainerRecipe) inventorySlots;

        this.prevGui = prevgui;
        this.firstGuiGeneral = prevgui;
        if (prevgui instanceof GuiContainer) this.firstGui = (GuiContainer) prevgui;

        if (prevgui instanceof IGuiContainerOverlay) {
            this.firstGui = ((IGuiContainerOverlay) prevgui).getFirstScreen();
            this.firstGuiGeneral = ((IGuiContainerOverlay) prevgui).getFirstScreenGeneral();
        }
    }

    /**
     * Many old mods assumed a fixed NEI window height of {@code 166} pixels. Now that this is no
     * longer the case, their fluid mouseover handling is broken. This helper class fixes these old
     * mods by hacking the {@link #height} property's value so that these old mods' calculations
     * return the correct value for the new height.
     *
     * <p>This class is an {@link AutoCloseable} so that it can be used with try-with-resources,
     * which will ensure that {@link #height} is returned to the correct value afterwards.
     */
    private class HeightHack implements AutoCloseable {
        private final int trueHeight;

        private HeightHack() {
            trueHeight = height;

            boolean applyHeightHack = NEIClientConfig.heightHackHandlerRegex.stream()
                    .map(pattern -> pattern.matcher(handler.getHandlerId()))
                    .anyMatch(Matcher::matches);
            if (applyHeightHack) {
                // The old mods use the calculation ((height - 166) / 2) to compute the y-value of
                // the top edge of the NEI window.
                // guiTop is exactly that: the y-value of the top edge of the NEI window.
                // So we set height to be equal to the inverse of this calculation, applied to
                // guiTop, so that old mods get the right result back when they use this calculation.
                height = (2 * guiTop) + 166;

                // For reference, in case we ever need to modify width as well:
                // the legacy calculation used for width is ((width - 176) / 2), which should
                // evaluate to be equal to guiWidth (the x-value of the left edge of the NEI window).
                // So if we wanted to override width as well, we'd do this:
                // width = (2 * guiWidth) + 176;
            }
        }

        @Override
        public void close() {
            height = trueHeight;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void initGui() {
        xSize = 176;
        ySize = Math.min(Math.max(height - 68, 166), 370);
        super.initGui();
        guiTop = (height - ySize) / 2 + 10;
        currenthandlers = getCurrentRecipeHandlers(); // Probably don't comment me out

        if (handler == null) setRecipePage(recipetype);
        else initOverlayButtons();

        checkYShift();

        final int rightButtonX = guiLeft + xSize - borderPadding - buttonWidth;
        final int leftButtonX = guiLeft + borderPadding;

        nexttype = new GuiNEIButton(0, leftButtonX, guiTop + 3, buttonWidth, buttonHeight, "<");
        prevtype = new GuiNEIButton(1, rightButtonX, guiTop + 3, buttonWidth, buttonHeight, ">");

        nextpage = new GuiNEIButton(2, leftButtonX, guiTop + 17, buttonWidth, buttonHeight, "<");
        prevpage = new GuiNEIButton(3, rightButtonX, guiTop + 17, buttonWidth, buttonHeight, ">");

        buttonList.addAll(Arrays.asList(nexttype, prevtype, nextpage, prevpage));

        if (currenthandlers.size() == 1) {
            nexttype.visible = false;
            prevtype.visible = false;
        }

        recipeTabs.initLayout();
        refreshPage();
    }

    @SuppressWarnings("unchecked")
    private void initOverlayButtons() {
        if (overlayButtons != null) {
            buttonList.removeIf(Arrays.asList(overlayButtons)::contains);
        }

        final int recipesPerPage = getRecipesPerPage();
        overlayButtons = new GuiButton[recipesPerPage];
        for (int i = 0; i < recipesPerPage; i++) {
            overlayButtons[i] = new GuiNEIButton(
                    OVERLAY_BUTTON_ID_START + i,
                    (width / 2) + 65,
                    guiTop + 16 + (handlerInfo.getHeight() * (i + 1)) - 2,
                    buttonWidth,
                    buttonHeight,
                    "?");
        }
        Collections.addAll(buttonList, overlayButtons);
        itemPresenceCacheRecipe = -1;
    }

    private void checkYShift() {
        yShift = handlerInfo == null ? 0 : handlerInfo.getYShift();
    }

    public void setRecipePage(int idx) {
        setRecipePage(idx, 0);
    }

    public void setRecipePage(int idx, int position) {
        recipetype = idx;
        if (recipetype < 0) recipetype = currenthandlers.size() - 1;
        else if (recipetype >= currenthandlers.size()) recipetype = 0;

        handler = currenthandlers.get(recipetype);
        handlerInfo = GuiRecipeTab.getHandlerInfo(handler);
        page = Math.min(Math.max(0, position), handler.numRecipes() - 1) / getRecipesPerPage();
        recipeTabs.calcPageNumber();
        checkYShift();
        initOverlayButtons();
    }

    public void openTargetRecipe(BookmarkRecipeId recipeId) {

        if (recipeId == null) {
            return;
        }

        if (recipeId.recipetype == -1 || recipeId.position == -1) {
            recipeId.recipetype = 0;
            recipeId.position = 0;

            if (recipeId.handlerName != null) {

                for (int j = 0; j < currenthandlers.size(); j++) {
                    IRecipeHandler localHandler = currenthandlers.get(j);
                    HandlerInfo localHandlerInfo = GuiRecipeTab.getHandlerInfo(localHandler);

                    if (localHandlerInfo.getHandlerName().equals(recipeId.handlerName)) {
                        recipeId.recipetype = j;

                        if (!recipeId.ingredients.isEmpty()) {
                            for (int i = 0; i < localHandler.numRecipes(); i++) {

                                if (recipeId.equalsIngredients(localHandler.getIngredientStacks(i))) {
                                    recipeId.position = i;
                                    break;
                                }
                            }
                        }

                        break;
                    }
                }
            }
        }

        setRecipePage(recipeId.recipetype, recipeId.position);
    }

    public List<PositionedStack> getFocusedRecipeIngredients() {
        final int recipesPerPage = getRecipesPerPage();

        for (int idx = page * recipesPerPage; idx < handler.numRecipes() && idx < (page + 1) * recipesPerPage; idx++) {
            if (recipeInFocus(idx)) {
                return handler.getIngredientStacks(idx);
            }
        }

        return null;
    }

    protected boolean recipeInFocus(int idx) {
        final PositionedStack result = handler.getResultStack(idx);
        if (result != null && isMouseOver(result, idx)) {
            return true;
        }

        final List<PositionedStack> stacks = handler.getOtherStacks(idx);
        for (PositionedStack stack : stacks) {
            if (isMouseOver(stack, idx)) {
                return true;
            }
        }

        return false;
    }

    public String getHandlerName() {
        return handlerInfo.getHandlerName();
    }

    public IRecipeHandler getHandler() {
        return handler;
    }

    public List<Integer> getRecipeIndices() {
        final int recipesPerPage = getRecipesPerPage();
        int minIndex = page * recipesPerPage;
        int maxIndex = Math.min(handler.numRecipes(), (page + 1) * recipesPerPage);

        return IntStream.range(minIndex, maxIndex).boxed().collect(Collectors.toList());
    }

    @Override
    public void keyTyped(char c, int i) {
        if (i == Keyboard.KEY_ESCAPE) // esc
        {
            mc.displayGuiScreen(firstGuiGeneral);
            return;
        }
        if (GuiContainerManager.getManager(this).lastKeyTyped(i, c)) return;

        try (HeightHack heightHack = new HeightHack()) {
            for (int recipe : getRecipeIndices()) if (handler.keyTyped(this, c, i, recipe)) return;
        }

        if (i == mc.gameSettings.keyBindInventory.getKeyCode()) mc.displayGuiScreen(firstGuiGeneral);
        else if (i == NEIClientConfig.getKeyBinding("gui.back")) mc.displayGuiScreen(prevGui);
        else if (i == NEIClientConfig.getKeyBinding("gui.prev_machine")) prevType();
        else if (i == NEIClientConfig.getKeyBinding("gui.next_machine")) nextType();
        else if (i == NEIClientConfig.getKeyBinding("gui.prev_recipe")) prevPage();
        else if (i == NEIClientConfig.getKeyBinding("gui.next_recipe")) nextPage();
    }

    @Override
    protected void mouseClicked(int x, int y, int button) {
        try (HeightHack heightHack = new HeightHack()) {
            for (int recipe : getRecipeIndices()) if (handler.mouseClicked(this, button, recipe)) return;
        }

        if (recipeTabs.mouseClicked(x, y, button)) return;

        super.mouseClicked(x, y, button);
    }

    @Override
    public void mouseScrolled(int i) {
        // Height hacking is not necessary here since mouse scrolling is a new feature, added in
        // GTNH NEI. So no old mods will use this. Though not hacking the height here does mean that
        // the value of the height field will be different from in other mouseover methods, which
        // could be confusing...

        for (int recipe : getRecipeIndices()) if (handler.mouseScrolled(this, i, recipe)) return;

        if (new Rectangle(guiLeft, guiTop, xSize, ySize).contains(GuiDraw.getMousePosition())) {
            if (i > 0) prevPage();
            else nextPage();
        }
    }

    @Override
    protected void actionPerformed(GuiButton guibutton) {
        super.actionPerformed(guibutton);
        switch (guibutton.id) {
            case 0:
                prevType();
                return;
            case 1:
                nextType();
                return;
            case 2:
                prevPage();
                return;
            case 3:
                nextPage();
                return;
        }
        if (overlayButtons != null
                && guibutton.id >= OVERLAY_BUTTON_ID_START
                && guibutton.id < OVERLAY_BUTTON_ID_START + overlayButtons.length) {
            overlayRecipe(page * getRecipesPerPage() + guibutton.id - OVERLAY_BUTTON_ID_START);
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        handler.onUpdate();
        refreshPage();
    }

    @Override
    public List<String> handleTooltip(GuiContainer gui, int mousex, int mousey, List<String> currenttip) {
        try (HeightHack heightHack = new HeightHack()) {
            for (int i : getRecipeIndices()) currenttip = handler.handleTooltip(this, currenttip, i);
        }
        recipeTabs.handleTooltip(mousex, mousey, currenttip);
        return currenttip;
    }

    @Override
    public List<String> handleItemTooltip(
            GuiContainer gui, ItemStack stack, int mousex, int mousey, List<String> currenttip) {
        // Height hacking is probably not needed as it doesn't look like any mods check mouse
        // position for this method.
        for (int i : getRecipeIndices()) currenttip = handler.handleItemTooltip(this, stack, currenttip, i);

        return currenttip;
    }

    @Override
    public List<String> handleItemDisplayName(GuiContainer gui, ItemStack itemstack, List<String> currenttip) {
        return currenttip;
    }

    private void nextPage() {
        page++;
        if (page > (handler.numRecipes() - 1) / getRecipesPerPage()) page = 0;
    }

    private void prevPage() {
        page--;
        if (page < 0) page = (handler.numRecipes() - 1) / getRecipesPerPage();
    }

    private void nextType() {
        setRecipePage(++recipetype);
    }

    private void prevType() {
        setRecipePage(--recipetype);
    }

    protected void overlayRecipe(int recipe) {
        if (handler == null || !handler.hasOverlay(firstGui, firstGui.inventorySlots, recipe)) {
            mc.displayGuiScreen(firstGui);
            return;
        }
        final IRecipeOverlayRenderer renderer = handler.getOverlayRenderer(firstGui, recipe);
        final IOverlayHandler overlayHandler = handler.getOverlayHandler(firstGui, recipe);
        final boolean shift = NEIClientUtils.shiftKey();

        mc.displayGuiScreen(firstGui);
        if (renderer == null || shift) {
            overlayHandler.overlayRecipe(firstGui, currenthandlers.get(recipetype), recipe, shift);
        } else {
            LayoutManager.overlayRenderer = renderer;
        }
    }

    public void refreshPage() {
        RecipeCatalysts.updatePosition(ySize - BG_TOP_HEIGHT - (GuiRecipeCatalyst.fullBorder * 2));
        refreshSlots();
        final int recipesPerPage = getRecipesPerPage();
        final boolean multiplepages = handler.numRecipes() > recipesPerPage;

        final int numRecipes = Math.min(handler.numRecipes() - (page * recipesPerPage), recipesPerPage);

        area.width = handlerInfo.getWidth();
        area.height = handlerInfo.getHeight() * numRecipes;
        area.x = guiLeft - 2;
        area.y = guiTop - 4 + yShift;
        checkYShift();

        nextpage.enabled = prevpage.enabled = multiplepages;

        if (firstGui == null) {
            for (GuiButton overlay : overlayButtons) {
                overlay.visible = false;
            }
        } else {
            for (int i = 0; i < overlayButtons.length; i++) {
                if (i >= numRecipes) {
                    overlayButtons[i].visible = false;
                } else {
                    final int curRecipe = page * recipesPerPage + i;
                    overlayButtons[i].visible = handler.hasOverlay(firstGui, firstGui.inventorySlots, curRecipe);
                }
            }
        }

        recipeTabs.refreshPage();
    }

    private void refreshSlots() {
        slotcontainer.inventorySlots.clear();
        final int recipesPerPage = getRecipesPerPage();
        for (int i = page * recipesPerPage; i < handler.numRecipes() && i < (page + 1) * recipesPerPage; i++) {
            Point p = getRecipePosition(i);

            List<PositionedStack> stacks = handler.getIngredientStacks(i);
            for (PositionedStack stack : stacks) slotcontainer.addSlot(stack, p.x, p.y);

            stacks = handler.getOtherStacks(i);
            for (PositionedStack stack : stacks) slotcontainer.addSlot(stack, p.x, p.y);

            PositionedStack result = handler.getResultStack(i);
            if (result != null) slotcontainer.addSlot(result, p.x, p.y);

            List<PositionedStack> catalysts = RecipeCatalysts.getRecipeCatalysts(handler);
            for (PositionedStack catalyst : catalysts) {
                int xOffset = -GuiRecipeCatalyst.ingredientSize + 1;
                int yOffset = BG_TOP_Y + GuiRecipeCatalyst.fullBorder;
                slotcontainer.addSlot(catalyst, xOffset, yOffset);
            }
        }
    }

    private int itemPresenceCacheRecipe = -1;
    private ArrayList<Boolean> itemPresenceCacheSlots;

    private void updateItemPresenceCache(int recipe) {
        if (itemPresenceCacheSlots == null) {
            itemPresenceCacheSlots = new ArrayList<>();
        }
        itemPresenceCacheRecipe = recipe;
        itemPresenceCacheSlots.clear();
        List<PositionedStack> ingredients = handler.getIngredientStacks(recipe);
        ArrayList<ItemStack> invStacks = ((List<Slot>) firstGui.inventorySlots.inventorySlots)
                .stream()
                        .filter(s -> s != null
                                && s.getStack() != null
                                && s.getStack().stackSize > 0
                                && s.isItemValid(s.getStack())
                                && s.canTakeStack(mc.thePlayer))
                        .map(s -> s.getStack().copy())
                        .collect(Collectors.toCollection(ArrayList::new));
        for (PositionedStack stack : ingredients) {
            Optional<ItemStack> used = invStacks.stream()
                    .filter(is -> is.stackSize > 0 && stack.contains(is))
                    .findFirst();
            itemPresenceCacheSlots.add(used.isPresent());
            if (used.isPresent()) {
                ItemStack is = used.get();
                is.stackSize -= 1;
            }
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        GuiContainerManager.enable2DRender();
        final int recipesPerPage = getRecipesPerPage();
        String s = handler.getRecipeName().trim();
        fontRendererObj.drawStringWithShadow(s, (xSize - fontRendererObj.getStringWidth(s)) / 2, 5, 0xffffff);
        s = NEIClientUtils.translate("recipe.page", page + 1, (handler.numRecipes() - 1) / recipesPerPage + 1);
        fontRendererObj.drawStringWithShadow(s, (xSize - fontRendererObj.getStringWidth(s)) / 2, 19, 0xffffff);
        final boolean drawItemPresence = NEIClientConfig.isJEIStyleItemPresenceOverlayVisible();

        GL11.glPushMatrix();
        GL11.glTranslatef(5, 32 + yShift, 0);
        try (HeightHack heightHack = new HeightHack()) {
            for (int i = page * recipesPerPage; i < handler.numRecipes() && i < (page + 1) * recipesPerPage; i++) {
                handler.drawForeground(i);
                if (drawItemPresence
                        && isMouseOverOverlayButton(i - page * recipesPerPage)
                        && firstGui.inventorySlots != null) {
                    List<PositionedStack> ingredients = handler.getIngredientStacks(i);
                    if (itemPresenceCacheRecipe != i
                            || itemPresenceCacheSlots == null
                            || itemPresenceCacheSlots.size() != ingredients.size()) {
                        updateItemPresenceCache(i);
                    }
                    for (int j = 0; j < ingredients.size(); j++) {
                        PositionedStack stack = ingredients.get(j);
                        boolean isPresent = itemPresenceCacheSlots.get(j);
                        LayoutManager.drawItemPresenceOverlay(stack.relx, stack.rely, isPresent);
                    }
                }
                GL11.glTranslatef(0, handlerInfo.getHeight(), 0);
            }
        }
        GL11.glPopMatrix();
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float f, int mouseX, int mouseY) {
        final int recipesPerPage = getRecipesPerPage();
        GL11.glColor4f(1, 1, 1, 1);
        final int j = (width - xSize) / 2;
        final int k = guiTop;

        drawBackgroundTiled(j, k);

        drawRect(
                guiLeft + borderPadding + buttonWidth - 1,
                nexttype.yPosition,
                guiLeft + xSize - borderPadding - buttonWidth,
                nexttype.yPosition + buttonHeight,
                0x30000000);
        drawRect(
                guiLeft + borderPadding + buttonWidth - 1,
                nextpage.yPosition,
                guiLeft + xSize - borderPadding - buttonWidth,
                nextpage.yPosition + buttonHeight,
                0x30000000);

        if (NEIClientConfig.areJEIStyleTabsVisible()) {
            RenderHelper.enableGUIStandardItemLighting();
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
            recipeTabs.draw(mouseX, mouseY);
            if (NEIClientConfig.areJEIStyleRecipeCatalystsVisible()) {
                guiRecipeCatalyst.draw();
            }
            RenderHelper.disableStandardItemLighting();
        }

        GL11.glPushMatrix();
        GL11.glTranslatef(j + 5, k + 32 + yShift, 0);
        try (HeightHack heightHack = new HeightHack()) {
            for (int i = page * recipesPerPage; i < handler.numRecipes() && i < (page + 1) * recipesPerPage; i++) {
                handler.drawBackground(i);
                GL11.glTranslatef(0, handlerInfo.getHeight(), 0);
            }
        }
        GL11.glPopMatrix();
    }

    private void drawBackgroundTiled(int j, int k) {
        // Draw the top
        bgTop.draw(j, k + BG_TOP_Y);

        // Maybe Tile the middle
        final int tiledHeight = ySize - BG_TOP_HEIGHT - BG_BOTTOM_HEIGHT;

        if (tiledHeight > 0) {
            final int yTileCount = tiledHeight / BG_MIDDLE_HEIGHT;
            final int yRemainder = tiledHeight - (yTileCount * BG_MIDDLE_HEIGHT);
            final int yStart = k + BG_TOP_HEIGHT + tiledHeight;

            for (int yTile = 0; yTile <= yTileCount; yTile++) {
                final int tileHeight = (yTile == yTileCount) ? yRemainder : BG_MIDDLE_HEIGHT;
                final int y = yStart - ((yTile + 1) * BG_MIDDLE_HEIGHT);
                if (tileHeight > 0) {
                    bgMiddle.draw(j, y, BG_MIDDLE_HEIGHT - tileHeight, 0, 0, 0);
                }
            }
        }

        // Draw the bottom
        bgBottom.draw(j, k + ySize - BG_BOTTOM_HEIGHT);
    }

    @Override
    public GuiContainer getFirstScreen() {
        return firstGui;
    }

    @Override
    public GuiScreen getFirstScreenGeneral() {
        return firstGuiGeneral;
    }

    public boolean isMouseOver(PositionedStack stack, int recipe) {
        Slot stackSlot =
                slotcontainer.getSlotWithStack(stack, getRecipePosition(recipe).x, getRecipePosition(recipe).y);
        Point mousepos = GuiDraw.getMousePosition();
        Slot mouseoverSlot = getSlotAtPosition(mousepos.x, mousepos.y);

        return stackSlot == mouseoverSlot;
    }

    private boolean isMouseOverOverlayButton(int buttonId) {
        if (buttonId >= 0 && buttonId < overlayButtons.length) {
            GuiButton button = overlayButtons[buttonId];
            Point mousePos = GuiDraw.getMousePosition();
            return button.visible
                    && mousePos.x >= button.xPosition
                    && mousePos.y >= button.yPosition
                    && mousePos.x < button.xPosition + button.width
                    && mousePos.y < button.yPosition + button.height;
        } else {
            return false;
        }
    }

    private int getRecipesPerPage() {
        return getRecipesPerPage(handlerInfo);
    }

    private int getRecipesPerPage(HandlerInfo handlerInfo) {
        if (handlerInfo != null)
            return Math.max(
                    Math.min(
                            ((ySize - (buttonHeight * 3)) / handlerInfo.getHeight()),
                            handlerInfo.getMaxRecipesPerPage()),
                    1);
        else return (handler.recipiesPerPage());
    }

    public Point getRecipePosition(int recipe) {
        return new Point(5, 32 + yShift + ((recipe % getRecipesPerPage()) * handlerInfo.getHeight()));
    }

    public abstract ArrayList<? extends IRecipeHandler> getCurrentRecipeHandlers();

    @Override
    public VisiblityData modifyVisiblity(GuiContainer gui, VisiblityData currentVisibility) {
        return currentVisibility;
    }

    @Override
    public Iterable<Integer> getItemSpawnSlots(GuiContainer gui, ItemStack item) {
        return Collections.emptyList();
    }

    @Override
    public List<TaggedInventoryArea> getInventoryAreas(GuiContainer gui) {
        return null;
    }

    @Override
    public boolean handleDragNDrop(GuiContainer gui, int mousex, int mousey, ItemStack draggedStack, int button) {
        return false;
    }

    @Override
    public boolean hideItemPanelSlot(GuiContainer gui, int x, int y, int w, int h) {
        // Because some of the handlers *cough avaritia* are oversized
        return area.intersects(x, y, w, h);
    }

    static BookmarkRecipeId getCurrentRecipe() {
        Minecraft mc = NEIClientUtils.mc();
        if (mc.currentScreen instanceof GuiRecipe) {
            GuiRecipe gui = (GuiRecipe) mc.currentScreen;
            return  new BookmarkRecipeId(gui.handlerInfo.getHandlerName(),
                    gui.getHandler().getIngredientStacks(gui.page * gui.getRecipesPerPage()));
        }
        return null;
    }
}
