package codechicken.nei.recipe.debug;

import java.util.function.Consumer;

import codechicken.nei.Button;
import codechicken.nei.NEIClientUtils;

public class CheckboxWidget extends Button implements IUpdatableWidget {

    protected Consumer<CheckboxWidget> onChange;
    protected boolean value;

    public CheckboxWidget(Consumer<CheckboxWidget> onChange) {
        super();
        this.onChange = onChange;
        this.h = 16;
        this.z = 2;
    }

    @Override
    public String getRenderLabel() {
        return this.value ? NEIClientUtils.translate("debug.RecipeHandler.checkbox.on")
                : NEIClientUtils.translate("debug.RecipeHandler.checkbox.off");
    }

    @Override
    public boolean onButtonPress(boolean rightclick) {
        this.value = !this.value;
        onChange.accept(this);
        return true;
    }

    public void updateValue(String newValue) {
        this.value = Boolean.parseBoolean(newValue);
    }

}
