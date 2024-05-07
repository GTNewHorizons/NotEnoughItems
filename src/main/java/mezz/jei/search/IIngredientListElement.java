package mezz.jei.search;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface IIngredientListElement<V> {

    V getIngredient();

    int getOrderIndex();

    IIngredientHelper<V> getIngredientHelper();

    String getDisplayName();

    String getModNameForSorting();

    Set<String> getModNameStrings();

    List<String> getTooltipStrings();

    Collection<String> getOreDictStrings();

    Collection<String> getCreativeTabsStrings();

    Collection<String> getColorStrings();

    boolean isVisible();

    void setVisible(boolean visible);

    default int getOrdinal() {
        return 0; // Preserve compatibility
    }
}
