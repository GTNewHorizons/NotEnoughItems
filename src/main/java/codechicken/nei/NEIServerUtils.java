package codechicken.nei;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipException;

import net.minecraft.command.ICommandSender;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings.GameType;
import net.minecraftforge.oredict.OreDictionary;

import org.apache.logging.log4j.Level;

import codechicken.core.CommonUtils;
import codechicken.core.ServerUtils;
import codechicken.lib.inventory.InventoryRange;
import codechicken.lib.inventory.InventoryUtils;
import codechicken.lib.packet.PacketCustom;
import codechicken.nei.PacketIDs.S2C;
import codechicken.nei.util.NBTHelper;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.ReflectionHelper;

public class NEIServerUtils {

    private static Class<?> gtMetaBaseItem;

    static {
        try {
            final ClassLoader loader = NEIServerUtils.class.getClassLoader();
            NEIServerUtils.gtMetaBaseItem = ReflectionHelper
                    .getClass(loader, "gregtech.api.items.MetaBaseItem", "gregtech.api.items.GT_MetaBase_Item");
        } catch (Exception ignored) {
            /* Do nothing */
        }
    }

    public static boolean isRaining(World world) {
        return world.getWorldInfo().isRaining();
    }

    public static void toggleRaining(World world, boolean notify) {
        boolean raining = !world.isRaining();
        if (!raining) // turn off
            ((WorldServer) world).provider.resetRainAndThunder();
        else world.getWorldInfo().setRaining(!isRaining(world));

        if (notify)
            ServerUtils.sendChatToAll(new ChatComponentTranslation("nei.chat.rain." + (raining ? "on" : "off")));
    }

    public static void healPlayer(EntityPlayer player) {
        player.heal(20);
        player.getFoodStats().addStats(20, 1);
        player.extinguish();
    }

    public static long getTime(World world) {
        return world.getWorldInfo().getWorldTime();
    }

    public static void setTime(long l, World world) {
        world.getWorldInfo().setWorldTime(l);
    }

    public static void setSlotContents(EntityPlayer player, int slot, ItemStack item, boolean containerInv) {
        if (slot == -999) player.inventory.setItemStack(item);
        else if (containerInv) player.openContainer.putStackInSlot(slot, item);
        else player.inventory.setInventorySlotContents(slot, item);
    }

    public static ItemStack getSlotContents(EntityPlayer player, int slot, boolean containerInv) {
        if (slot == -999) return player.inventory.getItemStack();
        else if (containerInv) return player.openContainer.getSlot(slot).getStack();
        else return player.inventory.getStackInSlot(slot);
    }

    public static void deleteAllItems(EntityPlayerMP player) {
        for (Slot slot : player.openContainer.inventorySlots) slot.putStack(null);

        player.sendContainerAndContentsToPlayer(player.openContainer, player.openContainer.getInventory());
    }

    public static void setHourForward(World world, int hour, boolean notify) {
        long day = (getTime(world) / 24000L) * 24000L;
        long newTime = day + 24000L + hour * 1000;
        setTime(newTime, world);
        if (notify)
            ServerUtils.sendChatToAll(new ChatComponentTranslation("nei.chat.time", getTime(world) / 24000L, hour));
    }

    public static void advanceDisabledTimes(World world) {
        int dim = CommonUtils.getDimension(world);
        int hour = (int) (getTime(world) % 24000) / 1000;
        int newhour = hour;
        while (NEIServerConfig.isActionDisabled(dim, NEIActions.timeZones[newhour / 6]))
            newhour = ((newhour / 6 + 1) % 4) * 6;

        if (newhour != hour) setHourForward(world, newhour, false);
    }

    public static boolean canItemFitInInventory(EntityPlayer player, ItemStack itemstack) {
        return InventoryUtils.insertItem(new InventoryRange(player.inventory, 0, 36), itemstack, true) == 0;
    }

    public static void sendNotice(ICommandSender sender, IChatComponent msg, String permission) {
        ChatComponentTranslation notice = new ChatComponentTranslation(
                "chat.type.admin",
                sender.getCommandSenderName(),
                msg.createCopy());
        notice.getChatStyle().setColor(EnumChatFormatting.GRAY).setItalic(true);

        if (NEIServerConfig.canPlayerPerformAction("CONSOLE", permission))
            MinecraftServer.getServer().addChatMessage(notice);

        for (EntityPlayer p : ServerUtils.getPlayers()) if (p == sender) p.addChatComponentMessage(msg);
        else if (NEIServerConfig.canPlayerPerformAction(p.getCommandSenderName(), permission))
            p.addChatComponentMessage(notice);
    }

    /**
     * @param stack1 The {@link ItemStack} being compared.
     * @param stack2 The {@link ItemStack} to compare to.
     * @return whether the two items are the same in terms of damage and itemID.
     */
    public static boolean areStacksSameType(ItemStack stack1, ItemStack stack2) {
        return stack1 != null && stack2 != null
                && (stack1.getItem() == stack2.getItem()
                        && (!stack2.getHasSubtypes() || stack2.getItemDamage() == stack1.getItemDamage())
                        && ItemStack.areItemStackTagsEqual(stack2, stack1));
    }

    /**
     * NBT-friendly version of {@link #areStacksSameType(ItemStack, ItemStack)}
     *
     * @param stack1 The {@link ItemStack} being compared.
     * @param stack2 The {@link ItemStack} to compare to.
     * @return whether the two items are the same in terms of itemID, damage and NBT.
     */
    public static boolean areStacksSameTypeWithNBT(ItemStack stack1, ItemStack stack2) {
        return stack1 != null && stack2 != null
                && stack1.getItem() == stack2.getItem()
                && (!stack2.getHasSubtypes() || stack2.getItemDamage() == stack1.getItemDamage())
                && NBTHelper.matchTag(stack1.getTagCompound(), stack2.getTagCompound());
    }

    /**
     * {@link ItemStack}s with damage -1 are wildcards allowing all damages. Eg all colours of wool are allowed to
     * create Beds.
     *
     * @param stack1 The {@link ItemStack} being compared.
     * @param stack2 The {@link ItemStack} to compare to.
     * @return whether the two items are the same from the perspective of a crafting inventory.
     */
    public static boolean areStacksSameTypeCrafting(ItemStack stack1, ItemStack stack2) {
        return stack1 != null && stack2 != null
                && stack1.getItem() == stack2.getItem()
                && (stack1.getItemDamage() == stack2.getItemDamage()
                        || stack1.getItemDamage() == OreDictionary.WILDCARD_VALUE
                        || stack2.getItemDamage() == OreDictionary.WILDCARD_VALUE
                        || stack1.getItem().isDamageable());
    }

    /**
     * NBT-friendly version of {@link #areStacksSameTypeCrafting(ItemStack, ItemStack)}
     *
     * @param stack1 The {@link ItemStack} being compared.
     * @param stack2 The {@link ItemStack} to compare to.
     * @return whether the two items are the same from the perspective of a crafting inventory.
     */
    public static boolean areStacksSameTypeCraftingWithNBT(ItemStack stack1, ItemStack stack2) {

        if (NEIServerUtils.areStacksSameTypeCrafting(stack2, stack1)) {
            if (NBTHelper.matchTag(stack1.getTagCompound(), stack2.getTagCompound())) return true;
            if (isItemTool(stack1)
                    && (stack1.stackTagCompound == null || stack1.stackTagCompound.hasKey("GT.ToolStats"))
                    && (stack2.stackTagCompound == null || stack2.stackTagCompound.hasKey("GT.ToolStats")))
                return true;
            return stack1.getMaxStackSize() == 1 && stack2.getMaxStackSize() == 1
                    && (stack1.stackTagCompound == null ^ stack2.stackTagCompound == null);
        }

        return false;
    }

    /**
     * GT Items don't have any NBT set for the recipe, so if either of the stacks has a NULL nbt, and the other doesn't,
     * pretend they stack
     *
     * @param ItemStack stack
     * @return
     */
    public static boolean isItemTool(ItemStack stack) {
        return NEIServerUtils.gtMetaBaseItem != null && NEIServerUtils.gtMetaBaseItem.isInstance(stack.getItem());
    }

    /**
     * A simple function for comparing ItemStacks in a compatible with comparators.
     *
     * @param stack1 The {@link ItemStack} being compared.
     * @param stack2 The {@link ItemStack} to compare to.
     * @return The ordering of stack1 relative to stack2.
     */
    public static int compareStacks(ItemStack stack1, ItemStack stack2) {
        if (stack1 == stack2) return 0; // catches both null
        if (stack1 == null || stack2 == null) return stack1 == null ? -1 : 1; // null stack goes first
        if (stack1.getItem() != stack2.getItem())
            return Item.getIdFromItem(stack1.getItem()) - Item.getIdFromItem(stack2.getItem());
        if (stack1.stackSize != stack2.stackSize) return stack1.stackSize - stack2.stackSize;
        return stack1.getItemDamage() - stack2.getItemDamage();
    }

    public static boolean areStacksIdentical(ItemStack stack1, ItemStack stack2) {
        return compareStacks(stack1, stack2) == 0;
    }

    public static IChatComponent setColour(IChatComponent msg, EnumChatFormatting colour) {
        msg.getChatStyle().setColor(colour);
        return msg;
    }

    public static void givePlayerItem(EntityPlayerMP player, ItemStack stack, boolean infinite, boolean doGive) {
        if (stack.getItem() == null) {
            player.addChatComponentMessage(
                    setColour(new ChatComponentTranslation("nei.chat.give.noitem"), EnumChatFormatting.WHITE));
            return;
        }

        int given = stack.stackSize;
        if (doGive) {
            if (infinite) player.inventory.addItemStackToInventory(stack);
            else given -= InventoryUtils.insertItem(player.inventory, stack, false);
        }

        sendNotice(
                player,
                new ChatComponentTranslation(
                        "commands.give.success",
                        stack.func_151000_E(),
                        infinite ? "\u221E" : Integer.toString(given),
                        player.getCommandSenderName()),
                "notify-item");
        player.openContainer.detectAndSendChanges();
    }

    public static ItemStack copyStack(ItemStack itemstack, int i) {
        if (itemstack == null) return null;

        itemstack.stackSize += i;
        return itemstack.splitStack(i);
    }

    public static ItemStack copyStack(ItemStack itemstack) {
        if (itemstack == null) return null;

        return copyStack(itemstack, itemstack.stackSize);
    }

    public static void toggleMagnetMode(EntityPlayerMP player) {
        PlayerSave playerSave = NEIServerConfig.forPlayer(player.getCommandSenderName());
        playerSave.enableAction("magnet", !playerSave.isActionEnabled("magnet"));
    }

    public static int getCreativeMode(EntityPlayerMP player) {
        if (NEIServerConfig.forPlayer(player.getCommandSenderName()).isActionEnabled("creative+")) return 2;
        else if (player.theItemInWorldManager.isCreative()) return 1;
        else if (player.theItemInWorldManager.getGameType().isAdventure()) return 3;
        else return 0;
    }

    public static GameType getGameType(int mode) {
        switch (mode) {
            case 0:
                return GameType.SURVIVAL;
            case 1:
            case 2:
                return GameType.CREATIVE;
            case 3:
                return GameType.ADVENTURE;
        }
        return null;
    }

    public static void setGamemode(EntityPlayerMP player, int mode) {
        if (mode < 0 || mode >= NEIActions.gameModes.length
                || NEIActions.nameActionMap.containsKey(NEIActions.gameModes[mode]) && !NEIServerConfig
                        .canPlayerPerformAction(player.getCommandSenderName(), NEIActions.gameModes[mode]))
            return;

        // creative+
        NEIServerConfig.forPlayer(player.getCommandSenderName()).enableAction("creative+", mode == 2);
        if (mode == 2 && !(player.openContainer instanceof ContainerCreativeInv)) // open the container immediately for
                                                                                  // the client
            NEISPH.processCreativeInv(player, true);

        // change it on the server
        player.theItemInWorldManager.setGameType(getGameType(mode));

        // tell the client to change it
        new PacketCustom(NEISPH.channel, S2C.SEND_GAME_MODE).writeByte(mode).sendToPlayer(player);
        player.addChatMessage(new ChatComponentTranslation("nei.chat.gamemode." + mode));
    }

    public static void cycleCreativeInv(EntityPlayerMP player, int steps) {
        InventoryPlayer inventory = player.inventory;

        // top down [row][col]
        ItemStack[][] slots = new ItemStack[10][9];
        PlayerSave playerSave = NEIServerConfig.forPlayer(player.getCommandSenderName());

        // get
        System.arraycopy(inventory.mainInventory, 0, slots[9], 0, 9);

        for (int row = 0; row < 3; row++)
            System.arraycopy(inventory.mainInventory, (row + 1) * 9, slots[row + 6], 0, 9);

        for (int row = 0; row < 6; row++) System.arraycopy(playerSave.creativeInv, row * 9, slots[row], 0, 9);

        ItemStack[][] newslots = new ItemStack[10][];

        // put back
        for (int row = 0; row < 10; row++) newslots[(row + steps + 10) % 10] = slots[row];

        System.arraycopy(newslots[9], 0, inventory.mainInventory, 0, 9);

        for (int row = 0; row < 3; row++)
            System.arraycopy(newslots[row + 6], 0, inventory.mainInventory, (row + 1) * 9, 9);

        for (int row = 0; row < 6; row++) System.arraycopy(newslots[row], 0, playerSave.creativeInv, row * 9, 9);

        playerSave.setDirty();
    }

    public static List<int[]> getEnchantments(ItemStack itemstack) {
        ArrayList<int[]> arraylist = new ArrayList<>();
        if (itemstack != null) {
            NBTTagList nbttaglist = itemstack.getEnchantmentTagList();
            if (nbttaglist != null) for (int i = 0; i < nbttaglist.tagCount(); i++) {
                NBTTagCompound tag = nbttaglist.getCompoundTagAt(i);
                arraylist.add(new int[] { tag.getShort("id"), tag.getShort("lvl") });
            }
        }
        return arraylist;
    }

    public static boolean stackHasEnchantment(ItemStack itemstack, int e) {
        List<int[]> allenchantments = getEnchantments(itemstack);
        for (int[] ai : allenchantments) if (ai[0] == e) return true;

        return false;
    }

    public static int getEnchantmentLevel(ItemStack itemstack, int e) {
        List<int[]> allenchantments = getEnchantments(itemstack);
        for (int[] ai : allenchantments) if (ai[0] == e) return ai[1];

        return -1;
    }

    public static boolean doesEnchantmentConflict(List<int[]> enchantments, Enchantment e) {
        for (int[] ai : enchantments) if (!e.canApplyTogether(Enchantment.enchantmentsList[ai[0]])) return true;

        return false;
    }

    public static RuntimeException throwCME(String msg) {
        if (CommonUtils.isClient()) return ClientHandler.throwCME(msg);

        throw new RuntimeException(msg);
    }

    @SuppressWarnings("unchecked")
    public static ItemStack[] extractRecipeItems(Object obj) {
        if (obj instanceof ItemStack) return new ItemStack[] { (ItemStack) obj };
        if (obj instanceof ItemStack[]) return (ItemStack[]) obj;
        if (obj instanceof List) return ((List<ItemStack>) obj).toArray(new ItemStack[0]);

        throw new ClassCastException(obj + " not an ItemStack, ItemStack[] or List<ItemStack?");
    }

    public static List<Integer> getRange(final int start, final int end) {
        return new AbstractList<Integer>() {

            @Override
            public Integer get(int index) {
                return start + index;
            }

            @Override
            public int size() {
                return end - start;
            }
        };
    }

    public static StringBuilder fixTrailingCommaList(StringBuilder sb) {
        if (sb.charAt(sb.length() - 1) == ',') sb.deleteCharAt(sb.length() - 1);
        return sb;
    }

    public static void logOnce(Throwable t, Set<String> stackTraces, String message) {
        logOnce(t, stackTraces, message, "");
    }

    public static void logOnce(Throwable t, Set<String> stackTraces, String message, String identifier) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        String stackTrace = identifier + sw.toString();
        if (!stackTraces.contains(stackTrace)) {
            NEIServerConfig.logger.error(message, t);
            stackTraces.add(stackTrace);
        }
    }

    public static ItemStack getModdedItem(String itemId, String nbtString) {
        int meta = -1;
        ItemStack itemStack;

        if (!itemId.contains(":")) {
            NEIClientConfig.logger.info(String.format("Item ID %s is missing colon", itemId));
            return null;
        }
        final String[] split = itemId.split(":");
        // ModID:ItemID
        String itemLookupId = split[0] + ":" + split[1];

        if (split.length >= 3) {
            int mIdx = -1;
            if (split[2].matches("\\d+")) {
                mIdx = 2;
            } else {
                itemLookupId += ":" + split[2];
                if (split.length >= 4) mIdx = 3;
            }
            if (mIdx != -1) meta = Integer.parseInt(split[mIdx]);
        }

        if (meta != -1) {
            itemStack = GameRegistry.makeItemStack(itemLookupId, meta, 1, nbtString == null ? "" : nbtString);
        } else {
            itemStack = GameRegistry.findItemStack(split[0], split[1], 1);
            if (nbtString != null && !nbtString.isEmpty()) {
                try {
                    final NBTBase nbttag = JsonToNBT.func_150315_a(nbtString);
                    if (!(nbttag instanceof NBTTagCompound)) {
                        NEIClientConfig.logger.info("Unexpected NBT string - multiple values {}", nbtString);
                        return null;
                    }
                    itemStack.setTagCompound((NBTTagCompound) nbttag);
                } catch (NBTException e) {
                    FMLLog.getLogger()
                            .log(Level.WARN, "Encountered an exception parsing ItemStack NBT string {}", nbtString, e);
                    return null;
                }
            }
        }
        return itemStack;
    }

    public static NBTTagCompound readNBT(File file) throws IOException {
        if (!file.exists()) return null;
        FileInputStream in = new FileInputStream(file);
        NBTTagCompound tag;
        try {
            tag = CompressedStreamTools.readCompressed(in);
        } catch (ZipException e) {
            if (e.getMessage().equals("Not in GZIP format")) tag = CompressedStreamTools.read(file);
            else throw e;
        }
        in.close();
        return tag;
    }

    public static void writeNBT(NBTTagCompound tag, File file) throws IOException {
        FileOutputStream out = new FileOutputStream(file);
        CompressedStreamTools.writeCompressed(tag, out);
        out.close();
    }

    public static int divideCeil(int numerator, int denominator) {
        return (int) Math.ceil((float) numerator / denominator);
    }
}
