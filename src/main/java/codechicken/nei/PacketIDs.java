package codechicken.nei;

public final class PacketIDs {

    public static final class C2S {

        public static final int GIVE_ITEM = 1;
        public static final int DELETE_ALL_ITEMS = 4;
        public static final int SET_SLOT = 5;
        public static final int TOGGLE_MAGNET = 6;
        public static final int SET_TIME = 7;
        public static final int HEAL = 8;
        public static final int TOGGLE_RAIN = 9;
        public static final int REQUEST_LOGIN_INFO = 10;
        public static final int REQUEST_CONTAINER_CONTENTS = 11;
        public static final int CHANGE_PROPERTY = 12;
        public static final int SET_GAME_MODE = 13;
        public static final int CYCLE_CREATIVE_INV = 14;
        public static final int SEND_MOB_SPAWNER_ID = 15;
        public static final int REQUEST_CONTAINER = 16;
        public static final int REQUEST_ENCHANTMENT_GUI = 21;
        public static final int MODIFY_ENCHANTMENT = 22;
        public static final int SET_CREATIVE_PLUS_MODE = 23;
        public static final int REQUEST_POTION_GUI = 24;
        public static final int SET_DUMMY_SLOT = 25;
        public static final int SEND_CHAT_LINK = 26;
    }

    public static final class S2C {

        public static final int SEND_SERVER_SIDE_CHECK = 1;
        public static final int SEND_LOGIN_STATE = 10;
        public static final int SEND_ACTION_DISABLED = 11;
        public static final int SEND_ACTION_ENABLED = 12;
        public static final int SEND_MAGNETIC_ITEM = 13;
        public static final int SEND_GAME_MODE = 14;
        public static final int OPEN_ENCHANTMENT_GUI = 21;
        public static final int SET_CREATIVE_PLUS_MODE = 23;
        public static final int OPEN_POTION_GUI = 24;
    }
}
