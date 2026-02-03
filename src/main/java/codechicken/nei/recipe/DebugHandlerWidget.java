package codechicken.nei.recipe;

import java.awt.Point;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.util.EnumChatFormatting;

import org.apache.commons.io.IOUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import codechicken.lib.gui.GuiDraw;
import codechicken.lib.vec.Rectangle4i;
import codechicken.nei.Button;
import codechicken.nei.ClientHandler;
import codechicken.nei.FormattedTextField;
import codechicken.nei.LayoutManager;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.TextField;
import codechicken.nei.Widget;
import codechicken.nei.drawable.DrawableBuilder;
import codechicken.nei.drawable.DrawableResource;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.guihook.IContainerInputHandler;
import codechicken.nei.recipe.TemplateRecipeHandler.RecipeTransferRect;

public class DebugHandlerWidget extends Widget implements IContainerInputHandler {

    private static abstract class IntegerField extends TextField {

        protected int defaultValue = 0;

        public IntegerField(String ident, int defaultValue) {
            super(ident);
            this.defaultValue = defaultValue;
            this.h = LINE_HEIGHT;
            this.z = 2;
            ((FormattedTextField) field).setPlaceholder(Integer.toString(this.defaultValue));
        }

        @Override
        protected void initInternalTextField() {
            field = new FormattedTextField(Minecraft.getMinecraft().fontRenderer, 0, 0, 0, 0) {

                @Override
                public String getText() {
                    String text = super.getText();
                    return !isFocused() && this.placeholder.equals(text) ? "" : text;
                }

                @Override
                protected boolean beforeWrite(String text) {
                    if (text == null || text.isEmpty()) return true;
                    try {
                        return Integer.parseInt(text) >= 0;
                    } catch (NumberFormatException e) {
                        return false;
                    }
                }

                @Override
                public boolean textboxKeyTyped(char keyChar, int keyID) {
                    if (!isFocused()) return false;

                    if (super.textboxKeyTyped(keyChar, keyID)) {
                        return true;
                    } else if (keyID == Keyboard.KEY_DOWN) {
                        setText(Integer.toString(getInteger() - 1));
                        return true;
                    } else if (keyID == Keyboard.KEY_UP) {
                        setText(Integer.toString(getInteger() + 1));
                        return true;
                    }

                    return false;
                }

            };
            field.setMaxStringLength(maxSearchLength);
            field.setCursorPositionZero();
        }

        protected int getInteger() {
            try {
                return Integer.parseInt(text());
            } catch (NumberFormatException nfe) {
                return this.defaultValue;
            }
        }

        @Override
        public boolean onMouseWheel(int i, int mx, int my) {
            if (!contains(mx, my)) return false;
            setText(Integer.toString(getInteger() + i));
            return true;
        }

    }

    private final int[] COLORS = new int[] { 0x2200FF00, 0x22FF0000, 0x220000FF, 0x2200FFFF, 0x22FF00FF, 0x22FFFF00 };

    private IRecipeHandler handler;
    private HandlerInfo handlerInfo;

    private static final int WINDOW_WIDTH = 176;
    private static final int LINE_HEIGHT = 16;
    private static final int INLINE_PADDING = 6;
    private static final int LABEL_WIDTH = 50;

    private IntegerField order;
    private IntegerField yShift;
    private IntegerField handlerHeight;
    private IntegerField handlerWidth;
    private Button multipleWidgetsAllowed;
    private Button useCustomScroll;

    private Point dragPoint = null;
    public boolean showWidget = false;

    private static final int BORDER_PADDING = 6;
    private static final int TRANSPARENCY_BORDER = 4;
    private static final String TOOLTIP_PREFIX = "debug.RecipeHandler.";
    private static final Map<String, String> TOOLTIP_KEYS = new HashMap<>();

    static {
        TOOLTIP_KEYS.put("Name", TOOLTIP_PREFIX + "Name");
        TOOLTIP_KEYS.put("ID", TOOLTIP_PREFIX + "Id");
        TOOLTIP_KEYS.put("Key", TOOLTIP_PREFIX + "Key");
        TOOLTIP_KEYS.put("H.Hack", TOOLTIP_PREFIX + "HHack");
        TOOLTIP_KEYS.put("Mod Name", TOOLTIP_PREFIX + "ModName");
        TOOLTIP_KEYS.put("Mod ID", TOOLTIP_PREFIX + "ModId");
        TOOLTIP_KEYS.put("Order", TOOLTIP_PREFIX + "Order");
        TOOLTIP_KEYS.put("yShift", TOOLTIP_PREFIX + "yShift");
        TOOLTIP_KEYS.put("Multi Widgets", TOOLTIP_PREFIX + "MultiWidgets");
        TOOLTIP_KEYS.put("Height", TOOLTIP_PREFIX + "Height");
        TOOLTIP_KEYS.put("Width", TOOLTIP_PREFIX + "Width");
        TOOLTIP_KEYS.put("Use Custom Scroll", TOOLTIP_PREFIX + "UseCustomScroll");
    }
    private final DrawableResource BG_TEXTURE = new DrawableBuilder(
            "nei:textures/gui/recipebg.png",
            0,
            0,
            176 + TRANSPARENCY_BORDER * 2,
            166 + TRANSPARENCY_BORDER * 2).build();

    public DebugHandlerWidget() {
        GuiContainerManager.addInputHandler(this);

        this.order = new IntegerField("order", 0) {

            @Override
            public void onTextChange(String oldText) {

                if (NEIClientConfig.handlerOrdering.get(handler.getOverlayIdentifier()) != null) {
                    if (getInteger() != NEIClientConfig.handlerOrdering.get(handler.getOverlayIdentifier())) {
                        updatePatch(5, getInteger(), this.defaultValue);
                    }
                } else if (NEIClientConfig.handlerOrdering.get(handler.getHandlerId()) != null) {
                    if (getInteger() != NEIClientConfig.handlerOrdering.get(handler.getHandlerId())) {
                        updatePatch(5, getInteger(), this.defaultValue);
                    }
                }
            }
        };

        this.yShift = new IntegerField("yShift", 0) {

            @Override
            public void onTextChange(String oldText) {
                if (getInteger() != handlerInfo.getYShift()) {
                    handlerInfo.setYShift(getInteger());
                    updatePatch(1, getInteger(), this.defaultValue);
                }
            }
        };

        this.multipleWidgetsAllowed = new Button() {

            {
                this.h = LINE_HEIGHT;
                this.z = 2;
            }

            public String getRenderLabel() {
                return handlerInfo.isMultipleWidgetsAllowed() ? "On" : "Off";
            }

            @Override
            public boolean onButtonPress(boolean rightclick) {
                handlerInfo.setHandlerDimensions(
                        handlerInfo.getWidth(),
                        handlerInfo.getHeight(),
                        !handlerInfo.isMultipleWidgetsAllowed());
                updatePatch(4, handlerInfo.isMultipleWidgetsAllowed() ? 1 : 0, 1);
                return true;
            }

        };

        this.handlerHeight = new IntegerField("handlerHeight", HandlerInfo.DEFAULT_HEIGHT) {

            @Override
            public void draw(int mx, int my) {
                this.field.setEnabled(handler.getRecipeHeight(0) <= 0);
                super.draw(mx, my);
            }

            @Override
            public void onTextChange(String oldText) {
                if (getInteger() != handlerInfo.getHeight()) {
                    handlerInfo.setHandlerDimensions(
                            handlerInfo.getWidth(),
                            getInteger(),
                            handlerInfo.isMultipleWidgetsAllowed());
                    updatePatch(2, getInteger(), this.defaultValue);
                }
            }
        };

        this.handlerWidth = new IntegerField("handlerWidth", HandlerInfo.DEFAULT_WIDTH) {

            @Override
            public void onTextChange(String oldText) {
                if (getInteger() != handlerInfo.getWidth()) {
                    handlerInfo.setHandlerDimensions(
                            getInteger(),
                            handlerInfo.getHeight(),
                            handlerInfo.isMultipleWidgetsAllowed());
                    updatePatch(3, getInteger(), this.defaultValue);
                }
            }
        };

        this.useCustomScroll = new Button() {

            {
                this.h = LINE_HEIGHT;
                this.z = 2;
            }

            public String getRenderLabel() {
                return handlerInfo.getUseCustomScroll() ? "On" : "Off";
            }

            @Override
            public boolean onButtonPress(boolean rightclick) {
                handlerInfo.setUseCustomScroll(!handlerInfo.getUseCustomScroll());
                updatePatch(6, handlerInfo.getUseCustomScroll() ? 1 : 0, 0);
                return true;
            }

        };

        this.w = WINDOW_WIDTH;
        this.h = 10 + 13 * LINE_HEIGHT;
        this.x = 10;
        this.y = 50;
        this.z = 1;
    }

    private void updatePatch(int key, int value, int defaultValue) {
        final String handlerKey = getHandlerID(handler);

        if (!patches.containsKey(handlerKey)) {
            patches.put(handlerKey, new String[] { handlerKey, null, null, null, null, null, null });
        }

        if (value == defaultValue) {
            patches.get(handlerKey)[key] = null;
        } else {
            patches.get(handlerKey)[key] = String.valueOf(value);
        }

        saveHandlerInfoPatch();

        if (NEIClientUtils.getGuiContainer() instanceof GuiRecipe<?>recipe) {
            recipe.forceRefreshPage();
        }

    }

    private void drawKeyLabel(String keyLabel, int topShift) {
        final String displayLabel = translateDisplayLabel(keyLabel);
        GuiDraw.drawString(
                NEIClientUtils.cropText(GuiDraw.fontRenderer, displayLabel, LABEL_WIDTH),
                this.x + INLINE_PADDING,
                topShift,
                0x66555555,
                false);
        GuiDraw.drawString(":", this.x + INLINE_PADDING + LABEL_WIDTH, topShift, 0x66555555, false);
    }

    private void drawDetail(String keyLabel, String valueLabel, int topShift, int spaceWidth) {
        drawKeyLabel(keyLabel, topShift);
        GuiDraw.drawString(
                NEIClientUtils.cropText(
                        GuiDraw.fontRenderer,
                        valueLabel,
                        this.w - INLINE_PADDING * 2 - LABEL_WIDTH - spaceWidth),
                this.x + INLINE_PADDING + LABEL_WIDTH + spaceWidth,
                topShift,
                0xffffff);
    }

    private void drawControl(String keyLabel, Widget field, int topShift, int spaceWidth) {
        drawKeyLabel(keyLabel, topShift);

        field.x = this.x + INLINE_PADDING + LABEL_WIDTH + spaceWidth;
        field.y = topShift - 4;
        field.w = this.w - INLINE_PADDING * 2 - LABEL_WIDTH - spaceWidth;
    }

    private static String translateTooltipLabel(String label) {
        String key = TOOLTIP_KEYS.get(label);
        String text = label;
        if (key == null) {
            return EnumChatFormatting.WHITE + EnumChatFormatting.getTextWithoutFormattingCodes(text);
        }
        text = NEIClientUtils.translate(key);
        return EnumChatFormatting.WHITE + EnumChatFormatting.getTextWithoutFormattingCodes(text);
    }

    private static String translateDisplayLabel(String label) {
        String key = TOOLTIP_KEYS.get(label);
        if (key == null) {
            return label;
        }
        return NEIClientUtils.translate(key);
    }

    @Override
    public void draw(int mx, int my) {

        if (this.handler != null) {
            final int spaceWidth = GuiDraw.fontRenderer.getStringWidth(": ");
            int topShift = this.y + BORDER_PADDING + 12 + 6;

            GL11.glColor4f(1F, 1F, 1F, 1F);
            GL11.glScaled(1, 1, 2f);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            BG_TEXTURE.draw(
                    this.x - TRANSPARENCY_BORDER,
                    this.y - TRANSPARENCY_BORDER,
                    this.w + TRANSPARENCY_BORDER * 2,
                    this.h + TRANSPARENCY_BORDER * 2,
                    BORDER_PADDING + TRANSPARENCY_BORDER,
                    BORDER_PADDING + TRANSPARENCY_BORDER,
                    BORDER_PADDING + TRANSPARENCY_BORDER,
                    BORDER_PADDING + TRANSPARENCY_BORDER);
            GuiDraw.drawRect(
                    this.x + INLINE_PADDING,
                    this.y + BORDER_PADDING,
                    this.w - INLINE_PADDING * 2,
                    12,
                    0x30000000);
            GuiDraw.drawStringC(
                    NEIClientUtils.translate("debug.RecipeHandler.title"),
                    this.x + this.w / 2,
                    this.y + BORDER_PADDING + 2,
                    0xffffff);

            for (Map.Entry<String, String> entry : getDetailsInfo().entrySet()) {
                drawDetail(entry.getKey(), entry.getValue(), topShift, spaceWidth);
                topShift += LINE_HEIGHT;
            }

            drawControl("Order", this.order, topShift, spaceWidth);
            topShift += LINE_HEIGHT;

            drawControl("yShift", this.yShift, topShift, spaceWidth);
            topShift += LINE_HEIGHT;

            drawControl("Multi Widgets", this.multipleWidgetsAllowed, topShift, spaceWidth);
            topShift += LINE_HEIGHT;

            drawControl("Height", this.handlerHeight, topShift, spaceWidth);
            topShift += LINE_HEIGHT;

            drawControl("Width", this.handlerWidth, topShift, spaceWidth);
            topShift += LINE_HEIGHT;

            drawControl("Use Custom Scroll", this.useCustomScroll, topShift, spaceWidth);
            topShift += LINE_HEIGHT;

            GL11.glScaled(1, 1, 1 / 2f);
        }
    }

    private Map<String, String> getDetailsInfo() {
        final Map<String, String> lines = new LinkedHashMap<>();
        final boolean isHeightHackApplied = NEIClientConfig.heightHackHandlerRegex.stream()
                .map(pattern -> pattern.matcher(this.handler.getHandlerId())).anyMatch(Matcher::matches);

        lines.put("Name", this.handler.getRecipeName());
        lines.put("ID", this.handler.getOverlayIdentifier());
        lines.put("Key", this.handler.getHandlerId());
        lines.put("H.Hack", String.valueOf(isHeightHackApplied));
        lines.put("Mod Name", this.handlerInfo.getModName());
        lines.put("Mod ID", this.handlerInfo.getModId());

        return lines;
    }

    public void setVisible() {
        if (showWidget && this.handler != null) {
            LayoutManager.addWidget(this.order);
            LayoutManager.addWidget(this.yShift);
            LayoutManager.addWidget(this.handlerHeight);
            LayoutManager.addWidget(this.handlerWidth);
            LayoutManager.addWidget(this.multipleWidgetsAllowed);
            LayoutManager.addWidget(this.useCustomScroll);
        }
    }

    @Override
    public void update() {
        final GuiContainer gui = NEIClientUtils.getGuiContainer();

        if (showWidget && gui instanceof GuiRecipe recipe) {

            if (this.handler != recipe.getHandler()) {
                this.handler = recipe.getHandler();
                this.handlerInfo = GuiRecipeTab.getHandlerInfo(handler);

                this.order.setText(String.valueOf(NEIClientConfig.getHandlerOrder(this.handler)));
                this.yShift.setText(String.valueOf(this.handlerInfo.getYShift()));
                this.handlerHeight.setText(String.valueOf(this.handlerInfo.getHeight()));
                this.handlerWidth.setText(String.valueOf(this.handlerInfo.getWidth()));
            }

        } else {
            this.handler = null;
            this.handlerInfo = null;
            showWidget = false;
        }

    }

    @Override
    public List<String> handleTooltip(int mx, int my, List<String> tooltip) {

        if (this.handler != null) {
            final int spaceWidth = GuiDraw.fontRenderer.getStringWidth(": ");
            final int leftShift = this.x + INLINE_PADDING;

            if (mx >= leftShift && mx < this.x + WINDOW_WIDTH - INLINE_PADDING) {
                int topShift = this.y + BORDER_PADDING + 12 + 6;

                for (Map.Entry<String, String> entry : getDetailsInfo().entrySet()) {
                    if (my >= topShift && my < topShift + LINE_HEIGHT) {

                        if (mx < leftShift + LABEL_WIDTH + spaceWidth) {
                            tooltip.add(translateTooltipLabel(entry.getKey()));
                        } else {
                            tooltip.add(String.valueOf(entry.getValue()));
                        }

                        return tooltip;
                    }
                    topShift += LINE_HEIGHT;
                }

                String[] controlLabels = new String[] { "Order", "yShift", "Multi Widgets", "Height", "Width",
                        "Use Custom Scroll" };
                for (String label : controlLabels) {
                    if (my >= topShift && my < topShift + LINE_HEIGHT) {
                        tooltip.add(translateTooltipLabel(label));
                        return tooltip;
                    }
                    topShift += LINE_HEIGHT;
                }
            }
        }

        return tooltip;
    }

    @Override
    public boolean keyTyped(GuiContainer gui, char keyChar, int keyCode) {

        if (NEIClientConfig.getBooleanSetting("inventory.guirecipe.handlerInfo") && showWidget
                && keyCode == Keyboard.KEY_C
                && NEIClientUtils.controlKey()) {
            final int spaceWidth = GuiDraw.fontRenderer.getStringWidth(": ");
            final Point mouse = GuiDraw.getMousePosition();

            if (mouse.x >= this.x + INLINE_PADDING + LABEL_WIDTH + spaceWidth
                    && mouse.x < this.x + WINDOW_WIDTH - INLINE_PADDING) {
                int topShift = this.y + BORDER_PADDING + 12 + 6;

                for (Map.Entry<String, String> entry : getDetailsInfo().entrySet()) {
                    if (mouse.y >= topShift && mouse.y < topShift + LINE_HEIGHT) {
                        GuiScreen.setClipboardString(entry.getValue());
                        return true;
                    }
                    topShift += LINE_HEIGHT;
                }
            }
        }

        return false;
    }

    @Override
    public boolean mouseClicked(GuiContainer gui, int mousex, int mousey, int button) {
        return false;
    }

    @Override
    public void onKeyTyped(GuiContainer gui, char keyChar, int keyID) {}

    @Override
    public boolean lastKeyTyped(GuiContainer gui, char keyChar, int keyID) {

        if (NEIClientConfig.getBooleanSetting("inventory.guirecipe.handlerInfo") && keyID == Keyboard.KEY_D
                && NEIClientUtils.shiftKey()) {
            showWidget = !showWidget;
            return true;
        } else if (this.handler != null) {
            return this.order.focused() || this.yShift.focused()
                    || this.handlerHeight.focused()
                    || this.handlerWidth.focused();
        }

        return false;
    }

    @Override
    public void onMouseClicked(GuiContainer gui, int mousex, int mousey, int button) {
        if (this.handler != null
                && new Rectangle4i(this.x + INLINE_PADDING, this.y + BORDER_PADDING, this.w - INLINE_PADDING * 2, 12)
                        .contains(mousex, mousey)) {
            this.dragPoint = new Point(mousex, mousey);
        }
    }

    @Override
    public boolean contains(int px, int py) {
        return this.handler != null && super.contains(px, py);
    }

    @Override
    public void onMouseUp(GuiContainer gui, int mousex, int mousey, int button) {
        this.dragPoint = null;
    }

    @Override
    public boolean mouseScrolled(GuiContainer gui, int mousex, int mousey, int scrolled) {
        return false;
    }

    @Override
    public void onMouseScrolled(GuiContainer gui, int mousex, int mousey, int scrolled) {}

    @Override
    public boolean handleClickExt(int mx, int my, int button) {
        return false;
    }

    @Override
    public void onMouseDragged(GuiContainer gui, int mousex, int mousey, int button, long heldTime) {
        if (this.dragPoint != null) {
            this.x -= this.dragPoint.x - mousex;
            this.y -= this.dragPoint.y - mousey;
            this.dragPoint.move(mousex, mousey);
        }
    }

    public void drawGuiPlaceholder(NEIRecipeWidget widget) {
        if (!showWidget) return;

        NEIClientUtils.gl2DRenderContext(() -> {

            // background
            GuiDraw.drawRect(
                    widget.x,
                    widget.y,
                    widget.w,
                    widget.h,
                    COLORS[widget.handlerRef.recipeIndex % COLORS.length]);

            // center cross
            GuiDraw.drawRect(widget.x + widget.w / 2 - 1, widget.y, 1, widget.h, 0x88ffffff);
            GuiDraw.drawRect(widget.x, widget.y + widget.h / 2 - 1, widget.w, 1, 0x88ffffff);

            // blue-line-top (grid-top)
            GuiDraw.drawRect(widget.x + 14, widget.y + 5, widget.w - 38, 1, 0xff0000aa);

            // blue-line-bottom {grid-bottom}
            GuiDraw.drawRect(widget.x + 14, widget.y + widget.h - 7, widget.w - 38, 1, 0xff0000aa);

            // blue-left (grid start)
            GuiDraw.drawRect(widget.x + 24, widget.y + (widget.h - 16) / 2, 1, 16, 0xffaa00aa);

            // purple (before favorite)
            GuiDraw.drawRect(
                    widget.x + Math.min(168, widget.w) - 27,
                    widget.y + widget.h - 6 - GuiRecipeButton.BUTTON_HEIGHT * 2 - 4,
                    GuiRecipeButton.BUTTON_WIDTH,
                    1,
                    0xffaa00aa);

            // green ()
            GuiDraw.drawRect(
                    widget.x + Math.min(168, widget.w) - 35,
                    widget.y + widget.h - 6 - GuiRecipeButton.BUTTON_HEIGHT - 3,
                    20,
                    1,
                    0xff00aa00);

            // purple space before bottom grid
            GuiDraw.drawRect(
                    widget.x + 14,
                    widget.y + widget.h - 7 - GuiRecipeButton.BUTTON_WIDTH / 2,
                    38,
                    1,
                    0xffaa00aa);

            if (widget.handlerRef.handler instanceof TemplateRecipeHandler handler) {
                int yShift = widget.getHandlerInfo().getYShift();

                for (RecipeTransferRect rect : handler.transferRects) {
                    GuiDraw.drawRect(
                            widget.x + rect.rect.x,
                            widget.y + yShift + rect.rect.y,
                            rect.rect.width,
                            rect.rect.height,
                            0x40ff0000);
                }
            }

        });
    }

    private static Map<String, String[]> patches = new HashMap<>();

    public static void loadHandlerInfoPatch() {

        ClientHandler.loadSettingsFile("handlers.patch", lines -> {

            for (String line : lines.collect(Collectors.toCollection(HashSet::new))) {
                final String[] parts = line.split(",");
                final String handler = parts[0];

                if (GuiRecipeTab.handlerMap.containsKey(handler)) {
                    final HandlerInfo info = GuiRecipeTab.handlerMap.get(handler);
                    final int yShift = intOrDefault(parts[1], info.getYShift());
                    final int height = intOrDefault(parts[2], info.getHeight());
                    final int width = intOrDefault(parts[3], info.getWidth());
                    final boolean isMultipleWidgetsAllowed = intOrDefault(
                            parts[4],
                            info.isMultipleWidgetsAllowed() ? 1 : 0) == 1;
                    final int order = intOrDefault(parts[5], NEIClientConfig.handlerOrdering.getOrDefault(handler, 0));
                    final boolean useCustomScroll = intOrDefault(parts[6], info.getUseCustomScroll() ? 1 : 0) == 1;

                    info.setYShift(yShift);
                    info.setHandlerDimensions(width, height, isMultipleWidgetsAllowed);
                    info.setUseCustomScroll(useCustomScroll);
                    NEIClientConfig.handlerOrdering.put(handler, order);
                }

                patches.put(handler, parts);
            }

        });

    }

    private static void saveHandlerInfoPatch() {
        final List<String> lines = new ArrayList<>();

        for (String[] line : patches.values()) {
            lines.add(String.join(",", line));
        }

        final File path = new File(NEIClientConfig.configDir, "handlers.patch");

        try (FileOutputStream output = new FileOutputStream(path)) {
            IOUtils.writeLines(lines, "\n", output, StandardCharsets.UTF_8);
        } catch (IOException e) {}
    }

    private static int intOrDefault(String str, int defaultValue) {
        if (str == null || str.equals("")) return defaultValue;
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static String getHandlerID(IRecipeHandler handler) {

        if (GuiRecipeTab.handlerMap.containsKey(handler.getHandlerId())) {
            return handler.getHandlerId();
        } else if (handler instanceof TemplateRecipeHandler) {
            return handler.getOverlayIdentifier();
        }

        return null;
    }

}
