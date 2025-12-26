package codechicken.nei;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import codechicken.core.CommonUtils;
import codechicken.core.launch.CodeChickenCorePlugin;
import codechicken.nei.api.IConfigureNEI;
import codechicken.nei.asm.NEICorePlugin;
import codechicken.nei.config.IMCHandler;
import codechicken.nei.guihook.HideousLinkedList;
import codechicken.nei.recipe.GuiRecipeTab;
import cpw.mods.fml.client.FMLFileResourcePack;
import cpw.mods.fml.client.FMLFolderResourcePack;
import cpw.mods.fml.common.DummyModContainer;
import cpw.mods.fml.common.LoadController;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModMetadata;
import cpw.mods.fml.common.discovery.ASMDataTable;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLInterModComms;
import cpw.mods.fml.common.event.FMLLoadCompleteEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerAboutToStartEvent;
import cpw.mods.fml.common.versioning.ArtifactVersion;
import cpw.mods.fml.common.versioning.VersionParser;
import cpw.mods.fml.common.versioning.VersionRange;

@SuppressWarnings("UnstableApiUsage")
public class NEIModContainer extends DummyModContainer {

    public static final LinkedList<IConfigureNEI> plugins = new HideousLinkedList<>(new CopyOnWriteArrayList<>());

    private static boolean gregTech5Loaded;
    private static boolean gtnhLibLoaded;

    private static ASMDataTable asmDataTable;

    public NEIModContainer() {
        super(getModMetadata());
    }

    private static ModMetadata getModMetadata() {
        final ModMetadata modMetadata = new ModMetadata();
        modMetadata.name = "NotEnoughItems";
        modMetadata.modId = "NotEnoughItems";
        modMetadata.version = Tags.VERSION;
        modMetadata.authorList = Arrays.asList("ChickenBones", "mitchej123");
        modMetadata.url = "https://github.com/GTNewHorizons/NotEnoughItems";
        modMetadata.description = "Recipe Viewer, Inventory Manager, Item Spawner, Cheats and more; GTNH Version includes many enhancements.";
        return modMetadata;
    }

    public static boolean isGT5Loaded() {
        return gregTech5Loaded;
    }

    public static boolean isGTNHLibLoaded() {
        return gtnhLibLoaded;
    }

    @Override
    public Set<ArtifactVersion> getRequirements() {
        Set<ArtifactVersion> deps = new HashSet<>();
        deps.add(VersionParser.parseVersionReference("CodeChickenCore@[" + codechicken.core.asm.Tags.VERSION + ",)"));
        return deps;
    }

    @Override
    public List<ArtifactVersion> getDependencies() {
        List<ArtifactVersion> deps = new ArrayList<>();
        deps.add(VersionParser.parseVersionReference("CodeChickenCore@[" + codechicken.core.asm.Tags.VERSION + ",)"));
        deps.add(VersionParser.parseVersionReference("gtnhlib@[0.6.0,)"));
        return deps;
    }

    private String description;

    @Override
    public boolean registerBus(EventBus bus, LoadController controller) {
        bus.register(this);
        return true;
    }

    @Subscribe
    public void preInit(FMLPreInitializationEvent event) {
        gregTech5Loaded = Loader.isModLoaded("gregtech") && !Loader.isModLoaded("gregapi_post");
        gtnhLibLoaded = Loader.isModLoaded("gtnhlib");
        if (CommonUtils.isClient()) ClientHandler.preInit();
        asmDataTable = event.getAsmData();
    }

    @Subscribe
    public void init(FMLInitializationEvent event) {
        if (CommonUtils.isClient()) {
            ClientHandler.load();
        }
        ServerHandler.load();
    }

    @Subscribe
    public void postInit(FMLPostInitializationEvent event) {
        if (CommonUtils.isClient()) {
            ClientHandler.postInit();
        }
    }

    @Subscribe
    public void loadComplete(FMLLoadCompleteEvent event) {
        if (CommonUtils.isClient()) {
            GuiRecipeTab.loadHandlerInfo();
            ClientHandler.loadPluginsList();
            ClientHandler.loadHandlerOrdering();
            asmDataTable = null;
        }
    }

    @Subscribe
    public void onServerAboutToStart(FMLServerAboutToStartEvent event) {
        NEIServerConfig.resetFirstLoad();
    }

    @Subscribe
    public void handleIMCMessages(FMLInterModComms.IMCEvent event) {
        IMCHandler.processIMC(event.getMessages());
    }

    @Override
    public VersionRange acceptableMinecraftVersionRange() {
        return VersionParser.parseRange(CodeChickenCorePlugin.mcVersion);
    }

    @Override
    public File getSource() {
        return NEICorePlugin.location;
    }

    @Override
    public Class<?> getCustomResourcePackClass() {
        return getSource().isDirectory() ? FMLFolderResourcePack.class : FMLFileResourcePack.class;
    }

    public static ASMDataTable getAsmDataTable() {
        return asmDataTable;
    }
}
