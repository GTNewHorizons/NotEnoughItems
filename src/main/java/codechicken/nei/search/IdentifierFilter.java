package codechicken.nei.search;

import java.util.regex.Pattern;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import codechicken.nei.api.ItemFilter;

public class IdentifierFilter implements ItemFilter {

    private final Pattern pattern;

    public IdentifierFilter(Pattern pattern) {
        this.pattern = pattern;
    }

    @Override
    public boolean matches(ItemStack itemStack) {
        return this.pattern.matcher(getIdentifier(itemStack)).find();
    }

    private String getIdentifier(ItemStack itemStack) {
        String mainname = String.valueOf(Item.getIdFromItem(itemStack.getItem()));

        if (itemStack.getItemDamage() != 0) {
            mainname += ":" + itemStack.getItemDamage();
        }

        return mainname;
    }

}
