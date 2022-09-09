package codechicken.nei;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.ItemList.AnyMultiItemFilter;
import codechicken.nei.ItemList.EverythingItemFilter;
import codechicken.nei.ItemList.PatternItemFilter;
import codechicken.nei.api.API;
import codechicken.nei.api.ItemFilter;
import codechicken.nei.api.ItemFilter.ItemFilterProvider;
import codechicken.nei.util.TextHistory;
import net.minecraft.client.Minecraft;
import net.minecraft.util.EnumChatFormatting;
import org.lwjgl.input.Keyboard;

import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static codechicken.nei.NEIClientConfig.world;

public class SearchField extends TextField implements ItemFilterProvider {
    /**
     * Interface for returning a custom filter based on search field text
     */
    public static interface ISearchProvider {
        /**
         * @return false if this filter should only be used if no other non-default filters match the search string
         */
        public boolean isPrimary();

        /**
         * @return An item filter for items matching SearchTex null to ignore this provider
         */
        public ItemFilter getFilter(String searchText);
    }

    private static class DefaultSearchProvider implements ISearchProvider {
        @Override
        public boolean isPrimary() {
            return false;
        }

        @Override
        public ItemFilter getFilter(String searchText) {
            Pattern pattern = getPattern(searchText);
            return pattern == null ? null : new PatternItemFilter(pattern);
        }
    }

    public static List<ISearchProvider> searchProviders = new LinkedList<>();
    private static final TextHistory history = new TextHistory();
    private boolean isVisible = true;

    long lastclicktime;

    public SearchField(String ident) {
        super(ident);
        API.addItemFilter(this);
        API.addSearchProvider(new DefaultSearchProvider());
    }

    public static boolean searchInventories() {
        return world.nbt.getBoolean("searchinventories");
    }

    public boolean isVisible() {
        return isVisible;
    }

    public void setVisible(boolean visible) {
        isVisible = visible;
    }

    @Override
    public int getTextColour() {
        if (ItemPanels.itemPanel.getItems().isEmpty()) {
            return focused() ? 0xFFcc3300 : 0xFF993300;
        } else {
            return focused() ? 0xFFE0E0E0 : 0xFF909090;
        }
    }

    @Override
    public void draw(int mousex, int mousey) {

        super.draw(mousex, mousey);

        if (searchInventories()) {
            GuiDraw.drawGradientRect(x - 1, y - 1, 1, h + 2, 0xFFFFFF00, 0xFFC0B000); // Left
            GuiDraw.drawGradientRect(x - 1, y - 1, w + 2, 1, 0xFFFFFF00, 0xFFC0B000); // Top
            GuiDraw.drawGradientRect(x + w, y - 1, 1, h + 2, 0xFFFFFF00, 0xFFC0B000); // Left
            GuiDraw.drawGradientRect(x - 1, y + h, w + 2, 1, 0xFFFFFF00, 0xFFC0B000); // Bottom
        }
    }

    @Override
    public boolean handleClick(int mousex, int mousey, int button) {
        if (button == 0) {
            if (focused() && (System.currentTimeMillis() - lastclicktime < 400)) { // double click
                NEIClientConfig.world.nbt.setBoolean("searchinventories", !searchInventories());
                NEIClientConfig.world.saveNBT();
            }
            lastclicktime = System.currentTimeMillis();
        }
        return super.handleClick(mousex, mousey, button);
    }

    @Override
    public void onTextChange(String oldText) {
        final String newText = text();
        if (newText.length() > 0) NEIClientConfig.logger.debug("Searching for " + text());

        NEIClientConfig.setSearchExpression(newText);
        ItemList.updateFilter.restart();
    }

    @Override
    public void lastKeyTyped(int keyID, char keyChar) {

        if (isVisible() && NEIClientConfig.isKeyHashDown("gui.search")) {
            setFocus(true);
        }
        if (NEIClientConfig.isKeyHashDown("gui.getprevioussearch") && focused()) {
            handleNavigateHistory(TextHistory.Direction.PREVIOUS);
        }

        if (NEIClientConfig.isKeyHashDown("gui.getnextsearch") && focused()) {
            handleNavigateHistory(TextHistory.Direction.NEXT);
        }
    }

    @Override
    public String filterText(String s) {
        return EnumChatFormatting.getTextWithoutFormattingCodes(s);
    }

    public static Pattern getPattern(String search) {
        switch (NEIClientConfig.getIntSetting("inventory.searchmode")) {
            case 0: // plain
                search = "\\Q" + search + "\\E";
                break;
            case 1:
                search = search.replace(".", "").replace("?", ".").replace("*", ".+?");
                break;
        }

        Pattern pattern = null;
        try {
            pattern = Pattern.compile(search);
        } catch (PatternSyntaxException ignored) {
        }
        return pattern == null || pattern.toString().length() == 0 ? null : pattern;
    }

    @Override
    public ItemFilter getFilter() {
        String s_filter = text().toLowerCase();

        List<ItemFilter> primary = new LinkedList<>();
        List<ItemFilter> secondary = new LinkedList<>();
        for (ISearchProvider p : searchProviders) {
            ItemFilter filter = p.getFilter(s_filter);
            if (filter != null) (p.isPrimary() ? primary : secondary).add(filter);
        }

        if (!primary.isEmpty()) return new AnyMultiItemFilter(primary);
        if (!secondary.isEmpty()) return new AnyMultiItemFilter(secondary);
        return new EverythingItemFilter();
    }

    @Override
    public void setFocus(boolean focus) {
        final boolean previousFocus = field.isFocused();

        if (previousFocus != focus) {
            history.add(text());
        }
        super.setFocus(focus);
    }

    private boolean handleNavigateHistory(TextHistory.Direction direction) {
        if (focused()) {
            return history.get(direction,text())
                    .map(newText -> {
                        setText(newText);   
                        return true;
                    })
                    .orElse(false);
        }
        return false;
    }
}
