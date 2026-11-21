package codechicken.nei.recipe;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.common.MinecraftForge;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.ColorUtils;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.Widget;
import codechicken.nei.drawable.DrawableBuilder;
import codechicken.nei.drawable.DrawableResource;
import codechicken.nei.event.NEIRegisterHandlerInfosEvent;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.recipe.debug.DebugHandlerWidget;
import cpw.mods.fml.common.Loader;

public abstract class GuiRecipeTab extends Widget {

    public static HandlerInfo DEFAULT_HANDLER_INFO = getDefaultHandlerInfo();
    public static HashMap<String, HandlerInfo> handlerMap = new HashMap<>();
    public static HashMap<String, HandlerInfo> handlerAdderFromIMC = new HashMap<>();
    public static Set<String> handlerRemoverFromIMC = new HashSet<>();

    protected final IRecipeHandler handler;
    protected final String handlerName;
    protected final String handlerID;

    public static final int TAB_WIDTH = 24;
    public static final int TAB_HEIGHT = 24;
    private static final DrawableResource selectedTabImage = new DrawableBuilder(
            "nei:textures/nei_tabbed_sprites.png",
            0,
            16,
            24,
            24).build();
    private static final DrawableResource unselectedTabImage = new DrawableBuilder(
            "nei:textures/nei_tabbed_sprites.png",
            24,
            16,
            24,
            24).build();

    private boolean selected;

    public GuiRecipeTab(IRecipeHandler handler, int x, int y) {
        super();
        this.x = x;
        this.y = y;
        this.w = TAB_WIDTH;
        this.h = TAB_HEIGHT;
        this.handler = handler;
        this.handlerName = handler.getHandlerId();
        this.selected = false;

        if (handler instanceof TemplateRecipeHandler) {
            handlerID = handler.getOverlayIdentifier();
        } else {
            handlerID = null;
        }
    }

    @Override
    public void draw(int mouseX, int mouseY) {
        drawBackground(mouseX, mouseY);
        drawForeground(mouseX, mouseY);
    }

    public void drawBackground(int mouseX, int mouseY) {
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glColor4f(1, 1, 1, 1);
        final DrawableResource image = this.selected ? selectedTabImage : unselectedTabImage;
        image.draw(this.x, this.y);
    }

    public void drawForeground(int mouseX, int mouseY) {
        final int iconX = this.x + 4;
        final int iconY = this.y + 4;

        final FontRenderer fontRenderer = GuiDraw.fontRenderer;
        final HandlerInfo handlerInfo = getHandlerInfo(this.handlerName, this.handlerID);
        final DrawableResource icon = handlerInfo != null ? handlerInfo.getImage() : null;
        final ItemStack itemStack = handlerInfo != null ? handlerInfo.getItemStack() : null;

        if (icon != null) {
            icon.draw(iconX + 1, iconY + 1);
        } else if (itemStack != null) {
            boolean isEnabled = GL11.glIsEnabled(GL12.GL_RESCALE_NORMAL);
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);
            GuiContainerManager.drawItems.zLevel += 100;
            GuiContainerManager.drawItem(iconX, iconY, itemStack);
            GuiContainerManager.drawItems.zLevel -= 100;
            if (!isEnabled) GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        } else {
            // Text fallback
            String text = this.handler.getRecipeName();
            if (text != null && text.length() > 0) {
                if (text.length() > 2) {
                    text = text.substring(0, 2);
                }
            } else {
                text = "??";
            }

            int textCenterX = this.x + (int) (this.w / 2f);
            int textCenterY = this.y + (int) (this.h / 2f) - 3;
            int color = this.selected ? ColorUtils.buttonLabelHover.getColor()
                    : ColorUtils.buttonLabelNormal.getColor();
            fontRenderer.drawStringWithShadow(
                    text,
                    textCenterX - (int) (fontRenderer.getStringWidth(text) / 2f),
                    textCenterY,
                    color);
            GL11.glColor4f(1, 1, 1, 1);
        }
    }

    public void addTooltips(List<String> tooltip) {
        tooltip.add(handler.getRecipeTabName().trim());
        String handlerMod = getHandlerMod(handlerName, handlerID);

        if (handlerMod.equals("Unknown") || NEIClientUtils.shiftKey()) {
            tooltip.add("");
            tooltip.add(EnumChatFormatting.GRAY + "HandlerName: " + handlerName);
            tooltip.add(EnumChatFormatting.GRAY + "HandlerID: " + handlerID);
            tooltip.add(EnumChatFormatting.GRAY + "HandlerOrder: " + NEIClientConfig.getHandlerOrder(handler));
        }

        tooltip.add(EnumChatFormatting.BLUE + handlerMod);
    }

    public abstract boolean onButtonPress(boolean rightclick);

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public static HandlerInfo getHandlerInfo(IRecipeHandler handler) {
        final String handlerID;

        if (handler instanceof TemplateRecipeHandler) {
            handlerID = handler.getOverlayIdentifier();
        } else {
            handlerID = null;
        }

        HandlerInfo info = getHandlerInfo(handler.getHandlerId(), handlerID);

        if (info == null) return GuiRecipeTab.DEFAULT_HANDLER_INFO;

        return info;
    }

    public static HandlerInfo getHandlerInfo(String name, String name2) {
        HandlerInfo res = handlerMap.get(name);
        if (res == null) res = handlerMap.get(name2);

        return res;
    }

    public static String getHandlerTitle(String handlerName) {
        String title = findRecipeName(GuiCraftingRecipe.craftinghandlers, handlerName);
        if (title == null) title = findRecipeName(GuiCraftingRecipe.serialCraftingHandlers, handlerName);
        if (title == null) title = findRecipeName(GuiUsageRecipe.usagehandlers, handlerName);
        if (title == null) title = findRecipeName(GuiUsageRecipe.serialUsageHandlers, handlerName);

        return title != null ? title : handlerName;
    }

    private static String findRecipeName(List<? extends IRecipeHandler> handlers, String handlerName) {
        for (int index = 0; index < handlers.size(); index++) {
            final IRecipeHandler handler = handlers.get(index);
            if (handlerName.equals(getHandlerInfo(handler).getHandlerName())) return handler.getRecipeName().trim();
        }

        return null;
    }

    public static String getHandlerMod(String name, String name2) {
        HandlerInfo info = getHandlerInfo(name, name2);
        if (info == null) return "Unknown";

        return info.getModName();
    }

    public static void loadHandlerInfo() {
        final boolean fromJar = !NEIClientConfig.handlerFile.exists();
        NEIClientConfig.logger.info("Loading handler info from " + (fromJar ? "JAR" : "Config"));
        handlerMap.clear();

        URL url;
        try {
            url = fromJar ? GuiRecipeTab.class.getResource("/assets/nei/csv/handlers.csv")
                    : NEIClientConfig.handlerFile.toURI().toURL();
        } catch (MalformedURLException e) {
            NEIClientConfig.logger.info("Invalid URL for handlers csv (via config).");
            e.printStackTrace();
            return;
        }

        if (url == null) {
            NEIClientConfig.logger.info("Invalid URL for handlers csv.");
            return;
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
            CSVParser csvParser = CSVFormat.EXCEL.builder().setHeader().setSkipHeaderRecord(true).build().parse(reader);
            for (CSVRecord record : csvParser) {
                final String handler = record.get("handler");
                final String modName = record.get("modName");
                final String modId = record.get("modId");
                final boolean requiresMod = Boolean.parseBoolean(record.get("modRequired"));
                final String excludedModId = record.get("excludedModId");

                if (requiresMod && !Loader.isModLoaded(modId)) continue;
                if (excludedModId != null && Loader.isModLoaded(excludedModId)) continue;

                HandlerInfo info = new HandlerInfo(handler, modName, modId, requiresMod, excludedModId);
                final String imageResource = record.get("imageResource");
                if (imageResource != null && !imageResource.equals("")) {
                    info.setImage(
                            imageResource,
                            Integer.parseInt(record.get("imageX")),
                            Integer.parseInt(record.get("imageY")),
                            Integer.parseInt(record.get("imageWidth")),
                            Integer.parseInt(record.get("imageHeight")));
                }
                if (!info.hasImageOrItem()) {
                    final String itemName = record.get("itemName");
                    if (itemName != null && !itemName.equals("")) {
                        info.setItem(itemName, record.get("nbtInfo"));
                    }
                }
                final String yShift = record.get("yShift");
                if (yShift != null && !yShift.equals("")) info.setYShift(Integer.parseInt(yShift));

                try {
                    final int handlerHeight = intOrDefault(record.get("handlerHeight"), HandlerInfo.DEFAULT_HEIGHT);
                    final int handlerWidth = intOrDefault(record.get("handlerWidth"), HandlerInfo.DEFAULT_WIDTH);
                    final boolean multipleWidgetsAllowed = intOrDefault(record.get("maxRecipesPerPage"), 1) > 1;
                    info.setHandlerDimensions(handlerWidth, handlerHeight);
                    info.setMultipleWidgetsAllowed(multipleWidgetsAllowed);
                } catch (NumberFormatException ignored) {
                    NEIClientConfig.logger.info("Error setting handler dimensions for " + handler);
                }

                final boolean allowOverflow = booleanOrDefault(record, "useCustomScroll", false);
                info.setAllowOverflowX(booleanOrDefault(record, "allowOverflowX", true));
                info.setAllowOverflowY(booleanOrDefault(record, "allowOverflowY", allowOverflow));
                info.setShowFavoritesButton(booleanOrDefault(record, "showFavorites", true));
                info.setShowOverlayButton(booleanOrDefault(record, "showOverlay", true));
                info.setShowBadge(booleanOrDefault(record, "showBadge", false));
                handlerMap.put(handler, info);
                NEIClientConfig.logger.info("Loaded " + handler);
            }
        } catch (Exception e) {
            NEIClientConfig.logger.info("Error parsing CSV");
            e.printStackTrace();
        }

        handlerMap.keySet().removeAll(handlerRemoverFromIMC);
        handlerMap.putAll(handlerAdderFromIMC);

        if (!handlerAdderFromIMC.isEmpty()) {
            // If we're getting an IMC message, it probably means it should match
            Set<String> handlerIds = new HashSet<>(handlerMap.keySet());
            handlerAdderFromIMC.keySet().forEach(handlerName -> {
                if (!handlerIds.contains(handlerName)) {
                    NEIClientConfig.logger.warn("Could not find a registered handlerID that matches " + handlerName);
                    handlerIds.forEach(handler -> {
                        if (handler.equalsIgnoreCase(handlerName)) {
                            NEIClientConfig.logger.warn("  -- Did you mean: " + handler);
                        }
                    });
                }
            });
        }
        NEIClientConfig.logger.info("Sending {}", NEIRegisterHandlerInfosEvent.class.getSimpleName());
        MinecraftForge.EVENT_BUS.post(new NEIRegisterHandlerInfosEvent());
        DebugHandlerWidget.instance.loadHandlerInfoPatch();
    }

    private static HandlerInfo getDefaultHandlerInfo() {
        final HandlerInfo info = new HandlerInfo("Unknown", "Unknown", "Unknown", false, "");
        info.setHandlerDimensions(HandlerInfo.DEFAULT_WIDTH, HandlerInfo.DEFAULT_HEIGHT);
        return info;
    }

    private static boolean booleanOrDefault(CSVRecord record, String name, boolean defaultValue) {
        final String str = record.isSet(name) ? record.get(name) : null;
        return str == null || str.isEmpty() ? defaultValue : Boolean.parseBoolean(str);
    }

    private static int intOrDefault(String str, int defaultValue) {
        if (str == null || str.equals("")) return defaultValue;
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
