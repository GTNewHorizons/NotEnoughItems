package codechicken.nei.util;

import java.util.StringJoiner;

import codechicken.nei.NEIClientUtils;

public class NEIMouseUtils {

    public static final int MOUSE_BTN_NONE = Integer.MIN_VALUE;

    public static final int MOUSE_BTN_LMB = 0;
    public static final int MOUSE_BTN_RMB = 1;
    public static final int MOUSE_BTN_MMB = 2;

    public static final int MOUSE_SCROLL = 1 << 27;
    public static final int MOUSE_DRAG = 1 << 26;

    private NEIMouseUtils() {}

    public static String getHashName(int mouseBind) {
        StringJoiner mouseText = new StringJoiner(" + ");

        if ((mouseBind & MOUSE_SCROLL) != 0) {
            mouseText.add(NEIClientUtils.translate("mouse.scroll"));
        }

        if ((mouseBind & MOUSE_DRAG) != 0) {
            mouseText.add(NEIClientUtils.translate("mouse.drag"));
        }

        return mouseText.toString();
    }

    public static String getKeyName(int mouseBind) {
        StringJoiner mouseText = new StringJoiner(" + ");
        String hashText = getHashName(mouseBind < 0 ? mouseBind - MOUSE_BTN_NONE : mouseBind);

        if (mouseBind >= 0) {
            int button = unhash(mouseBind);
            switch (button) {
                case 0:
                    mouseText.add(NEIClientUtils.translate("mouse.left"));
                    break;
                case 1:
                    mouseText.add(NEIClientUtils.translate("mouse.right"));
                    break;
                case 2:
                    mouseText.add(NEIClientUtils.translate("mouse.middle"));
                    break;
                default:
                    mouseText.add(NEIClientUtils.translate("mouse.other", button));
                    break;
            }
        }

        if (!hashText.isEmpty()) {
            mouseText.add(hashText);
        }

        return mouseText.toString();
    }

    public static int unhash(int mouseBind) {
        return mouseBind & ~(MOUSE_SCROLL | MOUSE_DRAG);
    }

}
