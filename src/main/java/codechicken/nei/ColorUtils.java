package codechicken.nei;

import com.gtnewhorizon.gtnhlib.color.ColorResource;

public class ColorUtils {

    private static final ColorResource.Factory color = new ColorResource.Factory("nei");

    public static final ColorResource
    // spotless:off
        textGray            = color.rgb("textGray",             "0x404040"),
        textLightGray       = color.rgb("textLightGray",        "0x606060"),
        recipeTitle         = color.rgb("recipeTitle",          "0xFFFF55"),
        recipeTitleHover    = color.rgb("recipeTitleHover",     "0xFFFFFF"),
        subsetWidget        = color.rgb("subsetWidget",         "0xAA00AA"),
        recipeBadge         = color.rgb("recipeBadge",          "0xFDD835"),
        buttonLabelNormal   = color.rgb("buttonLabelNormal",    "0xE0E0E0"),
        buttonLabelHover    = color.rgb("buttonLabelHover",     "0xFFFFA0"),
        buttonLabelDisabled = color.rgb("buttonLabelDisabled",  "0x601010"),
        buttonLabelInactive = color.rgb("buttonLabelInactive",  "0xA0A0A0");
    // spotless:on
}
