package codechicken.nei;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import net.minecraft.item.ItemStack;

import codechicken.lib.gui.GuiDraw;
import codechicken.lib.vec.Rectangle4i;

public class WidgetContainer extends Widget {

    protected final List<Widget> widgets = new ArrayList<>();
    protected boolean isFocused = false;

    public void addWidget(Widget widget) {
        this.widgets.add(widget);
    }

    public void setWidgets(List<Widget> widgets) {
        final boolean isFocused = this.isFocused;
        clear();
        loseFocus();

        for (Widget widget : widgets) {
            addWidget(widget);
        }

        this.isFocused = isFocused;
    }

    public void clear() {
        this.widgets.clear();
        this.isFocused = false;
    }

    public List<Widget> getWidgets() {
        return Collections.unmodifiableList(this.widgets);
    }

    public boolean removeIf(Predicate<Widget> filter) {
        return this.widgets.removeIf(filter);
    }

    @Override
    public void draw(int mousex, int mousey) {
        drawWidgets(mousex, mousey);
    }

    protected void drawWidgets(int mousex, int mousey) {
        final Rectangle4i bounds = bounds();

        for (Widget widget : this.widgets) {
            if (widget.bounds().intersects(bounds)) {
                widget.draw(mousex, mousey);
            }
        }
    }

    @Override
    public void update() {

        for (Widget widget : this.widgets) {
            widget.update();
        }

        Collections.sort(this.widgets, Comparator.comparingInt((Widget widget) -> widget.z));
    }

    public Widget getWidgetUnderMouse(int mousex, int mousey) {

        if (!bounds().contains(mousex, mousey)) {
            return null;
        }

        for (int index = this.widgets.size() - 1; index >= 0; index--) {
            final Widget widget = this.widgets.get(index);
            if (widget.contains(mousex, mousey)) {
                return widget;
            }
        }

        return null;
    }

    @Override
    public boolean onMouseWheel(int scroll, int mousex, int mousey) {
        final Widget widget = getWidgetUnderMouse(mousex, mousey);

        return widget != null && widget.onMouseWheel(scroll, mousex, mousey);
    }

    @Override
    public boolean handleClick(int mousex, int mousey, int button) {

        if (contains(mousex, mousey)) {
            gainFocus();
        }

        final Widget widget = getWidgetUnderMouse(mousex, mousey);

        return widget != null && widget.handleClick(mousex, mousey, button);
    }

    @Override
    public boolean handleClickExt(int mousex, int mousey, int button) {
        loseFocus();

        for (int index = this.widgets.size() - 1; index >= 0; index--) {
            final Widget widget = this.widgets.get(index);
            if (!widget.contains(mousex, mousey) && widget.handleClickExt(mousex, mousey, button)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void mouseDragged(int mousex, int mousey, int button, long heldTime) {

        for (int index = this.widgets.size() - 1; index >= 0; index--) {
            final Widget widget = this.widgets.get(index);
            widget.mouseDragged(mousex, mousey, button, heldTime);
        }
    }

    @Override
    public void mouseUp(int mousex, int mousey, int button) {

        for (int index = this.widgets.size() - 1; index >= 0; index--) {
            final Widget widget = this.widgets.get(index);
            widget.mouseUp(mousex, mousey, button);
        }
    }

    @Override
    public boolean handleKeyPress(int keyCode, char keyChar) {

        for (int index = this.widgets.size() - 1; index >= 0; index--) {
            final Widget widget = this.widgets.get(index);
            if (widget.handleKeyPress(keyCode, keyChar)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void lastKeyTyped(int keyID, char keyChar) {

        for (int index = this.widgets.size() - 1; index >= 0; index--) {
            final Widget widget = this.widgets.get(index);
            widget.lastKeyTyped(keyID, keyChar);
        }

    }

    @Override
    public boolean isFocused() {
        return this.isFocused;
    }

    @Override
    public void loseFocus() {

        for (int index = this.widgets.size() - 1; index >= 0; index--) {
            final Widget widget = this.widgets.get(index);
            if (widget.isFocused()) {
                widget.loseFocus();
            }
        }

        this.isFocused = false;
    }

    @Override
    public void gainFocus() {
        final Point mouse = GuiDraw.getMousePosition();
        final Widget widget = getWidgetUnderMouse(mouse.x, mouse.y);

        if (widget != null) {

            if (!widget.isFocused()) {
                loseFocus();
                widget.gainFocus();
            }

        } else {
            loseFocus();
        }

        this.isFocused = true;
    }

    @Override
    public ItemStack getStackMouseOver(int mousex, int mousey) {
        final Widget widget = getWidgetUnderMouse(mousex, mousey);

        if (widget != null) {
            return widget.getStackMouseOver(mousex, mousey);
        }

        return null;
    }

    @Override
    public void postDraw(int mousex, int mousey) {

        for (Widget widget : this.widgets) {
            widget.postDraw(mousex, mousey);
        }

    }

    @Override
    public void onGuiClick(int mousex, int mousey) {

        for (int index = this.widgets.size() - 1; index >= 0; index--) {
            final Widget widget = this.widgets.get(index);
            widget.onGuiClick(mousex, mousey);
        }

    }

    @Override
    public List<String> handleTooltip(int mousex, int mousey, List<String> tooltip) {
        final Widget widget = getWidgetUnderMouse(mousex, mousey);

        if (widget != null) {
            return widget.handleTooltip(mousex, mousey, tooltip);
        }

        return tooltip;
    }

    @Override
    public Map<String, String> handleHotkeys(int mousex, int mousey, Map<String, String> hotkeys) {
        final Widget widget = getWidgetUnderMouse(mousex, mousey);

        if (widget != null) {
            return widget.handleHotkeys(mousex, mousey, hotkeys);
        }

        return hotkeys;
    }

}
