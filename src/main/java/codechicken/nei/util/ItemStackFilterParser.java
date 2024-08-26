package codechicken.nei.util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.IntStream;

import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.oredict.OreDictionary;

import codechicken.nei.ItemList.AllMultiItemFilter;
import codechicken.nei.ItemList.AnyMultiItemFilter;
import codechicken.nei.ItemList.NegatedItemFilter;
import codechicken.nei.api.ItemFilter;
import cpw.mods.fml.common.registry.FMLControlledNamespacedRegistry;
import cpw.mods.fml.common.registry.GameData;

/**
 * @formatter:off
 *
 * parts:
 * modname:itemid    - identify
 * $orename          - ore dictionary
 * tag.color=red     - tag
 * 0 or 0-12         - damage
 *
 * modifiers:
 * ! - logical not. exclude items that match the following expression (!minecraft:portal)
 * r/.../ - standard java regex (r/^m\w{6}ft$/ = minecraft)
 * , - logical or in token (minecraft:potion 16384-16462,!16386)
 * | - logical or multi-item search (wrench|hammer)
 *
 *
 * example: minecraft:potion 16384-16462,!16386 | $oreiron | tag.color=red
 */
public class ItemStackFilterParser {

    private ItemStackFilterParser() {}

    public static ItemFilter parse(String filterText) {
        final List<ItemFilter> searchTokens = new ArrayList<>();
        filterText = filterText.trim();

        if (!filterText.isEmpty()) {
            for (String part : filterText.split("\\s*\\|\\s*")) {
                AllMultiItemFilter filter = parsePart(part);
                if (!filter.filters.isEmpty()) {
                    searchTokens.add(filter);
                }
            }
        }

        if (searchTokens.isEmpty()) {
            return null;
        } else if (searchTokens.size() == 1) {
            return searchTokens.get(0);
        } else {
            return new AnyMultiItemFilter(searchTokens);
        }
    }

    private static AllMultiItemFilter parsePart(String part) {
        final AllMultiItemFilter searchTokens = new AllMultiItemFilter();

        for (String token : part.split("\\s+")) {
            ItemFilter ruleFilter = parseRules(token);

            if (ruleFilter != null) {
                searchTokens.filters.add(ruleFilter);
            }
        }

        return searchTokens;
    }

    protected static ItemFilter parseRules(String token) {
        final AnyMultiItemFilter orFilter = new AnyMultiItemFilter();
        final AnyMultiItemFilter orNotFilter = new AnyMultiItemFilter();
        final AllMultiItemFilter ruleFilter = new AllMultiItemFilter();

        for (String rule : token.split(",")) {
            boolean ignore = rule.startsWith("!");
            ItemFilter filter = null;

            if (ignore) {
                rule = rule.substring(1);
            }

            if (rule.startsWith("$")) {
                filter = getOreDictFilter(rule.substring(1));
            } else if (rule.startsWith("tag.")) {
                filter = getTagFilter(rule.substring(4));
            } else if (Pattern.matches("^\\d+(-\\d+)?$", rule)) {
                filter = getDamageFilter(rule);
            } else {
                filter = getStringIdentifierFilter(rule);
            }

            if (filter == null) {
                continue;
            }

            if (ignore) {
                orNotFilter.filters.add(filter);
            } else {
                orFilter.filters.add(filter);
            }
        }

        if (!orFilter.filters.isEmpty()) {
            ruleFilter.filters.add(orFilter);
        }

        if (!orNotFilter.filters.isEmpty()) {
            ruleFilter.filters.add(new NegatedItemFilter(orNotFilter));
        }

        return ruleFilter.filters.isEmpty() ? null : ruleFilter;
    }

    protected static Predicate<String> getMatcher(String searchText) {

        if (searchText.length() >= 3 && searchText.startsWith("r/") && searchText.endsWith("/")) {

            try {
                Pattern pattern = Pattern.compile(
                        searchText.substring(2, searchText.length() - 1),
                        Pattern.MULTILINE | Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
                return value -> pattern.matcher(value).find();
            } catch (PatternSyntaxException ignored) {}

        } else if (!searchText.isEmpty()) {
            final String lowerCase = searchText.toLowerCase();
            return value -> value.toLowerCase().contains(lowerCase);
        }

        return null;
    }

    protected static ItemFilter getOreDictFilter(String rule) {
        final Predicate<String> matcher = getMatcher(rule);

        if (matcher == null) {
            return null;
        }

        return stack -> IntStream.of(OreDictionary.getOreIDs(stack))
                .anyMatch(id -> matcher.test(OreDictionary.getOreName(id)));
    }

    protected static ItemFilter getTagFilter(String rule) {
        final String[] parts = rule.split("=", 2);
        final String[] path = parts[0].split("\\.");
        final Predicate<String> value = getMatcher(parts[1]);

        return stack -> {
            Object tag = stack.getTagCompound();

            for (int i = 0; i < path.length && tag != null; i++) {
                if (tag instanceof NBTTagCompound) {
                    tag = ((NBTTagCompound) tag).getTag(path[i]);
                } else if (tag instanceof NBTTagList) {
                    tag = ((NBTTagList) tag).tagList.get(Integer.parseInt(path[i]));
                } else {
                    tag = null;
                }
            }

            return tag == null ? value == null : value != null && value.test(tag.toString());
        };
    }

    protected static ItemFilter getDamageFilter(String rule) {
        final String[] range = rule.split("-");
        final IntPredicate matcher;

        if (range.length == 1) {
            final int damage = Integer.parseInt(range[0]);
            matcher = dmg -> dmg == damage;
        } else {
            final int damageStart = Integer.parseInt(range[0]);
            final int damageEnd = Integer.parseInt(range[1]);
            matcher = dmg -> dmg >= damageStart && dmg <= damageEnd;
        }

        return stack -> matcher.test(stack.getItemDamage());
    }

    protected static ItemFilter getStringIdentifierFilter(String rule) {
        final FMLControlledNamespacedRegistry<Item> iItemRegistry = GameData.getItemRegistry();
        final Predicate<String> matcher = getMatcher(rule);

        return stack -> {
            String name = iItemRegistry.getNameForObject(stack.getItem());
            return name != null && !name.isEmpty() && matcher.test(name);
        };
    }
}
