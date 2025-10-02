package codechicken.nei.commands;

import java.io.File;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;

import codechicken.nei.ItemList;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.util.ItemUntranslator;

public class CommandUntranslator extends CommandBase {

    @Override
    public String getCommandName() {
        return "untranslator";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/untranslator dump OR /untranslator clear";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        final String command = args.length == 0 ? null : args[0];

        if ("dump".equals(command)) {
            if (ItemList.items.isEmpty()) {
                sendChatErrorMessage(sender, "nei.chat.untranslator.dump.error");
                return;
            } else {
                sendChatInfoMessage(sender, "nei.chat.untranslator.dump.start");
                ItemUntranslator.getInstance().generateUntranslatedNames();
                sendChatInfoMessage(sender, "nei.chat.untranslator.dump.finish");

                if (NEIClientConfig.enableItemUntranslator()) {
                    ItemUntranslator.getInstance().load();
                }
            }
        } else if ("clear".equals(command)) {
            final File file = new File(NEIClientConfig.configDir, "untranslator.cfg");

            if (file.exists() && file.delete()) {
                sendChatInfoMessage(sender, "nei.chat.untranslator.clear");
                if (NEIClientConfig.enableItemUntranslator()) {
                    ItemUntranslator.getInstance().load();
                }
            } else {
                sendChatErrorMessage(sender, "nei.chat.untranslator.clear.error");
            }
        } else {
            sendChatErrorMessage(sender, getCommandUsage(sender));
        }

    }

    private static void sendChatInfoMessage(ICommandSender sender, String translationKey, Object... args) {
        sender.addChatMessage(
                new ChatComponentTranslation(translationKey, args)
                        .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.AQUA)));
    }

    private static void sendChatErrorMessage(ICommandSender sender, String translationKey, Object... args) {
        sender.addChatMessage(
                new ChatComponentTranslation(translationKey, args)
                        .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.RED)));
    }

}
