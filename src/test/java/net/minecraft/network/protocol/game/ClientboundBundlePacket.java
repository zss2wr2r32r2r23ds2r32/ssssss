package net.minecraft.network.protocol.game;

import java.util.List;

public final class ClientboundBundlePacket {
    private final List<Object> packets;

    public ClientboundBundlePacket(List<Object> packets) {
        this.packets = packets;
    }

    public Iterable<Object> subPackets() {
        return packets;
    }
}
