package codechicken.nei;

import static codechicken.nei.NEIClientConfig.canCheatItem;
import static codechicken.nei.NEIClientConfig.canPerformAction;
import static codechicken.nei.NEIClientConfig.getStringArrSetting;
import static codechicken.nei.NEIClientConfig.getStringSetting;
import static codechicken.nei.NEIClientConfig.hasSMPCounterPart;
import static codechicken.nei.NEIClientConfig.invCreativeMode;
import static codechicken.nei.NEIClientConfig.world;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.input.Keyboard;

import com.google.common.collect.Iterables;

import codechicken.lib.inventory.InventoryRange;
import codechicken.lib.inventory.InventoryUtils;
import codechicken.lib.util.LangProxy;
import codechicken.nei.api.GuiInfo;
import codechicken.nei.api.IInfiniteItemHandler;
import codechicken.nei.api.ItemInfo;

public class NEIClientUtils extends NEIServerUtils {

    public static LangProxy lang = new LangProxy("nei");

    /** Formats a number with group separator and at most 2 fraction digits. */
    private static final Map<Locale, DecimalFormat> decimalFormatters = new HashMap<>();

    public static final int ALT_HASH = 1 << 27;
    public static final int SHIFT_HASH = 1 << 26;
    public static final int CTRL_HASH = 1 << 25;

    public static Minecraft mc() {
        return Minecraft.getMinecraft();
    }

    public static String translate(String key, Object... params) {
        return lang.format(key, params);
    }

    public static void printChatMessage(IChatComponent msg) {
        if (mc().ingameGUI != null) mc().ingameGUI.getChatGUI().printChatMessage(msg);
    }

    private static DecimalFormat getDecimalFormat() {
        return decimalFormatters.computeIfAbsent(Locale.getDefault(Locale.Category.FORMAT), locale -> {
            DecimalFormat numberFormat = new DecimalFormat(); // uses the necessary locale inside anyway
            numberFormat.setGroupingUsed(true);
            numberFormat.setMaximumFractionDigits(2);
            numberFormat.setRoundingMode(RoundingMode.HALF_UP);
            DecimalFormatSymbols decimalFormatSymbols = numberFormat.getDecimalFormatSymbols();
            decimalFormatSymbols.setGroupingSeparator(','); // Use sensible separator for best clarity.
            numberFormat.setDecimalFormatSymbols(decimalFormatSymbols);

            return numberFormat;
        });
    }

    public static String formatNumbers(BigInteger aNumber) {
        return getDecimalFormat().format(aNumber);
    }

    public static String formatNumbers(long aNumber) {
        return getDecimalFormat().format(aNumber);
    }

    public static String formatNumbers(double aNumber) {
        return getDecimalFormat().format(aNumber);
    }

    public static void deleteHeldItem() {
        deleteSlotStack(-999);
    }

    public static void dropHeldItem() {
        mc().playerController
                .windowClick(((GuiContainer) mc().currentScreen).inventorySlots.windowId, -999, 0, 0, mc().thePlayer);
    }

    public static void deleteSlotStack(int slotNumber) {
        setSlotContents(slotNumber, null, true);
    }

    public static void decreaseSlotStack(int slotNumber) {
        ItemStack stack = slotNumber == -999 ? getHeldItem()
                : mc().thePlayer.openContainer.getSlot(slotNumber).getStack();
        if (stack == null) return;

        if (stack.stackSize == 1) deleteSlotStack(slotNumber);
        else {
            stack = stack.copy();
            stack.stackSize--;
            setSlotContents(slotNumber, stack, true);
        }
    }

    public static void deleteEverything() {
        NEICPH.sendDeleteAllItems();
    }

    public static void deleteItemsOfType(ItemStack type) {
        Container c = getGuiContainer().inventorySlots;
        for (int i = 0; i < c.inventorySlots.size(); i++) {
            Slot slot = c.getSlot(i);
            if (slot == null) continue;

            ItemStack stack = slot.getStack();
            if (stack != null && stack.getItem() == type.getItem() && stack.getItemDamage() == type.getItemDamage()) {
                setSlotContents(i, null, true);
                slot.putStack(null);
            }
        }
    }

    public static ItemStack getHeldItem() {
        return mc().thePlayer.inventory.getItemStack();
    }

    public static void setSlotContents(int slot, ItemStack item, boolean containerInv) {
        NEICPH.sendSetSlot(slot, item, containerInv);

        if (slot == -999) mc().thePlayer.inventory.setItemStack(item);
    }

    /**
     * @param mode -1 = normal cheats, 0 = no infinites, 1 = replenish stack
     */
    public static void cheatItem(ItemStack stack, int button, int mode) {
        if (!canCheatItem(stack)) return;

        if (mode == -1 && button == 0 && shiftKey() && NEIClientConfig.hasSMPCounterPart()) {
            for (IInfiniteItemHandler handler : ItemInfo.infiniteHandlers) {
                if (!handler.canHandleItem(stack)) continue;

                ItemStack inf = handler.getInfiniteItem(stack);
                if (inf != null) {
                    giveStack(inf, inf.stackSize, true);
                    return;
                }
            }
            cheatItem(stack, button, 0);
        } else if (button == 1) {
            giveStack(stack, 1);
        } else {
            if (mode == 1 && stack.stackSize < stack.getMaxStackSize()) {
                giveStack(stack, stack.getMaxStackSize() - stack.stackSize);
            } else {
                giveStack(stack);
            }
        }
    }

    public static void giveStack(ItemStack itemstack) {
        giveStack(itemstack, itemstack.stackSize);
    }

    public static void giveStack(ItemStack itemstack, int i) {
        giveStack(itemstack, i, false);
    }

    public static void giveStack(ItemStack base, int i, boolean infinite) {
        ItemStack stack = copyStack(base, i);
        if (hasSMPCounterPart()) {
            ItemStack typestack = copyStack(stack, 1);
            if (!infinite && !canItemFitInInventory(mc().thePlayer, stack)
                    && (mc().currentScreen instanceof GuiContainer)) {
                GuiContainer gui = getGuiContainer();
                final List<Iterable<Integer>> handlerSlots;
                try {
                    GuiInfo.readLock.lock();
                    handlerSlots = GuiInfo.guiHandlers.stream()
                            .map(handler -> handler.getItemSpawnSlots(gui, typestack)).filter(x -> x != null)
                            .collect(Collectors.toCollection(LinkedList::new));
                } finally {
                    GuiInfo.readLock.unlock();
                }

                int increment = typestack.getMaxStackSize();
                int given = 0;
                for (int slotNo : Iterables.concat(handlerSlots)) {
                    Slot slot = gui.inventorySlots.getSlot(slotNo);
                    if (!slot.isItemValid(typestack) || !InventoryUtils.canStack(slot.getStack(), typestack)) continue;

                    int qty = Math.min(stack.stackSize - given, increment);
                    int current = slot.getHasStack() ? slot.getStack().stackSize : 0;
                    qty = Math.min(qty, slot.getSlotStackLimit() - current);

                    ItemStack newStack = copyStack(typestack, qty + current);
                    slot.putStack(newStack);
                    setSlotContents(slotNo, newStack, true);
                    given += qty;
                    if (given >= stack.stackSize) break;
                }
                if (given > 0) NEICPH.sendGiveItem(copyStack(typestack, given), false, false);
            } else NEICPH.sendGiveItem(stack, infinite, true);
        } else {
            for (int given = 0; given < stack.stackSize;) {
                int qty = Math.min(stack.stackSize - given, stack.getMaxStackSize());
                sendCommand(
                        getStringSetting("command.item"),
                        mc().thePlayer.getCommandSenderName(),
                        Item.getIdFromItem(stack.getItem()),
                        qty,
                        stack.getItemDamage(),
                        stack.hasTagCompound() ? stack.getTagCompound().toString() : "",
                        Item.itemRegistry.getNameForObject(stack.getItem()));
                given += qty;
            }
        }
    }

    public static boolean canItemFitInInventory(EntityPlayer player, ItemStack itemstack) {
        return InventoryUtils.getInsertibleQuantity(new InventoryRange(player.inventory, 0, 36), itemstack) > 0;
    }

    public static boolean shiftKey() {
        return Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
    }

    public static boolean controlKey() {
        if (Minecraft.isRunningOnMac) {
            return Keyboard.isKeyDown(Keyboard.KEY_LMETA) || Keyboard.isKeyDown(Keyboard.KEY_RMETA);
        }
        return Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
    }

    public static int getGamemode() {
        if (invCreativeMode()) return 2;
        else if (mc().playerController.isInCreativeMode()) return 1;
        else if (mc().playerController.currentGameType.isAdventure()) return 3;
        else return 0;
    }

    public static boolean isValidGamemode(String s) {
        return s.equals("survival")
                || canPerformAction(s) && Arrays.asList(getStringArrSetting("inventory.gamemodes")).contains(s);
    }

    public static int getNextGamemode() {
        int mode = getGamemode();
        int nmode = mode;
        while (true) {
            nmode = (nmode + 1) % NEIActions.gameModes.length;
            if (nmode == mode || isValidGamemode(NEIActions.gameModes[nmode])) break;
        }
        return nmode;
    }

    public static void cycleGamemode() {
        int mode = getGamemode();
        int nmode = getNextGamemode();
        if (mode == nmode) return;

        if (hasSMPCounterPart()) NEICPH.sendGamemode(nmode);
        else sendCommand(
                getStringSetting("command.creative"),
                getGameType(nmode),
                mc().thePlayer.getCommandSenderName());
    }

    public static long getTime() {
        return mc().theWorld.getWorldInfo().getWorldTime();
    }

    public static void setTime(long l) {
        mc().theWorld.getWorldInfo().setWorldTime(l);
    }

    public static void setHourForward(int hour) {
        long day = (getTime() / 24000L) * 24000L;
        long newTime = day + 24000L + hour * 1000;

        if (hasSMPCounterPart()) NEICPH.sendSetTime(hour);
        else sendCommand(getStringSetting("command.time"), newTime);
    }

    public static void sendCommand(String command, Object... args) {
        if (command.length() == 0) return;

        NumberFormat numberformat = NumberFormat.getIntegerInstance();
        numberformat.setGroupingUsed(false);
        MessageFormat messageformat = new MessageFormat(command);
        for (int i = 0; i < args.length; i++) if (args[i] instanceof Integer || args[i] instanceof Long)
            messageformat.setFormatByArgumentIndex(i, numberformat);

        mc().thePlayer.sendChatMessage(messageformat.format(args));
    }

    public static boolean isRaining() {
        return mc().theWorld.getWorldInfo().isRaining();
    }

    public static void toggleRaining() {
        if (hasSMPCounterPart()) NEICPH.sendToggleRain();
        else sendCommand(getStringSetting("command.rain"), isRaining() ? 0 : 1);
    }

    public static void healPlayer() {
        if (hasSMPCounterPart()) NEICPH.sendHeal();
        else sendCommand(getStringSetting("command.heal"), mc().thePlayer.getCommandSenderName());
    }

    public static void toggleMagnetMode() {
        if (hasSMPCounterPart()) NEICPH.sendToggleMagnetMode();
    }

    public static ArrayList<int[]> concatIntegersToRanges(List<Integer> damages) {
        ArrayList<int[]> ranges = new ArrayList<>();
        if (damages.size() == 0) return ranges;

        Collections.sort(damages);
        int start = -1;
        int next = 0;
        for (Integer i : damages) {
            if (start == -1) {
                start = next = i;
                continue;
            }
            if (next + 1 != i) {
                ranges.add(new int[] { start, next });
                start = next = i;
                continue;
            }
            next = i;
        }
        ranges.add(new int[] { start, next });
        return ranges;
    }

    public static ArrayList<int[]> addIntegersToRanges(List<int[]> ranges, List<Integer> damages) {
        for (int[] range : ranges) for (int integer = range[0]; integer <= range[1]; integer++) damages.add(integer);

        return concatIntegersToRanges(damages);
    }

    public static String cropText(FontRenderer fontRenderer, String text, int containerWidth) {
        int textWidth = fontRenderer.getStringWidth(text);

        if (textWidth > containerWidth) {
            int dots = fontRenderer.getStringWidth("...");

            if (containerWidth > dots) {
                textWidth += dots;

                while (textWidth > containerWidth) {
                    textWidth -= fontRenderer.getCharWidth(text.charAt(text.length() - 1));
                    text = text.substring(0, text.length() - 1);
                }

                return text + "...";
            }

            return "...";
        }

        return text;
    }

    public static boolean safeKeyDown(int keyCode) {
        try {
            return Keyboard.isKeyDown(keyCode);
        } catch (IndexOutOfBoundsException e) {
            return false;
        }
    }

    public static void setItemQuantity(int i) {
        world.nbt.setInteger("quantity", i);
    }

    public static GuiContainer getGuiContainer() {
        if (mc().currentScreen instanceof GuiContainer) return (GuiContainer) mc().currentScreen;

        return null;
    }

    public static boolean altKey() {
        return Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU);
    }

    public static int getMetaHash() {
        if (Keyboard.getEventKeyState()) {
            int hash = 0;

            if (altKey()) {
                hash = hash | ALT_HASH;
            }

            if (shiftKey()) {
                hash = hash | SHIFT_HASH;
            }

            if (controlKey()) {
                hash = hash | CTRL_HASH;
            }

            return hash;
        }

        return 0;
    }

    public static int getKeyHash() {

        if (Keyboard.getEventKeyState()) {
            final int keycode = Keyboard.getEventKey();

            if (keycode != Keyboard.KEY_LSHIFT && keycode != Keyboard.KEY_RSHIFT
                    && keycode != Keyboard.KEY_LCONTROL
                    && keycode != Keyboard.KEY_RCONTROL
                    && keycode != Keyboard.KEY_LMENU
                    && keycode != Keyboard.KEY_RMENU) {
                return getMetaHash() + keycode;
            }
        }

        return Keyboard.CHAR_NONE;
    }

    public static String getKeyHashName(int keyBind) {
        StringJoiner keyText = new StringJoiner(" + ");

        if ((keyBind & NEIClientUtils.CTRL_HASH) != 0) {
            keyText.add(NEIClientUtils.translate(Minecraft.isRunningOnMac ? "key.ctrl.mac" : "key.ctrl"));
        }

        if ((keyBind & NEIClientUtils.SHIFT_HASH) != 0) {
            keyText.add(NEIClientUtils.translate("key.shift"));
        }

        if ((keyBind & NEIClientUtils.ALT_HASH) != 0) {
            keyText.add(NEIClientUtils.translate("key.alt"));
        }

        return keyText.toString();
    }

    public static String getKeyName(int keyBind) {
        StringJoiner keyText = new StringJoiner(" + ");
        String hashText = NEIClientUtils.getKeyHashName(keyBind);
        int keyID = NEIClientUtils.unHashKey(keyBind);

        if (!hashText.isEmpty()) {
            keyText.add(hashText);
        }

        if (keyID != Keyboard.CHAR_NONE || hashText.isEmpty()) {
            keyText.add(Keyboard.getKeyName(keyID));
        }

        return keyText.toString();
    }

    public static int unHashKey(int keyBind) {
        return keyBind & ~(NEIClientUtils.CTRL_HASH | NEIClientUtils.SHIFT_HASH | NEIClientUtils.ALT_HASH);
    }

    public static void playClickSound() {
        mc().getSoundHandler()
                .playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.0F));
    }

    public static void reportErrorBuffered(Throwable e, Set<String> buffer, String cause) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        String stackTrace = cause + sw;
        if (buffer.contains(stackTrace)) return;

        System.err.println("Error while rendering: " + cause);
        e.printStackTrace();
        buffer.add(stackTrace);

        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (player != null) {
            IChatComponent chat = new ChatComponentTranslation("nei.chat.render.error");
            chat.getChatStyle().setColor(EnumChatFormatting.RED);
            player.addChatComponentMessage(chat);
        }
    }

    public static void reportErrorBuffered(Throwable e, Set<String> buffer, ItemStack cause) {
        if (cause != null) {
            reportErrorBuffered(e, buffer, cause.toString());
        } else {
            reportErrorBuffered(e, buffer, "null");
        }
    }
}
