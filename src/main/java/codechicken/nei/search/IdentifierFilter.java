package codechicken.nei.search;

import java.util.regex.Pattern;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import codechicken.nei.api.ItemFilter;
import cpw.mods.fml.common.registry.GameData;

public class IdentifierFilter implements ItemFilter {

    private final Pattern pattern;

    public IdentifierFilter(Pattern pattern) {
        this.pattern = pattern;
    }

    @Override
    public boolean matches(ItemStack stack) {
        return this.pattern.matcher(getStringIdentifier(stack) + "\n" + getIdentifier(stack)).find();
    }

    protected String getIdentifier(ItemStack stack) {
        return Item.getIdFromItem(stack.getItem()) + ":" + stack.getItemDamage();
    }

    protected String getStringIdentifier(ItemStack stack) {
        String name = GameData.getItemRegistry().getNameForObject(stack.getItem());
        return name == null || name.isEmpty() ? "Unknown:Unknown" : name;
    }

}
