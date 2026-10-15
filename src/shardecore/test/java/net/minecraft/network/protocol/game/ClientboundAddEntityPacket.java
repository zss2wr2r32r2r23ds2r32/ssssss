package net.minecraft.network.protocol.game;

import net.minecraft.world.phys.Vec3;

public final class ClientboundAddEntityPacket {
    private final int id;
    private final Vec3 movement;

    public ClientboundAddEntityPacket(int id, Vec3 movement) {
        this.id = id;
        this.movement = movement;
    }

    public int getId() {
        return id;
    }

    public Vec3 getMovement() {
        return movement;
    }
}
