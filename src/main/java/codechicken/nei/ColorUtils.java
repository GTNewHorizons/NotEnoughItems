package codechicken.nei;

import com.gtnewhorizon.gtnhlib.color.ColorResource;

public class ColorUtils {

    private static final ColorResource.Factory color = new ColorResource.Factory("nei");

    public static final ColorResource
    // spotless:off
        textGray            = color.rgb("textGray",             "#404040"),
        textLightGray       = color.rgb("textLightGray",        "#606060"),
        recipeTitle         = color.rgb("recipeTitle",          "#FFFF55"),
        recipeTitleHover    = color.rgb("recipeTitleHover",     "#FFFFFF"),
        subsetWidget        = color.rgb("subsetWidget",         "#AA00AA"),
        recipeBadge         = color.rgb("recipeBadge",          "#FDD835"),
        buttonLabelNormal   = color.rgb("buttonLabelNormal",    "#E0E0E0"),
        buttonLabelHover    = color.rgb("buttonLabelHover",     "#FFFFA0"),
        buttonLabelDisabled = color.rgb("buttonLabelDisabled",  "#601010"),
        buttonLabelInactive = color.rgb("buttonLabelInactive",  "#A0A0A0");
    // spotless:on
}
