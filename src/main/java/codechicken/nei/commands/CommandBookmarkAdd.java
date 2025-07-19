package codechicken.nei.commands;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;

import codechicken.nei.ItemPanels;

public class CommandBookmarkAdd extends CommandBase implements ICommand {

    @Override
    public String getCommandName() {
        return "nei_bookmark";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/nei_bookmark <nbt>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {

        if (args.length < 1 || args[0].length() < 3) {
            sender.addChatMessage(new ChatComponentText("§c/nei_bookmark <nbt>"));
            return;
        }

        try {
            ItemPanels.bookmarkPanel.addItem(
                    ItemStack.loadItemStackFromNBT((NBTTagCompound) JsonToNBT.func_150315_a(String.join(" ", args))));
        } catch (NBTException e) {
            sender.addChatMessage(new ChatComponentText(e.toString()));
        }
    }

}
