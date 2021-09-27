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
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public abstract class GuiRecipe extends GuiContainer implements IGuiContainerOverlay, IGuiClientSide, IGuiHandleMouseWheel, IContainerTooltipHandler, INEIGuiHandler {
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
    final DrawableResource bgTop =    new DrawableBuilder("nei:textures/gui/recipebg.png", 0, BG_TOP_Y,    176, BG_TOP_HEIGHT).build();
    final DrawableResource bgMiddle = new DrawableBuilder("nei:textures/gui/recipebg.png", 0, BG_MIDDLE_Y, 176, BG_MIDDLE_HEIGHT).build();
    final DrawableResource bgBottom = new DrawableBuilder("nei:textures/gui/recipebg.png", 0, BG_BOTTOM_Y, 176, BG_BOTTOM_HEIGHT).build();
    
    public ArrayList<? extends IRecipeHandler> currenthandlers = new ArrayList<>();

    public int page;
    public int recipetype;
    public ContainerRecipe slotcontainer;
    public GuiContainer firstGui;
    public GuiScreen prevGui;
    public GuiButton nextpage;
    public GuiButton prevpage;
    private GuiButton nexttype;
    private GuiButton prevtype;
    
    private int OVERLAY_BUTTON_ID_START = 4;
    private GuiButton[] overlayButtons;
    
    private final Rectangle area = new Rectangle();
    private final GuiRecipeTabs recipeTabs;
    private IRecipeHandler handler;
    private HandlerInfo handlerInfo;
    
    private int yShift = 0;

    protected GuiRecipe(GuiScreen prevgui) {
        super(new ContainerRecipe());
        recipeTabs = new GuiRecipeTabs(this);
        slotcontainer = (ContainerRecipe) inventorySlots;

        this.prevGui = prevgui;
        if(prevgui instanceof GuiContainer)
            this.firstGui = (GuiContainer)prevgui;

        if (prevgui instanceof IGuiContainerOverlay)
            this.firstGui = ((IGuiContainerOverlay) prevgui).getFirstScreen();
        
    }

   
    @Override
    @SuppressWarnings("unchecked")
    public void initGui() {
        xSize = 176;
        ySize = Math.min(Math.max(height - 68, 166), 370);
        super.initGui();
        guiTop = (height - ySize) / 2 + 10;
        currenthandlers = getCurrentRecipeHandlers(); // Probably don't comment me out
        
        if(handler == null) 
            setRecipePage(recipetype);
        else
            initOverlayButtons();
        
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
        for (int i = 0 ; i < recipesPerPage ; i++) {
            overlayButtons[i] = new GuiNEIButton(
                OVERLAY_BUTTON_ID_START + i,
                 (width / 2) + 65,
                 guiTop + 16 + (handlerInfo.getHeight() * (i+1)) - 2,
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
        recipetype = idx;
        if (recipetype < 0) recipetype = currenthandlers.size() - 1;
        else if (recipetype >= currenthandlers.size()) recipetype = 0;

        handler = currenthandlers.get(recipetype);
        handlerInfo = getHandlerInfo(handler);
        page = 0;
        recipeTabs.calcPageNumber();
        checkYShift();
        initOverlayButtons();
    }


    @Override
    public void keyTyped(char c, int i) {
        if (i == Keyboard.KEY_ESCAPE) //esc
        {
            mc.displayGuiScreen(firstGui);
            return;
        }
        if (GuiContainerManager.getManager(this).lastKeyTyped(i, c))
            return;

        final int recipesPerPage = getRecipesPerPage();
        for (int recipe = page * recipesPerPage; recipe < handler.numRecipes() && recipe < (page + 1) * recipesPerPage; recipe++)
            if (handler.keyTyped(this, c, i, recipe))
                return;

        if (i == mc.gameSettings.keyBindInventory.getKeyCode())
            mc.displayGuiScreen(firstGui);
        else if (i == NEIClientConfig.getKeyBinding("gui.back"))
            mc.displayGuiScreen(prevGui);
        else if (i == NEIClientConfig.getKeyBinding("gui.prev_machine"))
            prevType();
        else if (i == NEIClientConfig.getKeyBinding("gui.next_machine"))
            nextType();
        else if (i == NEIClientConfig.getKeyBinding("gui.prev_recipe"))
            prevPage();
        else if (i == NEIClientConfig.getKeyBinding("gui.next_recipe"))
            nextPage();

    }

    @Override
    protected void mouseClicked(int x, int y, int button) {
        final int recipesPerPage = getRecipesPerPage();
        for (int recipe = page * recipesPerPage; recipe < handler.numRecipes() && recipe < (page + 1) * recipesPerPage; recipe++)
            if (handler.mouseClicked(this, button, recipe))
                return;
        if (recipeTabs.mouseClicked(x, y, button))
            return;
        super.mouseClicked(x, y, button);
    }

    @Override
    public void mouseScrolled(int i) {
        final int recipesPerPage = getRecipesPerPage();
        for (int recipe = page * recipesPerPage; recipe < handler.numRecipes() && recipe < (page + 1) * recipesPerPage; recipe++)
            if (handler.mouseScrolled(this, i, recipe))
                return;

        if (new Rectangle(guiLeft, guiTop, xSize, ySize).contains(GuiDraw.getMousePosition())) {
            if (i > 0) prevPage();
            else       nextPage();
        }
    }

    @Override
    protected void actionPerformed(GuiButton guibutton) {
        super.actionPerformed(guibutton);
        final int recipesPerPage = getRecipesPerPage();
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
        if (overlayButtons != null && guibutton.id >= OVERLAY_BUTTON_ID_START && guibutton.id < OVERLAY_BUTTON_ID_START + overlayButtons.length) {
            overlayRecipe(page * recipesPerPage + guibutton.id - OVERLAY_BUTTON_ID_START);
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
        final int recipesPerPage = getRecipesPerPage();
        /* 
         * Several mods try to figure out the relative mouse position for fluid tooltips.  This worked fine
         * when it was a static 166.. however now that we're scaling it this no longer works.  Rather than
         * patching the individual mods (so that we can be as compatible as possible), we'll just fake
         * the height for their tooltip rendering.
         */
        
        // Begin Hax
        final int oldHeight = this.height;
        this.height = 166 + handlerInfo.getHeight() + 18 + (guiTop - 44) * 2;
        for (int i = page * recipesPerPage; i < handler.numRecipes() && i < (page + 1) * recipesPerPage; i++) {
            currenttip = handler.handleTooltip(this, currenttip, i);
        }
        recipeTabs.handleTooltip(mousex, mousey, currenttip);
        this.height = oldHeight;
        // End Hax
        
        return currenttip;
    }

    @Override
    public List<String> handleItemTooltip(GuiContainer gui, ItemStack stack, int mousex, int mousey, List<String> currenttip) {
        final int recipesPerPage = getRecipesPerPage();
        for (int i = page * recipesPerPage; i < handler.numRecipes() && i < (page + 1) * recipesPerPage; i++)
            currenttip = handler.handleItemTooltip(this, stack, currenttip, i);

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

    public HandlerInfo getHandlerInfo(IRecipeHandler handler) {
        final String handlerID;
        if(handler instanceof TemplateRecipeHandler) {
            handlerID = (((TemplateRecipeHandler)handler).getOverlayIdentifier());
        } else {
            handlerID = null;
        }
        HandlerInfo info = GuiRecipeTab.getHandlerInfo(handler.getHandlerId(), handlerID);
        
        if (info == null)
            return GuiRecipeTab.DEFAULT_HANDLER_INFO;
        
        return info;
    }

    private void overlayRecipe(int recipe) {
        final IRecipeOverlayRenderer renderer = handler.getOverlayRenderer(firstGui, recipe);
        final IOverlayHandler overlayHandler = handler.getOverlayHandler(firstGui, recipe);
        final boolean shift = NEIClientUtils.shiftKey();

        if (handler != null && (renderer == null || shift)) {
            mc.displayGuiScreen(firstGui);
            overlayHandler.overlayRecipe(firstGui, currenthandlers.get(recipetype), recipe, shift);
        } else if (renderer != null) {
            mc.displayGuiScreen(firstGui);
            LayoutManager.overlayRenderer = renderer;
        }
    }
    
    public void refreshPage() {
        refreshSlots();
        final int recipesPerPage = getRecipesPerPage();
        final boolean multiplepages = handler.numRecipes() > recipesPerPage;

        final int numRecipes = Math.min(handler.numRecipes() - (page * recipesPerPage), recipesPerPage);

        area.width = handlerInfo.getWidth();
        area.height = handlerInfo.getHeight() * numRecipes;
        area.x = guiLeft - 2;
        area.y = guiTop  - 4 + yShift;
        checkYShift();

        nextpage.enabled = prevpage.enabled = multiplepages;
        
        if(firstGui == null) {
            for (GuiButton overlay : overlayButtons) {
                overlay.visible = false;
            }
        } else {
            for(int i = 0 ; i < overlayButtons.length ; i ++) {
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
            for (PositionedStack stack : stacks)
                slotcontainer.addSlot(stack, p.x, p.y);

            stacks = handler.getOtherStacks(i);
            for (PositionedStack stack : stacks)
                slotcontainer.addSlot(stack, p.x, p.y);

            PositionedStack result = handler.getResultStack(i);
            if (result != null)
                slotcontainer.addSlot(result, p.x, p.y);
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
        ArrayList<ItemStack> invStacks = ((List<Slot>) firstGui.inventorySlots.inventorySlots).stream()
                .filter(s -> s != null && s.getStack() != null && s.getStack().stackSize > 0 && s.isItemValid(s.getStack()) && s.canTakeStack(mc.thePlayer))
                .map(s -> s.getStack().copy())
                .collect(Collectors.toCollection(ArrayList::new));
        for (PositionedStack stack : ingredients) {
            Optional<ItemStack> used = invStacks.stream().filter(is -> is.stackSize > 0 && stack.contains(is)).findFirst();
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
        for (int i = page * recipesPerPage; i < handler.numRecipes() && i < (page + 1) * recipesPerPage; i++) {
            handler.drawForeground(i);
            if (drawItemPresence && isMouseOverOverlayButton(i - page * recipesPerPage)
                    && firstGui.inventorySlots != null) {
                List<PositionedStack> ingredients = handler.getIngredientStacks(i);
                if (itemPresenceCacheRecipe != i || itemPresenceCacheSlots == null || itemPresenceCacheSlots.size() != ingredients.size()) {
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
        GL11.glPopMatrix();
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float f, int mouseX, int mouseY) {
        final int recipesPerPage = getRecipesPerPage();
        GL11.glColor4f(1, 1, 1, 1);
        final int j = (width  - xSize) / 2;
        final int k = guiTop;
        
        drawBackgroundTiled(j, k);

        drawRect(guiLeft + borderPadding + buttonWidth - 1,
                 nexttype.yPosition,
                 guiLeft + xSize - borderPadding - buttonWidth,
                 nexttype.yPosition + buttonHeight,
                 0x30000000);
        drawRect(guiLeft + borderPadding + buttonWidth - 1,
                 nextpage.yPosition,
                 guiLeft + xSize - borderPadding - buttonWidth,
                 nextpage.yPosition + buttonHeight,
                 0x30000000);
        
        if (NEIClientConfig.areJEIStyleTabsVisible()) {
            RenderHelper.enableGUIStandardItemLighting();
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
            recipeTabs.draw(mouseX, mouseY);
            RenderHelper.disableStandardItemLighting();
        }

        GL11.glPushMatrix();
        GL11.glTranslatef(j + 5, k + 32 + yShift, 0);
        for (int i = page * recipesPerPage; i < handler.numRecipes() && i < (page + 1) * recipesPerPage; i++) {
            handler.drawBackground(i);
            GL11.glTranslatef(0, handlerInfo.getHeight(), 0);
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

    public boolean isMouseOver(PositionedStack stack, int recipe) {
        Slot stackSlot = slotcontainer.getSlotWithStack(stack, getRecipePosition(recipe).x, getRecipePosition(recipe).y);
        Point mousepos = GuiDraw.getMousePosition();
        Slot mouseoverSlot = getSlotAtPosition(mousepos.x, mousepos.y);

        return stackSlot == mouseoverSlot;
    }

    private boolean isMouseOverOverlayButton(int buttonId) {
        if(buttonId >= 0 && buttonId < overlayButtons.length) {
            GuiButton button = overlayButtons[buttonId];
            Point mousePos = GuiDraw.getMousePosition();
            return button.visible && mousePos.x >= button.xPosition && mousePos.y >= button.yPosition
                    && mousePos.x < button.xPosition + button.width && mousePos.y < button.yPosition + button.height;
        } else {
            return false;
        }
    }
    
    private int getRecipesPerPage() {
        if(handlerInfo != null) 
            return Math.max(Math.min(((ySize - (buttonHeight*3)) / handlerInfo.getHeight()), handlerInfo.getMaxRecipesPerPage()), 1);
        else
            return (handler.recipiesPerPage());
    }

    public Point getRecipePosition(int recipe) {
        return new Point(5, 32 + yShift + ((recipe % getRecipesPerPage()) * handlerInfo.getHeight()));
    }

    public abstract ArrayList<? extends IRecipeHandler> getCurrentRecipeHandlers();

    @Override
    public VisiblityData modifyVisiblity(GuiContainer gui, VisiblityData currentVisibility)
    {
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
        Rectangle rect = new Rectangle(area);

        return rect.contains(x, y, w, h);
    }

}
