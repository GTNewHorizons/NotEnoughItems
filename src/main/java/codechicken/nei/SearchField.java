package codechicken.nei;

import static codechicken.nei.NEIClientConfig.world;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.fluids.FluidStack;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.ItemList.NothingItemFilter;
import codechicken.nei.SearchTokenParser.ISearchParserProvider;
import codechicken.nei.SearchTokenParser.SearchMode;
import codechicken.nei.api.API;
import codechicken.nei.api.ItemFilter;
import codechicken.nei.api.ItemFilter.ItemFilterProvider;
import codechicken.nei.recipe.StackInfo;
import codechicken.nei.util.TextHistory;

public class SearchField extends TextField implements ItemFilterProvider {

    /**
     * Interface for returning a custom filter based on search field text
     */
    @Deprecated
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

    public static class SearchParserProvider implements ISearchParserProvider {

        protected final Function<Pattern, ItemFilter> createFilter;
        protected final String name;
        protected final char prefix;
        protected final EnumChatFormatting highlightedColor;

        public SearchParserProvider(char prefix, String name, EnumChatFormatting highlightedColor,
                Function<Pattern, ItemFilter> createFilter) {
            this.prefix = prefix;
            this.name = name;
            this.highlightedColor = highlightedColor;
            this.createFilter = createFilter;
        }

        @Override
        public ItemFilter getFilter(String searchText) {
            Pattern pattern = SearchField.getPattern(searchText);

            if (pattern != null) {
                return this.createFilter(pattern);
            }

            return new NothingItemFilter();
        }

        protected ItemFilter createFilter(Pattern pattern) {
            final ItemFilter filter = this.createFilter.apply(pattern);

            return filter == null ? new NothingItemFilter() : filter;
        }

        @Override
        public char getPrefix() {
            return this.prefix;
        }

        @Override
        public EnumChatFormatting getHighlightedColor() {
            return this.highlightedColor;
        }

        @Override
        public SearchMode getSearchMode() {
            return SearchMode.fromInt(NEIClientConfig.getIntSetting("inventory.search." + this.name + "SearchMode"));
        }
    }

    @Deprecated
    public static List<ISearchProvider> searchProviders = new LinkedList<>();
    public static SearchTokenParser searchParser = new SearchTokenParser();
    private static final TextHistory history = new TextHistory();
    private boolean isVisible = true;
    private long lastclicktime;

    public SearchField(String ident) {
        super(ident);
        API.addItemFilter(this);
    }

    @Override
    protected void initInternalTextField() {
        field = new FormattedTextField(Minecraft.getMinecraft().fontRenderer, 0, 0, 0, 0);
        ((FormattedTextField) field).setFormatter(new SearchTextFormatter(SearchField.searchParser));
        field.setMaxStringLength(maxSearchLength);
        field.setCursorPositionZero();
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
            }
            lastclicktime = System.currentTimeMillis();
        }
        return super.handleClick(mousex, mousey, button);
    }

    @Override
    public void onTextChange(String oldText) {
        final String newText = text();
        if (!newText.equals(oldText)) {
            if (newText.length() > 0) NEIClientConfig.logger.debug("Searching for " + newText);
            NEIClientConfig.setSearchExpression(newText);
            ItemList.updateFilter.restart();
        }
    }

    @Override
    public void lastKeyTyped(int keyID, char keyChar) {

        if (isVisible() && NEIClientConfig.isKeyHashDown("gui.search")) {
            setFocus(true);
        }
        if (focused() && NEIClientConfig.isKeyHashDown("gui.getprevioussearch")) {
            handleNavigateHistory(TextHistory.Direction.PREVIOUS);
        }

        if (focused() && NEIClientConfig.isKeyHashDown("gui.getnextsearch")) {
            handleNavigateHistory(TextHistory.Direction.NEXT);
        }
    }

    @Override
    public String filterText(String s) {
        return EnumChatFormatting.getTextWithoutFormattingCodes(s);
    }

    public static Pattern getPattern(String search) {
        return getPattern(search, NEIClientConfig.getIntSetting("inventory.search.patternMode"));
    }

    public static Pattern getPattern(String search, int patternMode) {
        switch (patternMode) {
            case 0: // plain
                search = Pattern.quote(search);
                break;
            case 1: // extended

                if (search.length() >= 3 && search.startsWith("r/") && search.endsWith("/")) {
                    search = search.substring(2, search.length() - 1);
                } else {
                    final Matcher matcher = Pattern.compile("(\\?|\\*)").matcher(search);
                    String cleanedString = "";
                    int lastEndIndex = 0;

                    while (matcher.find()) {
                        cleanedString += Pattern.quote(search.substring(lastEndIndex, matcher.start()));

                        switch (matcher.group(0).charAt(0)) {
                            case '?':
                                cleanedString += ".";
                                break;
                            case '*':
                                cleanedString += ".+?";
                                break;
                            default:
                                break;
                        }

                        lastEndIndex = matcher.end();
                    }

                    search = cleanedString + Pattern.quote(search.substring(lastEndIndex, search.length()));
                }
                break;
        }

        if (!search.isEmpty()) {
            try {
                return Pattern.compile(search, Pattern.MULTILINE | Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            } catch (PatternSyntaxException ignored) {}
        }

        return null;
    }

    public static String getEscapedSearchText(ItemStack stack) {
        final FluidStack fluidStack = StackInfo.getFluid(stack);
        String displayName;

        if (fluidStack != null) {
            displayName = fluidStack.getLocalizedName();
        } else {
            displayName = stack.getDisplayName();
        }

        return getEscapedSearchText(displayName);
    }

    public static String getEscapedSearchText(String text) {
        text = EnumChatFormatting.getTextWithoutFormattingCodes(text);

        switch (NEIClientConfig.getIntSetting("inventory.search.patternMode")) {
            case 1:
                text = text.replaceAll("[\\?|\\*]", "\\\\$0");
                break;
            case 2:
                text = text.replaceAll("[{}()\\[\\].+*?^$\\\\|]", "\\\\$0");
                break;
        }

        if (text.contains(" ") && NEIClientConfig.getBooleanSetting("inventory.search.quoteDropItemName")) {
            text = "\"" + text + "\"";
        }

        return text;
    }

    @Override
    public ItemFilter getFilter() {
        return getFilter(text());
    }

    public static ItemFilter getFilter(String filterText) {
        return SearchField.searchParser.getFilter(filterText);
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
            return history.get(direction, text()).map(newText -> {
                setText(newText);
                return true;
            }).orElse(false);
        }
        return false;
    }
}
