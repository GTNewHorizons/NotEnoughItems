package codechicken.nei.config;

import java.lang.ref.WeakReference;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import codechicken.lib.config.ConfigTag;
import codechicken.nei.config.GuiOptionList.OptionScrollSlot;

public abstract class Option {

    public static void playClickSound() {
        Minecraft.getMinecraft().getSoundHandler()
                .playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.0F));
    }

    private WeakReference<OptionScrollSlot> slotRef;

    public OptionScrollSlot getSlot() {
        return slotRef != null ? slotRef.get() : null;
    }

    public String namespace = null;
    public final String name;
    public OptionList parent;

    public Option(String name) {
        this.name = name;
    }

    public String fullName() {
        return namespaced(name);
    }

    public String configName() {
        return fullName().substring(configBase().fullName().length() + 1);
    }

    public String namespaced(String name) {
        return namespace == null ? name : (namespace + "." + name);
    }

    public String translateN(String s, Object... args) {
        return StatCollector.translateToLocalFormatted(namespaced(s), args);
    }

    public int getHeight() {
        return 20;
    }

    private <T> T assertParentOverride(T elem) {
        if (elem == null) throw new IllegalStateException(
                "Option " + fullName()
                        + " does not have a defined parent list. Use OptionList.setOptionList to insert a parent");
        return elem;
    }

    public ConfigSet configSet() {
        return assertParentOverride(parent.configSet());
    }

    public OptionList configBase() {
        return assertParentOverride(parent.configBase());
    }

    /**
     * @return The tag with this name from the config set
     */
    public ConfigTag getTag() {
        return getTag(configName());
    }

    /**
     * @return The tag with this name from the config set
     */
    public ConfigTag getTag(String s) {
        return configSet().config.getTag(s);
    }

    public void onAdded(OptionScrollSlot slot) {
        this.slotRef = new WeakReference<>(slot);
    }

    public void onAdded(OptionList list) {
        parent = list;
    }

    /**
     * Mouse coordinates are relative to screen
     */
    public void onMouseClicked(int mx, int my, int button) {}

    public void mouseClicked(int mx, int my, int button) {}

    public void update() {}

    public void draw(int mx, int my, float frame) {}

    public void keyTyped(char c, int keycode) {}

    public List<String> handleTooltip(int mx, int my, List<String> currenttip) {
        return currenttip;
    }
}
