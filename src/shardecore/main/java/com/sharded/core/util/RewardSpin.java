package com.sharded.core.util;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public final class RewardSpin {
   static final Set<UUID> ACTIVE = ConcurrentHashMap.newKeySet();

   private RewardSpin() {
   }

   public static boolean isClaiming(UUID uuid) {
      return ACTIVE.contains(uuid);
   }

   public static List<RewardSpin.RewardOption> loadOptions(ConfigurationSection section) {
      List<RewardSpin.RewardOption> list = new ArrayList<>();
      if (section == null) {
         return list;
      } else {
         double d0 = 0.0;
         List<ConfigurationSection> list1 = new ArrayList<>();

         for (String s : section.getKeys(false)) {
            ConfigurationSection configurationsection = section.getConfigurationSection(s);
            if (configurationsection != null) {
               list1.add(configurationsection);
               d0 += configurationsection.getDouble("weight", 1.0);
            }
         }

         if (d0 <= 0.0) {
            d0 = 1.0;
         }

         for (ConfigurationSection configurationsection1 : list1) {
            double d2 = configurationsection1.getDouble("weight", 1.0);
            double d1 = d2 / d0 * 100.0;
            list.add(
               new RewardSpin.RewardOption(
                  configurationsection1.getString("display", "Reward"),
                  configurationsection1.getString("rarity-label", configurationsection1.getString("rarity", "Common")),
                  configurationsection1.getString("rarity-color", "&7"),
                  d2,
                  d1,
                  configurationsection1.getStringList("commands")
               )
            );
         }

         return list;
      }
   }

   public static boolean spin(
      Module module,
      ShardedCore plugin,
      Player player,
      List<RewardSpin.RewardOption> options,
      String cooldownKey,
      String winMessageKey,
      YamlConfiguration config
   ) {
      UUID uuid = player.getUniqueId();
      if (options == null || options.isEmpty()) {
         return false;
      } else if (!ACTIVE.add(uuid)) {
         return false;
      } else {
         plugin.stateStore().setLong(uuid, cooldownKey, System.currentTimeMillis());
         RewardSpin.RewardOption rewardspin$rewardoption = weightedPick(options);
         YamlConfiguration yamlconfiguration = config == null ? new YamlConfiguration() : config;
         RewardWheel.open(module, plugin, player, options, rewardspin$rewardoption, winMessageKey, yamlconfiguration);
         return true;
      }
   }

   static Material iconFor(RewardSpin.RewardOption option) {
      return RewardWheel.iconFor(option);
   }

   static String applyFormat(String format, RewardSpin.RewardOption option) {
      String s = option.rarityColor() == null ? "" : option.rarityColor();
      return format.replace("%reward%", option.display())
         .replace("%REWARD%", option.display())
         .replace("%rarity_color%", s)
         .replace("%RARITY_COLOR%", s)
         .replace("%rarity_colored%", option.coloredRarity())
         .replace("%RARITY_COLORED%", option.coloredRarity())
         .replace("%rarity%", option.rarityLabel())
         .replace("%RARITY%", option.rarityLabel())
         .replace("%percent%", option.percentText())
         .replace("%PERCENT%", option.percentText());
   }

   static void grant(Player player, RewardSpin.RewardOption option) {
      for (String s : option.commands()) {
         String s1 = s.replace("%player%", player.getName()).replace("%uuid%", player.getUniqueId().toString()).replace("%player_name%", player.getName());
         Bukkit.dispatchCommand(Bukkit.getConsoleSender(), s1);
      }
   }

   private static RewardSpin.RewardOption weightedPick(List<RewardSpin.RewardOption> options) {
      double d0 = 0.0;

      for (RewardSpin.RewardOption rewardspin$rewardoption : options) {
         d0 += rewardspin$rewardoption.weight();
      }

      double d2 = ThreadLocalRandom.current().nextDouble(d0);
      double d1 = 0.0;

      for (RewardSpin.RewardOption rewardspin$rewardoption1 : options) {
         d1 += rewardspin$rewardoption1.weight();
         if (d2 <= d1) {
            return rewardspin$rewardoption1;
         }
      }

      return options.get(options.size() - 1);
   }

   static void playSound(Player player, String sound) {
      if (sound != null && !sound.isBlank()) {
         try {
            player.playSound(player.getLocation(), Sound.valueOf(sound.toUpperCase(Locale.ROOT)), 1.0F, 1.0F);
         } catch (IllegalArgumentException illegalargumentexception) {
         }
      }
   }

   public static record RewardOption(String display, String rarityLabel, String rarityColor, double weight, double percent, List<String> commands) {
      public String coloredRarity() {
         return (this.rarityColor == null ? "" : this.rarityColor) + this.rarityLabel;
      }

      public String percentText() {
         return String.format(Locale.US, "%.0f", this.percent);
      }
   }
}
