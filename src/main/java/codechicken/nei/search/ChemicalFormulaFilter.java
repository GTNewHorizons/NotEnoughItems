package codechicken.nei.search;

import java.util.List;
import java.util.regex.Pattern;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import codechicken.nei.ItemStackMap;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.api.ItemFilter;

public class ChemicalFormulaFilter implements ItemFilter {

    private static final ItemStackMap<String> itemSearchNames = new ItemStackMap<>();

    private final Pattern pattern;

    public ChemicalFormulaFilter(Pattern pattern) {
        this.pattern = pattern;
    }

    @Override
    public boolean matches(ItemStack itemStack) {
        return pattern.matcher(getSearchFormula(itemStack)).find();
    }

    public static void clearCache() {
        itemSearchNames.clear();
    }

    public static void putItem(ItemStack stack) {
        String chemicalFormula = getChemicalFormula(stack.copy());
        synchronized (itemSearchNames) {
            itemSearchNames.put(stack, chemicalFormula);
        }
    }

    public static String getSearchFormula(ItemStack stack) {
        String chemicalFormula = itemSearchNames.get(stack);

        if (chemicalFormula == null) {
            chemicalFormula = getChemicalFormula(stack.copy());

            synchronized (itemSearchNames) {
                itemSearchNames.put(stack, chemicalFormula);
            }
        }

        return chemicalFormula;
    }

    private static String getChemicalFormula(ItemStack itemstack) {

        try {
            // TODO: Implement heuristics for getting the formula
            List<String> namelist = itemstack.getTooltip(NEIClientUtils.mc().thePlayer, false);

            if (namelist.size() > 1) {
                return EnumChatFormatting
                        .getTextWithoutFormattingCodes(String.join("\n", namelist.subList(1, namelist.size())));
            }

        } catch (Throwable ignored) {}

        return "";
    }

}
