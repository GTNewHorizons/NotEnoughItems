package mezz.jei.search;

import java.util.Collection;
import java.util.Set;

import net.minecraft.item.ItemStack;

public interface IElementSearch {

    void add(IIngredientListElement<ItemStack> ingredient);

    void addAll(Collection<IIngredientListElement<ItemStack>> ingredients);

    Collection<IIngredientListElement<ItemStack>> getAllIngredients();

    Set<IIngredientListElement<ItemStack>> getSearchResults(TokenInfo tokenInfo);

    @SuppressWarnings("unused") // used for debugging
    void logStatistics();

}
