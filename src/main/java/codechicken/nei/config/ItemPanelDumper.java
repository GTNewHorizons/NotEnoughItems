package codechicken.nei.config;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Keyboard;

import codechicken.lib.inventory.InventoryUtils;
import codechicken.lib.vec.Rectangle4i;
import codechicken.nei.ItemPanels;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.guihook.GuiContainerManager;

public class ItemPanelDumper extends DataDumper {

    public static final int CSV = 0;
    public static final int NBT = 1;
    public static final int JSON = 2;
    public static final int PNG = 3;
    private static final Minecraft mc = Minecraft.getMinecraft();

    public ItemPanelDumper(String name) {
        super(name);
    }

    @Override
    public String[] header() {
        return new String[] { "Item Name", "Item ID", "Item meta", "Has NBT", "Display Name" };
    }

    @Override
    public Iterable<String[]> dump(int mode) {
        LinkedList<String[]> list = new LinkedList<>();
        for (ItemStack stack : ItemPanels.itemPanel.getItems()) list.add(
                new String[] { Item.itemRegistry.getNameForObject(stack.getItem()),
                        Integer.toString(Item.getIdFromItem(stack.getItem())),
                        Integer.toString(InventoryUtils.actualDamage(stack)),
                        stack.stackTagCompound == null ? "false" : "true", EnumChatFormatting
                                .getTextWithoutFormattingCodes(GuiContainerManager.itemDisplayNameShort(stack)) });

        return list;
    }

    @Override
    public String renderName() {
        return translateN(name);
    }

    public int getRes() {
        int i = renderTag(name + ".res").getIntValue(0);
        int size = 16 << i;
        if (size >= getSmallerScreenAxisSize() || i < 0) {
            getTag(name + ".res").setIntValue(0);
            size = 16;
        }
        return size;
    }

    private int getSmallerScreenAxisSize() {
        int width = mc.displayWidth;
        int height = mc.displayHeight;
        return Math.min(width, height);
    }

    public Rectangle4i resButtonSize() {
        int width = 60;
        return new Rectangle4i(modeButtonSize().x - width - 6, 0, width, 20);
    }

    @Override
    public void draw(int mousex, int mousey, float frame) {
        super.draw(mousex, mousey, frame);
        if (getMode() == PNG) {
            int res = getRes();
            drawButton(mousex, mousey, resButtonSize(), res + "x" + res);
        }
    }

    @Override
    public void mouseClicked(int mousex, int mousey, int button) {
        if (getMode() != PNG) {
            super.mouseClicked(mousex, mousey, button);
            return;
        }
        if (resButtonSize().contains(mousex, mousey)) {
            NEIClientUtils.playClickSound();
            int current = renderTag(name + ".res").getIntValue(0);
            int next;
            // 31 - numberOfLeadingZeros(x) = floor(log2(x))
            int max = 31 - Integer.numberOfLeadingZeros(getSmallerScreenAxisSize()) - 3;
            if (button == 1) { // Right click to cycle backward
                next = (current - 1 + max) % max;
            } else { // Left click to cycle forward
                next = (current + 1) % max;
            }
            getTag(name + ".res").setIntValue(next);
        } else if (!dumpButtonSize().contains(mousex, mousey) || acknowledgedItemSizeWarning()) {
            super.mouseClicked(mousex, mousey, button);
        }
    }

    @Override
    public String getFileExtension() {
        return switch (getMode()) {
            case CSV -> ".csv";
            case NBT -> ".nbt";
            case JSON -> ".json";
            default -> null;
        };
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
    public void dumpFile() {
        if (getMode() == PNG && acknowledgedItemSizeWarning())
            mc.displayGuiScreen(new GuiItemIconDumper(this, getRes()));
        else super.dumpFile();
    }

    private boolean acknowledgedItemSizeWarning() {
        return !showItemSizeWarning() || Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)
                || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
    }

    private boolean showItemSizeWarning() {
        int res = getRes();
        // More than 4 items at 2048x2048, 16 items at 1024x1024, 64 at 512x512, etc.
        int WARNING_SIZE = 16_777_216;
        return ItemPanels.itemPanel.getItems().size() * res * res > WARNING_SIZE;
    }

    @Override
    public void dumpTo(File file) throws IOException {
        switch (getMode()) {
            case CSV -> super.dumpTo(file);
            case NBT -> dumpNBT(file);
            default -> dumpJson(file);
        }
    }

    public void dumpNBT(File file) throws IOException {
        NBTTagList list = new NBTTagList();
        for (ItemStack stack : ItemPanels.itemPanel.getItems()) list.appendTag(stack.writeToNBT(new NBTTagCompound()));

        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag("list", list);

        CompressedStreamTools.writeCompressed(tag, new FileOutputStream(file));
    }

    public void dumpJson(File file) throws IOException {
        PrintWriter p = new PrintWriter(file);
        for (ItemStack stack : ItemPanels.itemPanel.getItems()) {
            NBTTagCompound tag = stack.writeToNBT(new NBTTagCompound());
            tag.removeTag("Count");
            p.println(tag);
        }

        p.close();
    }

    @Override
    public int modeCount() {
        return 4;
    }

    @Override
    public List<String> handleTooltip(int mousex, int mousey, List<String> currenttip) {
        if (getMode() == PNG && dumpButtonSize().contains(mousex, mousey) && showItemSizeWarning()) {
            int res = getRes();
            currenttip.add(
                    StatCollector.translateToLocalFormatted(
                            "nei.options.tools.dump.itempanel.icon.warning.1",
                            ItemPanels.itemPanel.getItems().size(),
                            res,
                            res));
            currenttip.add(
                    StatCollector.translateToLocalFormatted(
                            "nei.options.tools.dump.itempanel.icon.warning.2",
                            StatCollector.translateToLocal("nei.key.shift")));
        }
        return currenttip;
    }
}
