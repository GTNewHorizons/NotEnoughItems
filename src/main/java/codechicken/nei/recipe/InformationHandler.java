package codechicken.nei.recipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import org.lwjgl.opengl.GL11;

import codechicken.lib.gui.GuiDraw;
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
        final CachedInfoPage page = (CachedInfoPage) this.arecipes.get(recipe);
        drawWrappedText(page.getLines(), 4, 24);
    }

    private void drawWrappedText(List<String> lines, int x, int y) {
        final FontRenderer font = Minecraft.getMinecraft().fontRenderer;
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
            if (page.filter.matches(result)) {
                arecipes.add(new CachedInfoPage(page));
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

    @Override
    public void drawBackground(int recipe) {
        GL11.glColor4f(1, 1, 1, 1);
        GuiDraw.changeTexture(getGuiTexture());
        GuiDraw.drawTexturedModalRect(0, 0, 7, 13, 166, 65);
    }

    @Override
    public int getRecipeHeight(int recipe) {
        final CachedInfoPage page = (CachedInfoPage) this.arecipes.get(recipe);
        return 24 + page.getLines().size() * 10;
    }

    private class CachedInfoPage extends CachedRecipe {

        private final PositionedStack stack;
        private final List<String> lines;

        public CachedInfoPage(InformationPage page) {
            final FontRenderer font = Minecraft.getMinecraft().fontRenderer;
            final String info = StatCollector.translateToLocal(page.info).replace("\\n", "\n");

            this.lines = font.listFormattedStringToWidth(info, 156);
            this.stack = new PositionedStack(page.items, 75, 2);
        }

        @Override
        public PositionedStack getResult() {
            return null;
        }

        @Override
        public List<PositionedStack> getIngredients() {
            return Collections.singletonList(this.stack);
        }

        public List<String> getLines() {
            return lines;
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
