package codechicken.nei.recipe;

import static codechicken.lib.gui.GuiDraw.getStringWidth;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import codechicken.core.TaskProfiler.ProfilerResult;
import codechicken.lib.gui.GuiDraw;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.PositionedStack;
import codechicken.nei.api.IOverlayHandler;
import codechicken.nei.api.IRecipeOverlayRenderer;
import codechicken.nei.util.AsyncTaskProfiler;

public class ProfilerRecipeHandler implements ICraftingHandler, IUsageHandler {

    public static class RecipeProfiler {

        private final AsyncTaskProfiler profiler = new AsyncTaskProfiler();
        private final Map<String, IRecipeHandler> handlerMap = new ConcurrentHashMap<>();
        private final AtomicInteger idCounter = new AtomicInteger(0);
        private boolean isProfiling = false;

        public <T extends IRecipeHandler> ArrayList<T> start(String profilerSection, Supplier<ArrayList<T>> callback) {
            profilerInfos.clear();
            this.handlerMap.clear();
            this.idCounter.set(0);

            if (NEIClientConfig.isProfileRecipeEnabled()) {
                this.profiler.clear();
                this.profiler.start(EnumChatFormatting.getTextWithoutFormattingCodes(profilerSection));
                this.isProfiling = true;

                try {
                    return callback.get();
                } finally {
                    this.profiler.end();
                    this.isProfiling = false;

                    final List<ProfilerResult> results = this.profiler.getResults();

                    results.sort(
                            Comparator.comparingLong((ProfilerResult profilerResult) -> profilerResult.time)
                                    .reversed());

                    for (ProfilerResult profilerResult : results) {
                        profilerInfos.add(new ProfilerInfo(handlerMap.get(profilerResult.name), profilerResult));
                    }
                }

            } else {
                return callback.get();
            }
        }

        public <T extends IRecipeHandler> T profile(T handler, Supplier<T> callback) {
            if (this.isProfiling && !(handler instanceof ProfilerRecipeHandler)) {
                final String id = String.valueOf(idCounter.incrementAndGet());
                this.handlerMap.put(id, handler);
                this.profiler.clearCurrent();
                this.profiler.start(id);

                try {
                    T result = callback.get();

                    if (result != null) {
                        this.handlerMap.put(id, result);
                    }

                    return result;
                } finally {
                    this.profiler.end();
                }
            } else {
                return callback.get();
            }
        }

    }

    protected static class ProfilerInfo {

        public final String modName;
        public final IRecipeHandler handler;
        public final List<String> tooltip = new ArrayList<>();
        public final String title;
        public final String time;

        public ProfilerInfo(IRecipeHandler handler, ProfilerResult result) {
            this.handler = handler;
            this.time = (result.time < 1_000_000 ? (result.time / 1_000) + "us" : (result.time / 1_000_000) + "ms");

            if (handler == null) {
                this.title = result.name;
                this.modName = "Unknown";
            } else {
                final HandlerInfo handlerInfo = GuiRecipeTab.getHandlerInfo(handler);
                final String handlerName = handlerInfo != null ? handlerInfo.getHandlerName() : "Unknown";

                this.title = EnumChatFormatting.getTextWithoutFormattingCodes(handler.getRecipeName());
                this.modName = handlerInfo != null ? handlerInfo.getModName() : "Unknown";

                this.tooltip.add(EnumChatFormatting.GRAY + "HandlerName: " + handlerName);
                this.tooltip.add(EnumChatFormatting.GRAY + "HandlerID: " + handler.getOverlayIdentifier());
                this.tooltip.add(EnumChatFormatting.GRAY + "HandlerOrder: " + NEIClientConfig.getHandlerOrder(handler));
            }

        }

    }

    public static final RecipeProfiler recipeProfiler = new RecipeProfiler();
    protected static final List<ProfilerInfo> profilerInfos = new ArrayList<>();
    private static final int WIDTH = 166;
    private final boolean crafting;

    public ProfilerRecipeHandler(boolean crafting) {
        this.crafting = crafting;
    }

    @Override
    public String getRecipeName() {
        return NEIClientUtils.translate("recipe.profiler." + (crafting ? "crafting" : "usage"));
    }

    @Override
    public int numRecipes() {
        return NEIClientConfig.isProfileRecipeEnabled() ? Math.max(1, profilerInfos.size()) : 0;
    }

    @Override
    public void drawBackground(int recipe) {}

    @Override
    public void drawForeground(int recipe) {
        if (recipe >= profilerInfos.size()) return;

        final ProfilerInfo info = profilerInfos.get(recipe);
        final int valueWidth = getStringWidth(info.time);
        final IRecipeHandler handler = info.handler;
        final boolean hovered = handler != null && handler.numRecipes() > 0
                && isHandlerTitleHovered(recipe, GuiDraw.getMousePosition());

        GuiDraw.drawString(
                NEIClientUtils.cropText(GuiDraw.fontRenderer, info.title, WIDTH - valueWidth - 2),
                0,
                3,
                hovered ? 0xFFFFDD00 : 0xFF808080,
                false);
        GuiDraw.drawString(info.time, WIDTH - valueWidth, 3, 0xFF404040, false);
    }

    private boolean isHandlerTitleHovered(int recipe, Point mouse) {
        final GuiContainer guiContainer = NEIClientUtils.getGuiContainer();

        if (guiContainer instanceof GuiRecipe guiRecipe) {
            final Point recipePosition = guiRecipe.getRecipePosition(recipe);
            return new Rectangle(recipePosition.x, recipePosition.y, WIDTH, 16)
                    .contains(mouse.x - guiContainer.guiLeft, mouse.y - guiContainer.guiTop);
        } else {
            return false;
        }
    }

    @Override
    public ArrayList<PositionedStack> getIngredientStacks(int recipe) {
        return new ArrayList<>();
    }

    @Override
    public ArrayList<PositionedStack> getOtherStacks(int recipe) {
        return new ArrayList<>();
    }

    @Override
    public PositionedStack getResultStack(int recipe) {
        return null;
    }

    @Override
    public void onUpdate() {}

    @Override
    public boolean hasOverlay(GuiContainer gui, Container container, int recipe) {
        return false;
    }

    @Override
    public IRecipeOverlayRenderer getOverlayRenderer(GuiContainer gui, int recipe) {
        return null;
    }

    @Override
    public IOverlayHandler getOverlayHandler(GuiContainer gui, int recipe) {
        return null;
    }

    @Override
    public List<String> handleTooltip(GuiRecipe<?> gui, List<String> currenttip, int recipe) {
        if (recipe >= profilerInfos.size()) return currenttip;

        final ProfilerInfo r = profilerInfos.get(recipe);

        currenttip.add(r.title + ": " + r.time);

        if (r.handler != null) {
            currenttip.add(EnumChatFormatting.GRAY + "Recipes: " + r.handler.numRecipes() + GuiDraw.TOOLTIP_LINESPACE);

            if (NEIClientUtils.shiftKey()) {
                currenttip.addAll(r.tooltip);
            }

            currenttip.add(EnumChatFormatting.BLUE + r.modName);
        }

        return currenttip;
    }

    @Override
    public List<String> handleItemTooltip(GuiRecipe<?> gui, ItemStack stack, List<String> currenttip, int recipe) {
        return currenttip;
    }

    @Override
    public boolean keyTyped(GuiRecipe<?> gui, char keyChar, int keyCode, int recipe) {
        return false;
    }

    @Override
    public boolean mouseClicked(GuiRecipe<?> gui, int button, int recipe) {
        if (recipe >= profilerInfos.size()) return false;

        final ProfilerInfo r = profilerInfos.get(recipe);

        if (r.handler != null) {
            final GuiContainer guiContainer = NEIClientUtils.getGuiContainer();

            if (guiContainer instanceof GuiRecipe guiRecipe) {
                int index = guiRecipe.currenthandlers.indexOf(r.handler);

                if (index != -1) {
                    guiRecipe.setRecipePage(index);
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public IUsageHandler getUsageHandler(String inputId, Object... ingredients) {
        return this;
    }

    @Override
    public ICraftingHandler getRecipeHandler(String outputId, Object... results) {
        return this;
    }
}
