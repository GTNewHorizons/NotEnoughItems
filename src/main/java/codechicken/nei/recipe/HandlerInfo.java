package codechicken.nei.recipe;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import codechicken.nei.NEIClientConfig;
import codechicken.nei.NEIServerUtils;
import codechicken.nei.drawable.DrawableBuilder;
import codechicken.nei.drawable.DrawableResource;

public class HandlerInfo {

    public static int DEFAULT_HEIGHT = 65;
    public static int DEFAULT_WIDTH = 166;

    @Deprecated
    public static int DEFAULT_MAX_PER_PAGE = 1;

    private String handlerName;

    private String modName;
    private String modId;
    private boolean requiresMod;
    private String excludedModId;

    private int yShift = 0;
    private int height = DEFAULT_HEIGHT;
    private int width = DEFAULT_WIDTH;

    @Deprecated
    private int maxRecipesPerPage = DEFAULT_MAX_PER_PAGE;
    private boolean multipleWidgetsAllowed = false;
    private boolean showFavoritesButton = true;
    private boolean showOverlayButton = true;
    private boolean useCustomScroll = false;

    private ItemStack itemStack = null;
    private DrawableResource image = null;

    public HandlerInfo(String handlerName, String modName, String modId, boolean requiresMod, String excludedModId) {
        this.handlerName = handlerName;
        this.modName = modName;
        this.modId = modId;
        this.requiresMod = requiresMod;
        this.excludedModId = excludedModId;
    }

    @Deprecated
    public void setHandlerDimensions(int height, int width, int maxRecipesPerPage) {
        setHandlerDimensions(width, height, maxRecipesPerPage > 1);
    }

    public void setHandlerDimensions(int width, int height, boolean multipleWidgetsAllowed) {
        this.height = height;
        this.width = width;
        this.multipleWidgetsAllowed = multipleWidgetsAllowed;
    }

    public boolean setItem(String itemId, String nbtString) {
        if (hasImageOrItem()) return false;

        this.itemStack = NEIServerUtils.getModdedItem(itemId, nbtString);

        if (this.itemStack == null) {
            NEIClientConfig.logger.info("Couldn't find " + this.modName + " - " + itemId);
        }

        return this.itemStack != null;
    }

    public boolean setImage(String resourceLocation, int imageX, int imageY, int imageWidth, int imageHeight) {
        if (hasImageOrItem()) return false;

        this.image = new DrawableBuilder(resourceLocation, imageX, imageY, imageWidth, imageHeight).build();
        return true;
    }

    public DrawableResource getImage() {
        return this.image;
    }

    public ItemStack getItemStack() {
        return this.itemStack;
    }

    public String getModName() {
        return this.modName;
    }

    public String getModId() {
        return this.modId;
    }

    public int getHeight() {
        return this.height;
    }

    public int getWidth() {
        return this.width;
    }

    @Deprecated
    public int getMaxRecipesPerPage() {
        return this.multipleWidgetsAllowed ? Integer.MAX_VALUE : 1;
    }

    public boolean isMultipleWidgetsAllowed() {
        return this.multipleWidgetsAllowed;
    }

    public int getYShift() {
        return this.yShift;
    }

    public String getHandlerName() {
        return this.handlerName;
    }

    public boolean hasImageOrItem() {
        return this.image != null || this.itemStack != null;
    }

    public void setYShift(int yShift) {
        this.yShift = yShift;
    }

    public boolean getShowFavoritesButton() {
        return this.showFavoritesButton;
    }

    public void setShowFavoritesButton(boolean showFavoritesButton) {
        this.showFavoritesButton = showFavoritesButton;
    }

    public boolean getShowOverlayButton() {
        return this.showOverlayButton;
    }

    public void setShowOverlayButton(boolean showOverlayButton) {
        this.showOverlayButton = showOverlayButton;
    }

    public boolean getUseCustomScroll() {
        return this.useCustomScroll;
    }

    public void setUseCustomScroll(boolean useCustomScroll) {
        this.useCustomScroll = useCustomScroll;
    }

    public static class Builder {

        private final HandlerInfo info;

        public Builder(String handlerName, String modName, String modId) {
            this.info = new HandlerInfo(handlerName, modName, modId, true, null);
            setMaxRecipesPerPage(Integer.MAX_VALUE);
        }

        public Builder(Class<? extends IRecipeHandler> handlerClazz, String modName, String modId) {
            this(handlerClazz.getName(), modName, modId);
        }

        public Builder setDisplayStack(ItemStack stack) {
            this.info.image = null;
            this.info.itemStack = stack;
            return this;
        }

        public Builder setDisplayImage(DrawableResource drawable) {
            this.info.itemStack = null;
            this.info.image = drawable;
            return this;
        }

        public Builder setDisplayImage(ResourceLocation location, int imageX, int imageY, int imageWidth,
                int imageHeight) {
            this.info.itemStack = null;
            this.info.setImage(location.toString(), imageX, imageY, imageWidth, imageHeight);
            return this;
        }

        public Builder setUseCustomScroll(boolean useCustomScroll) {
            this.info.setUseCustomScroll(useCustomScroll);
            return this;
        }

        public Builder setShiftY(int shiftY) {
            this.info.setYShift(shiftY);
            return this;
        }

        public Builder setWidth(int width) {
            this.info.width = width;
            return this;
        }

        public Builder setHeight(int height) {
            this.info.height = height;
            return this;
        }

        @Deprecated
        public Builder setMaxRecipesPerPage(int maxRecipesPerPage) {
            this.info.multipleWidgetsAllowed = maxRecipesPerPage > 1;
            return this;
        }

        public Builder setMultipleWidgetsAllowed(boolean multipleWidgetsAllowed) {
            this.info.multipleWidgetsAllowed = multipleWidgetsAllowed;
            return this;
        }

        public HandlerInfo build() {
            return this.info;
        }
    }
}
