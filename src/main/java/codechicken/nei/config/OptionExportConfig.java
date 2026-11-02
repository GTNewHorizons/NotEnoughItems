package codechicken.nei.config;

import net.minecraft.util.ChatComponentTranslation;

import codechicken.nei.ClientHandler;
import codechicken.nei.NEIClientUtils;

public class OptionExportConfig extends OptionButton {

    private final String fileName;

    public OptionExportConfig(String fileName) {
        super("tools.export_configs." + fileName.substring(0, fileName.lastIndexOf('.')));
        this.fileName = fileName;
    }

    @Override
    public String getPrefix() {
        return fileName;
    }

    @Override
    public String getButtonText() {
        return translateN("tools.export_configs.button");
    }

    @Override
    public String getTooltip() {
        return null;
    }

    @Override
    public boolean onClick(int button) {
        final boolean exported = ClientHandler.exportDefaultConfig(fileName);

        NEIClientUtils.printChatMessage(
                new ChatComponentTranslation(
                        exported ? "nei.options.tools.export_configs.done" : "nei.options.tools.export_configs.skipped",
                        fileName));
        return true;
    }
}
