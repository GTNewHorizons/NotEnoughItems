package mezz.jei.search;

import java.util.Collection;

import net.minecraft.item.ItemStack;

public interface IBuildable {

    void start();

    void stop();

    void submit(IIngredientListElement<ItemStack> ingredient);

    void submitAll(Collection<IIngredientListElement<ItemStack>> ingredients);

}
