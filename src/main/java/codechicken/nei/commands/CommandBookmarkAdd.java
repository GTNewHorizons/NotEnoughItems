package codechicken.nei.commands;

import java.util.Arrays;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;

import codechicken.nei.ItemPanels;
import codechicken.nei.LRUCache;
import codechicken.nei.bookmark.BookmarkGrid;
import codechicken.nei.bookmark.BookmarkGroup;
import codechicken.nei.bookmark.BookmarkPayload;

public class CommandBookmarkAdd extends CommandBase {

    private static final LRUCache<String, NBTTagCompound> PAYLOAD_CACHE = new LRUCache<>(64);

    public static void cachePayload(String payloadId, NBTTagCompound payload) {
        if (payloadId == null || payloadId.isEmpty() || payload == null) {
            return;
        }

        synchronized (PAYLOAD_CACHE) {
            PAYLOAD_CACHE.put(payloadId, (NBTTagCompound) payload.copy());
        }
    }

    private static NBTTagCompound getCachedPayload(String payloadId) {
        synchronized (PAYLOAD_CACHE) {
            return PAYLOAD_CACHE.get(payloadId);
        }
    }

    @Override
    public String getCommandName() {
        return "nei_bookmark";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/nei_bookmark <payloadId>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.addChatMessage(new ChatComponentText("§c/nei_bookmark <payloadId>"));
            return;
        }

        try {
            final String payloadId = String.join(" ", Arrays.copyOfRange(args, 0, args.length));
            final NBTTagCompound payload = getCachedPayload(payloadId);

            if (payload == null) {
                throw new IllegalArgumentException("Missing cached payload");
            }

            addBookmarkFromPayload((NBTTagCompound) payload.copy());
            sender.addChatMessage(new ChatComponentTranslation("nei.chat.bookmark_added.text"));
        } catch (Exception e) {
            sender.addChatMessage(new ChatComponentText(e.toString()));
        }
    }

    private static void addBookmarkFromPayload(NBTTagCompound nbt) {
        final BookmarkPayload payload = BookmarkPayload.of(nbt);
        final BookmarkGrid grid = ItemPanels.bookmarkPanel.getGrid();
        final BookmarkGroup group = payload.getGroup();
        int groupId = BookmarkGrid.DEFAULT_GROUP_ID;

        if (group != null) {
            groupId = grid.addGroup(group);
        }

        payload.getBookmarkItems(groupId).forEach(item -> grid.addItem(item, true));
    }

}
