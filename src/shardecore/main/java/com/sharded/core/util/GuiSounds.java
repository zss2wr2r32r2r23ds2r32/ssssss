package com.sharded.core.util;

import com.sharded.core.ShardedCore;
import com.sharded.core.gui.GuiNavigation;
import java.io.File;
import java.util.Locale;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public final class GuiSounds {
   private final ShardedCore plugin;
   private YamlConfiguration config;

   public GuiSounds(ShardedCore plugin) {
      this.plugin = plugin;
      this.reload();
   }

   public void reload() {
      this.config = YamlConfiguration.loadConfiguration(new File(this.plugin.getDataFolder(), "gui-navigation.yml"));
   }

   public void play(Player player, String key) {
      if (player == null) {
         return;
      }
      String s = this.config != null ? this.config.getString("sounds." + key) : null;
      if (s == null || s.isBlank()) {
         return;
      }
      float volume = this.config == null ? 1.0F : (float) this.config.getDouble("sounds.volume", 1.0D);
      float pitch = this.config == null ? 1.0F : (float) this.config.getDouble("sounds.pitch", 1.0D);
      playRaw(player, s, volume, pitch);
   }

   public static void playRaw(Player player, String key, float volume, float pitch) {
      if (player == null || key == null || key.isBlank()) {
         return;
      }
      String token = key.trim();
      try {
         Sound sound = Sound.valueOf(token.toUpperCase(Locale.ROOT).replace('.', '_').replace('-', '_'));
         player.playSound(player.getLocation(), sound, volume, pitch);
      } catch (IllegalArgumentException ignored) {
         player.playSound(player.getLocation(), token.toLowerCase(Locale.ROOT).replace('_', '.'), volume, pitch);
      }
   }

   public void playNamed(Player player, String soundKey) {
      if (player == null || soundKey == null || soundKey.isBlank()) {
         return;
      }
      GuiNavigation nav = this.plugin.guiNavigation();
      float volume = nav != null ? nav.soundVolume() : 1.0F;
      float pitch = nav != null ? nav.soundPitch() : 1.0F;
      playRaw(player, soundKey, volume, pitch);
   }
}
