package com.sharded.core.net;

import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class AddEntityPacketPatcherTest {
    private static AddEntityPacketPatcher patcher() {
        AddEntityPacketPatcher patcher = AddEntityPacketPatcher.tryCreate(Logger.getLogger("test"));
        assertNotNull(patcher, "patcher should bind to the test NMS stand-ins");
        return patcher;
    }

    @Test
    void fillsNullMovementWithZero() {
        AddEntityPacketPatcher patcher = patcher();
        ClientboundAddEntityPacket packet = new ClientboundAddEntityPacket(7, null);
        assertNull(packet.getMovement());
        Object returned = patcher.patch(packet);
        assertSame(packet, returned);
        assertSame(Vec3.ZERO, packet.getMovement());
        assertEquals(1, patcher.patchedCount());
    }

    @Test
    void leavesExistingMovementAlone() {
        AddEntityPacketPatcher patcher = patcher();
        Vec3 moving = new Vec3(1, 2, 3);
        ClientboundAddEntityPacket packet = new ClientboundAddEntityPacket(8, moving);
        patcher.patch(packet);
        assertSame(moving, packet.getMovement());
        assertEquals(0, patcher.patchedCount());
    }

    @Test
    void patchesSpawnPacketsInsideBundles() {
        AddEntityPacketPatcher patcher = patcher();
        ClientboundAddEntityPacket broken = new ClientboundAddEntityPacket(9, null);
        ClientboundAddEntityPacket fine = new ClientboundAddEntityPacket(10, new Vec3(0.1, 0.0, 0.0));
        ClientboundBundlePacket bundle = new ClientboundBundlePacket(new ArrayList<>(List.of(broken, fine, "ignore")));
        patcher.patch(bundle);
        assertSame(Vec3.ZERO, broken.getMovement());
        assertEquals(0.1d, fine.getMovement().x);
        assertEquals(1, patcher.patchedCount());
    }

    @Test
    void ignoresUnrelatedPackets() {
        AddEntityPacketPatcher patcher = patcher();
        Object marker = new Object();
        assertSame(marker, patcher.patch(marker));
        assertEquals(0, patcher.patchedCount());
    }
}
