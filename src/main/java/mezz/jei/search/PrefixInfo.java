package mezz.jei.search;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Supplier;

import net.minecraft.item.ItemStack;

import codechicken.nei.NEIClientConfig;
import it.unimi.dsi.fastutil.chars.Char2ObjectArrayMap;
import it.unimi.dsi.fastutil.chars.Char2ObjectMap;
import mezz.jei.util.StringUtil;

public class PrefixInfo implements Comparable<PrefixInfo> {

    public static final PrefixInfo NO_PREFIX;

    private static final Char2ObjectMap<PrefixInfo> instances = new Char2ObjectArrayMap<>(6);

    static {
        NO_PREFIX = new PrefixInfo(
                '\0',
                -1,
                true,
                "default",
                () -> NEIClientConfig.SearchMode.ENABLED,
                i -> Collections.singleton(i.getDisplayName().toLowerCase()),
                GeneralizedSuffixTree::new);
        addPrefix(
                new PrefixInfo(
                        '#',
                        0,
                        true,
                        false,
                        "tooltip",
                        NEIClientConfig::getTooltipSearchMode,
                        IIngredientListElement::getTooltipStrings,
                        GeneralizedSuffixTree::new));
        // addPrefix(new PrefixInfo('&', 1, false, "resource_id", NEIClientConfig::getResourceIdSearchMode, e ->
        // Collections.singleton(e.getResourceId()), GeneralizedSuffixTree::new));
        addPrefix(
                new PrefixInfo(
                        '^',
                        2,
                        true,
                        "color",
                        NEIClientConfig::getColorSearchMode,
                        IIngredientListElement::getColorStrings,
                        LimitedStringStorage::new));
        addPrefix(
                new PrefixInfo(
                        '$',
                        3,
                        false,
                        "oredict",
                        NEIClientConfig::getOreDictSearchMode,
                        IIngredientListElement::getOreDictStrings,
                        LimitedStringStorage::new));
        addPrefix(
                new PrefixInfo(
                        '@',
                        4,
                        false,
                        "mod_name",
                        NEIClientConfig::getModNameSearchMode,
                        IIngredientListElement::getModNameStrings,
                        LimitedStringStorage::new));
        addPrefix(
                new PrefixInfo(
                        '%',
                        5,
                        true,
                        "creative_tab",
                        NEIClientConfig::getCreativeTabSearchMode,
                        IIngredientListElement::getCreativeTabsStrings,
                        LimitedStringStorage::new));
    }

    private static void addPrefix(PrefixInfo info) {
        instances.put(info.getPrefix(), info);
    }

    public static Collection<PrefixInfo> all() {
        return Collections.unmodifiableCollection(instances.values());
    }

    public static PrefixInfo get(char ch) {
        return instances.get(ch);
    }

    private final char prefix;
    private final int priority;
    private final boolean potentialDialecticInclusion, async;
    private final String desc;
    private final IModeGetter modeGetter;
    private final IStringsGetter stringsGetter;
    private final Supplier<ISearchStorage<IIngredientListElement<ItemStack>>> storage;

    public PrefixInfo(char prefix, int priority, boolean potentialDialecticInclusion, String desc,
            IModeGetter modeGetter, IStringsGetter stringsGetter,
            Supplier<ISearchStorage<IIngredientListElement<ItemStack>>> storage) {
        this(prefix, priority, potentialDialecticInclusion, true, desc, modeGetter, stringsGetter, storage);
    }

    public PrefixInfo(char prefix, int priority, boolean potentialDialecticInclusion, boolean async, String desc,
            IModeGetter modeGetter, IStringsGetter stringsGetter,
            Supplier<ISearchStorage<IIngredientListElement<ItemStack>>> storage) {
        this.prefix = prefix;
        this.priority = priority;
        this.potentialDialecticInclusion = potentialDialecticInclusion;
        this.async = async;
        this.desc = desc;
        this.modeGetter = modeGetter;
        this.stringsGetter = stringsGetter;
        this.storage = storage;
    }

    public char getPrefix() {
        return prefix;
    }

    public int getPriority() {
        return priority;
    }

    public boolean hasPotentialDialecticInclusion() {
        return potentialDialecticInclusion;
    }

    public boolean isAsyncable() {
        return this.async;
    }

    public String getDesc() {
        return desc;
    }

    public NEIClientConfig.SearchMode getMode() {
        return modeGetter.getMode();
    }

    public ISearchStorage<IIngredientListElement<ItemStack>> createStorage() {
        return this.storage.get();
    }

    public Collection<String> getStrings(IIngredientListElement<ItemStack> element) {
        if (!NEIClientConfig.getSearchStrippedDiacritics() || !this.potentialDialecticInclusion) {
            return this.stringsGetter.getStrings(element);
        }
        Collection<String> strings = this.stringsGetter.getStrings(element);
        Collection<String> newStrings = null;
        for (String string : strings) {
            for (int i = 0; i < string.length(); i++) {
                if (string.charAt(i) > 0x7F) {
                    String stripped = StringUtil.stripAccents(string);
                    if (!stripped.equals(string)) {
                        if (newStrings == null) {
                            newStrings = new ArrayList<>(strings);
                        }
                        newStrings.add(stripped);
                    }
                    break;
                }
            }
        }
        return newStrings == null ? strings : newStrings;
    }

    @Override
    public int compareTo(PrefixInfo o) {
        return Integer.compare(o.priority, this.priority);
    }

    @FunctionalInterface
    public interface IStringsGetter {

        Collection<String> getStrings(IIngredientListElement<ItemStack> element);
    }

    @FunctionalInterface
    public interface IModeGetter {

        NEIClientConfig.SearchMode getMode();
    }

    @Override
    public String toString() {
        return "PrefixInfo{" + desc + '}';
    }

}
