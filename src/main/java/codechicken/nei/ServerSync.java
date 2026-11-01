package codechicken.nei;

import codechicken.lib.packet.PacketCustom;
import codechicken.nei.PacketIDs.C2S;

/**
 * Waits until the server has processed everything the client sent before. The server handles slot clicks and NEI
 * packets in the order they came and answers after it has sent the slot updates, so once the answer is here the client
 * container holds what the server computed.
 */
public final class ServerSync {

    private static int lastId;
    private static volatile int awaiting;

    private ServerSync() {}

    public static void request() {

        if (!NEIClientConfig.hasSMPCounterPart()) {
            awaiting = 0;
            return;
        }

        awaiting = ++lastId;
        new PacketCustom(NEICPH.channel, C2S.SYNC).writeInt(awaiting).sendToServer();
    }

    public static boolean pending() {
        return awaiting != 0;
    }

    static void onReply(int id) {
        if (id == awaiting) awaiting = 0;
    }
}
