package codechicken.nei.recipe.debug;

import java.awt.Point;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import net.minecraft.client.gui.inventory.GuiContainer;

import org.apache.commons.io.IOUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import codechicken.lib.gui.GuiDraw;
import codechicken.lib.vec.Rectangle4i;
import codechicken.nei.ClientHandler;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.Widget;
import codechicken.nei.drawable.DrawableBuilder;
import codechicken.nei.drawable.DrawableResource;
import codechicken.nei.recipe.GuiRecipe;
import codechicken.nei.recipe.GuiRecipeButton;
import codechicken.nei.recipe.GuiRecipeTab;
import codechicken.nei.recipe.HandlerInfo;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.NEIRecipeWidget;
import codechicken.nei.recipe.TemplateRecipeHandler;
import codechicken.nei.recipe.TemplateRecipeHandler.RecipeTransferRect;
import codechicken.nei.scroll.ScrollContainer;

public class DebugHandlerWidget extends Widget {

    private static class HandlerInfoRecord {

        public String handlerKey;
        public int yShift;
        public int width;
        public int height;
        public boolean multiWidgets;
        public int order;
        public boolean useCustomScroll;
        public boolean showFavorites;
        public boolean showOverlay;

        private String csvLine;

        public HandlerInfoRecord(String handlerKey, HandlerInfo info) {
            this.handlerKey = handlerKey;
            this.yShift = info.getYShift();
            this.width = info.getWidth();
            this.height = info.getHeight();
            this.multiWidgets = info.isMultipleWidgetsAllowed();
            this.order = NEIClientConfig.handlerOrdering.getOrDefault(handlerKey, 0);
            this.useCustomScroll = info.getUseCustomScroll();
            this.showFavorites = info.getShowFavoritesButton();
            this.showOverlay = info.getShowOverlayButton();
            this.csvLine = toCsvLine();
        }

        public void applyTo(HandlerInfo info) {
            info.setYShift(this.yShift);
            info.setHandlerDimensions(this.width, this.height);
            info.setMultipleWidgetsAllowed(this.multiWidgets);
            info.setUseCustomScroll(this.useCustomScroll);
            info.setShowFavoritesButton(this.showFavorites);
            info.setShowOverlayButton(this.showOverlay);
            NEIClientConfig.handlerOrdering.put(this.handlerKey, this.order);
        }

        public String toCsvLine() {
            return String.join(
                    ",",
                    handlerKey,
                    String.valueOf(yShift),
                    String.valueOf(height),
                    String.valueOf(width),
                    String.valueOf(multiWidgets ? 1 : 0),
                    String.valueOf(order),
                    String.valueOf(useCustomScroll ? 1 : 0),
                    String.valueOf(showFavorites ? 1 : 0),
                    String.valueOf(showOverlay ? 1 : 0));
        }

        public boolean isUnmodified() {
            return this.csvLine.equals(this.toCsvLine());
        }
    }

    private static final int WINDOW_WIDTH = 176;
    private static final int LINE_HEIGHT = 16;
    private static final int INLINE_PADDING = 6;
    private static final int LABEL_WIDTH = 50;

    private static final String TOOLTIP_PREFIX = "debug.RecipeHandler.";
    private static final String HEADER = "HandlerId,YShift,Height,Width,MultiWidgets,Order,UseCustomScroll,ShowFavorites,ShowOverlay";
    private final int[] COLORS = new int[] { 0x2200FF00, 0x22FF0000, 0x220000FF, 0x2200FFFF, 0x22FF00FF, 0x22FFFF00 };

    public static DebugHandlerWidget instance = new DebugHandlerWidget();

    private ScrollContainer container = new ScrollContainer();
    private final Map<String, IUpdatableWidget> values = new LinkedHashMap<>();
    private Map<String, HandlerInfoRecord> patches = new HashMap<>();

    private IRecipeHandler handler;
    private HandlerInfo handlerInfo;
    private HandlerInfoRecord record;

    private Point dragPoint = null;
    private boolean showWidget = false;

    private final DrawableResource BG_TEXTURE = new DrawableBuilder(
            "nei:textures/gui/recipebg.png",
            0,
            0,
            176 + TRANSPARENCY_BORDER * 2,
            166 + TRANSPARENCY_BORDER * 2).build();
    private static final int BORDER_PADDING = 6;
    private static final int TRANSPARENCY_BORDER = 4;

    public DebugHandlerWidget() {
        this.w = WINDOW_WIDTH;
        this.x = 10;
        this.y = 50;
        this.z = 4;

        this.container.x = this.x + INLINE_PADDING;
        this.container.y = this.y + 5 + LINE_HEIGHT;
        this.container.w = this.w - INLINE_PADDING * 2;

        int y = 0;
        y = addInfoWidget("Name", y);
        y = addInfoWidget("Id", y);
        y = addInfoWidget("Key", y);
        y = addInfoWidget("HHack", y);
        y = addInfoWidget("ModName", y);
        y = addInfoWidget("ModId", y);
        y = addInfoWidget("Override", y);

        y = addTextFieldWidget("Order", 0, (field, oldText) -> {

            if (field.getInteger() != record.order) {
                record.order = field.getInteger();
                saveHandlerInfoPatch();
            }

        }, y);

        y = addTextFieldWidget("yShift", 0, (field, oldText) -> {

            if (field.getInteger() != handlerInfo.getYShift()) {
                handlerInfo.setYShift(field.getInteger());
                record.yShift = field.getInteger();
                saveHandlerInfoPatch();
            }

        }, y);

        y = addCheckboxWidget("MultiWidgets", button -> {
            handlerInfo.setMultipleWidgetsAllowed(button.value);
            record.multiWidgets = button.value;
            saveHandlerInfoPatch();
        }, y);

        final LabelWidget readonly = new LabelWidget(0xffffff, true);

        readonly.y = y;
        readonly.w = 57;
        readonly.x = this.w - INLINE_PADDING * 2 - readonly.w;
        this.values.put("height_readonly", readonly);

        y = addTextFieldWidget("Height", HandlerInfo.DEFAULT_HEIGHT, (field, oldText) -> {

            if (field.getInteger() != handlerInfo.getHeight()) {
                handlerInfo.setHandlerDimensions(handlerInfo.getWidth(), field.getInteger());
                record.height = field.getInteger();
                saveHandlerInfoPatch();
            }

        }, y);

        y = addTextFieldWidget("Width", HandlerInfo.DEFAULT_WIDTH, (field, oldText) -> {

            if (field.getInteger() != handlerInfo.getWidth()) {
                handlerInfo.setHandlerDimensions(field.getInteger(), handlerInfo.getHeight());
                record.width = field.getInteger();
                saveHandlerInfoPatch();
            }

        }, y);

        y = addCheckboxWidget("UseCustomScroll", button -> {
            handlerInfo.setUseCustomScroll(button.value);
            record.useCustomScroll = button.value;
            saveHandlerInfoPatch();
        }, y);

        y = addCheckboxWidget("ShowFavorites", button -> {
            handlerInfo.setShowFavoritesButton(button.value);
            record.showFavorites = button.value;
            saveHandlerInfoPatch();
        }, y);

        y = addCheckboxWidget("ShowOverlay", button -> {
            handlerInfo.setShowOverlayButton(button.value);
            record.showOverlay = button.value;
            saveHandlerInfoPatch();
        }, y);

        this.h = 10 + LINE_HEIGHT + y;
        this.container.h = this.h - INLINE_PADDING * 2 - LINE_HEIGHT;
    }

    private int addInfoWidget(String key, int y) {
        final LabelWidget label = new LabelWidget(0x888888, false);
        final LabelWidget value = new LabelWidget(0xffffff, true);

        label.y = value.y = y;
        label.w = LABEL_WIDTH;
        value.x = LABEL_WIDTH + 5;
        value.w = this.w - INLINE_PADDING * 2 - value.x;

        label.updateValue(NEIClientUtils.translate(TOOLTIP_PREFIX + key));

        this.container.addWidget(label);
        this.container.addWidget(value);
        this.values.put(key.toLowerCase(), value);

        return y + value.h + 1;
    }

    private int addTextFieldWidget(String key, int defaultValue, BiConsumer<IntegerField, String> onChange, int y) {
        final LabelWidget label = new LabelWidget(0x888888, false);
        final IntegerField value = new IntegerField(key, onChange, defaultValue);

        label.y = value.y = y;
        value.w = 60;
        value.x = this.w - INLINE_PADDING * 2 - value.w;
        label.w = value.x;

        label.updateValue(NEIClientUtils.translate(TOOLTIP_PREFIX + key));

        this.container.addWidget(label);
        this.container.addWidget(value);

        this.values.put(key.toLowerCase(), value);

        return y + value.h + 1;
    }

    private int addCheckboxWidget(String key, Consumer<CheckboxWidget> onChange, int y) {
        final LabelWidget label = new LabelWidget(0x888888, false);
        final CheckboxWidget value = new CheckboxWidget(onChange);

        label.y = value.y = y;
        value.w = 58;
        value.x = this.w - INLINE_PADDING * 2 - value.w - 1;
        label.w = value.x;

        label.updateValue(NEIClientUtils.translate(TOOLTIP_PREFIX + key));

        this.container.addWidget(label);
        this.container.addWidget(value);
        this.values.put(key.toLowerCase(), value);

        return y + value.h + 1;
    }

    @Override
    public void draw(int mx, int my) {

        if (this.handler != null) {
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

            this.container.draw(mx, my);

            GL11.glScaled(1, 1, 1 / 2f);
        }
    }

    @Override
    public void update() {
        final GuiContainer gui = NEIClientUtils.getGuiContainer();

        if (this.showWidget && gui instanceof GuiRecipe recipe) {

            if (this.handler != recipe.getHandler()) {
                this.handler = recipe.getHandler();
                this.handlerInfo = GuiRecipeTab.getHandlerInfo(handler);
                this.record = this.patches.computeIfAbsent(
                        getHandlerID(handler),
                        handlerKey -> new HandlerInfoRecord(handlerKey, handlerInfo));

                final boolean isHeightHackApplied = NEIClientConfig.heightHackHandlerRegex.stream()
                        .map(pattern -> pattern.matcher(this.handler.getHandlerId())).anyMatch(Matcher::matches);

                this.values.get("name").updateValue(this.handler.getRecipeName());
                this.values.get("id").updateValue(this.handler.getOverlayIdentifier());
                this.values.get("key").updateValue(this.handler.getHandlerId());
                this.values.get("hhack").updateValue(
                        isHeightHackApplied ? NEIClientUtils.translate("debug.RecipeHandler.yes")
                                : NEIClientUtils.translate("debug.RecipeHandler.no"));
                this.values.get("modname").updateValue(this.handlerInfo.getModName());
                this.values.get("modid").updateValue(this.handlerInfo.getModId());

                this.values.get("order").updateValue(String.valueOf(this.record.order));
                this.values.get("yshift").updateValue(String.valueOf(this.record.yShift));
                this.values.get("height").updateValue(String.valueOf(this.record.height));
                this.values.get("height_readonly").updateValue(String.valueOf(this.record.height));
                this.values.get("width").updateValue(String.valueOf(this.record.width));

                this.values.get("multiwidgets").updateValue(String.valueOf(this.record.multiWidgets));
                this.values.get("usecustomscroll").updateValue(String.valueOf(this.record.useCustomScroll));

                this.values.get("showfavorites").updateValue(String.valueOf(this.record.showFavorites));
                this.values.get("showoverlay").updateValue(String.valueOf(this.record.showOverlay));

                this.values.get("override").updateValue(
                        this.record.isUnmodified() ? NEIClientUtils.translate("debug.RecipeHandler.no")
                                : NEIClientUtils.translate("debug.RecipeHandler.yes"));

                this.container.removeIf(
                        widget -> this.values.get("height") == widget || this.values.get("height_readonly") == widget);
                if (handler.getRecipeHeight(0) > 0) {
                    this.container.addWidget((Widget) this.values.get("height_readonly"));
                } else {
                    this.container.addWidget((Widget) this.values.get("height"));
                }

            }

        } else {
            this.handler = null;
            this.record = null;
            this.handlerInfo = null;
            this.showWidget = false;
        }

        this.container.update();
    }

    @Override
    public List<String> handleTooltip(int mx, int my, List<String> tooltip) {
        return this.handler != null ? this.container.handleTooltip(mx, my, tooltip) : tooltip;
    }

    @Override
    public Map<String, String> handleHotkeys(int mousex, int mousey, Map<String, String> hotkeys) {
        return this.handler != null ? this.container.handleHotkeys(mousex, mousey, hotkeys) : hotkeys;
    }

    @Override
    public boolean handleKeyPress(int keyID, char keyChar) {

        if (NEIClientConfig.getBooleanSetting("inventory.guirecipe.handlerInfo") && keyID == Keyboard.KEY_D
                && NEIClientUtils.shiftKey()) {
            this.showWidget = !this.showWidget;
            return true;
        } else if (this.handler != null) {

            if (this.container.handleKeyPress(keyID, keyChar)) {
                return true;
            }

            this.container.lastKeyTyped(keyID, keyChar);

            return this.container.isFocused();
        }

        return false;
    }

    @Override
    public void onGuiClick(int mx, int my) {
        if (this.handler != null) {
            this.container.onGuiClick(mx, my);
        }
    }

    @Override
    public boolean handleClick(int mx, int my, int button) {

        if (this.handler != null) {

            if (new Rectangle4i(this.x + INLINE_PADDING, this.y + BORDER_PADDING, this.w - INLINE_PADDING * 2, 12)
                    .contains(mx, my)) {
                this.dragPoint = new Point(mx, my);
            }

            this.container.handleClick(mx, my, button);
        }

        return this.handler != null;
    }

    @Override
    public boolean contains(int px, int py) {
        return this.handler != null && super.contains(px, py);
    }

    @Override
    public void mouseUp(int mx, int my, int button) {
        this.dragPoint = null;

        if (this.handler != null) {
            this.container.mouseUp(mx, my, button);
        }
    }

    @Override
    public boolean onMouseWheel(int scrolled, int mousex, int mousey) {

        if (this.handler != null && contains(mousex, mousey)) {
            this.container.onMouseWheel(scrolled, mousex, mousey);
            return true;
        }

        return false;
    }

    @Override
    public boolean handleClickExt(int mx, int my, int button) {
        return this.handler != null && this.container.handleClickExt(mx, my, button);
    }

    @Override
    public void mouseDragged(int mx, int my, int button, long heldTime) {
        if (this.dragPoint != null) {
            this.x -= this.dragPoint.x - mx;
            this.y -= this.dragPoint.y - my;

            this.container.y = this.y + INLINE_PADDING + LINE_HEIGHT;
            this.container.x = this.x + INLINE_PADDING;

            this.dragPoint.move(mx, my);
        }
    }

    public void drawGuiPlaceholder(NEIRecipeWidget widget) {
        if (!this.showWidget) return;

        NEIClientUtils.gl2DRenderContext(() -> {

            // background
            GuiDraw.drawRect(
                    widget.x,
                    widget.y,
                    widget.w,
                    widget.h,
                    COLORS[widget.getRecipeHandlerRef().recipeIndex % COLORS.length]);

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

            if (widget.getRecipeHandlerRef().handler instanceof TemplateRecipeHandler handler) {
                int yShift = widget.getHandlerInfo().getYShift();

                for (RecipeTransferRect rect : handler.transferRects) {
                    GuiDraw.drawRect(
                            widget.x + rect.getRect().x,
                            widget.y + yShift + rect.getRect().y,
                            rect.getRect().width,
                            rect.getRect().height,
                            0x40ff0000);
                }
            }

        });
    }

    public void loadHandlerInfoPatch() {

        ClientHandler.loadSettingsFile("handlers.patch", lines -> {

            for (String line : lines.collect(Collectors.toCollection(HashSet::new))) {
                if (HEADER.equals(line)) continue;
                final String[] parts = Arrays.copyOf((line + ",null,null,null,null,null,null,null,null").split(","), 9);
                final String handler = parts[0];

                if (GuiRecipeTab.handlerMap.containsKey(handler)) {
                    final HandlerInfo info = GuiRecipeTab.handlerMap.get(handler);
                    final HandlerInfoRecord record = patches
                            .computeIfAbsent(handler, handlerKey -> new HandlerInfoRecord(handler, info));

                    record.yShift = intOrDefault(parts[1], info.getYShift());
                    record.height = intOrDefault(parts[2], info.getHeight());
                    record.width = intOrDefault(parts[3], info.getWidth());
                    record.multiWidgets = intOrDefault(parts[4], info.isMultipleWidgetsAllowed() ? 1 : 0) == 1;
                    record.order = intOrDefault(parts[5], NEIClientConfig.handlerOrdering.getOrDefault(handler, 0));
                    record.useCustomScroll = intOrDefault(parts[6], info.getUseCustomScroll() ? 1 : 0) == 1;
                    record.showFavorites = intOrDefault(parts[7], info.getShowFavoritesButton() ? 1 : 0) == 1;
                    record.showOverlay = intOrDefault(parts[8], info.getShowOverlayButton() ? 1 : 0) == 1;

                    record.applyTo(info);
                }

            }

        });

    }

    private void saveHandlerInfoPatch() {
        final List<String> lines = new ArrayList<>();
        lines.add(HEADER);

        for (HandlerInfoRecord record : patches.values()) {
            if (!record.csvLine.equals(record.toCsvLine())) {
                lines.add(record.toCsvLine());
            }
        }

        final File path = new File(NEIClientConfig.configDir, "handlers.patch");

        try (FileOutputStream output = new FileOutputStream(path)) {
            IOUtils.writeLines(lines, "\n", output, StandardCharsets.UTF_8);
        } catch (IOException e) {}

        if (this.record != null) {
            this.values.get("override").updateValue(
                    this.record.isUnmodified() ? NEIClientUtils.translate("debug.RecipeHandler.no")
                            : NEIClientUtils.translate("debug.RecipeHandler.yes"));
        }

        if (NEIClientUtils.getGuiContainer() instanceof GuiRecipe<?>recipe) {
            recipe.forceRefreshPage();
        }
    }

    private int intOrDefault(String str, int defaultValue) {
        if (str == null || str.isEmpty()) return defaultValue;
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String getHandlerID(IRecipeHandler handler) {

        if (GuiRecipeTab.handlerMap.containsKey(handler.getHandlerId())) {
            return handler.getHandlerId();
        } else if (handler instanceof TemplateRecipeHandler) {
            return handler.getOverlayIdentifier();
        }

        return handler.getHandlerId();
    }

}
