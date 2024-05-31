package codechicken.nei;

import static codechicken.nei.NEIClientConfig.world;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.fluids.FluidStack;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.FormattedTextField.TextFormatter;
import codechicken.nei.ItemList.AllMultiItemFilter;
import codechicken.nei.ItemList.AnyMultiItemFilter;
import codechicken.nei.ItemList.EverythingItemFilter;
import codechicken.nei.ItemList.NegatedItemFilter;
import codechicken.nei.ItemList.NothingItemFilter;
import codechicken.nei.ItemList.PatternItemFilter;
import codechicken.nei.api.API;
import codechicken.nei.api.ItemFilter;
import codechicken.nei.api.ItemFilter.ItemFilterProvider;
import codechicken.nei.recipe.StackInfo;
import codechicken.nei.search.IdentifierFilter;
import codechicken.nei.search.ModNameFilter;
import codechicken.nei.search.OreDictionaryFilter;
import codechicken.nei.search.TooltipFilter;
import codechicken.nei.util.TextHistory;
import scala.collection.mutable.StringBuilder;

public class SearchField extends TextField implements ItemFilterProvider {

    public enum SearchMode {

        ALWAYS,
        PREFIX,
        NEVER;

        public static SearchMode fromInt(int value) {
            switch (value) {
                case 0:
                    return ALWAYS;
                case 1:
                    return PREFIX;
                case 2:
                    return NEVER;
                default:
                    return ALWAYS;
            }
        }
    }

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

    public static interface ISearchParserProvider {

        public ItemFilter getFilter(String searchText);

        public char getPrefix();

        public EnumChatFormatting getHighlightedColor();

        public SearchMode getSearchMode();
    }

    public static class SearchParserProvider implements ISearchParserProvider {

        protected final List<Function<Pattern, ItemFilter>> subFilters = new ArrayList<>();
        protected final String name;
        protected final char prefix;
        protected final EnumChatFormatting highlightedColor;

        public SearchParserProvider(char prefix, String name, EnumChatFormatting highlightedColor,
                Function<Pattern, ItemFilter> createFilter) {
            this.prefix = prefix;
            this.name = name;
            this.highlightedColor = highlightedColor;
            this.addSubFilter(createFilter);
        }

        public void addSubFilter(Function<Pattern, ItemFilter> subFilter) {
            this.subFilters.add(subFilter);
        }

        @Override
        public ItemFilter getFilter(String searchText) {
            Pattern pattern = SearchField.getPattern(searchText);

            if (pattern != null) {
                return this.createFilter(pattern);
            }

            return null;
        }

        protected ItemFilter createFilter(Pattern pattern) {
            final List<ItemFilter> filters = new ArrayList<>();

            for (Function<Pattern, ItemFilter> createFilter : this.subFilters) {
                ItemFilter filter = createFilter.apply(pattern);
                if (filter != null) {
                    filters.add(filter);
                }

            }

            return filters.isEmpty() ? null : new AnyMultiItemFilter(filters);
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

    public static class SearchTextFormatter implements TextFormatter {

        public String format(String text) {
            final String[] parts = text.split("\\|");
            StringJoiner formattedText = new StringJoiner(EnumChatFormatting.GRAY + "|");

            for (String filterText : parts) {
                Matcher filterMatcher = SearchField.getFilterSplitPattern().matcher(filterText);
                StringBuilder formattedPart = new StringBuilder();
                int startIndex = 0;

                while (filterMatcher.find()) {
                    boolean ignore = "-".equals(filterMatcher.group(2));
                    String firstChar = filterMatcher.group(3);
                    String token = filterMatcher.group(4);
                    boolean quotes = token.length() > 1 && token.startsWith("\"") && token.endsWith("\"");

                    if (quotes) {
                        token = token.substring(1, token.length() - 1);
                    }

                    formattedPart.append(filterText.substring(startIndex, filterMatcher.start()));
                    EnumChatFormatting tokenColor = EnumChatFormatting.RESET;

                    if (!firstChar.isEmpty()) {
                        tokenColor = SearchField.searchParserProviders.get(firstChar.charAt(0)).getHighlightedColor();
                    }

                    if (ignore) {
                        formattedPart.append(EnumChatFormatting.BLUE + "-");
                    }

                    if (!firstChar.isEmpty()) {
                        formattedPart.append(tokenColor + firstChar);
                    }

                    if (quotes) {
                        formattedPart.append(EnumChatFormatting.GOLD + "\"");
                    }

                    if (!token.isEmpty()) {
                        formattedPart.append(tokenColor + token);
                    }

                    if (quotes) {
                        formattedPart.append(EnumChatFormatting.GOLD + "\"");
                    }

                    startIndex = filterMatcher.end();
                }

                formattedPart.append(filterText.substring(startIndex, filterText.length()));
                formattedText.add(formattedPart);
            }

            if (text.endsWith("|")) {
                formattedText.add("");
            }

            return formattedText.toString();
        }
    }

    @Deprecated
    public static List<ISearchProvider> searchProviders = new LinkedList<>();
    public static final Map<Character, ISearchParserProvider> searchParserProviders = new HashMap<>();
    private static final TextHistory history = new TextHistory();
    private boolean isVisible = true;
    private long lastclicktime;

    public SearchField(String ident) {
        super(ident);
        API.addItemFilter(this);
        API.addSearchProvider(
                new SearchParserProvider(
                        '\0',
                        "default",
                        EnumChatFormatting.RESET,
                        (pattern) -> new PatternItemFilter(pattern)) {

                    @Override
                    public SearchMode getSearchMode() {
                        return SearchMode.ALWAYS;
                    }

                });
        API.addSearchProvider(
                new SearchParserProvider(
                        '@',
                        "modName",
                        EnumChatFormatting.LIGHT_PURPLE,
                        (pattern) -> new ModNameFilter(pattern)));
        API.addSearchProvider(
                new SearchParserProvider(
                        '$',
                        "oreDict",
                        EnumChatFormatting.AQUA,
                        (pattern) -> new OreDictionaryFilter(pattern)));
        API.addSearchProvider(
                new SearchParserProvider(
                        '#',
                        "tooltip",
                        EnumChatFormatting.YELLOW,
                        (pattern) -> new TooltipFilter(pattern)));
        API.addSearchProvider(
                new SearchParserProvider(
                        '&',
                        "identifier",
                        EnumChatFormatting.GOLD,
                        (pattern) -> new IdentifierFilter(pattern)));
    }

    @Override
    protected void initInternalTextField() {
        field = new FormattedTextField(Minecraft.getMinecraft().fontRenderer, 0, 0, 0, 0);
        ((FormattedTextField) field).setFormatter(new SearchTextFormatter());
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
        if (newText.length() > 0) NEIClientConfig.logger.debug("Searching for " + newText);
        NEIClientConfig.setSearchExpression(newText);
        ItemList.updateFilter.restart();
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
        switch (NEIClientConfig.getIntSetting("inventory.search.patternMode")) {
            case 0: // plain
                search = Pattern.quote(search);
                break;
            case 1: // extended
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

    protected static Pattern getFilterSplitPattern() {
        StringJoiner prefixes = new StringJoiner("");
        prefixes.add(String.valueOf('\0'));

        for (ISearchParserProvider provider : SearchField.searchParserProviders.values()) {
            if (provider.getSearchMode() == SearchMode.PREFIX) {
                prefixes.add(String.valueOf(provider.getPrefix()));
            }
        }

        return Pattern.compile("((-*)([" + Pattern.quote(prefixes.toString()) + "]*)(\\\".*?(?:\\\"|$)|\\S+))");
    }

    public static ItemFilter getFilter(String filterText) {
        final String[] parts = EnumChatFormatting.getTextWithoutFormattingCodes(filterText).toLowerCase().split("\\|");
        final List<ItemFilter> searchTokens = Arrays.stream(parts).map(SearchField::parseSearchTokens)
                .filter(s -> s != null).collect(Collectors.toCollection(ArrayList::new));

        if (searchTokens.isEmpty()) {
            return new EverythingItemFilter();
        } else {
            return new AnyMultiItemFilter(searchTokens);
        }
    }

    private static ItemFilter parseSearchTokens(String filterText) {

        if (filterText.isEmpty()) {
            return null;
        }

        final Matcher filterMatcher = getFilterSplitPattern().matcher(filterText);
        final AllMultiItemFilter searchTokens = new AllMultiItemFilter();

        while (filterMatcher.find()) {
            boolean ignore = "-".equals(filterMatcher.group(2));
            String firstChar = filterMatcher.group(3);
            String token = filterMatcher.group(4);
            boolean quotes = token.length() > 1 && token.startsWith("\"") && token.endsWith("\"");

            if (quotes) {
                token = token.substring(1, token.length() - 1);
            }

            if (!token.isEmpty()) {
                ItemFilter result = parseToken(firstChar, token);

                if (ignore) {
                    searchTokens.filters.add(new NegatedItemFilter(result));
                } else {
                    searchTokens.filters.add(result);
                }
            } else if (!ignore) {
                searchTokens.filters.add(new NothingItemFilter());
            }
        }

        return searchTokens;
    }

    private static ItemFilter parseToken(String firstChar, String token) {
        final ISearchParserProvider provider = firstChar.isEmpty() ? null
                : SearchField.searchParserProviders.get(firstChar.charAt(0));

        if (provider == null || provider.getSearchMode() == SearchMode.NEVER) {
            final List<ItemFilter> filters = new ArrayList<>();

            for (ISearchParserProvider _provider : SearchField.searchParserProviders.values()) {
                if (_provider.getSearchMode() == SearchMode.ALWAYS) {
                    ItemFilter filter = _provider.getFilter(token);
                    if (filter != null) {
                        filters.add(filter);
                    }
                }
            }

            return filters.isEmpty() ? new NothingItemFilter() : new AnyMultiItemFilter(filters);
        } else {
            ItemFilter filter = provider.getFilter(token);
            return filter != null ? filter : new NothingItemFilter();
        }
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
