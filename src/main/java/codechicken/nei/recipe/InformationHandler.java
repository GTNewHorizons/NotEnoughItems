package codechicken.nei.recipe;

import static net.minecraftforge.oredict.OreDictionary.itemMatches;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import codechicken.nei.ClientHandler;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.PositionedStack;
import codechicken.nei.api.ItemFilter;
import codechicken.nei.util.ItemStackFilterParser;

public class InformationHandler extends TemplateRecipeHandler {

    private static final List<InformationPage> ITEM_INFO = new ArrayList<>();

    public static void addInformationPage(String filter, String description) {
        if (filter.isEmpty() || description.isEmpty()) return;
        ITEM_INFO.add(new InformationPage(filter, description));
    }

    public static void populateStacks(ItemStack stack) {
        for (InformationPage page : ITEM_INFO) {
            page.addItem(stack);
        }
    }

    @Override
    public void drawExtras(int recipe) {
        CachedInfoPage page = (CachedInfoPage) this.arecipes.get(recipe);
        drawWrappedText(StatCollector.translateToLocal(page.getPage().info).replace("\\n", "\n"), 4, 24);
    }

    private void drawWrappedText(String text, int x, int y) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        List<String> lines = font.listFormattedStringToWidth(text, 156);
        for (String line : lines) {
            font.drawString(line, x, y, 0);
            y += 10;
        }
    }

    public static void clearCache() {
        for (InformationPage page : ITEM_INFO) {
            page.items.clear();
        }
    }

    @Override
    public String getRecipeName() {
        return NEIClientUtils.translate("recipe.information");
    }

    @Override
    public String getOverlayIdentifier() {
        return "information";
    }

    @Override
    public void loadCraftingRecipes(ItemStack result) {
        for (InformationPage page : ITEM_INFO) {
            for (ItemStack stack : page.items) {
                if (itemMatches(stack, result, false)) {
                    arecipes.add(new CachedInfoPage(page));
                    break;
                }
            }
        }
    }

    @Override
    public void loadUsageRecipes(ItemStack ingredient) {
        loadCraftingRecipes(ingredient);
    }

    @Override
    public String getGuiTexture() {
        return "nei:textures/gui/recipebg.png";
    }

    private class CachedInfoPage extends CachedRecipe {

        private final InformationPage page;
        private final PositionedStack stack;

        public CachedInfoPage(InformationPage page) {
            this.page = page;
            stack = new PositionedStack(page.items, 75, 2);
        }

        @Override
        public PositionedStack getResult() {
            return null;
        }

        @Override
        public PositionedStack getIngredient() {
            stack.setPermutationToRender((cycleticks / 20) % stack.getFilteredPermutations().size());
            return stack;
        }

        public InformationPage getPage() {
            return page;
        }
    }

    private static class InformationPage {

        final List<ItemStack> items = new ArrayList<>();
        final String info;
        final ItemFilter filter;

        public InformationPage(String filter, String info) {
            this.filter = ItemStackFilterParser.parse(filter.trim());
            this.info = info;
        }

        /**
         * Adds the item stack if it matches the filter.
         */
        public void addItem(ItemStack stack) {
            if (filter.matches(stack)) items.add(stack);
        }
    }

    public static void load() {
        ClientHandler.loadSettingsFile(
                "informationpages.cfg",
                lines -> parseFile(lines.collect(Collectors.toCollection(ArrayList::new))));
    }

    private static void parseFile(List<String> lines) {
        for (String rawLine : lines) {
            String line = rawLine.trim();

            int sepIndex = line.indexOf('=');
            if (sepIndex == -1) {
                NEIClientConfig.logger.warn("[NEI Info] Invalid line (no '=') in config: {}", line);
                continue;
            }

            String filter = line.substring(0, sepIndex).trim();
            String description = line.substring(sepIndex + 1).trim();
            addInformationPage(filter, description);
        }
    }
}
