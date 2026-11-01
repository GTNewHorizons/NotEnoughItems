package codechicken.nei.config;

import java.util.Collections;

import codechicken.lib.vec.Rectangle4i;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.recipe.RecipeJsonWriter;

public class RecipeDumper extends DataDumper {

    public RecipeDumper(String name) {
        super(name);
    }

    @Override
    public String[] header() {
        return new String[0];
    }

    @Override
    public Iterable<String[]> dump(int mode) {
        return Collections.emptyList();
    }

    @Override
    public String renderName() {
        return translateN(name);
    }

    public Rectangle4i separateButtonSize() {
        return dumpButtonSize();
    }

    public Rectangle4i combinedButtonSize() {
        return modeButtonSize();
    }

    @Override
    public void draw(int mousex, int mousey, float frame) {
        drawPrefix();
        drawButton(mousex, mousey, combinedButtonSize(), translateN(name + ".combined"));
        drawButton(mousex, mousey, separateButtonSize(), translateN(name + ".separate"));
    }

    @Override
    public void mouseClicked(int mousex, int mousey, int button) {
        final boolean combined = combinedButtonSize().contains(mousex, mousey);

        if (combined || separateButtonSize().contains(mousex, mousey)) {
            NEIClientUtils.playClickSound();
            RecipeJsonWriter.dumpAll(!combined);
        }
    }
}
