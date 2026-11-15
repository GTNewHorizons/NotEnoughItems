package codechicken.nei.search;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.RuleNode;

import codechicken.nei.SearchField;
import codechicken.nei.SearchTokenParser;
import codechicken.nei.filter.RecipeTextFilter;

public class RecipeTextVisitor extends AbstractSearchExpressionVisitor<Predicate<List<String>>> {

    public RecipeTextVisitor(SearchTokenParser searchParser) {
        super(searchParser);
    }

    @Override
    public Predicate<List<String>> visitChildren(RuleNode node) {
        if (!(node instanceof ParserRuleContext context) || context.children == null) return null;

        for (ParseTree child : context.children) {
            if (child instanceof RuleNode) return visit(child);
        }

        return null;
    }

    @Override
    public Predicate<List<String>> visitOrExpression(SearchExpressionParser.OrExpressionContext ctx) {
        final List<Predicate<List<String>>> parts = children(ctx);
        return parts == null ? null : lines -> parts.stream().anyMatch(part -> part.test(lines));
    }

    @Override
    public Predicate<List<String>> visitSequenceExpression(SearchExpressionParser.SequenceExpressionContext ctx) {
        final List<Predicate<List<String>>> parts = children(ctx);
        return parts == null ? null : lines -> parts.stream().allMatch(part -> part.test(lines));
    }

    @Override
    public Predicate<List<String>> visitNegateExpression(SearchExpressionParser.NegateExpressionContext ctx) {
        final Predicate<List<String>> inner = visitChildren(ctx);
        return inner == null ? null : inner.negate();
    }

    @Override
    public Predicate<List<String>> visitPrefixedExpression(SearchExpressionParser.PrefixedExpressionContext ctx) {
        final String prefix = Pattern.quote(String.valueOf(ctx.prefix));
        final String cleanPattern = ctx.token() != null ? SearchExpressionUtils.getCleanPattern(ctx.token()) : null;
        return matcher(cleanPattern == null ? prefix : prefix + cleanPattern);
    }

    @Override
    public Predicate<List<String>> visitToken(SearchExpressionParser.TokenContext ctx) {
        return matcher(SearchExpressionUtils.getCleanPattern(ctx));
    }

    @Override
    protected Predicate<List<String>> defaultResult() {
        return null;
    }

    private static Predicate<List<String>> matcher(String cleanPattern) {
        final Pattern pattern = cleanPattern != null ? SearchField.getPattern(cleanPattern) : null;
        return pattern == null ? null : RecipeTextFilter.matching(pattern);
    }

    private List<Predicate<List<String>>> children(ParserRuleContext ctx) {
        if (ctx.children == null) return null;
        final List<Predicate<List<String>>> parts = new ArrayList<>();

        for (ParseTree child : ctx.children) {
            if (!(child instanceof RuleNode)) continue;
            final Predicate<List<String>> part = visit(child);
            if (part == null) return null;
            parts.add(part);
        }

        return parts.isEmpty() ? null : parts;
    }
}
