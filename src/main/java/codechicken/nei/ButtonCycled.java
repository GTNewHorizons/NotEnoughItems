package codechicken.nei;

public abstract class ButtonCycled extends Button
{    
    public ButtonCycled() {}

    public ButtonCycled(int num) {
        icons = new Image[num];
        init();
    }
    @Override
    public Image getRenderIcon()
    {
        return icons[index];
    }

    public void init() {}
    public int index;
    public Image[] icons;
}
