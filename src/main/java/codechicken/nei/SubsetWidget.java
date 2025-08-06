package codechicken.nei;

import java.awt.Point;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.opengl.GL11;

import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import codechicken.core.gui.GuiScrollSlot;
import codechicken.lib.gui.GuiDraw;
import codechicken.lib.vec.Rectangle4i;
import codechicken.nei.ItemList.AnyMultiItemFilter;
import codechicken.nei.ItemList.NothingItemFilter;
import codechicken.nei.SearchTokenParser.ISearchParserProvider;
import codechicken.nei.SearchTokenParser.SearchMode;
import codechicken.nei.api.API;
import codechicken.nei.api.ItemFilter;
import codechicken.nei.api.ItemFilter.ItemFilterProvider;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.guihook.IContainerTooltipHandler;
import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.GuiRecipeTab;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.StackInfo;
import codechicken.nei.util.NBTJson;
import codechicken.nei.util.NEIMouseUtils;

public class SubsetWidget extends Button implements ItemFilterProvider, IContainerTooltipHandler {

    protected static final int SLOT_HEIGHT = 18;
    protected static final int MARGIN = 2;
    protected static final char PREFIX = '%';

    protected static ReentrantReadWriteLock hiddenItemLock = new ReentrantReadWriteLock();
    protected static Lock hiddenWriteLock = hiddenItemLock.writeLock();
    protected static Lock hiddenReadLock = hiddenItemLock.readLock();

    public static class SubsetTag {

        protected class SubsetSlot extends GuiScrollSlot {

            public SubsetSlot() {
                super(0, 0, 0, 0);
                setSmoothScroll(false);
            }

            @Override
            public int getSlotHeight(int slot) {
                return SubsetWidget.SLOT_HEIGHT;
            }

            @Override
            protected int getNumSlots() {
                return children.size() + items.size();
            }

            @Override
            protected void slotClicked(int slot, int button, int mx, int my, int count) {
                if (slot < children.size()) {
                    SubsetTag tag = children.get(slot);

                    if (SubsetWidget.enableSearchBySubsets && NEIClientUtils.shiftKey()) {
                        LayoutManager.searchField
                                .setText(SearchField.searchParser.getRedefinedPrefix(SubsetWidget.PREFIX) + tag.path);
                    } else if (button == 0 && count >= 2) {
                        SubsetWidget.showOnly(tag);
                    } else if (button == 0 || button == 1) {
                        SubsetWidget.setHidden(tag, button == 1);
                    }

                } else {
                    ItemStack item = items.get(slot - children.size());

                    if (NEIClientUtils.controlKey() && NEIClientConfig.canCheatItem(item)) {
                        NEIClientUtils.cheatItem(item, button, -1);
                    } else if (SubsetWidget.enableSearchBySubsets && NEIClientUtils.shiftKey()) {
                        LayoutManager.searchField.setText(SearchField.getEscapedSearchText(item));
                    } else if (button == 0 || button == 1) {
                        SubsetWidget.setHidden(item, button == 1);
                    }

                }
            }

            @Override
            protected void drawSlot(int slot, int x, int y, int mx, int my, float frame) {
                int w = windowBounds().width;
                Rectangle4i r = new Rectangle4i(0, 0, w, getSlotHeight(slot));

                if (slot < children.size()) {
                    SubsetTag tag = children.get(slot);
                    LayoutManager.getLayoutStyle()
                            .drawSubsetTag(tag.displayName(), x, y, r.w, r.h, tag.state, r.contains(mx, my));

                    if (r.contains(mx, my)) {
                        SubsetWidget.hoverTag = tag;
                    }
                } else {
                    ItemStack stack = items.get(slot - children.size());
                    boolean hidden = SubsetWidget.isHidden(stack);

                    LayoutManager.getLayoutStyle()
                            .drawSubsetTag(null, x, y, r.w, r.h, hidden ? 0 : 2, r.contains(mx, my));
                    GuiContainerManager.drawItem(x + (w / 2 - 8), y + 1, stack);

                    if (r.contains(mx, my)) {
                        SubsetWidget.hoverStack = stack;
                    }
                }
            }

            @Override
            public void drawOverlay(float frame) { /** disabled draw overlay */
            }

            @Override
            public void drawBackground(float frame) {
                drawRect(x, y, x + width, y + height, 0xFF202020);
            }

            @Override
            public int scrollbarAlignment() {
                return marginleft == 0 ? 1 : -1;
            }

            @Override
            public void drawScrollbar(float frame) {
                if (hasScrollbar()) super.drawScrollbar(frame);
            }

            @Override
            public int scrollbarGuideAlignment() {
                return 0;
            }
        }

        public final String fullname;
        public final String path;
        public final String parentPath;
        public final ItemFilter filter;

        // 0 - full hidden
        // 1 - partitial hidden
        // 2 - enabled
        public int state = 2;
        public int calculatedWidth;
        public final List<ItemStack> items;
        public final List<SubsetTag> children = new ArrayList<>();
        protected final SubsetSlot slot = new SubsetSlot();

        private SubsetTag selectedChild;
        private int visible;

        public SubsetTag(String fullname) {
            this(fullname, null);
        }

        public SubsetTag(String fullname, ItemFilter filter) {
            this.fullname = EnumChatFormatting.getTextWithoutFormattingCodes(fullname);
            this.filter = filter == null ? new NothingItemFilter() : filter;
            this.items = new ArrayList<>();

            if (this.fullname != null) {
                this.path = this.fullname.replaceAll("\\s+", "").toLowerCase();

                int idx = this.path.lastIndexOf('.');
                this.parentPath = idx < 0 ? null : this.path.substring(0, idx);
            } else {
                this.parentPath = this.path = null;
            }
        }

        public String displayName() {
            final String translated = NEIClientUtils.translate("subsets." + this.fullname);
            return translated.startsWith("nei.") ? name() : translated;
        }

        public String name() {
            int idx = this.fullname.indexOf('.');
            return idx < 0 ? this.fullname : this.fullname.substring(idx + 1);
        }

        public String parent() {
            int idx = this.fullname.lastIndexOf('.');
            return idx < 0 ? null : this.fullname.substring(0, idx);
        }

        public void clearCache() {
            this.state = 2;
            this.items.clear();
            this.children.clear();
            this.calculatedWidth = 0;
        }

        public void updateVisiblity(int mx, int my) {
            if (selectedChild != null) {
                selectedChild.updateVisiblity(mx, my);
                if (!selectedChild.isVisible()) {
                    selectedChild = null;
                }
            }

            if (slot.contains(mx, my) && (selectedChild == null || !selectedChild.contains(mx, my))) {
                int mslot = slot.getClickedSlot(my);

                if (mslot >= 0 && mslot < children.size()) {
                    SubsetTag mtag = children.get(mslot);
                    if (mtag != null) {

                        if (mtag != selectedChild && selectedChild != null) {
                            selectedChild.setHidden();
                        }

                        selectedChild = mtag;
                        selectedChild.setVisible();
                    }
                }

                setVisible();
            }

            if (this.selectedChild == null) {
                countdownVisible();
            }
        }

        public void setHidden() {
            this.visible = 0;
            slot.mouseMovedOrUp(0, 0, 0); // cancel any scrolling
            if (this.selectedChild != null) {
                this.selectedChild.setHidden();
                this.selectedChild = null;
            }
        }

        public void setVisible() {
            this.visible = 10;
        }

        private void countdownVisible() {
            if (this.visible > 0 && --this.visible == 0) {
                setHidden();
            }
        }

        public void resize(Rectangle4i screen, Rectangle4i parent, boolean dropRight) {
            int height = Math.min(slot.contentHeight(), screen.h);
            int width = Math.max(calculatedWidth + 2, this.items.isEmpty() ? 0 : 16 + MARGIN * 2);
            int scrollbarWidth = slot.scrollbarDim().width;

            if (slot.contentHeight() > height) {
                width += scrollbarWidth;
            }

            int slotY = parent.y1() + Math.min(0, alignValueToStep(screen.y2() - parent.y1() - height));
            int slotX = dropRight ? parent.x2() : parent.x1() - width;

            if (slotX + width >= screen.x2()) {
                slotX = parent.x1() - width;
                dropRight = false;
            } else if (slotX <= screen.x1()) {
                slotX = parent.x2();
                dropRight = true;
            }

            slot.setSize(slotX, slotY, width, height);

            if (dropRight) {
                slot.setMargins(0, 0, slot.hasScrollbar() ? scrollbarWidth : 0, 0);
            } else {
                slot.setMargins(slot.hasScrollbar() ? scrollbarWidth : 0, 0, 0, 0);
            }

            if (selectedChild != null) {
                selectedChild.resize(
                        screen,
                        new Rectangle4i(
                                slot.x,
                                slot.y + slot.getSlotY(this.children.indexOf(selectedChild)) - slot.scrolledPixels(),
                                width,
                                SLOT_HEIGHT),
                        dropRight);
            }
        }

        protected int nameWidth() {
            return Minecraft.getMinecraft().fontRenderer.getStringWidth(displayName()) + 4;
        }

        public boolean isVisible() {
            return this.visible > 0;
        }

        public void draw(int mx, int my) {
            slot.draw(mx, my, 0);
            if (selectedChild != null) selectedChild.draw(mx, my);
        }

        public boolean contains(int px, int py) {
            return slot.contains(px, py) || selectedChild != null && selectedChild.contains(px, py);
        }

        public void mouseClicked(int mx, int my, int button) {
            if (selectedChild != null && selectedChild.contains(mx, my)) {
                selectedChild.mouseClicked(mx, my, button);
            } else if (slot.contains(mx, my)) {
                slot.mouseClicked(mx, my, button);
            }
        }

        public void mouseDragged(int mx, int my, int button, long heldTime) {
            slot.mouseDragged(mx, my, button, heldTime);
            if (selectedChild != null) {
                selectedChild.mouseDragged(mx, my, button, heldTime);
            }
        }

        public void mouseUp(int mx, int my, int button) {
            slot.mouseMovedOrUp(mx, my, button);
            if (selectedChild != null) {
                selectedChild.mouseUp(mx, my, button);
            }
        }

        public boolean mouseScrolled(int mx, int my, int scroll) {
            if (slot.hasScrollbar() && slot.contains(mx, my)) {
                slot.scroll(scroll);
                return true;
            }

            if (selectedChild != null && selectedChild.mouseScrolled(mx, my, scroll)) return true;

            if (slot.hasScrollbar() && !contains(mx, my)) {
                slot.scroll(scroll);
                return true;
            }

            return false;
        }

        public boolean isScrolling() {
            return slot.isScrolling() || selectedChild != null && selectedChild.isScrolling();
        }
    }

    private static class DefaultParserProvider implements ISearchParserProvider {

        public ItemFilter getFilter(String searchText) {
            final String pathPart = searchText.replaceAll("\\s+", "").toLowerCase();
            final AnyMultiItemFilter filter = new AnyMultiItemFilter();
            final Set<ItemStack> filteredItems = new HashSet<>();

            for (SubsetTag tag : tags.values()) {
                if (tag.filter != null && tag.path.contains(pathPart)) {
                    filteredItems.addAll(tag.items);
                    filter.filters.add(tag.filter);
                }
            }

            return stack -> filteredItems.contains(stack) || !ItemList.items.contains(stack) && filter.matches(stack);
        }

        public char getPrefix() {
            return SubsetWidget.PREFIX;
        }

        public EnumChatFormatting getHighlightedColor() {
            return EnumChatFormatting.DARK_PURPLE;
        }

        @Override
        public SearchMode getSearchMode() {
            return SearchMode.fromInt(NEIClientConfig.getIntSetting("inventory.search.subsetsSearchMode"));
        }
    }

    /**
     * All operations on this variable should be synchronised.
     */
    private static final ItemStackSet hiddenItems = new ItemStackSet();
    private static final Map<String, SubsetTag> tags = new HashMap<>();
    private static final SubsetTag root = new SubsetTag("");
    protected static boolean enableSearchBySubsets = false;
    protected static ItemStack hoverStack;
    protected static SubsetTag hoverTag;
    private long lastclicktime;

    public static void addTag(SubsetTag tag) {
        updateState.stop();

        synchronized (tags) {
            tags.put(tag.path, tag);

            String parentname = tag.parent();

            while (parentname != null && !tags.containsKey(parentname.replaceAll("\\s+", "").toLowerCase())) {
                SubsetTag parent = new SubsetTag(parentname);
                tags.put(parent.path, parent);
                parentname = parent.parent();
            }

            updateHiddenItems();
        }
    }

    public static boolean isHidden(ItemStack item) {
        try {
            if (hiddenReadLock.tryLock(5, TimeUnit.SECONDS)) {
                try {
                    return hiddenItems.contains(item);
                } finally {
                    hiddenReadLock.unlock();
                }
            } else {
                NEIClientConfig.logger.error("Unable to obtain read lock in 'isHidden'");
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return false;
    }

    private static List<ItemStack> getItems(SubsetTag tag, List<ItemStack> items) {
        items.addAll(tag.items);

        for (SubsetTag child : tag.children) {
            getItems(child, items);
        }

        return items;
    }

    public static void showOnly(SubsetTag tag) {
        try {
            if (hiddenWriteLock.tryLock(5, TimeUnit.SECONDS)) {
                try {
                    hiddenItems.clear();
                    hiddenItems.addAll(getItems(root, new ArrayList<>()));
                    hiddenItems.removeAll(getItems(tag, new ArrayList<>()));
                } finally {
                    hiddenWriteLock.unlock();
                }
            } else {
                NEIClientConfig.logger.error("Unable to obtain write lock in 'showOnly'");
            }

            calculateVisibility();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void setHidden(SubsetTag tag, boolean hidden) {
        try {
            if (hiddenWriteLock.tryLock(5, TimeUnit.SECONDS)) {
                try {
                    List<ItemStack> tagItems = getItems(tag, new ArrayList<>());

                    if (hidden) {
                        hiddenItems.addAll(tagItems);
                    } else {
                        hiddenItems.removeAll(tagItems);
                    }

                } finally {
                    hiddenWriteLock.unlock();
                }
            } else {
                NEIClientConfig.logger.error("Unable to obtain write lock in 'setHidden'");
            }

            calculateVisibility();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void setHidden(ItemStack item, boolean hidden) {
        try {
            if (hiddenWriteLock.tryLock(5, TimeUnit.SECONDS)) {
                try {

                    if (hidden) {
                        hiddenItems.add(item);
                    } else {
                        hiddenItems.remove(item);
                    }

                } finally {
                    hiddenWriteLock.unlock();
                }
            } else {
                NEIClientConfig.logger.error("Unable to obtain write lock in 'setHidden'");
            }

            calculateVisibility();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void unhideAll() {
        try {
            if (hiddenWriteLock.tryLock(5, TimeUnit.SECONDS)) {
                try {
                    hiddenItems.clear();
                } finally {
                    hiddenWriteLock.unlock();
                }
            } else {
                NEIClientConfig.logger.error("Unable to obtain write lock in 'unhideAll'");
            }

            calculateVisibility();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void updateHiddenItems() {
        if (ItemList.loadFinished) {
            updateState.restart();
        }
    }

    public static void loadCustomSubsets() {
        File dir = new File(NEIClientConfig.configDir, "subsets");

        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }

        Stream.of(dir.listFiles()).filter(file -> !file.isDirectory()).forEach(file -> {
            ClientHandler.loadSettingsFile(
                    "subsets/" + file.getName(),
                    lines -> parseFile(file.getName(), lines.collect(Collectors.toList())));
        });
    }

    private static void parseFile(String resource, List<String> itemStrings) {
        final JsonParser parser = new JsonParser();
        final String subsetNamespace = resource.substring(0, resource.lastIndexOf('.'));
        SubsetTag processedTag = new SubsetTag(subsetNamespace, new ItemStackSet());

        for (String itemStr : itemStrings) {
            try {
                if (itemStr.startsWith("; ")) {
                    final String handlerName = itemStr.substring(2);
                    String recipeName = handlerName;
                    addTag(processedTag);
                    IRecipeHandler recipeHandler = GuiCraftingRecipe.craftinghandlers.stream()
                            .filter(handler -> handlerName.equals(getHandlerName(handler))).findAny().orElse(null);

                    if (recipeHandler == null) {
                        recipeHandler = GuiCraftingRecipe.serialCraftingHandlers.stream()
                                .filter(handler -> handlerName.equals(getHandlerName(handler))).findAny().orElse(null);
                    }

                    if (recipeHandler != null) {
                        recipeName = recipeHandler.getRecipeName().trim();
                    }

                    processedTag = new SubsetTag(
                            subsetNamespace + "." + recipeName.replace(".", ""),
                            new ItemStackSet());
                } else {
                    ((ItemStackSet) processedTag.filter)
                            .add(StackInfo.loadFromNBT((NBTTagCompound) NBTJson.toNbt(parser.parse(itemStr))));
                }
            } catch (IllegalArgumentException | JsonSyntaxException | IllegalStateException e) {
                NEIClientConfig.logger.error("Failed to load collapsible items from json string:\n{}", itemStr);
            }
        }
    }

    private static String getHandlerName(ICraftingHandler handler) {
        return GuiRecipeTab.getHandlerInfo(handler).getHandlerName();
    }

    public static void loadHidden() {
        final List<ItemStack> itemList = new LinkedList<>();

        try {
            final NBTTagList list = NEIClientConfig.world.nbt.getTagList("hiddenItems", 10);

            for (int i = 0; i < list.tagCount(); i++) {
                itemList.add(ItemStack.loadItemStackFromNBT(list.getCompoundTagAt(i)));
            }
        } catch (Exception e) {
            NEIClientConfig.logger.error("Error loading hiddenItems", e);
            return;
        }

        try {
            if (hiddenWriteLock.tryLock(5, TimeUnit.SECONDS)) {
                try {
                    hiddenItems.clear();
                    hiddenItems.addAll(itemList);
                } finally {
                    hiddenWriteLock.unlock();
                }
            } else {
                NEIClientConfig.logger.error("Unable to obtain second write lock in 'loadHidden'");
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    public static void saveHidden() {
        if (NEIClientConfig.world == null) return;

        final NBTTagList list = new NBTTagList();
        for (ItemStack stack : hiddenItems.values()) {
            list.appendTag(stack.writeToNBT(new NBTTagCompound()));
        }

        NEIClientConfig.world.nbt.setTag("hiddenItems", list);
    }

    private static final UpdateStateTask updateState = new UpdateStateTask();

    private static class UpdateStateTask extends RestartableTask {

        public UpdateStateTask() {
            super("NEI Subset Item Allocation");
        }

        @Override
        public void execute() {

            try {
                final List<SubsetTag> list = new ArrayList<>(tags.values());

                root.clearCache();

                list.parallelStream().forEach(tag -> {
                    tag.clearCache();
                    ItemList.items.stream().filter(tag.filter::matches)
                            .collect(Collectors.toCollection(() -> tag.items));
                });

                for (SubsetTag tag : list) {
                    if (tag.parentPath == null) {
                        root.children.add(tag);
                    } else {
                        tags.get(tag.parentPath).children.add(tag);
                    }
                }

                boolean changed = false;
                do {
                    changed = false;

                    for (SubsetTag tag : list) {
                        changed = tag.children.removeIf(child -> child.children.isEmpty() && child.items.isEmpty())
                                || changed;
                    }

                } while (changed);

                for (SubsetTag tag : list) {
                    tag.children.sort(Comparator.comparing(SubsetTag::displayName));
                    tag.calculatedWidth = tag.children.stream().mapToInt(SubsetTag::nameWidth).max().orElse(0);
                }

                root.children.removeIf(child -> child.children.isEmpty() && child.items.isEmpty());
                root.children.sort(Comparator.comparing(SubsetTag::displayName));
                root.calculatedWidth = root.children.stream().mapToInt(SubsetTag::nameWidth).max().orElse(0);

                calculateVisibility(root);
            } catch (Throwable e) {
                e.printStackTrace();
            }

        }
    }

    public SubsetWidget() {
        super("NEI Subsets");
        API.addItemFilter(this);
        API.addSearchProvider(new DefaultParserProvider());
        this.z = 1;
    }

    @Override
    public String getRenderLabel() {

        if (NEIClientConfig.subsetWidgetOnTop()) {
            return NEIClientUtils.translate("inventory.item_subsets");
        } else {
            return EnumChatFormatting.DARK_PURPLE + String.valueOf(SearchField.searchParser.getRedefinedPrefix(PREFIX))
                    + EnumChatFormatting.RESET;
        }

    }

    @Override
    public void draw(int mx, int my) {
        super.draw(mx, my);

        SubsetWidget.hoverTag = null;
        SubsetWidget.hoverStack = null;

        if (root.isVisible()) {
            final Minecraft mc = NEIClientUtils.mc();
            final Rectangle4i screen = new Rectangle4i(
                    MARGIN,
                    MARGIN,
                    mc.currentScreen.width - MARGIN * 2,
                    mc.currentScreen.height - MARGIN * 2);
            final Rectangle4i parent = new Rectangle4i();
            final boolean dropRight = this.x < (screen.x + screen.w / 2);
            final boolean dropDown = this.y < (screen.y + screen.h / 2);

            if (dropRight) {
                parent.x = this.x;
            } else {
                parent.x = this.x + this.w;
            }

            if (dropDown) {
                screen.y = parent.y = this.y + this.h;
                screen.h = alignValueToStep(mc.currentScreen.height - MARGIN - screen.y);
            } else {
                screen.h = alignValueToStep(this.y - screen.y);
                screen.y = this.y - screen.h;
                parent.y = this.y;
            }

            root.resize(screen, parent, dropRight);

            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            GuiContainerManager.enable2DRender();
            GuiContainerManager.drawItems.zLevel += 100;

            root.draw(mx, my);

            GuiContainerManager.drawItems.zLevel -= 100;
            GL11.glPopAttrib();
        }
    }

    private static int alignValueToStep(int height) {
        return (height / SLOT_HEIGHT) * SLOT_HEIGHT;
    }

    protected static void calculateVisibility() {
        calculateVisibility(root);
        ItemList.updateFilter.restart();
    }

    protected static void calculateVisibility(SubsetTag tag) {
        // 0 - full hidden
        // 1 - partitial hidden
        // 2 - enabled
        int hidden = 0;

        tag.state = 2;

        for (SubsetTag child : tag.children) {
            calculateVisibility(child);

            if (child.state == 1) {
                tag.state = 1;
            } else if (child.state == 0) {
                hidden++;
            }
        }

        if (tag.state == 1) return;

        for (ItemStack item : tag.items) {
            if (isHidden(item)) {
                hidden++;
            } else if (hidden > 0) {
                break;
            }
        }

        if (hidden == tag.children.size() + tag.items.size()) {
            tag.state = 0;
        } else if (hidden > 0) {
            tag.state = 1;
        }

    }

    @Override
    public void update() {
        SubsetWidget.enableSearchBySubsets = SearchMode
                .fromInt(NEIClientConfig.getIntSetting("inventory.search.subsetsSearchMode")) == SearchMode.PREFIX;
        Point mouse = GuiDraw.getMousePosition();
        updateVisiblity(mouse.x, mouse.y);
    }

    private void updateVisiblity(int mx, int my) {
        if (!root.isVisible() || root.isScrolling()) return;

        root.updateVisiblity(mx, my);

        if (!root.isVisible() && bounds().contains(mx, my)) {
            root.setVisible();
        }
    }

    @Override
    public boolean contains(int px, int py) {
        return super.contains(px, py) || root.isVisible() && root.contains(px, py);
    }

    @Override
    public boolean handleClick(int mx, int my, int button) {
        if (root.isVisible() && root.contains(mx, my)) {
            root.mouseClicked(mx, my, button);
            return true;
        }

        if (button == 0) {

            if (System.currentTimeMillis() - lastclicktime < 500) {
                unhideAll();
            } else {
                root.setVisible();
            }

            NEIClientUtils.playClickSound();
            lastclicktime = System.currentTimeMillis();
        }

        return true;
    }

    /**
     * Not called
     */
    @Override
    public boolean onButtonPress(boolean rightclick) {
        return false;
    }

    @Override
    public void mouseDragged(int mx, int my, int button, long heldTime) {
        if (root.isVisible()) {
            root.mouseDragged(mx, my, button, heldTime);
        }
    }

    @Override
    public void mouseUp(int mx, int my, int button) {
        if (root.isVisible()) root.mouseUp(mx, my, button);
    }

    @Override
    public boolean onMouseWheel(int i, int mx, int my) {
        return root.isVisible() && root.mouseScrolled(mx, my, -i) || contains(mx, my);
    }

    @Override
    public void onGuiClick(int mx, int my) {
        if (!contains(mx, my)) {
            root.setHidden();
        }
    }

    @Override
    public ItemStack getStackMouseOver(int mx, int my) {
        return SubsetWidget.hoverStack;
    }

    @Override
    public ItemFilter getFilter() {
        return item -> !isHidden(item);
    }

    @Override
    public void addTooltips(List<String> tooltip) {
        if (SubsetWidget.hoverStack == null && SubsetWidget.hoverTag != null) {
            tooltip.add(SubsetWidget.hoverTag.displayName() + GuiDraw.TOOLTIP_LINESPACE);
        }
    }

    @Override
    public Map<String, String> handleHotkeys(GuiContainer gui, int mousex, int mousey, Map<String, String> hotkeys) {

        if (SubsetWidget.hoverStack != null) {

            if (NEIClientConfig.canCheatItem(SubsetWidget.hoverStack)) {
                hotkeys.put(
                        NEIClientUtils.getKeyName(NEIClientUtils.CTRL_HASH, NEIMouseUtils.MOUSE_BTN_LMB),
                        NEIClientUtils.translate("subsets.item.cheat"));
            }

            if (SubsetWidget.enableSearchBySubsets) {
                hotkeys.put(
                        NEIClientUtils.getKeyName(NEIClientUtils.SHIFT_HASH, NEIMouseUtils.MOUSE_BTN_LMB),
                        NEIClientUtils.translate("subsets.item.search"));
            }

            hotkeys.put(
                    NEIMouseUtils.getKeyName(NEIMouseUtils.MOUSE_BTN_LMB),
                    NEIClientUtils.translate("subsets.item.show"));
            hotkeys.put(
                    NEIMouseUtils.getKeyName(NEIMouseUtils.MOUSE_BTN_RMB),
                    NEIClientUtils.translate("subsets.item.hide"));
        } else if (SubsetWidget.hoverTag != null) {
            hotkeys.put(
                    NEIClientUtils.translate("subsets.tag.onlythis.key"),
                    NEIClientUtils.translate("subsets.tag.onlythis"));

            if (SubsetWidget.enableSearchBySubsets) {
                hotkeys.put(
                        NEIClientUtils.getKeyName(NEIClientUtils.SHIFT_HASH, NEIMouseUtils.MOUSE_BTN_LMB),
                        NEIClientUtils.translate("subsets.tag.search"));
            }

            hotkeys.put(
                    NEIMouseUtils.getKeyName(NEIMouseUtils.MOUSE_BTN_LMB),
                    NEIClientUtils.translate("subsets.tag.show"));
            hotkeys.put(
                    NEIMouseUtils.getKeyName(NEIMouseUtils.MOUSE_BTN_RMB),
                    NEIClientUtils.translate("subsets.tag.hide"));
        }

        return hotkeys;
    }
}
