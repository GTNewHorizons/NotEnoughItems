package codechicken.nei.recipe;

import static codechicken.nei.NEIClientUtils.translate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

import org.lwjgl.opengl.GL11;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.BookmarkPanel.BookmarkViewMode;
import codechicken.nei.FavoriteRecipes;
import codechicken.nei.Image;
import codechicken.nei.ItemPanels;
import codechicken.nei.ItemStackSet;
import codechicken.nei.LayoutManager;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.drawable.DrawableBuilder;
import codechicken.nei.drawable.DrawableResource;
import codechicken.nei.recipe.Recipe.RecipeId;
import codechicken.nei.recipe.Recipe.RecipeIngredient;
import codechicken.nei.util.NEIMouseUtils;

public class GuiFavoriteButton extends GuiRecipeButton {

    private static final int BUTTON_ID_START = 14;

    protected static final DrawableResource ICON_STATE_OFF = new DrawableBuilder(
            "nei:textures/nei_sprites.png",
            10,
            76,
            9,
            10).build();
    protected static final DrawableResource ICON_STATE_ON = new DrawableBuilder(
            "nei:textures/nei_sprites.png",
            19,
            76,
            9,
            10).build();

    protected final Recipe recipe;
    protected boolean favorite = false;
    protected RecipeIngredient favoriteResult = null;
    protected RecipeIngredient selectedResult = null;

    public GuiFavoriteButton(RecipeHandlerRef handlerRef, int x, int y) {
        super(handlerRef, x, y, BUTTON_ID_START + handlerRef.recipeIndex, "❤");
        this.recipe = Recipe.of(this.handlerRef);

        ItemStack stack = FavoriteRecipes.getFavorite(this.recipe.getRecipeId());
        this.favorite = stack != null;

        if (stack == null) {
            stack = this.recipe.getResult();
        }

        for (RecipeIngredient result : this.recipe.getResults()) {
            if (StackInfo.equalItemAndNBT(result.getItemStack(), stack, true)) {
                this.favoriteResult = result;
                break;
            }
        }

        this.visible = this.favoriteResult != null;
    }

    public boolean isFavorite() {
        return favorite;
    }

    public void toggleFavorite() {
        this.favorite = !this.favorite;

        if (this.selectedResult != null) {
            this.favoriteResult = this.selectedResult;
        }

        FavoriteRecipes
                .setFavorite(this.favoriteResult.getItemStack(), this.favorite ? this.recipe.getRecipeId() : null);
    }

    @Override
    protected void drawContent(Minecraft minecraft, int y, int x, boolean mouseOver) {
        final DrawableResource icon = isFavorite() ? ICON_STATE_ON : ICON_STATE_OFF;
        final int iconX = this.xPosition + (this.width - icon.width - 1) / 2;
        final int iconY = this.yPosition + (this.height - icon.height) / 2;

        GL11.glColor4f(1, 1, 1, this.enabled ? 1 : 0.5f);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        icon.draw(iconX, iconY);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(1, 1, 1, 1);

        if (!mouseOver) {
            this.selectedResult = null;
        } else if (this.selectedResult == null) {
            this.selectedResult = this.favoriteResult;
        }
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY) {
        toggleFavorite();
    }

    public Recipe getRecipe() {
        return Recipe.of(this.handlerRef);
    }

    public void lastKeyTyped(GuiRecipe<?> gui, char keyChar, int keyID) {

        if (NEIClientConfig.isKeyHashDown("gui.bookmark") && NEIClientUtils.shiftKey()) {
            ItemPanels.bookmarkPanel.addGroup(getRecipesTree(getRecipe()), BookmarkViewMode.TODO_LIST, true);
        }

    }

    @Override
    public Map<String, String> handleHotkeys(GuiContainer gui, int mousex, int mousey, Map<String, String> hotkeys) {
        hotkeys.put(
                NEIClientConfig.getKeyName("gui.bookmark", NEIClientUtils.SHIFT_HASH),
                translate("recipe.favorite.bookmark_recipe"));

        if (this.recipe.getResults().size() > 1) {
            hotkeys.put(
                    NEIMouseUtils.getKeyName(NEIMouseUtils.MOUSE_BTN_NONE + NEIMouseUtils.MOUSE_SCROLL),
                    translate("recipe.favorite.change_result_index"));
        }

        return hotkeys;
    }

    @Override
    public List<String> handleTooltip(GuiRecipe<?> gui, List<String> currenttip) {
        currenttip.add(translate("recipe.favorite"));

        return currenttip;
    }

    @Override
    public void drawItemOverlay() {
        if (this.selectedResult == null) return;

        NEIClientUtils.gl2DRenderContext(
                () -> GuiDraw.drawRect(this.selectedResult.relx, this.selectedResult.rely, 16, 16, 0x66333333));

        final Image icon = isFavorite() ? ICON_STATE_ON : ICON_STATE_OFF;
        final int iconX = this.selectedResult.relx + (16 - icon.width) / 2;
        final int iconY = this.selectedResult.rely + (18 - icon.height) / 2;

        LayoutManager.drawIcon(iconX, iconY, icon);
    }

    @Override
    public boolean mouseScrolled(GuiRecipe<?> gui, int scroll) {
        if (this.selectedResult == null) return true;

        final List<RecipeIngredient> results = new ArrayList<>(this.recipe.getResults());
        if (results.size() <= 1) return true;

        final Set<String> uniqueResults = new HashSet<>();
        results.removeIf(ingr -> !uniqueResults.add(StackInfo.getItemStackGUID(ingr.getItemStack())));
        final int nextIndex = (results.size() - scroll + results.indexOf(this.selectedResult)) % results.size();

        this.selectedResult = results.get(nextIndex);
        this.favorite = StackInfo.equalItemAndNBT(
                results.get(nextIndex).getItemStack(),
                FavoriteRecipes.getFavorite(this.recipe.getRecipeId()),
                true);

        return true;
    }

    private List<Recipe> getRecipesTree(Recipe mainRecipe) {
        int depth = NEIClientConfig.getIntSetting("inventory.favorites.depth");
        final ItemStackSet items = new ItemStackSet();
        final List<Recipe> result = new ArrayList<>();
        List<Recipe> loop = new ArrayList<>();
        loop.add(mainRecipe);
        result.add(mainRecipe);

        while (!loop.isEmpty() && depth-- >= 0) {
            List<Recipe> localLoop = new ArrayList<>();

            for (Recipe localRecipe : loop) {
                for (RecipeIngredient ingr : localRecipe.getIngredients()) {
                    List<ItemStack> permutations = ingr.getPermutations();
                    ItemStack stack = permutations.stream().filter(items::contains).findFirst().orElse(null);

                    if (stack == null) {
                        stack = ingr.getItemStack();
                        RecipeId recipeId = FavoriteRecipes.getFavorite(stack);

                        if (recipeId == null) {
                            for (ItemStack permStack : permutations) {
                                if ((recipeId = FavoriteRecipes.getFavorite(permStack)) != null) {
                                    stack = permStack;
                                    break;
                                }
                            }
                        }

                        Recipe recipe = Recipe.of(recipeId);

                        if (recipe == null) {
                            stack = null;
                        } else {
                            items.add(stack);

                            if (depth >= 0) {
                                result.add(recipe);
                                localLoop.add(recipe);
                            }
                        }
                    }

                    if (stack != null) {
                        ingr.setActiveIndex(getIngrActiveIndex(permutations, stack));
                    }
                }
            }

            loop = localLoop;
        }

        return result;
    }

    private int getIngrActiveIndex(List<ItemStack> permutations, ItemStack stack) {

        for (int i = 0; i < permutations.size(); i++) {
            if (StackInfo.equalItemAndNBT(stack, permutations.get(i), true)) {
                return i;
            }
        }

        return 0;
    }

}
