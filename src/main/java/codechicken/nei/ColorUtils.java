package codechicken.nei;

import com.gtnewhorizon.gtnhlib.color.ColorResource;

public class ColorUtils {

    private static final ColorResource.Factory color = new ColorResource.Factory("nei");

    public static final ColorResource
    // spotless:off
        textGray            = color.rgb("textGray",             "0x404040"),
        textLightGray       = color.rgb("textLightGray",        "0x606060"),
        recipeTitle         = color.rgb("recipeTitle",          "0xFFFFFF"),
        recipeTitleBG       = color.argb("recipeTitleBG",        "0x30000000"),
        recipeTitleHover    = color.rgb("recipeTitleHover",     "0xFFFF55"),
        recipeBadge         = color.rgb("recipeBadge",          "0xFDD835"),

        subsetWidget        = color.rgb("subsetWidget",         "0xAA00AA"),
        buttonLabelNormal   = color.rgb("buttonLabelNormal",    "0xE0E0E0"),
        buttonLabelHover    = color.rgb("buttonLabelHover",     "0xFFFFA0"),
        buttonLabelDisabled = color.rgb("buttonLabelDisabled",  "0x601010"),
        buttonLabelInactive = color.rgb("buttonLabelInactive",  "0xA0A0A0"),

        craftingTreeLine    = color.argb("craftingTreeLine",    "0xFF888888");
    // spotless:on
}
