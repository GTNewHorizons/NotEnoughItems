package codechicken.nei.config;

import static codechicken.nei.NEIClientUtils.translate;

import net.minecraft.client.Minecraft;

import org.lwjgl.input.Keyboard;

import codechicken.core.gui.GuiCCButton;
import codechicken.core.gui.GuiScreenWidget;

public class GuiOverlays extends GuiScreenWidget {

    private final String name;
    private GuiCCButton toggleButton;
    private final Option opt;

    public GuiOverlays(Option opt) {
        super(180, 20);
        this.opt = opt;
        name = opt.name;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return true;
    }

    public void addWidgets() {
        add(toggleButton = new GuiCCButton(0, 0, 180, 20, "").setActionCommand("lock"));
        updateNames();
    }

    @Override
    public void actionPerformed(String ident, Object... params) {
        if (ident.equals("lock")) {
            opt.getTag(name).setBooleanValue(!lock());
            updateNames();
        }
    }

    private void updateNames() {
        toggleButton.text = translate("options." + name + "." + (lock() ? "lock" : "unlock"));
    }

    private boolean lock() {
        return opt.renderTag(name).getBooleanValue();
    }

    @Override
    public void keyTyped(char c, int keycode) {
        if (keycode == Keyboard.KEY_ESCAPE || keycode == Keyboard.KEY_BACK) {
            Minecraft.getMinecraft().displayGuiScreen(opt.slot.getGui());
            return;
        }
        super.keyTyped(c, keycode);
    }

}
