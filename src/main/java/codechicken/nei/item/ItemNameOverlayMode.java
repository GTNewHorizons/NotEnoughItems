package codechicken.nei.item;

public enum ItemNameOverlayMode {

    ITEMS(true, false),
    FLUIDS(false, true),
    ALL(true, true),
    OFF(false, false);

    private final boolean items;
    private final boolean fluids;

    private ItemNameOverlayMode(boolean items, boolean fluids) {
        this.items = items;
        this.fluids = fluids;
    }

    public boolean includes(boolean fluidDisplay) {
        return fluidDisplay ? fluids : items;
    }

    public static ItemNameOverlayMode fromConfigValue(int value) {
        return switch (value) {
            case 0 -> ITEMS;
            case 1 -> FLUIDS;
            case 2 -> ALL;
            default -> OFF;
        };
    }
}
