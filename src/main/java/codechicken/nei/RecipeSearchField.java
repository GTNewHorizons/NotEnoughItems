package codechicken.nei;

import net.minecraft.util.EnumChatFormatting;

import codechicken.nei.api.ItemFilter;
import codechicken.nei.util.TextHistory;

public abstract class RecipeSearchField extends TextField {

    private static final TextHistory history = new TextHistory();

    public RecipeSearchField(String ident) {
        super(ident);
        this.field.setVisible(false);
    }

    public boolean isVisible() {
        return this.field.getVisible();
    }

    public void setVisible(boolean visible) {
        this.field.setVisible(visible);
    }

    protected abstract boolean noResults();

    @Override
    public int getTextColour() {
        if (!text().isEmpty() && !noResults()) {
            return focused() ? 0xFFcc3300 : 0xFF993300;
        } else {
            return focused() ? 0xFFE0E0E0 : 0xFF909090;
        }
    }

    @Override
    public void lastKeyTyped(int keyID, char keyChar) {

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

    public ItemFilter getFilter() {
        return SearchField.getFilter(text());
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
