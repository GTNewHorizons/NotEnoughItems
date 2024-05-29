package codechicken.nei.search;

import java.util.regex.Pattern;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import codechicken.nei.NEIClientConfig;
import codechicken.nei.api.ItemFilter;
import codechicken.nei.api.ItemInfo;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.common.registry.GameRegistry.UniqueIdentifier;

public class ModNameFilter implements ItemFilter {

    private final Pattern pattern;

    public ModNameFilter(Pattern pattern) {
        this.pattern = pattern;
    }

    @Override
    public boolean matches(ItemStack itemStack) {
        return this.pattern.matcher(getModId(itemStack.getItem())).find();
    }

    protected static String getModId(Item item) {

        if (!ItemInfo.itemOwners.containsKey(item)) {
            try {
                UniqueIdentifier ident = GameRegistry.findUniqueIdentifierFor(item);
                ItemInfo.itemOwners.put(item, ident.modId);
            } catch (Exception ignored) {
                NEIClientConfig.logger.error("Failed to find identifier for: " + item);
                ItemInfo.itemOwners.put(item, "Unknown");
            }
        }

        return ItemInfo.itemOwners.get(item);
    }

}
