package codechicken.nei.config.overlay;

import static codechicken.nei.NEIClientUtils.translate;

import codechicken.core.gui.GuiCCButton;
import codechicken.core.gui.GuiCCTextField;
import codechicken.core.gui.GuiScreenWidget;
import codechicken.nei.config.Option;

public class ToggleButton implements IOverlaysOption {

    private final Option opt;
    private final String name;
    private final GuiScreenWidget parent;
    private final GuiCCButton toggleButton;
    private final GuiCCTextField description;

    public ToggleButton(Option opt, String name, int x, int y, GuiScreenWidget parent) {
        this.opt = opt;
        this.name = name;
        this.parent = parent;
        String text = translate("options." + name);
        this.toggleButton = new GuiCCButton(x, y, 50, 20, "").setActionCommand("toggle." + name);
        this.description = new GuiCCTextField(x + 100, y, text.length() * 5 + 30, 20, text);
        this.description.setEnabled(false);
        refresh();
        showButton();
    }

    private void showButton() {
        parent.add(toggleButton);
        parent.add(description);
    }

    private String getStatusText() {
        return opt.getTag(name).getBooleanValue() ? translate("options.world.overlays.toggle.enabled")
                : translate("options.world.overlays.toggle.disabled");
    }

    @Override
    public void refresh() {
        this.toggleButton.setText(getStatusText());
        this.toggleButton.width = getStatusText().length() * 5 + 30;
    }
}
