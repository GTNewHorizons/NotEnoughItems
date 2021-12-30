package codechicken.nei;

public class VisiblityData
{
    public boolean showUtilityButtons = true;
    /**
     * Item panel and associated buttons
     */
    public boolean showItemPanel = true;
    /**
     * Bookmark panel and associated buttons
     */
    public boolean showBookmarkPanel = true;
    /**
     * Item and search section
     */
    public boolean showItemSection = true;
    /**
     * Dropdown and Item search field
     */
    public boolean showSearchSection = true;
    /**
     * All widgets except options button
     */
    public boolean showWidgets = true;
    /**
     * The entire NEI interface, aka hidden
     */
    public boolean showNEI = true;
    public boolean enableDeleteMode = true;

    
    // Deprecated -- left in for campatability with other mods that mess with this
    public boolean showStateButtons = false;

    public void translateDependancies()
    {
        if(!showNEI)
            showWidgets = false;
        if(!showWidgets)
            showItemSection = showUtilityButtons = false;
        if(!showItemSection)
            showBookmarkPanel = showSearchSection = showItemPanel = false;
    }
}
