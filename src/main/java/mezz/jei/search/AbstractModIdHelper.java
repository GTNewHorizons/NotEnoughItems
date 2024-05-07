package mezz.jei.search;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractModIdHelper implements IModIdHelper {

    @Override
    public <T> String getModNameForIngredient(T ingredient, IIngredientHelper<T> ingredientHelper) {
        String modId = ingredientHelper.getModId(ingredient);
        return getModNameForModId(modId);
    }

    @Override
    public <T> List<String> addModNameToIngredientTooltip(List<String> tooltip, T ingredient,
            IIngredientHelper<T> ingredientHelper) {
        String modNameFormat = "9o";

        String modId = ingredientHelper.getDisplayModId(ingredient);
        String modName = getFormattedModNameForModId(modId);
        if (modName == null) {
            return tooltip;
        }
        List<String> tooltipCopy = new ArrayList<>(tooltip);
        tooltipCopy.add(modName);
        return tooltipCopy;
    }
}
