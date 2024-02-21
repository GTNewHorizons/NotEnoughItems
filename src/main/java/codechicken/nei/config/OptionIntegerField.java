package codechicken.nei.config;

public class OptionIntegerField extends OptionTextField {

    public final int min;
    public final int max;

    public OptionIntegerField(String name, int min, int max) {
        super(name);
        this.min = min;
        this.max = max;
    }

    public OptionIntegerField(String name) {
        this(name, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    @Override
    public boolean isValidInput(String s) {
        if (s.length() == 0) return true;

        try {
            if (s.startsWith("0x")) {
                Long.parseLong(s.substring(2), 16);
            } else {
                Integer.parseInt(s);
            }
            return true;
        } catch (NumberFormatException nfe) {
            return false;
        }
    }

    @Override
    public boolean isValidValue(String s) {
        if (s.length() == 0 || !isValidInput(s)) return false;
        int i = 0;

        if (s.startsWith("0x")) {
            i = (int) Long.parseLong(s.substring(2), 16);
        } else {
            i = Integer.parseInt(s);
        }

        return i >= min && i <= max;
    }
}
