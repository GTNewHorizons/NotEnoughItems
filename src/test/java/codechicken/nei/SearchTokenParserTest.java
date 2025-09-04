package codechicken.nei;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Named.named;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.Language;
import net.minecraft.client.resources.LanguageManager;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.FieldSource;
import org.mockito.MockedStatic;

import codechicken.nei.ItemList.AllMultiItemFilter;
import codechicken.nei.ItemList.AnyMultiItemFilter;
import codechicken.nei.ItemList.PatternItemFilter;
import codechicken.nei.SearchField.SearchParserProvider;
import codechicken.nei.api.ItemFilter;
import codechicken.nei.search.IdentifierFilter;

@DisplayName("Search expression")
public class SearchTokenParserTest {

    private static class ModeValue {

        private final String name;
        private final int value;

        public ModeValue(int value, String name) {
            this.value = value;
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public int getValue() {
            return value;
        }
    }

    private static final ModeValue[] spaceModes = { new ModeValue(0, "space works like AND"),
            new ModeValue(1, "space works like SPACE"), new ModeValue(2, "space works like AND-X") };
    private static final ModeValue[] spaceIsAndMode = { spaceModes[0] };
    private static final ModeValue[] spaceIsSpaceMode = { spaceModes[1] };
    private static final ModeValue[] patternModes = { new ModeValue(0, "Plain Text mode"),
            new ModeValue(1, "Extended mode"), new ModeValue(2, "Regex mode"), new ModeValue(3, "Extended+ mode") };
    private static final ModeValue[] extendedPlusPatternMode = { patternModes[3] };
    private static final ModeValue[] extendedPatternModes = { patternModes[1], patternModes[3] };

    private static final SearchParserProvider defaultParserProvider = parserProvider(
            '\0',
            "default",
            SearchTokenParser.SearchMode.ALWAYS,
            PatternItemFilter::new);

    private static final Function<Pattern, ItemFilter> customCreateFilter = (pattern) -> new IdentifierFilter(pattern) {

        @Override
        public boolean matches(ItemStack stack) {
            return pattern.matcher(getStringIdentifier(stack)).find();
        }

        @Override
        protected String getStringIdentifier(ItemStack stack) {
            if (stack == itemA) {
                return "A";
            }
            return "F";
        }

        @Override
        protected String getIdentifier(ItemStack stack) {
            return getStringIdentifier(stack);
        }
    };

    private static MockedStatic<NEIClientConfig> config;
    private static Language language;
    private static ItemStack itemA;
    private static ItemStack itemB;
    private static ItemStack itemC;
    private static ItemStack itemAB;
    private static ItemStack itemABC;
    private static ItemStack itemQuestionMark;

    private static List<Arguments> combinationsProvider(ModeValue[] spaceModes, ModeValue[] patternModes) {
        List<Arguments> combinations = new ArrayList<>();
        for (ModeValue s : spaceModes) {
            for (ModeValue p : patternModes) {
                combinations
                        .add(Arguments.arguments(named(s.getName(), s.getValue()), named(p.getName(), p.getValue())));
            }
        }
        return combinations;
    }

    private static final List<Arguments> allModesTestProvider = combinationsProvider(spaceModes, patternModes);

    private static final List<Arguments> extendedPlusAllSpaceModesTestProvider = combinationsProvider(
            spaceModes,
            extendedPlusPatternMode);

    private static final List<Arguments> spaceIsSpaceAllModesTestProvider = combinationsProvider(
            spaceIsSpaceMode,
            patternModes);

    private static final List<Arguments> spaceIsAndAllModesTestProvider = combinationsProvider(
            spaceIsAndMode,
            patternModes);

    private static final List<Arguments> extendedAllSpaceModesTestProvider = combinationsProvider(
            spaceModes,
            extendedPatternModes);

    private static final List<Arguments> spaceIsAndExtendedPlusModeTestProvider = combinationsProvider(
            spaceIsAndMode,
            extendedPlusPatternMode);

    @BeforeAll
    public static void initItems() {
        itemA = mock(ItemStack.class);
        when(itemA.getDisplayName()).thenReturn("a");
        itemB = mock(ItemStack.class);
        when(itemB.getDisplayName()).thenReturn("b");
        itemC = mock(ItemStack.class);
        when(itemC.getDisplayName()).thenReturn("c");
        itemAB = mock(ItemStack.class);
        when(itemAB.getDisplayName()).thenReturn("a b");
        itemABC = mock(ItemStack.class);
        when(itemABC.getDisplayName()).thenReturn("a b c");
        itemQuestionMark = mock(ItemStack.class);
        when(itemQuestionMark.getDisplayName()).thenReturn("?");

        MockedStatic<Minecraft> staticMinecraft = mockStatic(Minecraft.class);
        Minecraft minecraft = mock(Minecraft.class);
        staticMinecraft.when(() -> Minecraft.getMinecraft()).thenReturn(minecraft);
        LanguageManager languageManager = mock(LanguageManager.class);
        when(minecraft.getLanguageManager()).thenReturn(languageManager);
        language = mock(Language.class);
        when(languageManager.getCurrentLanguage()).thenReturn(language);
        when(language.getLanguageCode()).thenReturn("en");
        config = mockStatic(NEIClientConfig.class);
        config.when(() -> NEIClientConfig.getBooleanSetting(eq("inventory.search.logSearchExceptions")))
                .thenReturn(false);

    }

    // All modes tests

    @DisplayName("No providers")
    @ParameterizedTest(name = "{argumentsWithNames}")
    @FieldSource("allModesTestProvider")
    public void testWithoutProviders(int spaceMode, int patternMode) {
        SearchTokenParser searchParser = new SearchTokenParser();
        Set<ItemStack> matchedItems = testFilterAgainstAllItems(spaceMode, patternMode, searchParser, "test");

        assertEquals(0, matchedItems.size());
    }

    @DisplayName("Or")
    @ParameterizedTest(name = "{argumentsWithNames}")
    @FieldSource("allModesTestProvider")
    public void testOr(int spaceMode, int patternMode) {
        SearchTokenParser searchParser = new SearchTokenParser();
        searchParser.addProvider(defaultParserProvider);
        Set<ItemStack> matchedItems = testFilterAgainstAllItems(
                spaceMode,
                patternMode,
                searchParser,
                itemA.getDisplayName() + "|" + itemC.getDisplayName());
        assertEquals(4, matchedItems.size());
        assertTrue(matchedItems.contains(itemA));
        assertTrue(matchedItems.contains(itemC));
        assertTrue(matchedItems.contains(itemAB));
        assertTrue(matchedItems.contains(itemABC));
    }

    @DisplayName("Negate")
    @ParameterizedTest(name = "{argumentsWithNames}")
    @FieldSource("allModesTestProvider")
    public void testNegate(int spaceMode, int patternMode) {
        SearchTokenParser searchParser = new SearchTokenParser();
        searchParser.addProvider(defaultParserProvider);
        Set<ItemStack> matchedItems = testFilterAgainstAllItems(
                spaceMode,
                patternMode,
                searchParser,
                "-" + itemA.getDisplayName());

        assertEquals(3, matchedItems.size());
        assertTrue(matchedItems.contains(itemB));
        assertTrue(matchedItems.contains(itemC));
        assertTrue(matchedItems.contains(itemQuestionMark));
    }

    @DisplayName("Custom prefix provider")
    @ParameterizedTest(name = "{argumentsWithNames}")
    @FieldSource("allModesTestProvider")
    public void testCustomPrefixProvider(int spaceMode, int patternMode) {
        SearchTokenParser searchParser = new SearchTokenParser();
        searchParser.addProvider(defaultParserProvider);
        searchParser
                .addProvider(parserProvider('?', "custom", SearchTokenParser.SearchMode.PREFIX, customCreateFilter));

        Set<ItemStack> matchedItems = testFilterAgainstAllItems(spaceMode, patternMode, searchParser, "?A");

        assertEquals(1, matchedItems.size());
        assertTrue(matchedItems.contains(itemA));
    }

    @DisplayName("Space mode")
    @ParameterizedTest(name = "{argumentsWithNames}")
    @FieldSource("spaceIsSpaceAllModesTestProvider")
    public void testSpaceMode(int spaceMode, int patternMode) {
        SearchTokenParser searchParser = new SearchTokenParser();
        searchParser.addProvider(defaultParserProvider);
        Set<ItemStack> matchedItems = testFilterAgainstAllItems(
                spaceMode,
                patternMode,
                searchParser,
                itemAB.getDisplayName());
        assertEquals(2, matchedItems.size());
        assertTrue(matchedItems.contains(itemAB));
        assertTrue(matchedItems.contains(itemABC));
    }

    @DisplayName("Spaced expression")
    @ParameterizedTest(name = "{argumentsWithNames}")
    @FieldSource("spaceIsAndAllModesTestProvider")
    public void testSpacedExpression(int spaceMode, int patternMode) {
        SearchTokenParser searchParser = new SearchTokenParser();
        searchParser.addProvider(defaultParserProvider);
        Set<ItemStack> matchedItems = testFilterAgainstAllItems(
                spaceMode,
                patternMode,
                searchParser,
                itemA.getDisplayName() + " " + itemC.getDisplayName());
        assertEquals(1, matchedItems.size());
        assertTrue(matchedItems.contains(itemABC));
    }

    // Extended modes tests

    @DisplayName("Regex")
    @ParameterizedTest(name = "{argumentsWithNames}")
    @FieldSource("extendedAllSpaceModesTestProvider")
    public void testRegex(int spaceMode, int patternMode) {
        SearchTokenParser searchParser = new SearchTokenParser();
        searchParser.addProvider(defaultParserProvider);
        Set<ItemStack> matchedItems = testFilterAgainstAllItems(
                spaceMode,
                patternMode,
                searchParser,
                "r/z?\\s" + itemB.getDisplayName() + "/");

        assertEquals(2, matchedItems.size());
        assertTrue(matchedItems.contains(itemAB));
        assertTrue(matchedItems.contains(itemABC));
    }

    // Extended+ specific tests

    @DisplayName("Sequence")
    @ParameterizedTest(name = "{argumentsWithNames}")
    @FieldSource("extendedPlusAllSpaceModesTestProvider")
    public void testSequence(int spaceMode, int patternMode) {
        SearchTokenParser searchParser = new SearchTokenParser();
        searchParser.addProvider(defaultParserProvider);
        searchParser
                .addProvider(parserProvider('?', "custom", SearchTokenParser.SearchMode.PREFIX, customCreateFilter));

        Set<ItemStack> matchedItems = testFilterAgainstAllItems(
                spaceMode,
                patternMode,
                searchParser,
                "\"" + itemA.getDisplayName() + "\"?A");

        assertEquals(1, matchedItems.size());
        assertTrue(matchedItems.contains(itemA));
    }

    @DisplayName("Brackets")
    @ParameterizedTest(name = "{argumentsWithNames}")
    @FieldSource("extendedPlusAllSpaceModesTestProvider")
    public void testBrackets(int spaceMode, int patternMode) {
        SearchTokenParser searchParser = new SearchTokenParser();
        searchParser.addProvider(defaultParserProvider);
        Set<ItemStack> matchedItems = testFilterAgainstAllItems(
                spaceMode,
                patternMode,
                searchParser,
                "{" + itemA.getDisplayName() + "|" + itemC.getDisplayName() + "}" + itemB.getDisplayName());

        assertEquals(2, matchedItems.size());
        assertTrue(matchedItems.contains(itemAB));
        assertTrue(matchedItems.contains(itemABC));
    }

    @DisplayName("Quoted")
    @ParameterizedTest(name = "{argumentsWithNames}")
    @FieldSource("extendedPlusAllSpaceModesTestProvider")
    public void testQuoted(int spaceMode, int patternMode) {
        SearchTokenParser searchParser = new SearchTokenParser();
        searchParser.addProvider(defaultParserProvider);
        Set<ItemStack> matchedItems = testFilterAgainstAllItems(
                spaceMode,
                patternMode,
                searchParser,
                "\"" + itemB.getDisplayName() + " " + itemC.getDisplayName());

        assertEquals(1, matchedItems.size());
        assertTrue(matchedItems.contains(itemABC));
    }

    @DisplayName("Escaped symbols")
    @ParameterizedTest(name = "{argumentsWithNames}")
    @FieldSource("spaceIsAndExtendedPlusModeTestProvider")
    public void testEscapedSymbols(int spaceMode, int patternMode) {
        SearchTokenParser searchParser = new SearchTokenParser();
        searchParser.addProvider(defaultParserProvider);
        searchParser
                .addProvider(parserProvider('?', "custom", SearchTokenParser.SearchMode.PREFIX, customCreateFilter));
        Set<ItemStack> matchedItems = testFilterAgainstAllItems(
                spaceMode,
                patternMode,
                searchParser,
                '\\' + itemQuestionMark.getDisplayName());

        assertEquals(1, matchedItems.size());
        assertTrue(matchedItems.contains(itemQuestionMark));

        matchedItems = testFilterAgainstAllItems(spaceMode, patternMode, searchParser, '\\' + " ");

        assertEquals(2, matchedItems.size());
        assertTrue(matchedItems.contains(itemAB));
        assertTrue(matchedItems.contains(itemABC));
    }

    private static Set<ItemStack> testFilterAgainstAllItems(int spaceMode, int patternMode,
            SearchTokenParser searchParser, String search) {
        config.when(() -> NEIClientConfig.getIntSetting(eq("inventory.search.patternMode"))).thenReturn(patternMode);
        config.when(() -> NEIClientConfig.getIntSetting(eq("inventory.search.spaceMode"))).thenReturn(spaceMode);

        ItemFilter filter = searchParser.getFilter(search);
        if (filter instanceof SearchTokenParser.IsRegisteredItemFilter) {
            filter = ((SearchTokenParser.IsRegisteredItemFilter) filter).filter;
        }
        Set<ItemStack> matchedItems = Stream.of(itemA, itemB, itemC, itemAB, itemABC, itemQuestionMark)
                .filter(filter::matches).collect(Collectors.toSet());
        return matchedItems;
    }

    private static SearchParserProvider parserProvider(char prefix, String name,
            SearchTokenParser.SearchMode searchMode, Function<Pattern, ItemFilter> createFilter) {
        return new SearchParserProvider(prefix, name, EnumChatFormatting.RESET, createFilter) {

            @Override
            public SearchTokenParser.SearchMode getSearchMode() {
                return searchMode;
            }

            @Override
            public List<Language> getMatchingLanguages() {
                List<Language> languages = new ArrayList<>();
                languages.add(language);
                return languages;
            }

        };
    }

    private static String printResult(Set<ItemStack> items) {
        return items.stream().map(ItemStack::getDisplayName).collect(Collectors.joining(","));
    }

    private static String printFilterContents(ItemFilter filter) {
        if (filter instanceof AnyMultiItemFilter) {
            return "ANY: ("
                    + ((AnyMultiItemFilter) filter).filters.stream().map(SearchTokenParserTest::printFilterContents)
                            .collect(Collectors.joining(","))
                    + ")";
        }
        if (filter instanceof AllMultiItemFilter) {
            return "ALL: ("
                    + ((AllMultiItemFilter) filter).filters.stream().map(SearchTokenParserTest::printFilterContents)
                            .collect(Collectors.joining(","))
                    + ")";
        }
        if (filter instanceof PatternItemFilter) {
            return "pattern(" + ((PatternItemFilter) filter).pattern + ")";
        }
        return filter.toString();
    }
}
