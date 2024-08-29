package codechicken.nei;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiErrorScreen;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSelectWorld;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

import org.apache.commons.io.IOUtils;

import com.google.common.collect.Lists;

import codechicken.core.ClassDiscoverer;
import codechicken.core.ClientUtils;
import codechicken.core.GuiModListScroll;
import codechicken.lib.packet.PacketCustom;
import codechicken.nei.api.API;
import codechicken.nei.api.IConfigureNEI;
import codechicken.nei.api.ItemInfo;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.recipe.GuiRecipeTab;
import codechicken.nei.recipe.StackInfo;
import cpw.mods.fml.client.CustomModLoadingErrorDisplayException;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.Phase;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class ClientHandler {

    private static String[] defaultHandlerOrdering = {
            "# Each line in this file should either be a comment (starts with '#') or an ordering.",
            "# Ordering lines are <handler ID>,<ordering number>.",
            "# Handlers will be sorted in order of number ascending, so smaller numbers first.",
            "# Any handlers that are missing from this file will be assigned to 0.", "# Negative numbers are fine.",
            "# If you delete this file, it will be regenerated with all registered handler IDs.", };
    private static ClientHandler instance;

    private ArrayList<EntityItem> SMPmagneticItems = new ArrayList<>();
    private World lastworld;
    private GuiScreen lastGui;

    public void addSMPMagneticItem(int i, World world) {
        WorldClient cworld = (WorldClient) world;
        Entity e = cworld.getEntityByID(i);
        if (!(e instanceof EntityItem)) {
            return;
        }
        SMPmagneticItems.add((EntityItem) e);
    }

    private void updateMagnetMode(World world, EntityPlayerSP player) {
        if (!NEIClientConfig.getMagnetMode()) return;

        float distancexz = 16;
        float distancey = 8;
        double maxspeedxz = 0.5;
        double maxspeedy = 0.5;
        double speedxz = 0.05;
        double speedy = 0.07;

        List<EntityItem> items;
        if (world.isRemote) {
            items = SMPmagneticItems;
        } else {
            items = world.getEntitiesWithinAABB(
                    EntityItem.class,
                    player.boundingBox.expand(distancexz, distancey, distancexz));
        }
        for (Iterator<EntityItem> iterator = items.iterator(); iterator.hasNext();) {
            EntityItem item = iterator.next();

            if (item.delayBeforeCanPickup > 0) continue;
            if (item.isDead && world.isRemote) iterator.remove();

            if (!NEIClientUtils.canItemFitInInventory(player, item.getEntityItem())) continue;

            double dx = player.posX - item.posX;
            double dy = player.posY + player.getEyeHeight() - item.posY;
            double dz = player.posZ - item.posZ;
            double absxz = Math.sqrt(dx * dx + dz * dz);
            double absy = Math.abs(dy);
            if (absxz > distancexz) {
                continue;
            }

            if (absxz > 1) {
                dx /= absxz;
                dz /= absxz;
            }

            if (absy > 1) {
                dy /= absy;
            }

            double vx = item.motionX + speedxz * dx;
            double vy = item.motionY + speedy * dy;
            double vz = item.motionZ + speedxz * dz;

            double absvxz = Math.sqrt(vx * vx + vz * vz);
            double absvy = Math.abs(vy);

            double rationspeedxz = absvxz / maxspeedxz;
            if (rationspeedxz > 1) {
                vx /= rationspeedxz;
                vz /= rationspeedxz;
            }

            double rationspeedy = absvy / maxspeedy;
            if (rationspeedy > 1) {
                vy /= rationspeedy;
            }

            if (absvxz < 0.2 && absxz < 0.2 && world.isRemote) {
                item.setDead();
            }

            item.setVelocity(vx, vy, vz);
        }
    }

    public static void preInit() {
        loadSerialHandlers();
        loadHiddenItems();
        loadHeightHackHandlers();
        loadHiddenHandlers();
        loadEnableAutoFocus();
        ItemInfo.preInit();
        StackInfo.loadGuidFilters();
    }

    public static void loadSettingsFile(String resource, Consumer<Stream<String>> callback) {
        loadSettingsFile(resource, (file, writer) -> {
            String folder = resource.substring(resource.lastIndexOf(".") + 1);
            URL defaultResource = ClientHandler.class.getResource("/assets/nei/" + folder + "/" + resource);

            if (defaultResource != null) {
                try {
                    IOUtils.copy(defaultResource.openStream(), writer);
                } catch (IOException e) {}
            }
        }, callback);
    }

    public static void loadSettingsFile(String resource, BiConsumer<File, FileWriter> createDefault,
            Consumer<Stream<String>> callback) {
        File file = new File(NEIClientConfig.configDir, resource);

        if (!file.exists()) {
            try (FileWriter writer = new FileWriter(file)) {
                NEIClientConfig.logger.info("Creating default '{}' {}", resource, file);
                createDefault.accept(file, writer);
            } catch (IOException e) {
                NEIClientConfig.logger.error("Failed to save default '{}' to file {}", resource, file, e);
            }
        }

        try (FileReader reader = new FileReader(file)) {
            NEIClientConfig.logger.info("Loading '{}' file {}", resource, file);
            callback.accept(
                    IOUtils.readLines(reader).stream().filter(line -> !line.startsWith("#") && !line.trim().isEmpty()));
        } catch (IOException e) {
            NEIClientConfig.logger.error("Failed to load '{}' file {}", resource, file, e);
        }
    }

    public static void loadSerialHandlers() {
        loadSettingsFile(
                "serialhandlers.cfg",
                lines -> NEIClientConfig.serialHandlers = lines.collect(Collectors.toCollection(HashSet::new)));
    }

    public static void loadHeightHackHandlers() {
        loadSettingsFile(
                "heighthackhandlers.cfg",
                lines -> NEIClientConfig.heightHackHandlerRegex = lines.map(Pattern::compile)
                        .collect(Collectors.toCollection(HashSet::new)));
    }

    public static void loadHiddenHandlers() {
        loadSettingsFile(
                "hiddenhandlers.cfg",
                lines -> NEIClientConfig.hiddenHandlers = lines.collect(Collectors.toCollection(HashSet::new)));
    }

    public static void loadEnableAutoFocus() {
        loadSettingsFile(
                "enableautofocus.cfg",
                lines -> AutoFocusWidget.enableAutoFocusPrefixes = lines
                        .collect(Collectors.toCollection(ArrayList::new)));
    }

    public static void loadHiddenItems() {
        loadSettingsFile("hiddenitems.cfg", lines -> lines.forEach(API::hideItem));
    }

    public static void load() {
        instance = new ClientHandler();

        GuiModListScroll.register("NotEnoughItems");
        PacketCustom.assignHandler(NEICPH.channel, new NEICPH());
        FMLCommonHandler.instance().bus().register(instance);
        MinecraftForge.EVENT_BUS.register(instance);

        API.registerHighlightHandler(new DefaultHighlightHandler(), ItemInfo.Layout.HEADER);
        HUDRenderer.load();
        WorldOverlayRenderer.load();
    }

    public static void postInit() {
        loadHandlerOrdering();
        loadPluginsList();
        GuiContainerManager.registerReloadResourceListener();
    }

    public static void loadHandlerOrdering() {
        final String COMMA_DELIMITER = ",";

        loadSettingsFile("handlerordering.csv", (file, writer) -> {
            List<String> toWrite = Lists.newArrayList(defaultHandlerOrdering);
            GuiRecipeTab.handlerMap.keySet().stream().sorted()
                    .forEach(handlerId -> toWrite.add(String.format("%s,0", handlerId)));
            try {
                IOUtils.writeLines(toWrite, "\n", writer);
            } catch (IOException e) {}
        }, lines -> lines.map(line -> line.split(COMMA_DELIMITER)).filter(parts -> parts.length == 2).forEach(parts -> {
            String handlerId = parts[0];
            int ordering = Integer.getInteger(parts[1], 0);
            NEIClientConfig.handlerOrdering.put(handlerId, ordering);
        }));
    }

    public static void loadPluginsList() {
        final ClassDiscoverer classDiscoverer = new ClassDiscoverer(
                test -> test.startsWith("NEI") && test.endsWith("Config.class"),
                IConfigureNEI.class);

        NEIClientConfig.pluginsList.addAll(classDiscoverer.findClasses());
    }

    @SubscribeEvent
    public void tickEvent(TickEvent.ClientTickEvent event) {
        if (event.phase == Phase.END) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld != null) {
            loadWorld(mc.theWorld, false);

            if (!NEIClientConfig.isEnabled()) return;

            /*
             * NEI plugins are loaded in a separate thread. Make sure loading them has finished otherwise a CME might
             * occur in KeyManager if a plugin calls API.addKeyBind() during initialization.
             */
            if (NEIClientConfig.isLoaded()) KeyManager.tickKeyStates();

            NEIController.updateUnlimitedItems(mc.thePlayer.inventory);
            if (mc.currentScreen == null) NEIController.processCreativeCycling(mc.thePlayer.inventory);

            updateMagnetMode(mc.theWorld, mc.thePlayer);
        }

        GuiScreen gui = mc.currentScreen;
        if (gui != lastGui) {
            if (gui instanceof GuiMainMenu) lastworld = null;
            else if (gui instanceof GuiSelectWorld) NEIClientConfig.reloadSaves();
            else if (gui == null) {
                /* prevent WorldClient reference being held in the Gui */
                NEIController.manager = null;
            }
        }
        lastGui = gui;
    }

    @SubscribeEvent
    public void tickEvent(TickEvent.RenderTickEvent event) {
        if (event.phase == Phase.END && NEIClientConfig.isEnabled()) HUDRenderer.renderOverlay();
    }

    @SubscribeEvent
    public void renderLastEvent(RenderWorldLastEvent event) {
        if (NEIClientConfig.isEnabled()) WorldOverlayRenderer.render(event.partialTicks);
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.world == mc.theWorld) {
            NEIClientConfig.unloadWorld();
        }
    }

    public void loadWorld(World world, boolean fromServer) {
        if (world != lastworld) {
            SMPmagneticItems.clear();
            WorldOverlayRenderer.reset();

            if (!fromServer) {
                NEIClientConfig.setHasSMPCounterPart(false);
                NEIClientConfig.setInternalEnabled(false);

                if (!Minecraft.getMinecraft().isSingleplayer()) // wait for server to initiate in singleplayer
                    NEIClientConfig.loadWorld("remote/" + ClientUtils.getServerIP().replace(':', '~'));

                ItemMobSpawner.clearEntityReferences(world);
            }

            lastworld = world;
        }
    }

    public static ClientHandler instance() {
        return instance;
    }

    public static RuntimeException throwCME(final String message) {
        final GuiScreen errorGui = new GuiErrorScreen(null, null) {

            @Override
            public void handleMouseInput() {}

            @Override
            public void handleKeyboardInput() {}

            @Override
            public void drawScreen(int par1, int par2, float par3) {
                drawDefaultBackground();
                String[] s_msg = message.split("\n");
                for (int i = 0; i < s_msg.length; ++i)
                    drawCenteredString(fontRendererObj, s_msg[i], width / 2, height / 3 + 12 * i, 0xFFFFFFFF);
            }
        };

        CustomModLoadingErrorDisplayException e = new CustomModLoadingErrorDisplayException() {

            private static final long serialVersionUID = -5593387489666663375L;

            @Override
            public void initGui(GuiErrorScreen errorScreen, FontRenderer fontRenderer) {
                Minecraft.getMinecraft().displayGuiScreen(errorGui);
            }

            @Override
            public void drawScreen(GuiErrorScreen errorScreen, FontRenderer fontRenderer, int mouseRelX, int mouseRelY,
                    float tickTime) {}
        };
        throw e;
    }
}
