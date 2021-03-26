package codechicken.nei.recipe;

import codechicken.lib.gui.GuiDraw;
import codechicken.lib.render.CCRenderState;
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

public abstract class GuiRecipe extends GuiContainer implements IGuiContainerOverlay, IGuiClientSide, IGuiHandleMouseWheel, IContainerTooltipHandler, INEIGuiHandler
{
    private static final int borderPadding = 6;
    private static final int buttonWidth = 13;
    private static final int buttonHeight = 12;
    
    public ArrayList<? extends IRecipeHandler> currenthandlers = new ArrayList<>();
    private static Class gregtechHandler;

    static {
        try {
            gregtechHandler = Class.forName("gregtech.nei.GT_NEI_DefaultHandler");
        } catch (ClassNotFoundException e) {
            gregtechHandler = null;
        }
    }

    public int page;
    public int recipetype;
    public ContainerRecipe slotcontainer;
    public GuiContainer firstGui;
    public GuiScreen prevGui;
    public GuiButton nextpage;
    public GuiButton prevpage;
    public GuiButton overlay1;
    public GuiButton overlay2;
    private GuiButton nexttype;
    private GuiButton prevtype;
    private final Rectangle area = new Rectangle();
    private final GuiRecipeTabs recipeTabs;
    
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
    public void initGui() {
        super.initGui();

        currenthandlers = getCurrentRecipeHandlers(); // Probably don't comment me out
        final int rightButtonX = guiLeft + xSize - borderPadding - buttonWidth;
        final int leftButtonX = guiLeft + borderPadding;

        nexttype = new GuiNEIButton(0, leftButtonX, (height - ySize) / 2 + 3, buttonWidth, buttonHeight, "<");
        prevtype = new GuiNEIButton(1, rightButtonX, (height - ySize) / 2 + 3, buttonWidth, buttonHeight, ">");
        
        nextpage = new GuiNEIButton(2, leftButtonX, (height - ySize) / 2 + 17, buttonWidth, buttonHeight, "<");
        prevpage = new GuiNEIButton(3, rightButtonX, (height - ySize) / 2 + 17, buttonWidth, buttonHeight, ">");
        
        overlay1 = new GuiNEIButton(4, width / 2 + 65, (height - ySize) / 2 + 63, buttonWidth, buttonHeight, "?");
        overlay2 = new GuiNEIButton(5, width / 2 + 65, (height - ySize) / 2 + 128, buttonWidth, buttonHeight, "?");
        
        buttonList.addAll(Arrays.asList(nexttype, prevtype, nextpage, prevpage, overlay1, overlay2));

        if (currenthandlers.size() == 1) {
            nexttype.visible = false;
            prevtype.visible = false;
        }
        checkGregTech();
        recipeTabs.initLayout();
        refreshPage();
    }
    private void checkGregTech() {
        IRecipeHandler handler = currenthandlers.get(recipetype);
        if (gregtechHandler != null && gregtechHandler.isInstance(handler)) {
            // Gregtech decided to shift their handler by -8 and draw over shit...
            yShift = 6;
        } else {
            yShift = 0;
        }
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

        IRecipeHandler recipehandler = currenthandlers.get(recipetype);
        for (int recipe = page * recipehandler.recipiesPerPage(); recipe < recipehandler.numRecipes() && recipe < (page + 1) * recipehandler.recipiesPerPage(); recipe++)
            if (recipehandler.keyTyped(this, c, i, recipe))
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
        IRecipeHandler recipehandler = currenthandlers.get(recipetype);
        for (int recipe = page * recipehandler.recipiesPerPage(); recipe < recipehandler.numRecipes() && recipe < (page + 1) * recipehandler.recipiesPerPage(); recipe++)
            if (recipehandler.mouseClicked(this, button, recipe))
                return;
        if (recipeTabs.mouseClicked(x, y, button))
            return;
        super.mouseClicked(x, y, button);
    }

    @Override
    protected void actionPerformed(GuiButton guibutton) {
        super.actionPerformed(guibutton);
        switch (guibutton.id) {
            case 0:
                prevType();
                break;
            case 1:
                nextType();
                break;
            case 2:
                prevPage();
                break;
            case 3:
                nextPage();
                break;
            case 4:
                overlayRecipe(page * currenthandlers.get(recipetype).recipiesPerPage());
                break;
            case 5:
                overlayRecipe(page * currenthandlers.get(recipetype).recipiesPerPage() + 1);
                break;
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        currenthandlers.get(recipetype).onUpdate();
        refreshPage();
    }

    @Override
    public List<String> handleTooltip(GuiContainer gui, int mousex, int mousey, List<String> currenttip) {
        IRecipeHandler recipehandler = currenthandlers.get(recipetype);
        for (int i = page * recipehandler.recipiesPerPage(); i < recipehandler.numRecipes() && i < (page + 1) * recipehandler.recipiesPerPage(); i++)
            currenttip = recipehandler.handleTooltip(this, currenttip, i);
        recipeTabs.handleTooltip(mousex, mousey, currenttip);
        return currenttip;
    }

    @Override
    public List<String> handleItemTooltip(GuiContainer gui, ItemStack stack, int mousex, int mousey, List<String> currenttip) {
        IRecipeHandler recipehandler = currenthandlers.get(recipetype);
        for (int i = page * recipehandler.recipiesPerPage(); i < recipehandler.numRecipes() && i < (page + 1) * recipehandler.recipiesPerPage(); i++)
            currenttip = recipehandler.handleItemTooltip(this, stack, currenttip, i);

        return currenttip;
    }

    @Override
    public List<String> handleItemDisplayName(GuiContainer gui, ItemStack itemstack, List<String> currenttip) {
        return currenttip;
    }

    private void nextPage() {
        page++;
        if (page > (currenthandlers.get(recipetype).numRecipes() - 1) / currenthandlers.get(recipetype).recipiesPerPage())
            page = 0;
    }

    private void prevPage() {
        page--;
        if (page < 0)
            page = (currenthandlers.get(recipetype).numRecipes() - 1) / currenthandlers.get(recipetype).recipiesPerPage();
    }

    private void nextType() {
        setRecipePage(++recipetype);
    }

    private void prevType() {
        setRecipePage(--recipetype);
    }
    
    public void setRecipePage(int idx) {
        recipetype = idx;
        if (recipetype < 0)
            recipetype = currenthandlers.size() - 1;
        else if (recipetype >= currenthandlers.size())
            recipetype = 0;
        page = 0;
        recipeTabs.calcPageNumber();
        checkGregTech();
    }

    private void overlayRecipe(int recipe) {
        IRecipeOverlayRenderer renderer = currenthandlers.get(recipetype).getOverlayRenderer(firstGui, recipe);
        IOverlayHandler handler = currenthandlers.get(recipetype).getOverlayHandler(firstGui, recipe);
        boolean shift = NEIClientUtils.shiftKey();

        if (handler != null && (renderer == null || shift)) {
            mc.displayGuiScreen(firstGui);
            handler.overlayRecipe(firstGui, currenthandlers.get(recipetype), recipe, shift);
        } else if (renderer != null) {
            mc.displayGuiScreen(firstGui);
            LayoutManager.overlayRenderer = renderer;
        }
    }

    public void refreshPage() {
        refreshSlots();
        IRecipeHandler handler = currenthandlers.get(recipetype);
        area.width  = xSize;
        area.height = ySize;
        area.x = guiLeft - 2;
        area.y = guiTop  - 4;

        calcArea(handler);
        checkGregTech();

        boolean multiplepages = handler.numRecipes() > handler.recipiesPerPage();
        nextpage.enabled = prevpage.enabled = multiplepages;

        if(firstGui != null) {
            overlay1.yPosition = (height - ySize) / 2 + 17 + (handler.recipiesPerPage() == 2 ? 63 : 128);
            overlay1.visible = handler.hasOverlay(firstGui, firstGui.inventorySlots, page * handler.recipiesPerPage());
            overlay2.visible = handler.recipiesPerPage() == 2 && page * handler.recipiesPerPage() + 1 < handler.numRecipes() &&
                    handler.hasOverlay(firstGui, firstGui.inventorySlots, page * handler.recipiesPerPage() + 1);
        } else {
            overlay1.visible = overlay2.visible = false;
        }
        
        recipeTabs.refreshPage();
    }

    private void calcArea(IRecipeHandler handler) {
        if ( handler.getClass().toString().contains("fox.spiteful.avaritia.compat.nei.")) {
            // Handle avarita's oversized handler area
            area.width  = 256;
            area.height = 208;
        }
    }

    private void refreshSlots() {
        slotcontainer.inventorySlots.clear();
        IRecipeHandler recipehandler = currenthandlers.get(recipetype);
        for (int i = page * recipehandler.recipiesPerPage(); i < recipehandler.numRecipes() && i < (page + 1) * recipehandler.recipiesPerPage(); i++) {
            Point p = getRecipePosition(i);

            List<PositionedStack> stacks = recipehandler.getIngredientStacks(i);
            for (PositionedStack stack : stacks)
                slotcontainer.addSlot(stack, p.x, p.y);

            stacks = recipehandler.getOtherStacks(i);
            for (PositionedStack stack : stacks)
                slotcontainer.addSlot(stack, p.x, p.y);

            PositionedStack result = recipehandler.getResultStack(i);
            if (result != null)
                slotcontainer.addSlot(result, p.x, p.y);
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        GuiContainerManager.enable2DRender();

        IRecipeHandler recipehandler = currenthandlers.get(recipetype);
        String s = recipehandler.getRecipeName();
        fontRendererObj.drawStringWithShadow(s, (xSize - fontRendererObj.getStringWidth(s)) / 2, 5, 0xffffff);
        s = NEIClientUtils.translate("recipe.page", page + 1, (currenthandlers.get(recipetype).numRecipes() - 1) / recipehandler.recipiesPerPage() + 1);
        fontRendererObj.drawStringWithShadow(s, (xSize - fontRendererObj.getStringWidth(s)) / 2, 19, 0xffffff);

        GL11.glPushMatrix();
        GL11.glTranslatef(5, 32 + yShift, 0);
        for (int i = page * recipehandler.recipiesPerPage(); i < recipehandler.numRecipes() && i < (page + 1) * recipehandler.recipiesPerPage(); i++) {
            recipehandler.drawForeground(i);
            GL11.glTranslatef(0, 65, 0);
        }
        GL11.glPopMatrix();
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float f, int mouseX, int mouseY) {
        GL11.glColor4f(1, 1, 1, 1);
        CCRenderState.changeTexture("nei:textures/gui/recipebg.png");
        int j = (width  - xSize) / 2;
        int k = (height - ySize) / 2;
        drawTexturedModalRect(j, k, 0, 0, xSize, ySize);

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
        IRecipeHandler recipehandler = currenthandlers.get(recipetype);
        for (int i = page * recipehandler.recipiesPerPage(); i < recipehandler.numRecipes() && i < (page + 1) * recipehandler.recipiesPerPage(); i++) {
            recipehandler.drawBackground(i);
            GL11.glTranslatef(0, 65, 0);
        }
        GL11.glPopMatrix();
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

    public Point getRecipePosition(int recipe) {
        return new Point(5, 32 + yShift + (recipe % currenthandlers.get(recipetype).recipiesPerPage()) * 65);
    }

    @Override
    public void mouseScrolled(int i) {
        if (new Rectangle(guiLeft, guiTop, xSize, ySize).contains(GuiDraw.getMousePosition())) {
            if (i > 0) prevPage();
            else       nextPage();
        }
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
