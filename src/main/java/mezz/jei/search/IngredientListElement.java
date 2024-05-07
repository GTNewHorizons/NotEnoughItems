package mezz.jei.search;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableSet;

import codechicken.nei.NEIClientConfig;
import cpw.mods.fml.relauncher.FMLLaunchHandler;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import mezz.jei.util.Translator;

public class IngredientListElement<V> implements IIngredientListElement<V> {

    public static ObjectOpenHashSet<String[]> canonicalizedStringArrays = new ObjectOpenHashSet<>();
    private static final Pattern SPACE_PATTERN = Pattern.compile("\\s");

    private final V ingredient;
    private final int orderIndex;
    private final IIngredientHelper<V> ingredientHelper;
    private final Object modIds; // Can be String or String[]
    private final Object modNames; // Can be String or String[]
    private final String displayName;
    private final int ordinal;

    private boolean visible = true;

    @Nullable
    public static <V> IngredientListElement<V> create(V ingredient, IIngredientHelper<V> ingredientHelper,
            IModIdHelper modIdHelper, int orderIndex) {
        try {
            return new IngredientListElement<>(ingredient, orderIndex, ingredientHelper, modIdHelper);
        } catch (RuntimeException e) {
            try {
                String ingredientInfo = ingredientHelper.getErrorInfo(ingredient);
                NEIClientConfig.logger.warn("Found a broken ingredient {}", ingredientInfo, e);
            } catch (RuntimeException e2) {
                NEIClientConfig.logger.warn("Found a broken ingredient.", e2);
            }
            return null;
        }
    }

    protected IngredientListElement(V ingredient, int orderIndex, IIngredientHelper<V> ingredientHelper,
            IModIdHelper modIdHelper) {
        this.ingredient = ingredient;
        this.orderIndex = orderIndex;
        this.ingredientHelper = ingredientHelper;
        String displayModId = ingredientHelper.getDisplayModId(ingredient);
        String modId = ingredientHelper.getModId(ingredient);
        this.modIds = modId.equals(displayModId) ? displayModId.intern()
                : canonicalizedStringArrays.addOrGet(new String[] { modId.intern(), displayModId.intern() });
        this.modNames = this.modIds instanceof String ? modIdHelper.getModNameForModId((String) this.modIds).intern()
                : canonicalizedStringArrays.addOrGet(
                        Arrays.stream((String[]) this.modIds).map(modIdHelper::getModNameForModId).map(String::intern)
                                .toArray(String[]::new));
        this.displayName = IngredientInformation.getDisplayName(ingredient, ingredientHelper);
        this.ordinal = ingredientHelper.getOrdinal(ingredient);
    }

    @Override
    public final V getIngredient() {
        return ingredient;
    }

    @Override
    public int getOrderIndex() {
        return orderIndex;
    }

    @Override
    public IIngredientHelper<V> getIngredientHelper() {
        return ingredientHelper;
    }

    @Override
    public final String getDisplayName() {
        return displayName;
    }

    @Override
    public String getModNameForSorting() {
        return this.modNames instanceof String ? (String) this.modNames : ((String[]) this.modNames)[0];
    }

    @Override
    public Set<String> getModNameStrings() {
        Set<String> modNameStrings = new ObjectArraySet<>();
        if (modIds instanceof String[]) {
            String[] modIdsCasted = (String[]) modIds;
            String[] modNamesCasted = (String[]) modNames;
            for (int i = 0; i < modIdsCasted.length; i++) {
                String modId = modIdsCasted[i];
                String modName = modNamesCasted[i];
                addModNameStrings(modNameStrings, modId, modName);
            }
        } else {
            addModNameStrings(modNameStrings, (String) modIds, (String) modNames);
        }
        return modNameStrings;
    }

    private static void addModNameStrings(Set<String> modNames, String modId, String modName) {
        String modNameLowercase = modName.toLowerCase(Locale.ENGLISH);
        String modNameNoSpaces = SPACE_PATTERN.matcher(modNameLowercase).replaceAll("");
        String modIdNoSpaces = SPACE_PATTERN.matcher(modId).replaceAll("");
        modNames.add(modId);
        modNames.add(modNameNoSpaces);
        modNames.add(modIdNoSpaces);
    }

    @Override
    public final List<String> getTooltipStrings() {
        String modId = this.modIds instanceof String ? (String) this.modIds : ((String[]) this.modIds)[0];
        String modName = this.modNames instanceof String ? (String) this.modNames : ((String[]) this.modNames)[0];
        String modNameLowercase = modName.toLowerCase(Locale.ENGLISH);
        String displayNameLowercase = Translator.toLowercaseWithLocale(this.displayName);
        return IngredientInformation
                .getTooltipStrings(ingredient, ImmutableSet.of(modId, modNameLowercase, displayNameLowercase));
    }

    @Override
    public Collection<String> getOreDictStrings() {
        Collection<String> oreDictNames = ingredientHelper.getOreDictNames(ingredient);
        return oreDictNames.stream().map(s -> s.toLowerCase(Locale.ENGLISH)).collect(Collectors.toList());
    }

    @Override
    public Collection<String> getCreativeTabsStrings() {
        Collection<String> creativeTabsStrings = ingredientHelper.getCreativeTabNames(ingredient);
        return creativeTabsStrings.stream().map(Translator::toLowercaseWithLocale).collect(Collectors.toList());
    }

    @Override
    public Collection<String> getColorStrings() {
        return Collections.emptyList();
    }

    @Override
    public boolean isVisible() {
        if (visible) {
            return true;
        }
        if (FMLLaunchHandler.side().isClient()) {
            return true; // TODO Blacklist
        }
        return false;
    }

    @Override
    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    @Override
    public int getOrdinal() {
        return ordinal;
    }
}
