package codechicken.nei.guihook;

import static codechicken.lib.gui.GuiDraw.drawMultilineTip;
import static codechicken.lib.gui.GuiDraw.fontRenderer;
import static codechicken.lib.gui.GuiDraw.getMousePosition;
import static codechicken.lib.gui.GuiDraw.renderEngine;
import static codechicken.nei.NEIClientUtils.translate;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.client.resources.SimpleReloadableResourceManager;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.fluids.FluidStack;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.ItemStackSet;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.recipe.StackInfo;
import codechicken.nei.util.ReadableNumberConverter;

public class GuiContainerManager {

    private static class ResourcePackReloaded implements IResourceManagerReloadListener {

        @Override
        public void onResourceManagerReload(IResourceManager p_110549_1_) {
            renderingErrorItems.clear();
        }
    }

    public GuiContainer window;

    public static RenderItem drawItems = new RenderItem();
    public static final LinkedList<IContainerTooltipHandler> tooltipHandlers = new HideousLinkedList<>(
            new CopyOnWriteArrayList<>());
    public static final LinkedList<IContainerInputHandler> inputHandlers = new HideousLinkedList<>(
            new CopyOnWriteArrayList<>());
    public static final LinkedList<IContainerDrawHandler> drawHandlers = new HideousLinkedList<>(
            new CopyOnWriteArrayList<>());
    public static final LinkedList<IContainerObjectHandler> objectHandlers = new HideousLinkedList<>(
            new CopyOnWriteArrayList<>());
    public static final LinkedList<IContainerSlotClickHandler> slotClickHandlers = new HideousLinkedList<>(
            new CopyOnWriteArrayList<>());

    static {
        addSlotClickHandler(new DefaultSlotClickHandler());
    }

    public static GuiContainerManager getManager() {
        GuiScreen gui = Minecraft.getMinecraft().currentScreen;
        return gui instanceof GuiContainer ? getManager((GuiContainer) gui) : null;
    }

    public static GuiContainerManager getManager(GuiContainer gui) {
        // gets GuiContainer.manager using ASM
        return null;
    }

    /**
     * Register a new Tooltip render handler;
     *
     * @param handler The handler to register
     */
    public static void addTooltipHandler(IContainerTooltipHandler handler) {
        tooltipHandlers.add(handler);
    }

    /**
     * Register a new Input handler;
     *
     * @param handler The handler to register
     */
    public static void addInputHandler(IContainerInputHandler handler) {
        inputHandlers.add(handler);
    }

    /**
     * Register a new Drawing handler;
     *
     * @param handler The handler to register
     */
    public static void addDrawHandler(IContainerDrawHandler handler) {
        drawHandlers.add(handler);
    }

    /**
     * Register a new Object handler;
     *
     * @param handler The handler to register
     */
    public static void addObjectHandler(IContainerObjectHandler handler) {
        objectHandlers.add(handler);
    }

    /**
     * Care needs to be taken with this method. It will insert your handler at the start of the list to be called first.
     * You may need to simply edit the list yourself.
     *
     * @param handler The handler to register.
     */
    public static void addSlotClickHandler(IContainerSlotClickHandler handler) {
        slotClickHandlers.addFirst(handler);
    }

    public static FontRenderer getFontRenderer(ItemStack stack) {
        if (stack != null && stack.getItem() != null) {
            FontRenderer f = stack.getItem().getFontRenderer(stack);
            if (f != null) return f;
        }
        return fontRenderer;
    }

    /**
     * Extra lines are often used for more information. For example enchantments, potion effects and mob spawner
     * contents.
     *
     * @param stack           The item to get the name for.
     * @param gui             An instance of the currentscreen passed to tooltip handlers. If null, only gui inspecific
     *                        handlers should respond
     * @param includeHandlers If true tooltip handlers will add to the item tip
     * @return A list of Strings representing the text to be displayed on each line of the tool tip.
     */
    public static List<String> itemDisplayNameMultiline(ItemStack stack, GuiContainer gui, boolean includeHandlers) {
        List<String> namelist = null;
        try {
            namelist = stack.getTooltip(
                    Minecraft.getMinecraft().thePlayer,
                    includeHandlers && Minecraft.getMinecraft().gameSettings.advancedItemTooltips);
        } catch (Throwable ignored) {
            namelist = new ArrayList<>();
        }

        if (namelist.isEmpty()) {
            namelist.add("Unnamed");
        } else if (namelist.get(0) == null || namelist.get(0).isEmpty()) {
            namelist.set(0, "Unnamed");
        }

        if (includeHandlers) {
            for (IContainerTooltipHandler handler : tooltipHandlers) {
                namelist = handler.handleItemDisplayName(gui, stack, namelist);
            }
        }

        namelist.set(0, stack.getRarity().rarityColor.toString() + namelist.get(0));

        for (int i = 1; i < namelist.size(); i++) {
            namelist.set(i, EnumChatFormatting.GRAY + namelist.get(i) + EnumChatFormatting.RESET);
        }

        return namelist;
    }

    @Nullable
    public static String countDetails(int stackSize, int maxStackSize, String fullPattern, String shortPattern) {
        if (maxStackSize > 1 && stackSize > maxStackSize) {
            final int remainder = stackSize % maxStackSize;

            if (remainder > 0) {
                return String.format(
                        fullPattern,
                        NEIClientUtils.formatNumbers(stackSize),
                        NEIClientUtils.formatNumbers(stackSize / maxStackSize),
                        NEIClientUtils.formatNumbers(maxStackSize),
                        NEIClientUtils.formatNumbers(remainder));
            } else {
                return String.format(
                        shortPattern,
                        NEIClientUtils.formatNumbers(stackSize),
                        NEIClientUtils.formatNumbers(stackSize / maxStackSize),
                        NEIClientUtils.formatNumbers(maxStackSize));
            }
        }

        return null;
    }

    @Nullable
    public static String itemCountDetails(ItemStack stack) {
        FluidStack fluid = StackInfo.getFluid(stack);
        if (fluid != null) {
            return fluidAmountDetails(fluid.amount * Math.max(1, stack.stackSize));
        } else {
            return countDetails(
                    stack.stackSize,
                    stack.getMaxStackSize(),
                    "Count: %s = %s * %s + %s",
                    "Count: %s = %s * %s");
        }
    }

    @SuppressWarnings("unused")
    @Nullable
    public static String fluidAmountDetails(FluidStack fluid) {
        return fluidAmountDetails(fluid.amount);
    }

    @Nullable
    public static String fluidAmountDetails(int amount) {
        return countDetails(amount, 144, "Amount: %s L = %s * %s L + %s L", "Amount: %s L = %s * %s L");
    }

    /**
     * The general name of this item.
     *
     * @param itemstack The {@link ItemStack} to get the name for.
     * @return The first line of the multiline display name.
     */
    public static String itemDisplayNameShort(ItemStack itemstack) {

        try {
            List<String> namelist = itemstack.getTooltip(Minecraft.getMinecraft().thePlayer, false);

            if (!namelist.isEmpty() && !"".equals(namelist.get(0))) {
                return itemstack.getRarity().rarityColor.toString() + namelist.get(0) + EnumChatFormatting.RESET;
            }

        } catch (Throwable ignored) {}

        return "Unnamed";
    }

    /**
     * Concatenates the multiline display name into one line for easy searching using string and {@link Pattern}
     * functions.
     *
     * @param itemstack The stack to get the name for
     * @return The multiline display name of this item separated by '#'
     */
    @Deprecated
    public static String concatenatedDisplayName(ItemStack itemstack, boolean includeHandlers) {
        List<String> list = itemDisplayNameMultiline(itemstack, null, includeHandlers);
        StringJoiner sb = new StringJoiner("#");

        for (String name : list) {
            if (!name.isEmpty()) {
                sb.add(name);
            }
        }

        return EnumChatFormatting.getTextWithoutFormattingCodes(sb.toString());
    }

    public static void drawItem(int i, int j, ItemStack itemstack) {
        drawItem(i, j, itemstack, false);
    }

    public static void drawItem(int i, int j, ItemStack itemstack, boolean smallAmount) {
        drawItem(i, j, itemstack, smallAmount, null);
    }

    public static void drawItem(int i, int j, ItemStack itemstack, boolean smallAmount, String stackSize) {
        drawItem(i, j, itemstack, getFontRenderer(itemstack), smallAmount, stackSize);
    }

    public static void drawItem(int i, int j, ItemStack itemstack, FontRenderer fontRenderer) {
        drawItem(i, j, itemstack, fontRenderer, false, null);
    }

    private static int modelviewDepth = -1;
    private static final HashSet<String> stackTraces = new HashSet<>();
    private static final ItemStackSet renderingErrorItems = new ItemStackSet();

    public static void drawItem(int i, int j, ItemStack itemstack, FontRenderer fontRenderer, boolean smallAmount,
            String stackSize) {
        enable3DRender();
        // for lighting correction
        boolean glRescale = GL11.glGetBoolean(GL12.GL_RESCALE_NORMAL);
        float zLevel = drawItems.zLevel += 100F;

        if (!renderingErrorItems.contains(itemstack)) {
            float scale = smallAmount ? 0.5f : 1f;

            try {
                drawItems.renderItemAndEffectIntoGUI(fontRenderer, renderEngine, itemstack, i, j);

                if (stackSize == null) {
                    if (itemstack.stackSize > 1) {
                        stackSize = ReadableNumberConverter.INSTANCE.toWideReadableForm(itemstack.stackSize);

                        if (stackSize.length() == 3) {
                            scale = 0.8f;
                        } else if (stackSize.length() == 4) {
                            scale = 0.6f;
                        } else if (stackSize.length() > 4) {
                            scale = 0.5f;
                        }

                    } else {
                        stackSize = "";
                    }

                }

                if (scale != 1f) {

                    if (!stackSize.isEmpty()) {
                        drawBigStackSize(i, j, stackSize, scale);
                    }

                    // stackSize = "". it needed for correct draw item with alpha and blend
                    drawItems.renderItemOverlayIntoGUI(fontRenderer, renderEngine, itemstack, i, j, "");
                } else {
                    drawItems.renderItemOverlayIntoGUI(fontRenderer, renderEngine, itemstack, i, j, stackSize);
                }

                if (!checkMatrixStack()) throw new IllegalStateException("Modelview matrix stack too deep");
                if (Tessellator.instance.isDrawing) throw new IllegalStateException("Still drawing");
            } catch (Exception e) {
                NEIClientUtils.reportErrorBuffered(e, stackTraces, itemstack);

                restoreMatrixStack();
                if (Tessellator.instance.isDrawing) Tessellator.instance.draw();

                drawItems.zLevel = zLevel;
                drawItems.renderItemIntoGUI(fontRenderer, renderEngine, new ItemStack(Blocks.fire), i, j);
                renderingErrorItems.add(itemstack);
            }
        } else {
            drawItems.renderItemIntoGUI(fontRenderer, renderEngine, new ItemStack(Blocks.fire), i, j);
        }

        if (glRescale) {
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        } else {
            GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        }

        enable2DRender();
        drawItems.zLevel = zLevel - 100;
    }

    // copy from appeng.client.render.AppEngRenderItem
    protected static void drawBigStackSize(int offsetX, int offsetY, String stackSize, float scale) {
        final float inverseScaleFactor = 1.0f / scale;

        enable2DRender();
        GL11.glScaled(scale, scale, scale);

        final int X = (int) (((float) offsetX + 16.0f - fontRenderer.getStringWidth(stackSize) * scale)
                * inverseScaleFactor);
        final int Y = (int) (((float) offsetY + 16.0f - 7.0f * scale) * inverseScaleFactor);
        fontRenderer.drawStringWithShadow(stackSize, X, Y, 16777215);

        GL11.glScaled(inverseScaleFactor, inverseScaleFactor, inverseScaleFactor);
        enable3DRender();
    }

    public static void registerReloadResourceListener() {
        if (Minecraft.getMinecraft().getResourceManager() instanceof SimpleReloadableResourceManager) {
            SimpleReloadableResourceManager manager = (SimpleReloadableResourceManager) Minecraft.getMinecraft()
                    .getResourceManager();
            manager.registerReloadListener(new ResourcePackReloaded());
        }
    }

    public static void enableMatrixStackLogging() {
        modelviewDepth = GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH);
    }

    public static void disableMatrixStackLogging() {
        modelviewDepth = -1;
    }

    public static boolean checkMatrixStack() {
        return modelviewDepth < 0 || GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH) == modelviewDepth;
    }

    public static void restoreMatrixStack() {
        if (modelviewDepth >= 0)
            for (int i = GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH); i > modelviewDepth; i--) GL11.glPopMatrix();
    }

    public static void setColouredItemRender(boolean enable) {
        drawItems.renderWithColor = !enable;
    }

    public static void enable3DRender() {
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    public static void enable2DRender() {
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
    }

    private int clickHandled = 0;
    public final List<IContainerTooltipHandler> instanceTooltipHandlers;

    public GuiContainerManager(GuiContainer screen) {
        window = screen;
        if (screen instanceof IContainerTooltipHandler) {
            instanceTooltipHandlers = Collections.synchronizedList(new LinkedList<>());
            instanceTooltipHandlers.add((IContainerTooltipHandler) screen);
            instanceTooltipHandlers.addAll(tooltipHandlers);
        } else instanceTooltipHandlers = tooltipHandlers;
    }

    public static ItemStack getStackMouseOver(GuiContainer window) {
        Point mousePos = getMousePosition();

        for (IContainerObjectHandler objectHandler : objectHandlers) {
            ItemStack item = objectHandler.getStackUnderMouse(window, mousePos.x, mousePos.y);
            if (item != null) return item;
        }

        Slot slot = getSlotMouseOver(window);
        if (slot != null) return slot.getStack();

        return null;
    }

    public static Slot getSlotMouseOver(GuiContainer window) {
        Point mousePos = getMousePosition();
        if (getManager(window).objectUnderMouse(mousePos.x, mousePos.y)) return null;

        return window.getSlotAtPosition(mousePos.x, mousePos.y);
    }

    public void load() {
        clickHandled = 0;
        for (IContainerObjectHandler objectHandler : objectHandlers) objectHandler.load(window);
    }

    /**
     * Called from updateScreen
     */
    public void updateScreen() {
        for (IContainerObjectHandler objectHandler : objectHandlers) objectHandler.guiTick(window);
    }

    /**
     * Override for keyTyped
     */
    public boolean lastKeyTyped(int keyID, char keyChar) {
        if (keyID == 1) return false;

        for (IContainerInputHandler inputhander : inputHandlers)
            if (inputhander.lastKeyTyped(window, keyChar, keyID)) return true;

        return false;
    }

    public boolean firstKeyTyped(char keyChar, int keyID) {
        for (IContainerInputHandler inputhander : inputHandlers) inputhander.onKeyTyped(window, keyChar, keyID);

        for (IContainerInputHandler inputhander : inputHandlers)
            if (inputhander.keyTyped(window, keyChar, keyID)) return true;

        return false;
    }

    public boolean mouseClicked(int mousex, int mousey, int button) {
        for (IContainerInputHandler inputhander : inputHandlers)
            inputhander.onMouseClicked(window, mousex, mousey, button);

        for (IContainerInputHandler inputhander : inputHandlers)
            if (inputhander.mouseClicked(window, mousex, mousey, button)) {
                clickHandled |= 1 << button;
                return true;
            }

        return false;
    }

    public void mouseScrolled(int scrolled) {
        Point mousepos = getMousePosition();

        for (IContainerInputHandler inputHandler : inputHandlers)
            inputHandler.onMouseScrolled(window, mousepos.x, mousepos.y, scrolled);

        for (IContainerInputHandler inputHandler : inputHandlers)
            if (inputHandler.mouseScrolled(window, mousepos.x, mousepos.y, scrolled)) return;

        if (window instanceof IGuiHandleMouseWheel) ((IGuiHandleMouseWheel) window).mouseScrolled(scrolled);
    }

    /**
     * Override for mouseMovedOrUp
     */
    public boolean overrideMouseUp(int mousex, int mousey, int button) {
        if (button >= 0 && (clickHandled & 1 << button) != 0) {
            clickHandled &= ~(1 << button);
            mouseUp(mousex, mousey, button);
            return true;
        }
        return false;
    }

    public void mouseUp(int mousex, int mousey, int button) {
        for (IContainerInputHandler inputhander : inputHandlers) inputhander.onMouseUp(window, mousex, mousey, button);
    }

    /**
     * Called from mouseClickMove
     */
    public void mouseDragged(int mousex, int mousey, int button, long heldTime) {
        for (IContainerInputHandler inputhander : inputHandlers)
            inputhander.onMouseDragged(window, mousex, mousey, button, heldTime);
    }

    /**
     * Called at the start of drawScreen
     */
    public void preDraw() {
        for (IContainerDrawHandler drawHandler : drawHandlers) drawHandler.onPreDraw(window);
    }

    public void renderObjects(int mousex, int mousey) {
        GL11.glTranslatef(-window.guiLeft, -window.guiTop, 200F);
        for (IContainerDrawHandler drawHandler : drawHandlers) drawHandler.renderObjects(window, mousex, mousey);

        for (IContainerDrawHandler drawHandler : drawHandlers) drawHandler.postRenderObjects(window, mousex, mousey);
        GL11.glTranslatef(window.guiLeft, window.guiTop, -200F);
    }

    public void renderToolTips(int mousex, int mousey) {
        List<String> tooltip = new LinkedList<>();
        Map<String, String> hotkeys = new HashMap<>();
        FontRenderer font = GuiDraw.fontRenderer;

        synchronized (instanceTooltipHandlers) {
            for (IContainerTooltipHandler handler : instanceTooltipHandlers)
                tooltip = handler.handleTooltip(window, mousex, mousey, tooltip);
        }

        if (tooltip.isEmpty() && shouldShowTooltip(window)) { // mouseover tip, not holding an item
            ItemStack stack = getStackMouseOver(window);
            font = getFontRenderer(stack);
            if (stack != null) {
                tooltip = itemDisplayNameMultiline(stack, window, true);
                applyItemCountDetails(tooltip, stack);
            }

            synchronized (instanceTooltipHandlers) {
                for (IContainerTooltipHandler handler : instanceTooltipHandlers)
                    tooltip = handler.handleItemTooltip(window, stack, mousex, mousey, tooltip);
            }
        }

        if (tooltip.size() > 0) tooltip.set(0, tooltip.get(0) + GuiDraw.TOOLTIP_LINESPACE); // add space after 'title'

        synchronized (instanceTooltipHandlers) {
            for (IContainerTooltipHandler handler : instanceTooltipHandlers) {
                hotkeys = handler.handleHotkeys(window, mousex, mousey, hotkeys);
            }
        }

        if (!hotkeys.isEmpty()) {

            if (!NEIClientUtils.altKey()) {
                tooltip.add(
                        1,
                        EnumChatFormatting.GRAY + translate(
                                "showHotkeys",
                                "" + EnumChatFormatting.GOLD + "ALT" + EnumChatFormatting.GRAY));
            } else {
                List<String> hotkeystips = new LinkedList<>();

                for (String key : hotkeys.keySet()) {
                    hotkeystips.add(getHotkeyTip(key, hotkeys.get(key)));
                }

                hotkeystips.sort((a, b) -> Integer.compare(a.indexOf(" - "), b.indexOf(" - ")));
                hotkeystips.set(
                        hotkeystips.size() - 1,
                        hotkeystips.get(hotkeystips.size() - 1) + GuiDraw.TOOLTIP_LINESPACE);

                tooltip.addAll(1, hotkeystips);
            }
        }

        drawPagedTooltip(font, mousex + 12, mousey - 12, tooltip);

        for (IContainerDrawHandler drawHandler : drawHandlers) {
            drawHandler.postRenderTooltips(window, mousex, mousey, tooltip);
        }
    }

    private String getHotkeyTip(String key, String message) {
        return EnumChatFormatting.GOLD + key + EnumChatFormatting.GRAY + " - " + message + EnumChatFormatting.RESET;
    }

    private static int tooltipPage;

    public static void drawPagedTooltip(FontRenderer font, int x, int y, List<String> list) {
        if (list.isEmpty()) return;
        List<List<String>> tooltips = splitTooltipByPage(list);
        tooltipPage = tooltipPage < tooltips.size() ? tooltipPage : 0;
        if (tooltips.size() > 1) {
            tooltips.get(tooltipPage).add(
                    EnumChatFormatting.ITALIC + NEIClientUtils.translate(
                            "inventory.tooltip.page",
                            tooltipPage + 1,
                            tooltips.size(),
                            NEIClientConfig.getKeyName(NEIClientConfig.getKeyBinding("gui.next_tooltip"), true)));
        }
        drawMultilineTip(font, x, y, tooltips.get(tooltipPage));
    }

    public static List<List<String>> splitTooltipByPage(List<String> list) {
        List<List<String>> ret = new ArrayList<>();
        List<String> tmp = new ArrayList<>();
        int height = -2;
        for (int i = 0; i < list.size(); i++) {
            String text = list.get(i);
            GuiDraw.ITooltipLineHandler line = GuiDraw.getTipLine(text);
            int lineHeight = line != null ? line.getSize().height
                    : text.endsWith(GuiDraw.TOOLTIP_LINESPACE) && i + 1 < list.size() ? 12 : 10;
            if (height + lineHeight <= GuiDraw.displaySize().height - 8 * 2 - 10) {
                // top & bottom 8px + last line for showing page
                height += lineHeight;
            } else {
                ret.add(new ArrayList<>(tmp));
                tmp.clear();
                height = -2;
            }
            tmp.add(text);
        }
        if (!tmp.isEmpty()) {
            ret.add(tmp);
        }
        return ret;
    }

    public static boolean shouldShowTooltip(GuiContainer window) {
        if (window == null) return false;
        for (IContainerObjectHandler handler : objectHandlers) if (!handler.shouldShowTooltip(window)) return false;

        return window.mc.thePlayer.inventory.getItemStack() == null;
    }

    public static void applyItemCountDetails(List<String> tooltip, ItemStack stack) {
        if (stack.stackSize != Integer.MAX_VALUE && NEIClientUtils.shiftKey()) {
            final String itemCount = itemCountDetails(stack);

            if (itemCount != null) {
                tooltip.add(String.format(EnumChatFormatting.GRAY + itemCount + EnumChatFormatting.RESET));
            }
        }
    }

    public static void incrementTooltipPage() {
        tooltipPage++;
    }

    public void renderSlotUnderlay(Slot slot) {
        for (IContainerDrawHandler drawHandler : drawHandlers) drawHandler.renderSlotUnderlay(window, slot);
    }

    public void renderSlotOverlay(Slot slot) {
        for (IContainerDrawHandler drawHandler : drawHandlers) drawHandler.renderSlotOverlay(window, slot);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
    }

    /**
     * Returns true if there is an object of yours obscuring the slot that the mouse would otherwise be hovering over.
     */
    public boolean objectUnderMouse(int mousex, int mousey) {
        for (IContainerObjectHandler objectHandler : objectHandlers)
            if (objectHandler.objectUnderMouse(window, mousex, mousey)) return true;

        return false;
    }

    public void handleMouseClick(Slot slot, int slotIndex, int button, int modifier) {
        for (IContainerSlotClickHandler handler : slotClickHandlers)
            handler.beforeSlotClick(window, slotIndex, button, slot, modifier);

        boolean eventHandled = false;
        for (IContainerSlotClickHandler handler : slotClickHandlers)
            eventHandled = handler.handleSlotClick(window, slotIndex, button, slot, modifier, eventHandled);

        for (IContainerSlotClickHandler handler : slotClickHandlers)
            handler.afterSlotClick(window, slotIndex, button, slot, modifier);
    }

    // Support inputting Chinese characters
    public void handleKeyboardInput() {
        // Support for LWGJL 2.9.0 or later
        int k = Keyboard.getEventKey();
        char c = Keyboard.getEventCharacter();
        if (Keyboard.getEventKeyState() || (k == 0 && Character.isDefined(c))) {
            try {
                keyTyped(c, k);
            } catch (java.lang.IndexOutOfBoundsException e) {
                System.err.println("Caught out of bounds exception pressing " + c + " " + k);
                e.printStackTrace();
            }
        }

        window.mc.func_152348_aa();
    }

    public void keyTyped(char c, int k) {
        if (firstKeyTyped(c, k)) return;

        callKeyTyped(window, c, k);
    }

    private static void callKeyTyped(GuiContainer window, char c, int k) {
        // calls GuiContainer.keyTyped using ASM generated forwarder
    }

    /**
     * Delegate for changing item rendering for certain slots. Eg. Shrinking text for large itemstacks
     */
    public void drawSlotItem(Slot slot, ItemStack stack, int x, int y, String quantity) {
        // for lighting correction
        boolean glRescale = GL11.glGetBoolean(GL12.GL_RESCALE_NORMAL);
        float zLevel = drawItems.zLevel += 100F;

        if (!renderingErrorItems.contains(stack)) {
            try {
                if (window instanceof IGuiSlotDraw) {
                    ((IGuiSlotDraw) window).drawSlotItem(slot, stack, x, y, quantity);
                } else {

                    if (quantity == null) {
                        quantity = stack != null && stack.stackSize > 1 ? String.valueOf(stack.stackSize) : "";
                    }

                    drawItems.renderItemAndEffectIntoGUI(fontRenderer, renderEngine, stack, x, y);
                    drawItems.renderItemOverlayIntoGUI(fontRenderer, renderEngine, stack, x, y, quantity);
                }
            } catch (Exception e) {
                NEIClientUtils.reportErrorBuffered(e, stackTraces, stack);

                restoreMatrixStack();
                if (Tessellator.instance.isDrawing) Tessellator.instance.draw();

                drawItems.zLevel = zLevel;
                drawItems.renderItemIntoGUI(fontRenderer, renderEngine, new ItemStack(Blocks.fire), x, y);
                renderingErrorItems.add(stack);
            }
        } else {
            drawItems.renderItemIntoGUI(fontRenderer, renderEngine, new ItemStack(Blocks.fire), x, y);
        }

        if (glRescale) {
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        } else {
            GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        }
        drawItems.zLevel = zLevel - 100;
    }

    /**
     * Implementation for handleMouseClick
     */
    public void handleSlotClick(int slotIndex, int button, int modifiers) {
        if (slotIndex == -1) return;

        if (window instanceof IGuiClientSide) // send the calls directly to the container bypassing the MPController
                                              // window send
            window.mc.thePlayer.openContainer.slotClick(slotIndex, button, modifiers, window.mc.thePlayer);
        else window.mc.playerController
                .windowClick(window.inventorySlots.windowId, slotIndex, button, modifiers, window.mc.thePlayer);
    }

    /**
     * Called from handleMouseInput
     */
    public void handleMouseWheel() {
        int i = Mouse.getEventDWheel();
        if (i != 0) mouseScrolled(i > 0 ? 1 : -1);
    }
}
