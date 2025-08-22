package codechicken.nei.search;

import java.util.regex.Pattern;
import java.util.stream.IntStream;

import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import codechicken.nei.api.ItemFilter;

public class OreDictionaryFilter implements ItemFilter {

    private final Pattern pattern;

    public OreDictionaryFilter(Pattern pattern) {
        this.pattern = pattern;
    }

    @Override
    public boolean matches(ItemStack stack) {
        return IntStream.of(OreDictionary.getOreIDs(stack))
                .anyMatch(id -> this.pattern.matcher(OreDictionary.getOreName(id)).find());
    }

}
