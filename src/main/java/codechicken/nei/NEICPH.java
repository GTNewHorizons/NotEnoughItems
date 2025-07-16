package codechicken.nei;

import static codechicken.nei.PacketIDs.C2S;
import static codechicken.nei.PacketIDs.S2C;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

import codechicken.core.ClientUtils;
import codechicken.lib.inventory.InventoryUtils;
import codechicken.lib.packet.PacketCustom;
import codechicken.lib.packet.PacketCustom.IClientPacketHandler;
import cpw.mods.fml.relauncher.Side;

public class NEICPH implements IClientPacketHandler {

    public static final String channel = "NEI";

    @Override
    public void handlePacket(PacketCustom packet, Minecraft mc, INetHandlerPlayClient netHandler) {
        switch (packet.getType()) {
            case S2C.SEND_SERVER_SIDE_CHECK:
                handleSMPCheck(packet.readUByte(), packet.readString(), mc.theWorld);
                break;
            case S2C.SEND_LOGIN_STATE:
                handleLoginState(packet);
                break;
            case S2C.SEND_ACTION_DISABLED:
                handleActionDisabled(packet);
                break;
            case S2C.SEND_ACTION_ENABLED:
                handleActionEnabled(packet);
                break;
            case S2C.SEND_MAGNETIC_ITEM:
                ClientHandler.instance().addSMPMagneticItem(packet.readInt(), mc.theWorld);
                break;
            case S2C.SEND_GAME_MODE:
                handleGamemode(mc, packet.readUByte());
                break;
            case S2C.OPEN_ENCHANTMENT_GUI:
                ClientUtils.openSMPGui(
                        packet.readUByte(),
                        new GuiEnchantmentModifier(mc.thePlayer.inventory, mc.theWorld, 0, 0, 0));
                break;
            case S2C.SET_CREATIVE_PLUS_MODE:
                if (packet.readBoolean()) ClientUtils.openSMPGui(
                        packet.readUByte(),
                        new GuiExtendedCreativeInv(
                                new ContainerCreativeInv(mc.thePlayer, new ExtendedCreativeInv(null, Side.CLIENT))));
                else mc.displayGuiScreen(new GuiInventory(mc.thePlayer));
                break;
            case S2C.OPEN_POTION_GUI:
                ClientUtils.openSMPGui(packet.readUByte(), new GuiPotionCreator(mc.thePlayer.inventory));
                break;
        }
    }

    private void handleGamemode(Minecraft mc, int mode) {
        mc.playerController.setGameType(NEIServerUtils.getGameType(mode));
    }

    private void handleActionEnabled(PacketCustom packet) {
        String name = packet.readString();
        if (packet.readBoolean()) NEIClientConfig.enabledActions.add(name);
        else NEIClientConfig.enabledActions.remove(name);
    }

    private void handleActionDisabled(PacketCustom packet) {
        String name = packet.readString();
        if (packet.readBoolean()) NEIClientConfig.disabledActions.add(name);
        else NEIClientConfig.disabledActions.remove(name);
    }

    private void handleLoginState(PacketCustom packet) {
        NEIClientConfig.permissableActions.clear();
        int num = packet.readUByte();
        for (int i = 0; i < num; i++) NEIClientConfig.permissableActions.add(packet.readString());

        NEIClientConfig.disabledActions.clear();
        num = packet.readUByte();
        for (int i = 0; i < num; i++) NEIClientConfig.disabledActions.add(packet.readString());

        NEIClientConfig.enabledActions.clear();
        num = packet.readUByte();
        for (int i = 0; i < num; i++) NEIClientConfig.enabledActions.add(packet.readString());

        NEIClientConfig.bannedBlocks.clear();
        num = packet.readInt();
        for (int i = 0; i < num; i++) NEIClientConfig.bannedBlocks.add(packet.readItemStack());

        if (NEIClientUtils.getGuiContainer() != null)
            LayoutManager.instance().refresh(NEIClientUtils.getGuiContainer());
    }

    private void handleSMPCheck(int serverprotocol, String worldName, World world) {
        if (serverprotocol > NEIActions.protocol) {
            NEIClientUtils.printChatMessage(new ChatComponentTranslation("nei.chat.mismatch.client"));
        } else if (serverprotocol < NEIActions.protocol) {
            NEIClientUtils.printChatMessage(new ChatComponentTranslation("nei.chat.mismatch.server"));
        } else {
            try {
                ClientHandler.instance().loadWorld(world, true);
                NEIClientConfig.setHasSMPCounterPart(true);
                NEIClientConfig.loadWorld(getSaveName());
                sendRequestLoginInfo();
            } catch (Exception e) {
                NEIClientConfig.logger.error("Error handling SMP Check", e);
            }
        }
    }

    private static String getSaveName() {
        if (Minecraft.getMinecraft().isSingleplayer()) return "local/" + ClientUtils.getWorldSaveName();

        return "remote/" + ClientUtils.getServerIP().replace(':', '~');
    }

    public static void sendGiveItem(ItemStack spawnstack, boolean infinite, boolean doSpawn) {
        PacketCustom packet = new PacketCustom(channel, C2S.GIVE_ITEM);
        packet.writeItemStack(spawnstack, true);
        packet.writeBoolean(infinite);
        packet.writeBoolean(doSpawn);
        packet.sendToServer();
    }

    public static void sendDeleteAllItems() {
        PacketCustom packet = new PacketCustom(channel, C2S.DELETE_ALL_ITEMS);
        packet.sendToServer();
    }

    public static void sendStateLoad(ItemStack[] state) {
        sendDeleteAllItems();
        for (int slot = 0; slot < state.length; slot++) {
            ItemStack item = state[slot];
            if (item == null) {
                continue;
            }
            sendSetSlot(slot, item, false);
        }

        PacketCustom packet = new PacketCustom(channel, C2S.REQUEST_CONTAINER_CONTENTS);
        packet.sendToServer();
    }

    public static void sendRequestContainer() {
        PacketCustom packet = new PacketCustom(channel, C2S.REQUEST_CONTAINER);
        packet.writeInt(Minecraft.getMinecraft().thePlayer.openContainer.getInventory().size());
        packet.sendToServer();
    }

    public static void sendSetSlot(int slot, ItemStack stack, boolean container) {
        PacketCustom packet = new PacketCustom(channel, C2S.SET_SLOT);
        packet.writeBoolean(container);
        packet.writeShort(slot);
        packet.writeItemStack(stack);
        packet.sendToServer();
    }

    private static void sendRequestLoginInfo() {
        PacketCustom packet = new PacketCustom(channel, C2S.REQUEST_LOGIN_INFO);
        packet.sendToServer();
    }

    public static void sendToggleMagnetMode() {
        PacketCustom packet = new PacketCustom(channel, C2S.TOGGLE_MAGNET);
        packet.sendToServer();
    }

    public static void sendSetTime(int hour) {
        PacketCustom packet = new PacketCustom(channel, C2S.SET_TIME);
        packet.writeByte(hour);
        packet.sendToServer();
    }

    public static void sendHeal() {
        PacketCustom packet = new PacketCustom(channel, C2S.HEAL);
        packet.sendToServer();
    }

    public static void sendToggleRain() {
        PacketCustom packet = new PacketCustom(channel, C2S.TOGGLE_RAIN);
        packet.sendToServer();
    }

    public static void sendChatLink(ItemStack stackover) {
        PacketCustom packet = new PacketCustom(channel, C2S.SEND_CHAT_ITEM_LINK);
        packet.writeItemStack(stackover);
        packet.sendToServer();
    }

    public static void sendOpenEnchantmentWindow() {
        PacketCustom packet = new PacketCustom(channel, C2S.REQUEST_ENCHANTMENT_GUI);
        packet.sendToServer();
    }

    public static void sendModifyEnchantment(int enchID, int level, boolean add) {
        PacketCustom packet = new PacketCustom(channel, C2S.MODIFY_ENCHANTMENT);
        packet.writeByte(enchID);
        packet.writeByte(level);
        packet.writeBoolean(add);
        packet.sendToServer();
    }

    public static void sendSetPropertyDisabled(String name, boolean enable) {
        PacketCustom packet = new PacketCustom(channel, C2S.CHANGE_PROPERTY);
        packet.writeString(name);
        packet.writeBoolean(enable);
        packet.sendToServer();
    }

    public static void sendGamemode(int mode) {
        new PacketCustom(channel, C2S.SET_GAME_MODE).writeByte(mode).sendToServer();
    }

    public static void sendCreativeInv(boolean open) {
        PacketCustom packet = new PacketCustom(channel, C2S.SET_CREATIVE_PLUS_MODE);
        packet.writeBoolean(open);
        packet.sendToServer();
    }

    public static void sendCreativeScroll(int steps) {
        PacketCustom packet = new PacketCustom(channel, C2S.CYCLE_CREATIVE_INV);
        packet.writeInt(steps);
        packet.sendToServer();
    }

    public static void sendMobSpawnerID(int x, int y, int z, String mobtype) {
        PacketCustom packet = new PacketCustom(channel, C2S.SEND_MOB_SPAWNER_ID);
        packet.writeCoord(x, y, z);
        packet.writeString(mobtype);
        packet.sendToServer();
    }

    public static void sendOpenPotionWindow() {
        ItemStack[] potionStore = new ItemStack[9];
        InventoryUtils.readItemStacksFromTag(
                potionStore,
                NEIClientConfig.global.nbt.getCompoundTag("potionStore").getTagList("items", 10));
        PacketCustom packet = new PacketCustom(channel, C2S.REQUEST_POTION_GUI);
        for (ItemStack stack : potionStore) packet.writeItemStack(stack);
        packet.sendToServer();
    }

    public static void sendDummySlotSet(int slotNumber, ItemStack stack) {
        PacketCustom packet = new PacketCustom(channel, C2S.SET_DUMMY_SLOT);
        packet.writeShort(slotNumber);
        packet.writeItemStack(stack, true);
        packet.sendToServer();
    }
}
