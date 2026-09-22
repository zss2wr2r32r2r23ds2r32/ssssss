package com.sharded.core.modules.combat;

import org.bukkit.event.player.PlayerTeleportEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatModuleTest {
    @Test
    void commandAndPluginTeleportsIntoSpawnAreAllowed() {
        assertFalse(CombatRules.blocksTaggedSpawnTeleport(PlayerTeleportEvent.TeleportCause.COMMAND));
        assertFalse(CombatRules.blocksTaggedSpawnTeleport(PlayerTeleportEvent.TeleportCause.PLUGIN));
        assertFalse(CombatRules.blocksTaggedSpawnTeleport(PlayerTeleportEvent.TeleportCause.UNKNOWN));
        assertFalse(CombatRules.blocksTaggedSpawnTeleport(PlayerTeleportEvent.TeleportCause.NETHER_PORTAL));
    }

    @Test
    void pearlsAndChorusIntoSpawnStayBlocked() {
        assertTrue(CombatRules.blocksTaggedSpawnTeleport(PlayerTeleportEvent.TeleportCause.ENDER_PEARL));
        assertTrue(CombatRules.blocksTaggedSpawnTeleport(PlayerTeleportEvent.TeleportCause.CONSUMABLE_EFFECT));
    }
}
