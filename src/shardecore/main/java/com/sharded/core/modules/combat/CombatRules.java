package com.sharded.core.modules.combat;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

public final class CombatRules {
   private CombatRules() {
   }

   public static boolean blocksTaggedSpawnTeleport(TeleportCause cause) {
      if (cause == null) {
         return false;
      } else if (cause == TeleportCause.ENDER_PEARL) {
         return true;
      } else {
         return cause == TeleportCause.CONSUMABLE_EFFECT ? true : "CHORUS_FRUIT".equals(cause.name());
      }
   }

   public static boolean bypassesCombatLock(Player player) {
      return player.hasPermission("sharded.combat.commandbypass") || player.hasPermission("sharded.combat.tpbypass");
   }
}
