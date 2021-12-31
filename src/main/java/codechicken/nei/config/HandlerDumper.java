package codechicken.nei.config;

import codechicken.nei.NEIClientConfig;
import codechicken.nei.recipe.GuiRecipeTab;
import codechicken.nei.recipe.GuiUsageRecipe;
import codechicken.nei.recipe.HandlerInfo;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import codechicken.nei.util.NBTJson;
import com.google.common.base.Objects;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedList;

public class HandlerDumper extends DataDumper
{
    public HandlerDumper(String name) {
        super(name);
    }

    @Override
    public String[] header() {
        return new String[]{"Handler Recipe Name", "Handler Class", "Overlay Identifier", "Mod DisplayName", "ItemStack"};
    }

    @Override
    public Iterable<String[]> dump(int mode) {
        LinkedList<String[]> list = new LinkedList<>();
        for (IRecipeHandler handler : GuiUsageRecipe.usagehandlers) {
            final String handlerName = handler.getHandlerId();
            final String handlerId = Objects.firstNonNull(handler instanceof TemplateRecipeHandler ? ((TemplateRecipeHandler)handler).getOverlayIdentifier() : null, "null");
            HandlerInfo info = GuiRecipeTab.getHandlerInfo(handlerName, handlerId);

            list.add(new String[] {
                handler.getRecipeName(),
                handlerName,
                handlerId,
                info != null ? info.getModName() : "Unknown",
                info != null && info.getItemStack() != null ? info.getItemStack().toString() : "Unknown"
            });
        }
        return list;
    }

    @Override
    public String renderName() {
        return translateN(name);
    }

    @Override
    public String getFileExtension() {
        switch(getMode()) {
            case 0: return ".csv";
            case 1: return ".json";
        }
        return null;
    }

    @Override
    public ChatComponentTranslation dumpMessage(File file) {
        return new ChatComponentTranslation(namespaced(name + ".dumped"), "dumps/" + file.getName());
    }

    @Override
    public String modeButtonText() {
        return translateN(name + ".mode." + getMode());
    }


    @Override
    public void dumpTo(File file) throws IOException {
        if (getMode() == 0)
            super.dumpTo(file);
        else
            dumpJson(file);
    }

    public void dumpJson(File file) throws IOException {
        final String[] header = header();
        final FileWriter writer;
        try {
            writer = new FileWriter(file);
            for (String[] list : dump(getMode())) {
                NBTTagCompound tag = new NBTTagCompound();
                for (int i = 0; i < header.length; i++) {
                    tag.setString(header[i], list[i]);
                }
                IOUtils.write(NBTJson.toJson(tag) + "\n", writer);
            }
            writer.close();
        }  catch (IOException e) {
            NEIClientConfig.logger.error("Filed to save dump handler list to file {}", file, e);
        } 
        

    }

    @Override
    public int modeCount() {
        return 2;
    }
}
