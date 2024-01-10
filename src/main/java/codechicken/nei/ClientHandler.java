package codechicken.nei;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.IOUtils;

import com.google.common.collect.Lists;

import codechicken.core.ClientUtils;
import codechicken.core.GuiModListScroll;
import codechicken.lib.packet.PacketCustom;
import codechicken.nei.api.API;
import codechicken.nei.api.ItemInfo;
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
        loadHeightHackHandlers();
        loadHiddenHandlers();
        ItemInfo.preInit();
        StackInfo.loadGuidFilters();
    }

    public static void loadSerialHandlers() {
        File file = NEIClientConfig.serialHandlersFile;
        if (!file.exists()) {
            try (FileWriter writer = new FileWriter(file)) {
                NEIClientConfig.logger.info("Creating default serial handlers list {}", file);
                URL defaultSerialHandlersResource = ClientHandler.class
                        .getResource("/assets/nei/cfg/serialhandlers.cfg");
                if (defaultSerialHandlersResource != null) {
                    IOUtils.copy(defaultSerialHandlersResource.openStream(), writer);
                }
            } catch (IOException e) {
                NEIClientConfig.logger.error("Failed to save default serial handlers list to file {}", file, e);
            }
        }
        try (FileReader reader = new FileReader(file)) {
            NEIClientConfig.logger.info("Loading serial handlers from file {}", file);
            NEIClientConfig.serialHandlers = IOUtils.readLines(reader).stream().filter((line) -> !line.startsWith("#"))
                    .collect(Collectors.toCollection(HashSet::new));
        } catch (IOException e) {
            NEIClientConfig.logger.error("Failed to load serial handlers from file {}", file, e);
        }
    }

    public static void loadHeightHackHandlers() {
        File file = NEIClientConfig.heightHackHandlersFile;
        if (!file.exists()) {
            try (FileWriter writer = new FileWriter(file)) {
                NEIClientConfig.logger.info("Creating default height hack handlers list {}", file);
                URL defaultHeightHackHandlersResource = ClientHandler.class
                        .getResource("/assets/nei/cfg/heighthackhandlers.cfg");
                if (defaultHeightHackHandlersResource != null) {
                    IOUtils.copy(defaultHeightHackHandlersResource.openStream(), writer);
                }
            } catch (IOException e) {
                NEIClientConfig.logger.error("Failed to save default height hack handlers list to file {}", file, e);
            }
        }

        try (FileReader reader = new FileReader(file)) {
            NEIClientConfig.logger.info("Loading height hack handlers from file {}", file);
            NEIClientConfig.heightHackHandlerRegex = IOUtils.readLines(reader).stream()
                    .filter((line) -> !line.startsWith("#")).map(Pattern::compile)
                    .collect(Collectors.toCollection(HashSet::new));
        } catch (IOException e) {
            NEIClientConfig.logger.error("Failed to load height hack handlers from file {}", file, e);
        }
    }

    public static void loadHiddenHandlers() {
        File file = NEIClientConfig.hiddenHandlersFile;
        if (!file.exists()) {
            try (FileWriter writer = new FileWriter(file)) {
                NEIClientConfig.logger.info("Creating default hidden handlers list {}", file);
                URL defaultHeightHackHandlersResource = ClientHandler.class
                        .getResource("/assets/nei/cfg/hiddenhandlers.cfg");
                if (defaultHeightHackHandlersResource != null) {
                    IOUtils.copy(defaultHeightHackHandlersResource.openStream(), writer);
                }
            } catch (IOException e) {
                NEIClientConfig.logger.error("Failed to save default hidden handlers list to file {}", file, e);
            }
        }

        try (FileReader reader = new FileReader(file)) {
            NEIClientConfig.logger.info("Loading hidden handlers from file {}", file);
            NEIClientConfig.hiddenHandlerRegex = IOUtils.readLines(reader).stream()
                    .filter((line) -> !line.startsWith("#")).map(Pattern::compile)
                    .collect(Collectors.toCollection(HashSet::new));
        } catch (IOException e) {
            NEIClientConfig.logger.error("Failed to load hidden handlers from file {}", file, e);
        }
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
    }

    public static void loadHandlerOrdering() {
        File file = NEIClientConfig.handlerOrderingFile;
        if (!file.exists()) {
            try (FileWriter writer = new FileWriter(file)) {
                NEIClientConfig.logger.info("Creating default handler ordering CSV {}", file);

                List<String> toWrite = Lists.newArrayList(defaultHandlerOrdering);
                GuiRecipeTab.handlerMap.keySet().stream().sorted()
                        .forEach(handlerId -> toWrite.add(String.format("%s,0", handlerId)));

                IOUtils.writeLines(toWrite, "\n", writer);
            } catch (IOException e) {
                NEIClientConfig.logger.error("Failed to save default handler ordering to file {}", file, e);
            }
        }

        URL url;
        try {
            url = file.toURI().toURL();
        } catch (MalformedURLException e) {
            NEIClientConfig.logger.info("Invalid URL for handler ordering CSV.");
            e.printStackTrace();
            return;
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()))) {
            NEIClientConfig.logger.info("Loading handler ordering from file {}", file);
            CSVParser csvParser = CSVFormat.EXCEL.withCommentMarker('#').parse(reader);
            for (CSVRecord record : csvParser) {
                final String handlerId = record.get(0);

                int ordering;
                try {
                    ordering = Integer.parseInt(record.get(1));
                } catch (NumberFormatException e) {
                    NEIClientConfig.logger.error("Error parsing CSV record {}: {}", record, e);
                    continue;
                }

                NEIClientConfig.handlerOrdering.put(handlerId, ordering);
            }
        } catch (Exception e) {
            NEIClientConfig.logger.info("Error parsing CSV");
            e.printStackTrace();
        }
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

        @SuppressWarnings("serial")
        CustomModLoadingErrorDisplayException e = new CustomModLoadingErrorDisplayException() {

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
