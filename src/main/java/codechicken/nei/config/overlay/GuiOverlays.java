package codechicken.nei.config.overlay;

import java.util.HashMap;

import net.minecraft.client.Minecraft;

import org.lwjgl.input.Keyboard;

import codechicken.core.gui.GuiScreenWidget;
import codechicken.nei.config.Option;

public class GuiOverlays extends GuiScreenWidget {

    private final Option opt;
    private final HashMap<String, IOverlaysOption> options;

    public GuiOverlays(Option opt) {
        super(0, 0);
        this.initGui();
        this.opt = opt;
        this.options = new HashMap<>();
        this.options.put(opt.name + ".lock", new ToggleButton(this.opt, opt.name + ".lock", 10, 10, this));
    }

    @Override
    public void initGui() {
        guiTop = ySize;
        guiLeft = xSize;
        if (!widgets.isEmpty()) resize();
    }

    @Override
    public void drawBackground() {
        drawDefaultBackground();
    }

    @Override
    public void actionPerformed(String ident, Object... params) {
        if (ident.startsWith("toggle.")) {
            String key = ident.substring(7);
            opt.getTag(key).setBooleanValue(!opt.getTag(key).getBooleanValue());
            this.options.get(key).refresh();
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
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
