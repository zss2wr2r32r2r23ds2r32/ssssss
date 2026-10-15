package com.sharded.core.util;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.modules.crates.CrateMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitTask;

final class RewardWheel {
   static final int[] RING = new int[]{20, 11, 3, 5, 15, 24, 33, 41, 39, 29};
   static final int ARROW_SLOT = 19;
   static final int POINTER_INDEX = 0;
   /** Oak Wood Arrow Right (minecraft-heads.com/2763) — MHF_ArrowRight. */
   private static final String ARROW_HEAD_TEXTURE =
         "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZDM0ZWYwNjM4NTM3MjIyYjIwZjQ4MDY5NGRhZGMwZjg1ZmJlMDc1OWQ1ODFhYTdmY2RmMmU0MzEzOTM3NzE1OCJ9fX0=";
   private static final UUID ARROW_HEAD_UUID = UUID.fromString("50c8510b-5ea0-4d60-be9a-7d542d6cd156");

   private RewardWheel() {
   }

   static void open(
      Module module,
      ShardedCore plugin,
      Player player,
      List<RewardSpin.RewardOption> options,
      RewardSpin.RewardOption winner,
      String winMessageKey,
      YamlConfiguration config
   ) {
      UUID uuid = player.getUniqueId();
      player.closeInventory();
      String s = config.getString("wheel-title", config.getString("actionbar-prefix", "&8Rewards"));
      if (s != null && s.contains("&8▷")) {
         s = s.replace("&8▷", "").replace("&8 ▷", "").strip();
      }

      CrateMenu cratemenu = new CrateMenu(s != null && !s.isBlank() ? s : "&8Rewards", 6);
      cratemenu.onAny(event -> event.setCancelled(true));
      fillPanes(cratemenu);
      cratemenu.set(19, arrowItem());
      int i = Math.max(0, options.indexOf(winner));
      paint(cratemenu, options, i, false);
      cratemenu.open(player);
      int j = Math.max(14, Math.min(40, config.getInt("spin-ticks", 20) + 12));
      long k = Math.max(2L, config.getLong("spin-interval-ticks", 4L));
      int l = ThreadLocalRandom.current().nextInt(Math.max(1, options.size()));
      int[] aint = new int[]{0};
      BukkitTask[] abukkittask = new BukkitTask[1];
      abukkittask[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
         if (!player.isOnline()) {
            abukkittask[0].cancel();
            finish(module, player, winner, winMessageKey, uuid);
         } else {
            boolean flag = aint[0] >= j - 1;
            int i1 = flag ? i : Math.floorMod(l + aint[0], options.size());
            paint(cratemenu, options, i1, flag);
            RewardSpin.playSound(player, config.getString(flag ? "sounds.win" : "sounds.spin", flag ? "ENTITY_PLAYER_LEVELUP" : "UI_BUTTON_CLICK"));
            aint[0]++;
            if (flag) {
               abukkittask[0].cancel();
               plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                  if (player.isOnline()) {
                     player.closeInventory();
                  }

                  finish(module, player, winner, winMessageKey, uuid);
               }, 30L);
            }
         }
      }, 0L, k);
   }

   static Material iconFor(RewardSpin.RewardOption option) {
      String s = ((option.display() == null ? "" : option.display()) + " " + String.join(" ", option.commands() == null ? List.of() : option.commands()))
         .toLowerCase(Locale.ROOT);
      if (s.contains("rank")) {
         return Material.PAPER;
      } else if (s.contains("craft")) {
         return Material.CRAFTING_TABLE;
      } else if (s.contains("key")) {
         return Material.TRIPWIRE_HOOK;
      } else {
         return s.contains("token") ? Material.SUNFLOWER : Material.CHEST;
      }
   }

   static ItemStack iconItem(RewardSpin.RewardOption option, boolean winner) {
      String s = option.display() != null && !option.display().isBlank() ? option.display() : "Reward";
      String s1 = option.rarityColor() == null ? "&f" : option.rarityColor();
      List<String> list = new ArrayList<>();
      list.add("&8Reward");
      list.add("");
      list.add(s1 + option.rarityLabel() + " &8(" + option.percentText() + "%)");
      if (winner) {
         list.add("");
         list.add("&aYou won this!");
      }

      ItemStack itemstack = new ItemBuilder(iconFor(option)).name(s1 + "&l" + s).lore(list).hideAll().build();
      if (winner) {
         ItemMeta itemmeta = itemstack.getItemMeta();
         if (itemmeta != null) {
            itemmeta.setEnchantmentGlintOverride(true);
            itemstack.setItemMeta(itemmeta);
         }
      }

      return itemstack;
   }

   private static void paint(CrateMenu menu, List<RewardSpin.RewardOption> options, int shift, boolean land) {
      for (int i = 0; i < RING.length; i++) {
         RewardSpin.RewardOption rewardspin$rewardoption = options.get(Math.floorMod(shift + i, options.size()));
         boolean flag = land && i == 0;
         menu.inventory().setItem(RING[i], iconItem(rewardspin$rewardoption, flag));
      }

      menu.inventory().setItem(19, arrowItem());
   }

   private static void fillPanes(CrateMenu menu) {
      ItemStack itemstack = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").hideAll().build();

      for (int i = 0; i < 54; i++) {
         menu.set(i, itemstack);
      }
   }

   private static ItemStack arrowItem() {
      ItemStack head = arrowHead();
      return new ItemBuilder(head).name("&#FFEE00&l▶").lore(List.of("&7Points right at the prize")).hideAll().build();
   }

   private static ItemStack arrowHead() {
      ItemStack itemstack = HeadUtil.textureHead(ARROW_HEAD_TEXTURE);
      if (itemstack.getItemMeta() instanceof SkullMeta skullmeta) {
         try {
            PlayerProfile playerprofile = Bukkit.createProfile(ARROW_HEAD_UUID, "MHF_ArrowRight");
            playerprofile.setProperty(new ProfileProperty("textures", ARROW_HEAD_TEXTURE));
            skullmeta.setPlayerProfile(playerprofile);
            itemstack.setItemMeta(skullmeta);
         } catch (Throwable ignored) {
         }
      }
      return itemstack;
   }

   private static void finish(Module module, Player player, RewardSpin.RewardOption winner, String winMessageKey, UUID uuid) {
      RewardSpin.grant(player, winner);
      String s = winner.rarityColor() == null ? "" : winner.rarityColor();
      module.send(
         player,
         winMessageKey,
         "%reward%",
         winner.display(),
         "%REWARD%",
         winner.display(),
         "%rarity%",
         winner.coloredRarity(),
         "%RARITY%",
         winner.coloredRarity(),
         "%rarity_color%",
         s,
         "%RARITY_COLOR%",
         s,
         "%rarity_colored%",
         winner.coloredRarity(),
         "%RARITY_COLORED%",
         winner.coloredRarity(),
         "%percent%",
         winner.percentText(),
         "%PERCENT%",
         winner.percentText()
      );
      RewardSpin.ACTIVE.remove(uuid);
   }
}
