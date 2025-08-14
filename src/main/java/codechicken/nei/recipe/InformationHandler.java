package codechicken.nei.recipe;

import static net.minecraftforge.oredict.OreDictionary.itemMatches;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import codechicken.nei.PositionedStack;

public class InformationHandler extends TemplateRecipeHandler {

    private static final List<InformationPage> ITEM_INFO = new ArrayList<>();
    private ItemStack currentStack;

    public static void addInformationPage(ItemStack stack, String description) {
        if (stack == null || stack.getItem() == null || description.isEmpty()) return;
        ITEM_INFO.add(new InformationPage(stack, description));
    }

    @Override
    public void drawExtras(int recipe) {
        CachedInfoRecipe page = (CachedInfoRecipe) this.arecipes.get(recipe);
        if (page != null && !page.getIngredients().isEmpty()) {
            drawWrappedText(StatCollector.translateToLocal(page.getPage().info).replace("\\n", "\n"), 4, 24);
        }
    }

    private void drawWrappedText(String text, int x, int y) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        List<String> lines = font.listFormattedStringToWidth(text, 156);
        for (String line : lines) {
            font.drawString(line, x, y, 0);
            y += 10;
        }
    }

    @Override
    public String getRecipeName() {
        return StatCollector.translateToLocal("nei.recipe.information");
    }

    @Override
    public String getOverlayIdentifier() {
        return "information";
    }

    @Override
    public void loadCraftingRecipes(ItemStack result) {
        currentStack = result.copy();
        currentStack.stackSize = 1;
        for (InformationPage page : ITEM_INFO) {
            if (itemMatches(page.item, result, false)) {
                arecipes.add(new CachedInfoRecipe(page));
            }
        }
    }

    @Override
    public void loadUsageRecipes(ItemStack ingredient) {
        currentStack = ingredient.copy();
        currentStack.stackSize = 1;
        for (InformationPage page : ITEM_INFO) {
            if (itemMatches(page.item, ingredient, false)) {
                arecipes.add(new CachedInfoRecipe(page));
            }
        }
    }

    @Override
    public String getGuiTexture() {
        return new ResourceLocation("nei", "textures/gui/recipebg.png").toString();
    }

    private class CachedInfoRecipe extends CachedRecipe {

        private final InformationPage page;

        public CachedInfoRecipe(InformationPage page) {
            this.page = page;
        }

        @Override
        public PositionedStack getResult() {
            return null;
        }

        @Override
        public List<PositionedStack> getIngredients() {
            return Collections.singletonList(new PositionedStack(page.item, 75, 2));
        }

        public InformationPage getPage() {
            return page;
        }
    }

    private static class InformationPage {

        ItemStack item;
        String info;

        public InformationPage(ItemStack item, String info) {
            this.item = item;
            this.info = info;
        }
    }
}
