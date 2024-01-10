package codechicken.nei;

import static codechicken.nei.NEIClientUtils.translate;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.item.ItemStack;

import org.apache.commons.io.IOUtils;
import org.lwjgl.input.Keyboard;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;

import codechicken.core.CommonUtils;
import codechicken.core.gui.GuiScrollSlot;
import codechicken.lib.gui.GuiDraw;
import codechicken.lib.vec.Rectangle4i;
import codechicken.nei.ItemList.ItemsLoadedCallback;
import codechicken.nei.ItemPanel.ItemPanelSlot;
import codechicken.nei.api.API;
import codechicken.nei.api.IRecipeFilter;
import codechicken.nei.api.IRecipeFilter.IRecipeFilterProvider;
import codechicken.nei.api.ItemFilter;
import codechicken.nei.api.ItemFilter.ItemFilterProvider;
import codechicken.nei.recipe.GuiRecipe;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.StackInfo;
import codechicken.nei.util.NBTJson;
import cpw.mods.fml.common.registry.GameRegistry;

public class PresetsWidget extends Widget implements ItemFilterProvider, ItemsLoadedCallback, ItemFilter {

    protected static enum PresetTagState {

        WHITELIST,
        BLACKLIST,
        BLACKLIST_PLUS;

        public PresetTagState next() {
            if (this == PresetTagState.WHITELIST) return PresetTagState.BLACKLIST;
            if (this == PresetTagState.BLACKLIST) return PresetTagState.BLACKLIST_PLUS;
            if (this == PresetTagState.BLACKLIST_PLUS) return PresetTagState.WHITELIST;
            return this;
        }
    }

    public static class PresetTag {

        public String filename = null;
        public String displayName = "";
        public PresetTagState state = PresetTagState.BLACKLIST;
        public ItemStackSet items;

        public PresetTag(String displayName) {
            this(displayName, null, PresetTagState.BLACKLIST, null);
        }

        public PresetTag(String displayName, ItemStackSet items, PresetTagState state, String filename) {
            this.displayName = displayName;
            this.items = items != null ? items : new ItemStackSet();
            this.state = state;
            this.filename = filename;
        }

        public boolean matches(final ItemStack stack) {
            return items.contains(stack) == (state == PresetTagState.WHITELIST);
        }

        public static PresetTag loadFromFile(File file) {
            if (!file.isFile() || !file.exists() || file.getName().equals("selected.ini")) {
                return null;
            }

            try {

                List<String> itemStrings;
                try (FileInputStream input = new FileInputStream(file)) {
                    NEIClientConfig.logger.info("Loading presets from file {}", file);
                    itemStrings = IOUtils.readLines(input, "UTF-8");
                } catch (IOException e) {
                    NEIClientConfig.logger.error("Failed to load presets from file {}", file, e);
                    return null;
                }

                final JsonParser parser = new JsonParser();
                final JsonObject metaObject = parser.parse(itemStrings.remove(0)).getAsJsonObject();
                final String displayName = metaObject.get("displayName").getAsString();
                final PresetTagState state = metaObject.has("state")
                        ? PresetTagState.valueOf(metaObject.get("state").getAsString())
                        : PresetTagState.BLACKLIST;
                final ItemStackSet items = new ItemStackSet();

                for (ItemStack stack : ItemList.items) {
                    if (itemStrings.contains(StackInfo.getItemStackGUID(stack))) {
                        items.add(stack);
                    }
                }

                return new PresetTag(displayName, items, state, file.getName());
            } catch (Throwable th) {
                NEIClientConfig.logger.error("Failed to load presets ItemStack from file", file);
            }

            return null;
        }

        public void deleteFile() {
            if (presetsDir == null || filename == null) return;
            final File tagFile = new File(presetsDir, filename);

            if (tagFile.exists()) {
                tagFile.delete();
            }
        }

        public void saveToFile() {
            if (presetsDir == null) return;

            if (filename == null) {
                final String sanitized = displayName.replaceAll("[^_\\-.0-9a-zA-Z]", "_");
                int index = 1;

                filename = sanitized + ".ini";

                while ((new File(presetsDir, filename)).exists() || filename.equals("selected.ini")) {
                    filename = sanitized + " (" + index + ").ini";
                    index++;
                }
            }

            final List<String> strings = new ArrayList<>();
            final JsonObject row = new JsonObject();

            row.add("displayName", new JsonPrimitive(displayName));
            row.add("state", new JsonPrimitive(state.toString()));

            strings.add(NBTJson.toJson(row));

            try {

                for (ItemStack stack : items.values()) {
                    strings.add(StackInfo.getItemStackGUID(stack));
                }

            } catch (JsonSyntaxException e) {
                NEIClientConfig.logger.error("Failed to stringify presets ItemStack to json string");
            }

            final File tagFile = new File(presetsDir, filename);

            try (FileOutputStream output = new FileOutputStream(tagFile)) {
                IOUtils.writeLines(strings, "\n", output, "UTF-8");
            } catch (IOException e) {
                NEIClientConfig.logger.error("Filed to save presets list to file {}", tagFile, e);
            }
        }

        public PresetTag copy() {
            return new PresetTag(displayName, new ItemStackSet().addAll(items.values()), state, null);
        }
    }

    protected static class SubsetListBox extends GuiScrollSlot {

        protected final List<PresetTag> presets = new ArrayList<>();
        protected final List<PresetTag> selected = new ArrayList<>();
        protected int lastSelectedIndex = 0;

        public SubsetListBox(File presetsDir) {
            super(0, 0, 0, 0);
            setSmoothScroll(false);

            try {

                presets.addAll(
                        ItemList.forkJoinPool.submit(
                                () -> Arrays.asList(presetsDir.listFiles()).parallelStream()
                                        .map(f -> PresetTag.loadFromFile(f)).filter(p -> p != null)
                                        .collect(Collectors.toCollection(ArrayList::new)))
                                .get());

            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
                return;
            }

            selected.addAll(loadSelFromFile(presetsDir, presets));
            sort();
        }

        public SubsetListBox() {
            super(0, 0, 0, 0);
            setSmoothScroll(false);
        }

        public void addTag(final PresetTag tag, boolean sel) {
            presets.add(tag);

            if (sel) {
                selected.add(tag);
            }
        }

        protected List<PresetTag> getPresets() {
            return presets;
        }

        protected List<PresetTag> getSelected() {
            return selected;
        }

        public void replaceSeleted(final PresetTag tag) {

            if (selected.size() == 1) {
                selected.get(0).deleteFile();
                presets.set(presets.indexOf(selected.get(0)), tag);
                selected.set(0, tag);
            } else {
                presets.add(tag);
                selected.add(tag);
            }

            tag.saveToFile();
            saveSelToFile();
            sort();
        }

        public void sort() {
            presets.sort((o1, o2) -> o1.displayName.compareTo(o2.displayName));
        }

        public void clear() {
            presets.clear();
            selected.clear();
        }

        @Override
        public int getSlotHeight(int slot) {
            return 18;
        }

        @Override
        protected int getNumSlots() {
            return presets.size();
        }

        @Override
        protected void slotClicked(int slot, int button, int mx, int my, int count) {

            if (slot >= presets.size()) {
                return;
            }

            final PresetTag tag = presets.get(slot);
            final int width = windowBounds().width;

            final Rectangle4i direction = new Rectangle4i(0, 0, 18, 18);
            final Rectangle4i delete = new Rectangle4i(width - 18, 0, 18, 18);
            final Rectangle4i option = new Rectangle4i(18, 0, width - 18 - 18, 18);

            if (option.contains(mx, my)) {
                final boolean sel = selected.contains(tag);

                if (NEIClientUtils.controlKey()) {

                    if (sel) {
                        selected.remove(tag);
                    } else {
                        selected.add(tag);
                    }

                } else if (NEIClientUtils.shiftKey()) {
                    selected.clear();

                    for (int i = Math.min(lastSelectedIndex, slot); i <= Math.max(lastSelectedIndex, slot); i++) {
                        if (!selected.contains(presets.get(i))) {
                            selected.add(presets.get(i));
                        }
                    }

                } else {
                    final int size = selected.size();
                    selected.clear();

                    if (!sel || size > 1) {
                        selected.add(tag);
                    }
                }

                if (selected.isEmpty()) {
                    lastSelectedIndex = 0;
                } else if (!NEIClientUtils.shiftKey()) {
                    lastSelectedIndex = slot;
                }

            } else if (delete.contains(mx, my)) {
                presets.remove(tag);
                selected.remove(tag);
                tag.deleteFile();
            } else if (direction.contains(mx, my)) {
                tag.state = tag.state.next();
                tag.saveToFile();
            }

            saveSelToFile();
            PresetsWidget.edit = null;
            PresetsWidget.openListBox = !presets.isEmpty()
                    && (NEIClientUtils.controlKey() || NEIClientUtils.shiftKey() || !option.contains(mx, my));
            ItemList.updateFilter.restart();
            PresetsRecipeFilter.blacklist = null;
        }

        @Override
        protected void drawSlot(int slot, int x, int y, int mx, int my, float frame) {
            if (slot >= presets.size()) {
                return;
            }

            final int width = windowBounds().width;
            final Rectangle4i direction = new Rectangle4i(x, y, 18, 18);
            final Rectangle4i delete = new Rectangle4i(x + width - 18, y, 18, 18);
            final Rectangle4i option = new Rectangle4i(x + 18, y, width - 18 - 18, 18);
            final PresetTag tag = presets.get(slot);

            final int optionState = selected.contains(tag) ? 0 : option.contains(x + mx, y + my) ? 1 : 2;
            final int directionState = direction.contains(x + mx, y + my) ? 1 : 2;
            final int deleteState = delete.contains(x + mx, y + my) ? 1 : 2;

            final String displayName = NEIClientUtils
                    .cropText(PresetsWidget.fontRenderer, tag.displayName, option.w - 6);
            String stateLabel = translate("presets.blacklist.label");

            if (tag.state == PresetTagState.WHITELIST) {
                stateLabel = translate("presets.whitelist.label");
            } else if (tag.state == PresetTagState.BLACKLIST_PLUS) {
                stateLabel = translate("presets.blacklistPlus.label");
            }

            // blacklist or whitelist
            LayoutManager.getLayoutStyle()
                    .drawSubsetTag(null, direction.x, direction.y, direction.w, direction.h, directionState, false);
            GuiDraw.drawString(
                    stateLabel,
                    direction.x + 6,
                    direction.y + 5,
                    directionState == 2 ? 0xFFE0E0E0 : 0xFFFFA0);

            // option name
            LayoutManager.getLayoutStyle()
                    .drawSubsetTag(null, option.x, option.y, option.w, option.h, optionState, false);
            GuiDraw.drawString(displayName, option.x + 3, option.y + 5, optionState == 2 ? 0xFFE0E0E0 : 0xFFFFA0);

            // remove icon
            LayoutManager.getLayoutStyle()
                    .drawSubsetTag(null, delete.x, delete.y, delete.w, delete.h, deleteState, false);
            GuiDraw.drawString("✕", delete.x + 6, delete.y + 5, deleteState == 2 ? 0xFFE0E0E0 : 0x601010);
        }

        @Override
        public void drawOverlay(float frame) {}

        @Override
        public void drawBackground(float frame) {
            drawRect(x, y, x + width, y + height, 0xFF202020);
        }

        @Override
        public void drawScrollbar(float frame) {
            if (hasScrollbar()) {
                super.drawScrollbar(frame);
            }
        }

        @Override
        public int scrollbarGuideAlignment() {
            return 0;
        }

        protected List<PresetTag> loadSelFromFile(File presetsDir, List<PresetTag> presets) {
            final File file = new File(presetsDir, "selected.ini");
            List<PresetTag> selected = new ArrayList<>();

            if (file.exists()) {

                try {

                    List<String> itemStrings;
                    try (FileInputStream input = new FileInputStream(file)) {
                        NEIClientConfig.logger.info("Loading presets selected from file {}", file);
                        itemStrings = IOUtils.readLines(input, "UTF-8");
                    } catch (IOException e) {
                        NEIClientConfig.logger.error("Failed to load presets selected from file {}", file, e);
                        return selected;
                    }

                    final HashMap<String, PresetTag> map = new HashMap<>();

                    for (PresetTag tag : presets) {
                        map.put(tag.filename, tag);
                    }

                    for (String filename : itemStrings) {
                        if (map.containsKey(filename)) {
                            selected.add(map.get(filename));
                        }
                    }

                } catch (Throwable th) {
                    NEIClientConfig.logger.error("Failed to load presets selected from file", file);
                }
            }

            return selected;
        }

        public void saveSelToFile() {

            final File file = new File(presetsDir, "selected.ini");
            final List<String> list = new ArrayList<>();

            for (final PresetTag tag : selected) {
                list.add(tag.filename);
            }

            try (FileOutputStream output = new FileOutputStream(file)) {
                IOUtils.writeLines(list, "\n", output, "UTF-8");
            } catch (IOException e) {
                NEIClientConfig.logger.error("Filed to save presets selected list to file {}", file, e);
            }
        }
    }

    protected static class MouseSelection {

        public int startX = -1;
        public int startY = -1;
        public int startIndex = -1;
        public int endIndex = -1;
        public HashSet<ItemStack> items = new HashSet<>();
        public boolean append = true;

        public MouseSelection(int slotIndex, boolean append) {
            final ItemsGrid grid = ItemPanels.itemPanel.getGrid();
            final Rectangle4i rec = grid.getItemRect(slotIndex);

            this.append = append;
            endIndex = slotIndex;
            startIndex = slotIndex;
            startX = rec.x;
            startY = rec.y;
        }
    }

    protected static class PresetsRecipeFilter implements IRecipeFilterProvider, IRecipeFilter {

        protected static ItemStackSet blacklist;

        public IRecipeFilter getFilter() {

            if (PresetsWidget.edit != null) {
                return null;
            }

            if (blacklist == null) {
                blacklist = new ItemStackSet();

                for (PresetTag tag : listbox.getSelected()) {
                    if (tag.state == PresetTagState.BLACKLIST_PLUS) {
                        blacklist.addAll(tag.items.values());
                    }
                }
            }

            return blacklist.isEmpty() ? null : this;
        }

        @Override
        public boolean matches(IRecipeHandler handler, List<PositionedStack> ingredients, PositionedStack result,
                List<PositionedStack> others) {

            if (matchPositionedStack(ingredients, false)) {
                return false;
            }

            if (result != null && matchPositionedStack(result)) {
                return true;
            }

            if (!others.isEmpty() && matchPositionedStack(others, true)) {
                return true;
            }

            return result == null && others.isEmpty();
        }

        private boolean matchPositionedStack(List<PositionedStack> items, boolean dir) {
            for (PositionedStack pStack : items) {
                if (matchPositionedStack(pStack) == dir) {
                    return true;
                }
            }

            return false;
        }

        private boolean matchPositionedStack(PositionedStack pStack) {
            for (ItemStack stack : pStack.items) {
                if (!blacklist.contains(stack)) {
                    return true;
                }
            }

            return false;
        }

    }

    protected static final FontRenderer fontRenderer = Minecraft.getMinecraft().fontRenderer;
    protected static SubsetListBox listbox = new SubsetListBox();
    protected static File presetsDir;

    protected static boolean openListBox = false;
    protected static PresetTag edit;
    protected long lastclicktime;
    protected static MouseSelection mouseSelection;

    protected static final Button dropdown = new Button() {

        public boolean onButtonPress(boolean rightclick) {
            if (!rightclick) {
                openListBox = !openListBox;
                return true;
            }

            return false;
        }

        @Override
        public String getRenderLabel() {
            return "...";
        }
    };

    protected static final Label selectedValue = new Label("", false) {

        @Override
        public void draw(int mousex, int mousey) {
            final String text = NEIClientUtils.cropText(fontRenderer, this.text, w);

            if (text.equals(this.text)) {
                GuiDraw.drawString(text, x + w - fontRenderer.getStringWidth(text), y + (h - 8) / 2, colour);
            } else {
                GuiDraw.drawString(text, x, y + (h - 8) / 2, colour);
            }
        }

        @Override
        public List<String> handleTooltip(int mx, int my, List<String> tooltip) {
            if (contains(mx, my) && listbox.getSelected().size() <= 1) {
                tooltip.add(translate("presets.label.tooltip"));
            }

            return tooltip;
        }
    };

    protected static final TextField selectedDisplayName = new TextField("") {

        public void onTextChange(String oldText) {
            if (edit != null) {
                edit.displayName = text();
            }
        }

        @Override
        public void draw(int mousex, int mousey) {
            super.draw(mousex, mousey);

            if (text().isEmpty()) {
                GuiDraw.drawGradientRect(x - 1, y - 1, 1, h + 2, 0xFFcc3300, 0xFFcc3300); // Left
                GuiDraw.drawGradientRect(x - 1, y - 1, w + 2, 1, 0xFFcc3300, 0xFFcc3300); // Top
                GuiDraw.drawGradientRect(x + w, y - 1, 1, h + 2, 0xFFcc3300, 0xFFcc3300); // Left
                GuiDraw.drawGradientRect(x - 1, y + h, w + 2, 1, 0xFFcc3300, 0xFFcc3300); // Bottom
            }
        }

        @Override
        public List<String> handleTooltip(int mx, int my, List<String> tooltip) {
            if (contains(mx, my) && listbox.getSelected().size() <= 1) {
                tooltip.add(translate("presets.textfield.tooltip.1"));
                tooltip.add(translate("presets.textfield.tooltip.2"));
            }

            return tooltip;
        }
    };

    protected static final Button selectedState = new Button("B") {

        public boolean onButtonPress(boolean rightclick) {
            if (!rightclick && edit != null) {
                edit.state = edit.state.next();
                return true;
            }

            return false;
        }

        @Override
        public String getRenderLabel() {

            if (edit == null || edit.state == PresetTagState.BLACKLIST) {
                return translate("presets.blacklist.label");
            } else if (edit.state == PresetTagState.WHITELIST) {
                return translate("presets.whitelist.label");
            } else if (edit.state == PresetTagState.BLACKLIST_PLUS) {
                return translate("presets.blacklistPlus.label");
            }

            return translate("presets.blacklist.label");
        }

        @Override
        public void addTooltips(List<String> tooltip) {
            if (edit != null) {
                if (edit.state == PresetTagState.BLACKLIST) {
                    tooltip.add(translate("presets.blacklist.tooltip"));
                } else if (edit.state == PresetTagState.WHITELIST) {
                    tooltip.add(translate("presets.whitelist.tooltip"));
                } else if (edit.state == PresetTagState.BLACKLIST_PLUS) {
                    tooltip.add(translate("presets.blacklistPlus.tooltip"));
                }
            }
        }

    };

    public void itemsLoaded() {

        if (presetsDir != null) {
            listbox = new SubsetListBox(presetsDir);

            edit = null;
            openListBox = false;
            selectedDisplayName.setText("");

            ItemList.updateFilter.restart();
            PresetsRecipeFilter.blacklist = null;
        }

    }

    public PresetsWidget() {
        API.addItemFilter(this);
        API.addRecipeFilter(new PresetsRecipeFilter());
        ItemList.loadCallbacks.add(this);
    }

    protected static void setEditedTag(final PresetTag tag) {
        edit = tag;
        openListBox = false;
        selectedDisplayName.setText(tag != null ? tag.displayName : "");
        ItemList.updateFilter.restart();
    }

    public static void addTag(final PresetTag tag) {
        addTag(tag, false);
    }

    public static void addTag(final PresetTag tag, boolean sel) {
        listbox.addTag(tag, sel);
    }

    public static boolean inEditMode() {
        return edit != null;
    }

    public static boolean isHidden(final ItemStack item) {

        if (edit != null) {

            if (mouseSelection != null && mouseSelection.items.contains(item)) {
                return mouseSelection.append != (edit.state == PresetTagState.WHITELIST);
            }

            return !edit.matches(item);
        }

        for (PresetTag tag : listbox.getSelected()) {
            if (!tag.matches(item)) {
                return true;
            }
        }

        return false;
    }

    public static void setHidden(final ItemStack stack, final boolean append) {
        if (edit == null) {
            return;
        }

        if (NEIClientUtils.shiftKey()) {
            final ArrayList<ItemStack> items = ItemPanels.itemPanel.getItems();

            for (int i = 0; i < items.size(); i++) {
                if (stack.getItem().equals(items.get(i).getItem())) {
                    hideItem(items.get(i), append);
                }
            }

        } else if (NEIClientUtils.controlKey()) {
            final String modId = getModId(stack);

            if (modId == null) {
                hideItem(stack, append);
            } else {
                final ArrayList<ItemStack> items = ItemPanels.itemPanel.getItems();

                for (int i = 0; i < items.size(); i++) {
                    final String mod = getModId(items.get(i));
                    if (mod != null && mod.equals(modId)) {
                        hideItem(items.get(i), append);
                    }
                }
            }

        } else {
            hideItem(stack, append);
        }
    }

    protected static void hideItem(final ItemStack stack, boolean append) {
        if (append) {
            edit.items.add(stack);
        } else {
            edit.items.remove(stack);
        }
    }

    protected static String getModId(final ItemStack stack) {
        try {
            return GameRegistry.findUniqueIdentifierFor(stack.getItem()).modId;
        } catch (Exception ignored) {}

        return null;
    }

    public static void loadPresets(final String worldPath) {
        presetsDir = new File(CommonUtils.getMinecraftDir(), "saves/NEI/" + worldPath + "/presets");

        if (!presetsDir.getParentFile().exists()) {
            presetsDir.getParentFile().mkdirs();
        }

        if (!presetsDir.exists()) {
            presetsDir.mkdirs();
        }

        if (!(new File(presetsDir, "selected.ini")).exists()) {
            final File configPresets = new File(NEIClientConfig.configDir, "/presets");

            if (configPresets.exists()) {
                for (File file : configPresets.listFiles()) {
                    try {
                        InputStream src = new FileInputStream(file);
                        OutputStream dst = new FileOutputStream(new File(presetsDir, file.getName()));

                        IOUtils.copy(src, dst);

                        src.close();
                        dst.close();
                    } catch (IOException e) {}
                }
            }
        }

        if (LayoutManager.itemsLoaded) {
            listbox = new SubsetListBox(presetsDir);

            edit = null;
            openListBox = false;
            selectedDisplayName.setText("");
            ItemList.updateFilter.restart();
            PresetsRecipeFilter.blacklist = null;
        }

    }

    @Override
    public void draw(int mx, int my) {

        if (edit != null) {

            selectedState.x = x;
            selectedState.y = y;
            selectedState.w = selectedState.h = h;
            selectedState.draw(mx, my);

            selectedDisplayName.y = y;
            selectedDisplayName.h = h;
            selectedDisplayName.x = selectedState.x + selectedState.w + 1;
            selectedDisplayName.w = w - h * 2 - 5;
            selectedDisplayName.draw(mx, my);

        } else {
            final List<PresetTag> selected = listbox.getSelected();
            final int size = selected.size();

            selectedValue.text = size > 1 ? translate("presets.label.selected", size)
                    : (size == 1 ? selected.get(0).displayName : "");
            selectedValue.y = y;
            selectedValue.h = h;
            selectedValue.w = w - h - 4;
            selectedValue.x = x;

            selectedValue.draw(mx, my);
        }

        dropdown.y = y;
        dropdown.w = dropdown.h = h;
        dropdown.x = x + w - dropdown.w;
        dropdown.state = edit != null || listbox.getNumSlots() == 0 ? 2 : 0;
        dropdown.draw(mx, my);

        if (openListBox) {
            final int scrollbarWidth = listbox.hasScrollbar() ? listbox.scrollbarDim().width : 0;

            listbox.setSize(
                    x,
                    y + h + 1,
                    w,
                    Math.min(((LayoutManager.searchField.y - h - y) / 18) * 18, listbox.contentHeight()));

            listbox.setMargins(0, 0, scrollbarWidth, 0);
            listbox.draw(mx, my, 0);
        }
    }

    @Override
    public boolean contains(int px, int py) {
        return super.contains(px, py) || openListBox && listbox.contains(px, py);
    }

    @Override
    public boolean handleClick(int mx, int my, int button) {

        if (openListBox && listbox.contains(mx, my)) {
            listbox.mouseClicked(mx, my, button);
            return true;
        }

        if (edit != null) {

            if (selectedState.contains(mx, my)) {
                selectedState.handleClick(mx, my, button);
                return true;
            }

            if (selectedDisplayName.contains(mx, my)) {
                selectedDisplayName.handleClick(mx, my, button);
                return true;
            }

        } else {

            if (button == 0 && dropdown.contains(mx, my)) {
                openListBox = !openListBox;
                return true;
            }

            openListBox = false;

            if (button == 0 && selectedValue.contains(mx, my)) {

                if (System.currentTimeMillis() - lastclicktime < 400) { // double click
                    final List<PresetTag> selected = listbox.getSelected();
                    final int size = selected.size();

                    if (size <= 1) {
                        setEditedTag(size == 1 ? selected.get(0).copy() : new PresetTag(""));
                    }
                }

                lastclicktime = System.currentTimeMillis();
            }
        }

        return contains(mx, my);
    }

    @Override
    public boolean handleClickExt(int mx, int my, int button) {

        if (edit != null && mouseSelection == null) {
            ItemPanelSlot slot = ItemPanels.itemPanel.getSlotMouseOver(mx, my);

            if (slot != null && button != 2) {
                mouseSelection = new MouseSelection(slot.slotIndex, button == 0);
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean handleKeyPress(int keyID, char keyChar) {
        if (keyID == Keyboard.KEY_ESCAPE) {
            openListBox = false;
        }

        if (edit == null) {
            return false;
        }

        if (selectedDisplayName.focused() && (keyID == Keyboard.KEY_RETURN || keyID == Keyboard.KEY_NUMPADENTER)
                && !selectedDisplayName.text().isEmpty()) {
            selectedDisplayName.handleKeyPress(keyID, keyChar);
            selectedDisplayName.setFocus(false);
            listbox.replaceSeleted(edit);
            setEditedTag(null);
            return true;
        } else if (selectedDisplayName.focused()) {
            return selectedDisplayName.handleKeyPress(keyID, keyChar);
        } else if (keyID == Keyboard.KEY_ESCAPE && !(NEIClientUtils.mc().currentScreen instanceof GuiRecipe)) {
            setEditedTag(null);
            return true;
        }

        return false;
    }

    @Override
    public void mouseDragged(int mx, int my, int button, long heldTime) {

        if (openListBox) {
            listbox.mouseDragged(mx, my, button, heldTime);
        }

        if (edit != null && button != 2 && mouseSelection != null) {
            final ItemPanelSlot slot = ItemPanels.itemPanel.getSlotMouseOver(mx, my);

            if (slot != null && slot.slotIndex != mouseSelection.endIndex) {
                final ItemsGrid grid = ItemPanels.itemPanel.getGrid();
                mouseSelection.endIndex = slot.slotIndex;
                mouseSelection.items.clear();

                final Rectangle4i rec = grid.getItemRect(slot.slotIndex);
                final Rectangle4i sel = new Rectangle4i(
                        Math.min(rec.x, mouseSelection.startX),
                        Math.min(rec.y, mouseSelection.startY),
                        Math.max(rec.x, mouseSelection.startX) - Math.min(rec.x, mouseSelection.startX),
                        Math.max(rec.y, mouseSelection.startY) - Math.min(rec.y, mouseSelection.startY));

                for (int x = sel.x; x <= sel.x + sel.w; x += ItemsGrid.SLOT_SIZE) {
                    for (int y = sel.y; y <= sel.y + sel.h; y += ItemsGrid.SLOT_SIZE) {
                        ItemStack stack = ItemPanels.itemPanel.getStackMouseOver(x, y);

                        if (stack != null) {
                            mouseSelection.items.add(stack);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void mouseUp(int mx, int my, int button) {

        if (openListBox) {
            listbox.mouseMovedOrUp(mx, my, button);
        }

        if (edit != null && mouseSelection != null) {
            ItemPanelSlot hoverSlot = ItemPanels.itemPanel.getSlotMouseOver(mx, my);

            if (hoverSlot != null && hoverSlot.slotIndex == mouseSelection.startIndex) {
                setHidden(hoverSlot.item, button == 0);
            } else if (!mouseSelection.items.isEmpty()) {

                for (ItemStack stack : mouseSelection.items) {
                    hideItem(stack, mouseSelection.append);
                }
            }

            mouseSelection = null;
        }
    }

    @Override
    public boolean onMouseWheel(int i, int mx, int my) {

        if (openListBox && listbox.hasScrollbar() && listbox.contains(mx, my)) {
            listbox.scroll(-i);
            return true;
        }

        return false;
    }

    @Override
    public void onGuiClick(int mx, int my) {

        if (!contains(mx, my)) {
            openListBox = false;
        }

        selectedDisplayName.onGuiClick(mx, my);
    }

    @Override
    public List<String> handleTooltip(int mx, int my, List<String> tooltip) {

        if (edit != null) {

            if (selectedState.contains(mx, my)) {
                return selectedState.handleTooltip(mx, my, tooltip);
            }

            if (selectedDisplayName.contains(mx, my)) {
                return selectedDisplayName.handleTooltip(mx, my, tooltip);
            }

        } else {

            if (selectedValue.contains(mx, my)) {
                return selectedValue.handleTooltip(mx, my, tooltip);
            }
        }

        return tooltip;
    }

    @Override
    public ItemFilter getFilter() {
        return this;
    }

    @Override
    public boolean matches(ItemStack stack) {

        if (edit != null) {
            return true;
        }

        for (PresetTag tag : listbox.getSelected()) {
            if (!tag.matches(stack)) {
                return false;
            }
        }

        return true;
    }
}
