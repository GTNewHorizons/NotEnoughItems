package codechicken.nei.config;

public class OptionCycled extends OptionButton {

    public final int count;
    public final boolean prefixed;

    public OptionCycled(String name, int count, boolean prefixed) {
        super(name);
        this.count = count;
        this.prefixed = prefixed;
    }

    public OptionCycled(String name, int count) {
        this(name, count, false);
    }

    public int value() {
        return getTag().getIntValue();
    }

    public void setValue(int value) {
        getTag().setIntValue(value);
    }

    @Override
    public String getButtonText() {
        return translateN(name + "." + value());
    }

    @Override
    public String getPrefix() {
        return prefixed ? translateN(name) : null;
    }

    @Override
    public String getTooltip() {
        String tooltip = name + "." + value() + ".tip";
        String s = translateN(tooltip);

        if (!s.equals(namespaced(tooltip))) {
            return s;
        }

        return super.getTooltip();
    }

    @Override
    public boolean onClick(int button) {
        return cycle();
    }

    public boolean cycle() {
        int next = value();
        do next = (next + 1) % count; while (!optionValid(next));

        if (next == value()) return false;

        setValue(next);
        return true;
    }

    public boolean optionValid(int index) {
        return true;
    }
}
