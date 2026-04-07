package codechicken.nei.recipe;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import net.minecraft.util.EnumChatFormatting;

import codechicken.lib.vec.Rectangle4i;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.NEIClientUtils.Alignment;

public class Badge {

    protected final String text;
    protected final List<String> tooltip;
    protected int badgeTextColor = 0xFDD835;
    protected boolean shadow = false;
    protected Alignment alignment = Alignment.TopLeft;

    public Badge(String text, String... tooltip) {
        this.text = text;
        this.tooltip = Arrays.stream(tooltip).map(t -> EnumChatFormatting.GRAY + t).collect(Collectors.toList());
        this.badgeTextColor = getHexValue(
                NEIClientUtils.getTextColorOrDefault("recipe.badge.color", "0xFDD835"),
                0xFDD835);
    }

    public String getText() {
        return this.text;
    }

    public List<String> getTooltip() {
        return this.tooltip;
    }

    public Badge setColor(int color) {
        this.badgeTextColor = color;
        return this;
    }

    public int getColor() {
        return this.badgeTextColor;
    }

    public Badge setShadow(boolean shadow) {
        this.shadow = shadow;
        return this;
    }

    public boolean hasShadow() {
        return this.shadow;
    }

    public Badge setAlignment(Alignment alignment) {
        this.alignment = alignment;
        return this;
    }

    public Alignment getAlignment() {
        return this.alignment;
    }

    private static int getHexValue(String color, int defaultValue) {
        try {
            return (int) Long.parseLong(color.replace("0x", ""), 16);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public void draw(Rectangle4i rect) {
        NEIClientUtils.drawNEIOverlayText(getText(), rect, 0.5f, getColor(), hasShadow(), getAlignment());
    }

    public static Badge notConsumed() {
        return new Badge(
                NEIClientUtils.translate("recipe.badge.nc"),
                NEIClientUtils.translate("recipe.badge.nc.tooltip"));
    }

    public static Badge notConsumedParallel() {
        return new Badge(
                NEIClientUtils.translate("recipe.badge.ncp"),
                NEIClientUtils.translate("recipe.badge.ncp.tooltip"));
    }

    public static Badge consumeChance(float chance) {
        final String chanceText = NEIClientUtils.formatChance(chance);
        return new Badge(chanceText, NEIClientUtils.translate("recipe.badge.chance.consume", chanceText));
    }

    public static Badge outputChance(float chance) {
        final String chanceText = NEIClientUtils.formatChance(chance);
        return new Badge(chanceText, NEIClientUtils.translate("recipe.badge.chance.output", chanceText));
    }

}
