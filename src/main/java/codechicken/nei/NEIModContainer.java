package codechicken.nei;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import net.minecraft.util.EnumChatFormatting;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import codechicken.core.CommonUtils;
import codechicken.core.launch.CodeChickenCorePlugin;
import codechicken.nei.api.IConfigureNEI;
import codechicken.nei.asm.NEICorePlugin;
import codechicken.nei.config.IMCHandler;
import codechicken.nei.recipe.GuiRecipeTab;
import cpw.mods.fml.client.FMLFileResourcePack;
import cpw.mods.fml.client.FMLFolderResourcePack;
import cpw.mods.fml.common.DummyModContainer;
import cpw.mods.fml.common.LoadController;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModMetadata;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLInterModComms;
import cpw.mods.fml.common.event.FMLLoadCompleteEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.versioning.ArtifactVersion;
import cpw.mods.fml.common.versioning.VersionParser;
import cpw.mods.fml.common.versioning.VersionRange;

public class NEIModContainer extends DummyModContainer {

    public static LinkedList<IConfigureNEI> plugins = new LinkedList<>();

    private static boolean gregTech5Loaded;

    public NEIModContainer() {
        super(getModMetadata());
        loadMetadata();
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

    @Override
    public Set<ArtifactVersion> getRequirements() {
        Set<ArtifactVersion> deps = new HashSet<>();
        deps.add(VersionParser.parseVersionReference("CodeChickenCore@[" + CodeChickenCorePlugin.version + ",)"));
        deps.add(VersionParser.parseVersionReference("hodgepodge@[2.5.44,)"));
        return deps;
    }

    @Override
    public List<ArtifactVersion> getDependencies() {
        return new LinkedList<>(getRequirements());
    }

    private String description;

    private void loadMetadata() {
        description = super.getMetadata().description.replace("Supporters:", EnumChatFormatting.AQUA + "Supporters:");
    }

    @Override
    public ModMetadata getMetadata() {
        StringBuilder s_plugins = new StringBuilder();
        if (plugins.size() == 0) {
            s_plugins.append(EnumChatFormatting.RED).append("No installed plugins.");
        } else {
            s_plugins.append(EnumChatFormatting.GREEN).append("Installed plugins: ");
            for (int i = 0; i < plugins.size(); i++) {
                if (i > 0) s_plugins.append(", ");
                IConfigureNEI plugin = plugins.get(i);
                s_plugins.append(plugin.getName()).append(" ").append(plugin.getVersion());
            }
            s_plugins.append(".");
        }

        ModMetadata meta = super.getMetadata();
        meta.description = description.replace("<plugins>", s_plugins.toString());
        return meta;
    }

    @Override
    public boolean registerBus(EventBus bus, LoadController controller) {
        bus.register(this);
        return true;
    }

    @Subscribe
    public void preInit(FMLPreInitializationEvent event) {
        gregTech5Loaded = Loader.isModLoaded("gregtech") && !Loader.isModLoaded("gregapi_post");
        if (CommonUtils.isClient()) ClientHandler.preInit();
    }

    @Subscribe
    public void init(FMLInitializationEvent event) {
        if (CommonUtils.isClient()) ClientHandler.load();

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
        }
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
}
