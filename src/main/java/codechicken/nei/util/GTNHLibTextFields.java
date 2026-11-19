package codechicken.nei.util;

import net.minecraft.client.gui.GuiTextField;

import com.gtnewhorizon.gtnhlib.util.font.TextFieldFormatting;

import codechicken.nei.NEIModContainer;

public class GTNHLibTextFields {

    private static final boolean HAS_API = NEIModContainer.isGTNHLibLoaded() && hasApi();

    private GTNHLibTextFields() {}

    public static void disableAmpCodeHandling(GuiTextField field) {
        if (HAS_API) TextFieldFormatting.setAmpCodeAware(field, false);
    }

    private static boolean hasApi() {
        try {
            Class.forName("com.gtnewhorizon.gtnhlib.util.font.TextFieldFormatting");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }
}
