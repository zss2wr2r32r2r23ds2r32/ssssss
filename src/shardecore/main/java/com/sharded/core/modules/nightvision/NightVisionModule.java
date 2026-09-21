package com.sharded.core.modules.nightvision;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class NightVisionModule extends Module implements CommandExecutor {
   public static final String STATE_KEY = "nightvision-enabled";

   public NightVisionModule(ShardedCore plugin) {
      super(plugin, "nightvision");
   }

   @Override
   protected void onEnable() {
      this.registerCommand("nightvision", this);

      for (Player player : this.plugin.getServer().getOnlinePlayers()) {
         if (this.isNightVisionEnabled(player)) {
            this.apply(player, true);
         }
      }
   }

   @Override
   protected void onDisable() {
      for (Player player : this.plugin.getServer().getOnlinePlayers()) {
         player.removePotionEffect(PotionEffectType.NIGHT_VISION);
      }
   }

   public boolean isNightVisionEnabled(Player player) {
      return this.plugin.stateStore().getBool(player.getUniqueId(), "nightvision-enabled", false);
   }

   public void setNightVision(Player player, boolean enabled) {
      this.plugin.stateStore().setBool(player.getUniqueId(), "nightvision-enabled", enabled);
      this.apply(player, enabled);
      this.send(player, enabled ? "nv-enabled" : "nv-disabled", new String[0]);
   }

   private void apply(Player player, boolean enabled) {
      if (enabled) {
         player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, -1, 0, true, false, this.config.getBoolean("show-icon", true)));
      } else {
         player.removePotionEffect(PotionEffectType.NIGHT_VISION);
      }
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (sender instanceof Player player) {
         if (!player.hasPermission("sharded.nightvision.use")) {
            this.send(player, "no-permission", new String[0]);
            return true;
         } else {
            this.setNightVision(player, !this.isNightVisionEnabled(player));
            return true;
         }
      } else {
         this.send(sender, "players-only", new String[0]);
         return true;
      }
   }

   @EventHandler
   public void onJoin(PlayerJoinEvent event) {
      if (this.isNightVisionEnabled(event.getPlayer())) {
         this.apply(event.getPlayer(), true);
      }
   }
}
