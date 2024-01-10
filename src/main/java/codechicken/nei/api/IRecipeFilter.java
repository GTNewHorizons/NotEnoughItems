package codechicken.nei.api;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;

public interface IRecipeFilter {

    public static interface IRecipeFilterProvider {

        static HashMap<String, Boolean> override = new HashMap<>();

        static boolean filteringAvailable(IRecipeHandler handler) {
            return handler instanceof TemplateRecipeHandler && !overrideMethod(handler.getClass().getName());
        }

        static boolean overrideMethod(String className) {
            try {

                if (override.containsKey(className)) {
                    return override.get(className);
                }

                String[] methods = new String[] { "getResultStack", "getOtherStacks", "getIngredientStacks" };
                Class<?> cls = Class.forName(className);

                for (String method : methods) {
                    Method m = cls.getMethod(method, Integer.TYPE);
                    if (!m.getDeclaringClass().getName().equals("codechicken.nei.recipe.TemplateRecipeHandler")) {
                        override.put(className, true);
                        return true;
                    }
                }

                Method m = cls.getMethod("numRecipes");
                if (!m.getDeclaringClass().getName().equals("codechicken.nei.recipe.TemplateRecipeHandler")) {
                    override.put(className, true);
                    return true;
                }

                override.put(className, false);
                return false;
            } catch (Throwable e) {
                return true;
            }
        }

        public IRecipeFilter getFilter();
    }

    public boolean matches(IRecipeHandler handler, List<PositionedStack> ingredients, PositionedStack result,
            List<PositionedStack> others);

}
