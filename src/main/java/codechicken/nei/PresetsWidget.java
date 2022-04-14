package codechicken.nei;

import codechicken.core.CommonUtils;
import codechicken.core.gui.GuiScrollSlot;
import codechicken.lib.gui.GuiDraw;
import codechicken.lib.vec.Rectangle4i;
import codechicken.nei.ItemList.AllMultiItemFilter;
import codechicken.nei.ItemList.EverythingItemFilter;
import codechicken.nei.ItemPanel.ItemPanelSlot;
import codechicken.nei.api.API;
import codechicken.nei.api.ItemFilter.ItemFilterProvider;
import codechicken.nei.api.ItemFilter;
import codechicken.nei.recipe.GuiRecipe;
import codechicken.nei.recipe.StackInfo;
import codechicken.nei.util.NBTJson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import cpw.mods.fml.common.registry.GameRegistry;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
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
import static codechicken.nei.NEIClientUtils.translate;

public class PresetsWidget extends Widget// implements ItemFilterProvider
{

    public static class PresetTag implements ItemFilter
    {
        public String filename = null;
        public String displayName = "";
        public boolean whitelist = true;
        public HashSet<String> items;

        public PresetTag(String displayName)
        {
            this(displayName, new HashSet<>(), false, null);
        }

        public PresetTag(String displayName, HashSet<String> items)
        {
            this(displayName, items, true, null);
        }

        public PresetTag(String displayName, HashSet<String> items, boolean whitelist, String filename)
        {
            this.displayName = displayName;
            this.items = items != null? items: new HashSet<>();
            this.whitelist = whitelist;
            this.filename = filename;
        }

        public boolean matches(final ItemStack stack)
        {
            return items.contains(StackInfo.getItemStackGUID(stack)) == whitelist;
        }

        public static PresetTag loadFromFile(File file)
        {
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
                final boolean whitelist = metaObject.get("whitelist").getAsBoolean();
                final HashSet<String> items = new HashSet<>();

                for (String itemStr: itemStrings) {
                    items.add(itemStr);
                }

                return new PresetTag(displayName, items, whitelist, file.getName());
            } catch (Throwable th) {
                NEIClientConfig.logger.error("Failed to load presets ItemStack from file", file);
            }

            return null;
        }
            
        public void deleteFile()
        {
            if (presetsDir == null || filename == null) return;
            final File tagFile = new File(presetsDir, filename);

            if (tagFile.exists()) {
                tagFile.delete();
            }

        }

        public void saveToFile()
        {
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
            row.add("whitelist", new JsonPrimitive(whitelist));

            strings.add(NBTJson.toJson(row));

            try {
                strings.addAll(items);
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

        @SuppressWarnings("unchecked")
        public PresetTag copy()
        {
            return new PresetTag(displayName, (HashSet) items.clone(), whitelist, null);
        }

    }
    protected static class SubsetListBox extends GuiScrollSlot
    {
        protected final List<PresetTag> presets = new ArrayList<>();
        protected final List<PresetTag> selected = new ArrayList<>();
        protected int lastSelectedIndex = 0;

        public SubsetListBox(File presetsDir)
        {
            super(0, 0, 0, 0);
            setSmoothScroll(false);

            try {
    
                presets.addAll(ItemList.forkJoinPool
                    .submit(() -> Arrays.asList(presetsDir.listFiles()).parallelStream()
                    .map(f -> PresetTag.loadFromFile(f))
                    .filter(p -> p != null)
                    .collect(Collectors.toCollection(ArrayList::new))).get());
    
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
                return;
            }

            selected.addAll(loadSelFromFile(presetsDir, presets));
            sort();
        }

        public SubsetListBox()
        {
            super(0, 0, 0, 0);
            setSmoothScroll(false);
        }

        public void addTag(final PresetTag tag, boolean sel)
        {
            presets.add(tag);

            if (sel) {
                selected.add(tag);
            }
        }

        protected List<PresetTag> getPresets()
        {
            return presets;
        }

        protected List<PresetTag> getSelected()
        {
            return selected;
        }

        public void replaceSeleted(final PresetTag tag)
        {

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

        public void sort()
        {
            presets.sort((o1, o2) -> o1.displayName.compareTo(o2.displayName));
        }

        public void clear()
        {
            presets.clear();
            selected.clear();
        }

        @Override
        public int getSlotHeight(int slot)
        {
            return 18;
        }

        @Override
        protected int getNumSlots()
        {
            return presets.size();
        }

        @Override
        protected void slotClicked(int slot, int button, int mx, int my, int count)
        {

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
                tag.whitelist = !tag.whitelist;
                tag.saveToFile();
            }

            saveSelToFile();
            PresetsWidget.edit = null;
            PresetsWidget.openListBox = !presets.isEmpty() && (NEIClientUtils.controlKey() || NEIClientUtils.shiftKey() || !option.contains(mx, my));
            ItemList.updateFilter.restart();
        }

        @Override
        protected void drawSlot(int slot, int x, int y, int mx, int my, float frame)
        {
            if (slot >= presets.size()) {
                return;
            }

            final int width = windowBounds().width;
            final Rectangle4i direction = new Rectangle4i(x, y, 18, 18);
            final Rectangle4i delete = new Rectangle4i(x + width - 18, y, 18, 18);
            final Rectangle4i option = new Rectangle4i(x + 18, y, width - 18 - 18, 18);
            final PresetTag tag = presets.get(slot);

            final int optionState = selected.contains(tag)? 0: option.contains(x + mx, y + my)? 1: 2;
            final int directionState = direction.contains(x + mx, y + my)? 1: 2;
            final int deleteState = delete.contains(x + mx, y + my)? 1: 2;

            final String displayName = cropText(tag.displayName, option.w - 6);
            final String dirName = tag.whitelist? translate("presets.whitelist.label"): translate("presets.blacklist.label");

            //blacklist or whitelist
            LayoutManager.getLayoutStyle().drawSubsetTag(null, direction.x, direction.y, direction.w, direction.h, directionState, false);
            GuiDraw.drawString(dirName, direction.x + 6, direction.y + 5, directionState == 2 ? 0xFFE0E0E0 : 0xFFFFA0);
            
            //option name
            LayoutManager.getLayoutStyle().drawSubsetTag(null, option.x, option.y, option.w, option.h, optionState, false);
            GuiDraw.drawString(displayName, option.x + 3, option.y + 5, optionState == 2 ? 0xFFE0E0E0 : 0xFFFFA0);

            //remove icon
            LayoutManager.getLayoutStyle().drawSubsetTag(null, delete.x, delete.y, delete.w, delete.h, deleteState, false);
            GuiDraw.drawString("✕", delete.x + 6, delete.y + 5, deleteState == 2 ? 0xFFE0E0E0 : 0x601010);

        }

        @Override
        public void drawOverlay(float frame)
        {
        }

        @Override 
        public void drawBackground(float frame)
        {
            drawRect(x , y, x + width, y + height, 0xFF202020);
        }

        @Override
        public void drawScrollbar(float frame)
        {
            if (hasScrollbar()) {
                super.drawScrollbar(frame);
            }
        }

        @Override
        public int scrollbarGuideAlignment()
        {
            return 0;
        }

        protected List<PresetTag> loadSelFromFile(File presetsDir, List<PresetTag> presets)
        {
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

                    for (PresetTag tag: presets) {
                        map.put(tag.filename, tag);
                    }

                    for (String filename: itemStrings) {
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

        public void saveSelToFile()
        {

            final File file = new File(presetsDir, "selected.ini");
            final List<String> list = new ArrayList<>();

            for (final PresetTag tag: selected) {
                list.add(tag.filename);
            }

            try (FileOutputStream output = new FileOutputStream(file)) {
                IOUtils.writeLines(list, "\n", output, "UTF-8");
            } catch (IOException e) {
                NEIClientConfig.logger.error("Filed to save presets selected list to file {}", file, e);
            }

        }

    }
    protected static class MouseSelection
    {
        public int startX = -1;
        public int startY = -1;
        public int startIndex = -1;
        public int endIndex = -1;
        public HashSet<ItemStack> items = new HashSet<>();
        public boolean append = true;

        public MouseSelection(int slotIndex, boolean append)
        {
            final ItemsGrid grid  = ItemPanels.itemPanel.getGrid();
            final Rectangle4i rec = grid.getSlotRect(slotIndex - (grid.getPage() - 1) * grid.getPerPage());

            this.append = append;
            endIndex = slotIndex;
            startIndex = slotIndex;
            startX = rec.x;
            startY = rec.y;
        }

    }

    protected final static FontRenderer fontRenderer = Minecraft.getMinecraft().fontRenderer;
    protected static SubsetListBox listbox = new SubsetListBox();
    protected static File presetsDir;

    protected static boolean openListBox = false;
    protected static PresetTag edit;
    protected long lastclicktime;
    protected static MouseSelection mouseSelection;

    protected final static Button dropdown = new Button()
    {

        public boolean onButtonPress(boolean rightclick) 
        {
            if (!rightclick) {
                openListBox = !openListBox;
                return true;
            }

            return false;   
        }
        
        @Override
        public String getRenderLabel()
        {
            return "...";
        }

    };

    protected final static Label selectedValue = new Label("", false)
    {

        @Override
        public void draw(int mousex, int mousey)
        {
            final String text = cropText(this.text, w);

            if (text.equals(this.text)) {
                GuiDraw.drawString(text, x + w - fontRenderer.getStringWidth(text), y + (h - 8) / 2, colour);
            } else {
                GuiDraw.drawString(text, x, y + (h - 8) / 2, colour);
            }

        }

        @Override
        public List<String> handleTooltip(int mx, int my, List<String> tooltip)
        {
            if (contains(mx, my)) {
                tooltip.add(translate("presets.label.tooltip"));
            }
            
            return tooltip;
        }

    };
    
    protected final static TextField selectedDisplayName = new TextField("")
    {

        public void onTextChange(String oldText)
        {
            if (edit != null) {
                edit.displayName = text();
            }
        }

        @Override
        public void draw(int mousex, int mousey)
        {
            super.draw(mousex, mousey);

            if (text().isEmpty()) {
                GuiDraw.drawGradientRect(x - 1, y - 1, 1, h + 2, 0xFFcc3300, 0xFFcc3300); //Left
                GuiDraw.drawGradientRect(x - 1, y - 1, w + 2, 1, 0xFFcc3300, 0xFFcc3300); //Top
                GuiDraw.drawGradientRect(x + w, y - 1, 1, h + 2, 0xFFcc3300, 0xFFcc3300); //Left
                GuiDraw.drawGradientRect(x - 1, y + h, w + 2, 1, 0xFFcc3300, 0xFFcc3300); //Bottom
            }

        }

        @Override
        public List<String> handleTooltip(int mx, int my, List<String> tooltip)
        {
            if (contains(mx, my) && listbox.getSelected().size() <= 1) {
                tooltip.add(translate("presets.textfield.tooltip.1"));
                tooltip.add(translate("presets.textfield.tooltip.2"));
            }

            return tooltip;
        }

    };

    protected final static Button selectedState = new Button("B")
    {

        public boolean onButtonPress(boolean rightclick) 
        {
            if (!rightclick && edit != null) {
                edit.whitelist = !edit.whitelist;
                return true;
            }

            return false;   
        }
        
        @Override
        public String getRenderLabel()
        {
            return edit != null && edit.whitelist? translate("presets.whitelist.label"): translate("presets.blacklist.label");
        }
            
        @Override
        public void addTooltips(List<String> tooltip)
        {
            if (edit != null) {
                tooltip.add(edit.whitelist? translate("presets.whitelist.tooltip"): translate("presets.blacklist.tooltip"));
            }
        } 

    };
    
    protected static String cropText(String text, int containerWidth)
    {

        int textWidth = fontRenderer.getStringWidth(text);

        if (textWidth > containerWidth) {
            textWidth += fontRenderer.getStringWidth("...");

            while (textWidth > containerWidth) {
                textWidth -= fontRenderer.getCharWidth(text.charAt(text.length() - 1));
                text = text.substring(0, text.length() - 1);
            }

            return text + "...";
        }

        return text;
    }

    protected static void setEditedTag(final PresetTag tag)
    {
        edit = tag;
        openListBox = false;
        selectedDisplayName.setText(tag != null? tag.displayName: "");
        ItemList.updateFilter.restart();
    }

    public static void addTag(final PresetTag tag)
    {
        addTag(tag, false);
    }

    public static void addTag(final PresetTag tag, boolean sel)
    {
        listbox.addTag(tag, sel);
    }

    public static boolean inEditMode()
    {
        return edit != null;
    }

    public static boolean isHidden(final ItemStack item)
    {

        if (edit != null || listbox.getSelected().isEmpty()) {

            if (edit != null) {

                if (mouseSelection != null && mouseSelection.items.contains(item)) {
                    return mouseSelection.append != edit.whitelist;
                }

                return !edit.matches(item);
            }

            final List<PresetTag> selected = listbox.getSelected();

            for (int idx = 0; idx < selected.size(); idx++) {
                if (!selected.get(idx).matches(item)) {
                    return true;
                }
            }
            
        }

        return false;
    }

    public static void setHidden(final ItemStack stack, final boolean append)
    {
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

    protected static void hideItem(final ItemStack stack, boolean append)
    {
        final String guid = StackInfo.getItemStackGUID(stack);

        if (append) {
            edit.items.add(guid);
        } else {
            edit.items.remove(guid);
        }

    }

    protected static String getModId(final ItemStack stack)
    {
        try {
            return GameRegistry.findUniqueIdentifierFor(stack.getItem()).modId;
        } catch (Exception ignored) {}
    
        return null;
    }

    public static void loadPresets(final String worldPath)
    {
        final File dir = new File(CommonUtils.getMinecraftDir(), "saves/NEI/" + worldPath);
        presetsDir = new File(CommonUtils.getMinecraftDir(), "saves/NEI/" + worldPath + "/presets");

        if (!dir.exists()) {
            dir.mkdirs();
        }

        if (!presetsDir.exists()) {
            presetsDir.mkdirs();
        }

        listbox = new SubsetListBox(presetsDir);
        
        edit = null;
        openListBox = false;
        selectedDisplayName.setText("");

        ItemList.updateFilter.restart();
    }

    // public PresetsWidget()
    // {
    //     API.addItemFilter(this);
    // }

    @Override
    public void draw(int mx, int my)
    {
        final int panelHeight = 16;
        final int panelWidth = 153;

        w = panelWidth;
        h = panelHeight;
        y = 2;
        x = ItemPanels.itemPanel.x - 4 - w;
        
        if (edit != null) {

            selectedState.x = x;
            selectedState.y = y;
            selectedState.w = selectedState.h = panelHeight;
            selectedState.draw(mx, my);

            selectedDisplayName.y = y;
            selectedDisplayName.h = h;
            selectedDisplayName.x = selectedState.x + selectedState.w + 1;
            selectedDisplayName.w = w - panelHeight * 2 - 5;
            selectedDisplayName.draw(mx, my);

        } else {
            final List<PresetTag> selected = listbox.getSelected();
            final int size = selected.size();
            
            selectedValue.text = size> 1? translate("presets.label.selected", size): (size == 1? selected.get(0).displayName: "");
            selectedValue.y = y;
            selectedValue.h = h;
            selectedValue.w = w - panelHeight - 4;
            selectedValue.x = x;

            selectedValue.draw(mx, my);   
        }
    

        dropdown.y = y;
        dropdown.w = dropdown.h = panelHeight;
        dropdown.x = x + w - dropdown.w;
        dropdown.state = edit != null || listbox.getNumSlots() == 0? 2: 0;
        dropdown.draw(mx, my);

        if (openListBox) {
            final int scrollbarWidth = listbox.hasScrollbar() ? listbox.scrollbarDim().width : 0;

            listbox.setSize(
                x, 
                y + h + 1, 
                w,
                Math.min(((LayoutManager.searchField.y - h - y) / 18) * 18, listbox.contentHeight())
            );

            listbox.setMargins(0, 0, scrollbarWidth, 0);
            listbox.draw(mx, my, 0);
        }

    }

    @Override
    public boolean contains(int px, int py)
    {
        return super.contains(px, py) || openListBox && listbox.contains(px, py);
    }

    @Override
    public boolean handleClick(int mx, int my, int button)
    {

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

                if (System.currentTimeMillis() - lastclicktime < 400) { //double click
                    final List<PresetTag> selected = listbox.getSelected();
                    final int size = selected.size();

                    if (size <= 1) {
                        setEditedTag(size == 1? selected.get(0).copy(): new PresetTag(""));
                    }

                }

                lastclicktime = System.currentTimeMillis();
            }

        }

        return contains(mx, my);
    }

    @Override
    public boolean handleClickExt(int mx, int my, int button)
    {

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
    public boolean handleKeyPress(int keyID, char keyChar)
    {
        if (keyID == Keyboard.KEY_ESCAPE) {
            openListBox = false;
        }

        if (edit == null) {
            return false;
        }

        if (selectedDisplayName.focused() && (keyID == Keyboard.KEY_RETURN || keyID == Keyboard.KEY_NUMPADENTER) && !selectedDisplayName.text().isEmpty()) {
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
    public void mouseDragged(int mx, int my, int button, long heldTime)
    {

        if (openListBox) {
            listbox.mouseDragged(mx, my, button, heldTime);
        }

        if (edit != null && button != 2 && mouseSelection != null) {
            final ItemPanelSlot slot = ItemPanels.itemPanel.getSlotMouseOver(mx, my);

            if (slot != null && slot.slotIndex != mouseSelection.endIndex) {
                final ItemsGrid grid = ItemPanels.itemPanel.getGrid();
                mouseSelection.endIndex = slot.slotIndex;
                mouseSelection.items.clear();

                final Rectangle4i rec = grid.getSlotRect(slot.slotIndex - (grid.getPage() - 1) * grid.getPerPage());
                final Rectangle4i sel = new Rectangle4i(
                    Math.min(rec.x, mouseSelection.startX),
                    Math.min(rec.y, mouseSelection.startY),
                    Math.max(rec.x, mouseSelection.startX) - Math.min(rec.x, mouseSelection.startX),
                    Math.max(rec.y, mouseSelection.startY) - Math.min(rec.y, mouseSelection.startY)
                );

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
    public void mouseUp(int mx, int my, int button)
    {

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
    public boolean onMouseWheel(int i, int mx, int my)
    {

        if (openListBox && listbox.hasScrollbar() && listbox.contains(mx, my)) {
            listbox.scroll(-i);
            return true;
        }

        return false;
    }

    @Override
    public void onGuiClick(int mx, int my)
    {

        if (!contains(mx, my)) {
            openListBox = false;
        }

        selectedDisplayName.onGuiClick(mx, my);
    }

    @Override
    public List<String> handleTooltip(int mx, int my, List<String> tooltip)
    {

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

    public static boolean matches(ItemStack stack)
    {
        final List<PresetTag> selected = listbox.getSelected();

        for (int idx = 0; idx < selected.size(); idx++) {
            if (!selected.get(idx).matches(stack)) {
                return false;
            }
        }

        return true;
    }

}
