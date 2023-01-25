package codechicken.nei.recipe;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.Widget;
import codechicken.nei.drawable.DrawableResource;
import codechicken.nei.event.NEIRegisterHandlerInfosEvent;
import codechicken.nei.guihook.GuiContainerManager;
import cpw.mods.fml.common.Loader;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.*;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.common.MinecraftForge;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

public abstract class GuiRecipeTab extends Widget {
    public static HandlerInfo DEFAULT_HANDLER_INFO = getDefaultHandlerInfo();
    public static HashMap<String, HandlerInfo> handlerMap = new HashMap<>();
    public static HashMap<String, HandlerInfo> handlerAdderFromIMC = new HashMap<>();
    public static Set<String> handlerRemoverFromIMC = new HashSet<>();

    private final GuiRecipe<?> guiRecipe;
    private final IRecipeHandler handler;
    private final String handlerName;
    private final String handlerID;

    private boolean selected;

    public abstract int getWidth();

    public abstract int getHeight();

    public abstract DrawableResource getSelectedTabImage();

    public abstract DrawableResource getUnselectedTabImage();

    protected abstract int getForegroundIconX();

    protected abstract int getForegroundIconY();

    public GuiRecipeTab(GuiRecipe<?> guiRecipe, IRecipeHandler handler, int x, int y) {
        super();
        this.x = x;
        this.y = y;
        this.w = getWidth();
        this.h = getHeight();
        this.handler = handler;
        this.handlerName = handler.getHandlerId();
        this.guiRecipe = guiRecipe;
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
        final DrawableResource image;
        if (selected) image = getSelectedTabImage();
        else image = getUnselectedTabImage();

        final int iconX = x + (w - image.getWidth()) / 2;
        final int iconY = y + (h - image.getHeight()) / 2;
        image.draw(iconX, iconY);
    }

    public void drawForeground(int mouseX, int mouseY) {
        final int iconX = getForegroundIconX();
        final int iconY = getForegroundIconY();

        final FontRenderer fontRenderer = GuiDraw.fontRenderer;
        final HandlerInfo handlerInfo = getHandlerInfo(handlerName, handlerID);
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
            String text = handler.getRecipeName();
            if (text != null && text.length() > 0) {
                if (text.length() > 2) {
                    text = text.substring(0, 2);
                }
            } else {
                text = "??";
            }

            int textCenterX = x + (int) (getWidth() / 2f);
            int textCenterY = y + (int) (getHeight() / 2f) - 3;
            int color = selected ? 0xffffa0 : 0xe0e0e0;
            fontRenderer.drawStringWithShadow(
                    text, textCenterX - (int) (fontRenderer.getStringWidth(text) / 2f), textCenterY, color);
            GL11.glColor4f(1, 1, 1, 1);
        }
    }

    public void addTooltips(List<String> tooltip) {
        tooltip.add(handler.getRecipeTabName().trim());

        String handlerMod = getHandlerMod(handlerName, handlerID);
        tooltip.add(EnumChatFormatting.BLUE + handlerMod);

        boolean shiftHeld = Keyboard.getEventKeyState()
                && (Keyboard.getEventKey() == Keyboard.KEY_LSHIFT || Keyboard.getEventKey() == Keyboard.KEY_RSHIFT);
        if (handlerMod.equals("Unknown") || shiftHeld) {
            tooltip.add("");
            tooltip.add("HandlerName: " + handlerName);
            tooltip.add("HandlerID: " + handlerID);
            tooltip.add("HandlerOrder: " + NEIClientConfig.getHandlerOrder(handler));
        }
    }

    public boolean onButtonPress(boolean rightclick) {
        int newIdx = guiRecipe.currenthandlers.indexOf(handler);
        if (newIdx == -1) return false;

        guiRecipe.setRecipePage(newIdx);
        return true;
    }

    public void setSelected(IRecipeHandler current) {
        selected = handler == current;
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

    public static String getHandlerMod(String name, String name2) {
        HandlerInfo info = getHandlerInfo(name, name2);
        if (info == null) return "Unknown";

        return info.getModName();
    }

    public static void loadHandlerInfo() {
        final boolean fromJar = NEIClientConfig.loadHandlersFromJar();
        NEIClientConfig.logger.info("Loading handler info from " + (fromJar ? "JAR" : "Config"));
        handlerMap.clear();
        URL handlerUrl = GuiRecipeTab.class.getResource("/assets/nei/csv/handlers.csv");

        URL url;
        if (fromJar) {
            url = handlerUrl;
            if (url == null) {
                NEIClientConfig.logger.info("Invalid URL for handlers csv.");
                return;
            }
        } else {
            File handlerFile = NEIClientConfig.handlerFile;
            if (!handlerFile.exists()) {
                NEIClientConfig.logger.info("Config file doesn't exist, creating");
                try {
                    assert handlerUrl != null;
                    ReadableByteChannel readableByteChannel = Channels.newChannel(handlerUrl.openStream());
                    FileOutputStream fileOutputStream = new FileOutputStream(handlerFile.getAbsoluteFile());
                    FileChannel fileChannel = fileOutputStream.getChannel();
                    fileChannel.transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
                } catch (IOException e) {
                    e.printStackTrace();
                    return;
                }
            }
            try {
                url = NEIClientConfig.handlerFile.toURI().toURL();
            } catch (MalformedURLException e) {
                NEIClientConfig.logger.info("Invalid URL for handlers csv (via config).");
                e.printStackTrace();
                return;
            }
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()))) {
            CSVParser csvParser = CSVFormat.EXCEL.withFirstRecordAsHeader().parse(reader);
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
                    final int imageHeight = intOrDefault(record.get("handlerHeight"), HandlerInfo.DEFAULT_HEIGHT);
                    final int imageWidth = intOrDefault(record.get("handlerWidth"), HandlerInfo.DEFAULT_WIDTH);
                    final int maxRecipesPerPage =
                            intOrDefault(record.get("maxRecipesPerPage"), HandlerInfo.DEFAULT_MAX_PER_PAGE);
                    info.setHandlerDimensions(imageHeight, imageWidth, maxRecipesPerPage);
                } catch (NumberFormatException ignored) {
                    NEIClientConfig.logger.info("Error setting handler dimensions for " + handler);
                }

                handlerMap.put(handler, info);
                NEIClientConfig.logger.info("Loaded " + handler);
            }
        } catch (Exception e) {
            NEIClientConfig.logger.info("Error parsing CSV");
            e.printStackTrace();
        }

        handlerMap.keySet().removeAll(handlerRemoverFromIMC);
        handlerMap.putAll(handlerAdderFromIMC);

        NEIClientConfig.logger.info("Sending {}", NEIRegisterHandlerInfosEvent.class.getSimpleName());
        MinecraftForge.EVENT_BUS.post(new NEIRegisterHandlerInfosEvent());
    }

    private static HandlerInfo getDefaultHandlerInfo() {
        final HandlerInfo info = new HandlerInfo("Unknown", "Unknown", "Unknown", false, "");
        info.setHandlerDimensions(
                HandlerInfo.DEFAULT_HEIGHT, HandlerInfo.DEFAULT_WIDTH, HandlerInfo.DEFAULT_MAX_PER_PAGE);
        return info;
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
