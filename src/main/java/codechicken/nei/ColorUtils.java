package codechicken.nei;

import com.gtnewhorizon.gtnhlib.color.ColorResource;

public class ColorUtils {

    private static final ColorResource.Factory color = new ColorResource.Factory("nei");

    public static final ColorResource
    // spotless:off
        textGray            = color.rgb("textGray",             "0x404040"),
        textLightGray       = color.rgb("textLightGray",        "0x606060"),
        recipeTitle         = color.rgb("recipeTitle",          "0xFFFFFF"),
        recipeTitleBG       = color.argb("recipeTitleBG",       "0x30000000"),
        recipeTitleHover    = color.rgb("recipeTitleHover",     "0xFFFF55"),
        recipeBadge         = color.rgb("recipeBadge",          "0xFDD835"),

        subsetWidget        = color.rgb("subsetWidget",         "0xAA00AA"),
        buttonLabelNormal   = color.rgb("buttonLabelNormal",    "0xE0E0E0"),
        buttonLabelHover    = color.rgb("buttonLabelHover",     "0xFFFFA0"),
        buttonLabelDisabled = color.rgb("buttonLabelDisabled",  "0x601010"),
        buttonLabelInactive = color.rgb("buttonLabelInactive",  "0xA0A0A0"),

        buttonIconNormal                 = color.argb("buttonIconNormal",                "0xFFFFFFFF"),
        buttonIconHover                  = color.argb("buttonIconHover",                 "0xFFFFFFA0"),
        buttonIconActive                 = color.argb("buttonIconActive",                "0xFFFFFFA0"),
        buttonIconDisabled               = color.argb("buttonIconDisabled",              "0xFF808080"),

        buttonIconFavoriteNormal         = color.argb("buttonIconFavoriteNormal",        "0xFF75C775"),
        buttonIconFavoriteHover          = color.argb("buttonIconFavoriteHover",         "0xFF9ED89E"),
        buttonIconFavoriteActive         = color.argb("buttonIconFavoriteActive",        "0xFF9ED89E"),
        buttonIconFavoriteDisabled       = color.argb("buttonIconFavoriteDisabled",      "0xFF3A633A"),

        buttonIconOverlayErrorNormal     = color.argb("buttonIconOverlayErrorNormal",    "0xFFD14545"),
        buttonIconOverlayErrorHover      = color.argb("buttonIconOverlayErrorHover",     "0xFFDF7D7D"),
        buttonIconOverlayErrorActive     = color.argb("buttonIconOverlayErrorActive",    "0xFFDF7D7D"),
        buttonIconOverlayErrorDisabled   = color.argb("buttonIconOverlayErrorDisabled",  "0xFF682222"),

        craftingTreeLine    = color.argb("craftingTreeLine",    "0xFF888888");
    // spotless:on
}
