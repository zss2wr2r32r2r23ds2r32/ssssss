package com.sharded.core.util;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

public final class EventBossBar {
   private BossBar bar;
   private String owner = "";

   public void show(String id, String title, BarColor color, double progress) {
      String s = Text.legacySection(title);
      if (this.bar != null && id.equals(this.owner)) {
         this.bar.setTitle(s);
         this.bar.setColor(color);
      } else {
         this.hide();
         this.bar = Bukkit.createBossBar(s, color, BarStyle.SOLID, new BarFlag[0]);
         this.owner = id;
      }

      this.bar.setProgress(Math.max(0.0, Math.min(1.0, progress)));

      for (Player player : Bukkit.getOnlinePlayers()) {
         if (!this.bar.getPlayers().contains(player)) {
            this.bar.addPlayer(player);
         }
      }
   }

   public void hide(String id) {
      if (id.equals(this.owner)) {
         this.hide();
      }
   }

   public void hide() {
      if (this.bar != null) {
         this.bar.removeAll();
         this.bar = null;
         this.owner = "";
      }
   }

   public void syncPlayers() {
      if (this.bar != null) {
         Set<UUID> set = new HashSet<>();

         for (Player player : Bukkit.getOnlinePlayers()) {
            set.add(player.getUniqueId());
         }

         for (Player player1 : this.bar.getPlayers()) {
            if (!set.contains(player1.getUniqueId())) {
               this.bar.removePlayer(player1);
            }
         }

         for (Player player2 : Bukkit.getOnlinePlayers()) {
            if (!this.bar.getPlayers().contains(player2)) {
               this.bar.addPlayer(player2);
            }
         }
      }
   }

   public void shutdown() {
      this.hide();
   }
}
