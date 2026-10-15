package com.sharded.core.modules.killrewards;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.ItemBuilder;
import com.sharded.core.util.Text;
import com.sharded.core.util.TrackedInventories;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitTask;

public final class KillRewardsModule extends Module implements CommandExecutor, TabCompleter {
   private static final String CLAIM_PREFIX = "killreward-claimed-";
   private final Set<UUID> claiming = ConcurrentHashMap.newKeySet();
   private BukkitTask notifyTask;

   public KillRewardsModule(ShardedCore plugin) {
      super(plugin, "killrewards");
   }

   @Override
   protected void onEnable() {
      this.registerCommand("killrewards", this);
      this.registerCommand("krewards", this);
      this.registerListener(this);
      if (this.config.getBoolean("notify.enabled", true)) {
         long i = Math.max(180L, this.config.getLong("notify.interval-seconds", 180L)) * 20L;
         this.notifyTask = Bukkit.getScheduler().runTaskTimer(this.plugin, this::notifyReady, i, i);
      }
   }

   @Override
   protected void onDisable() {
      if (this.notifyTask != null) {
         this.notifyTask.cancel();
         this.notifyTask = null;
      }
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (sender instanceof Player player) {
         if (!player.hasPermission("sharded.killrewards.use")) {
            this.send(player, "no-permission", new String[0]);
            return true;
         } else {
            this.openGui(player);
            return true;
         }
      } else {
         this.send(sender, "players-only", new String[0]);
         return true;
      }
   }

   private void openGui(Player player) {
      this.loadConfigs();
      int i = player.getStatistic(Statistic.PLAYER_KILLS);
      List<KillRewardsModule.RewardTier> list = this.loadTiers();
      int j = Math.max(1, Math.min(6, this.config.getInt("menu.rows", this.config.getInt("gui.rows", 6))));
      String s = this.config.getString("menu.title", this.config.getString("gui.title", "&8Kill Rewards"));
      KillRewardsModule.Holder killrewardsmodule$holder = new KillRewardsModule.Holder(player.getUniqueId());
      Inventory inventory = Bukkit.createInventory(killrewardsmodule$holder, j * 9, Text.c(s));
      killrewardsmodule$holder.inventory = inventory;
      TrackedInventories.track(inventory, killrewardsmodule$holder);
      Material material = this.material(
         this.config.getString("menu.filler.material", this.config.getString("gui.filler-material")), Material.GRAY_STAINED_GLASS_PANE
      );
      ItemStack itemstack = new ItemBuilder(material)
         .name(this.config.getString("menu.filler.name", " "))
         .lore(this.config.getStringList("menu.filler.lore"))
         .build();

      for (int k = 0; k < inventory.getSize(); k++) {
         inventory.setItem(k, itemstack);
      }

      int i1 = 0;
      int l = 0;
      KillRewardsModule.RewardTier killrewardsmodule$rewardtier = null;

      for (KillRewardsModule.RewardTier killrewardsmodule$rewardtier1 : list) {
         boolean flag = this.claimed(player.getUniqueId(), killrewardsmodule$rewardtier1.key);
         boolean flag1 = i >= killrewardsmodule$rewardtier1.required;
         if (flag) {
            i1++;
         } else if (flag1) {
            l++;
         } else if (killrewardsmodule$rewardtier == null) {
            killrewardsmodule$rewardtier = killrewardsmodule$rewardtier1;
         }

         if (killrewardsmodule$rewardtier1.slot >= 0 && killrewardsmodule$rewardtier1.slot < inventory.getSize()) {
            inventory.setItem(killrewardsmodule$rewardtier1.slot, this.icon(killrewardsmodule$rewardtier1, i, flag, flag1));
            killrewardsmodule$holder.slots.put(killrewardsmodule$rewardtier1.slot, killrewardsmodule$rewardtier1.key);
         }
      }

      if (this.config.getBoolean("menu.stats.enabled", true)) {
         int j1 = this.config.getInt("menu.stats.slot", 49);
         if (j1 >= 0 && j1 < inventory.getSize()) {
            ItemStack itemstack1 = new ItemStack(this.material(this.config.getString("menu.stats.material"), Material.PLAYER_HEAD));
            if (itemstack1.getItemMeta() instanceof SkullMeta skullmeta) {
               skullmeta.setOwningPlayer(player);
               itemstack1.setItemMeta(skullmeta);
            }

            String s2 = killrewardsmodule$rewardtier == null
               ? this.config.getString("no-next", "-")
               : String.valueOf(Math.max(0, killrewardsmodule$rewardtier.required - i));
            List<String> list1 = new ArrayList<>();

            for (String s1 : this.config.getStringList("menu.stats.lore")) {
               list1.add(
                  s1.replace("%kills%", String.valueOf(i))
                     .replace("%claimed%", String.valueOf(i1))
                     .replace("%total%", String.valueOf(list.size()))
                     .replace("%ready%", String.valueOf(l))
                     .replace("%next%", s2)
               );
            }

            inventory.setItem(j1, new ItemBuilder(itemstack1).name(this.config.getString("menu.stats.name", "&#FFEE00&lYOUR KILLS")).lore(list1).build());
            killrewardsmodule$holder.slots.put(j1, "__refresh__");
         }
      }

      this.play(player, "open");
      player.openInventory(inventory);
   }

   private ItemStack icon(KillRewardsModule.RewardTier tier, int kills, boolean claimed, boolean unlocked) {
      String s = claimed ? "icons.claimed" : (unlocked ? "icons.ready" : "icons.locked");
      Material material = this.material(
         this.config.getString(s + ".material"), claimed ? Material.LIME_DYE : (unlocked ? Material.YELLOW_DYE : Material.RED_DYE)
      );
      int i = Math.max(0, tier.required - kills);
      String s1 = String.join("\n", tier.display);
      if (s1.isBlank()) {
         s1 = this.config.getString("icons.no-rewards", "&f- &7none");
      }

      String s2 = this.config.getString(s + ".name", "&e%required%").replace("%required%", String.valueOf(tier.required)).replace("%left%", String.valueOf(i));
      List<String> list = new ArrayList<>();

      for (String s3 : this.config.getStringList(s + ".lore")) {
         list.add(
            s3.replace("%required%", String.valueOf(tier.required))
               .replace("%left%", String.valueOf(i))
               .replace("%rewards%", s1)
               .replace("%kills%", String.valueOf(kills))
         );
      }

      List<String> list1 = new ArrayList<>();

      for (String s4 : list) {
         if (s4.contains("\n")) {
            for (String s5 : s4.split("\n", -1)) {
               list1.add(s5);
            }
         } else {
            list1.add(s4);
         }
      }

      ItemBuilder itembuilder = new ItemBuilder(material).name(s2).lore(list1);
      if (!claimed && unlocked && this.config.getBoolean(s + ".glow", false)) {
         itembuilder.glow(true);
      }

      return itembuilder.build();
   }

   @EventHandler
   public void onClick(InventoryClickEvent event) {
      if (event.getWhoClicked() instanceof Player player) {
         KillRewardsModule.Holder killrewardsmodule$holder = TrackedInventories.lookup(event.getView().getTopInventory(), KillRewardsModule.Holder.class);
         if (killrewardsmodule$holder != null && killrewardsmodule$holder.owner.equals(player.getUniqueId())) {
            event.setCancelled(true);
            if (event.getClickedInventory() == event.getView().getTopInventory()) {
               String s = killrewardsmodule$holder.slots.get(event.getSlot());
               if (s != null) {
                  if ("__refresh__".equals(s)) {
                     this.openGui(player);
                     this.play(player, "open");
                  } else {
                     KillRewardsModule.RewardTier killrewardsmodule$rewardtier = this.tierByKey(s);
                     if (killrewardsmodule$rewardtier != null) {
                        int i = player.getStatistic(Statistic.PLAYER_KILLS);
                        if (i < killrewardsmodule$rewardtier.required) {
                           this.play(player, "error");
                           this.openGui(player);
                        } else if (this.claimed(player.getUniqueId(), killrewardsmodule$rewardtier.key)) {
                           this.send(player, "already-claimed", new String[0]);
                           this.play(player, "error");
                        } else if (this.claiming.add(player.getUniqueId())) {
                           try {
                              for (String s1 : killrewardsmodule$rewardtier.commands) {
                                 if (!this.isMoneyCommand(s1)) {
                                    String s2 = Text.apply(this.normalizeTokenCommand(s1), "%player%", player.getName());
                                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), s2.startsWith("/") ? s2.substring(1) : s2);
                                 }
                              }

                              this.plugin.stateStore().setBool(player.getUniqueId(), "killreward-claimed-" + killrewardsmodule$rewardtier.key, true);
                              this.send(
                                 player,
                                 "claimed",
                                 new String[]{"%required%", String.valueOf(killrewardsmodule$rewardtier.required), "%tier%", killrewardsmodule$rewardtier.key}
                              );
                              this.play(player, "claim");
                              this.openGui(player);
                           } finally {
                              this.claiming.remove(player.getUniqueId());
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void notifyReady() {
      for (Player player : Bukkit.getOnlinePlayers()) {
         if (player.hasPermission("sharded.killrewards.use")) {
            int i = player.getStatistic(Statistic.PLAYER_KILLS);
            int j = 0;

            for (KillRewardsModule.RewardTier killrewardsmodule$rewardtier : this.loadTiers()) {
               if (!this.claimed(player.getUniqueId(), killrewardsmodule$rewardtier.key) && i >= killrewardsmodule$rewardtier.required) {
                  j++;
               }
            }

            if (j > 0) {
               this.send(player, "ready", new String[]{"%amount%", String.valueOf(j)});
               this.play(player, "notify");
            }
         }
      }
   }

   private boolean claimed(UUID uuid, String key) {
      return this.plugin.stateStore().getBool(uuid, "killreward-claimed-" + key, false);
   }

   private KillRewardsModule.RewardTier tierByKey(String key) {
      for (KillRewardsModule.RewardTier killrewardsmodule$rewardtier : this.loadTiers()) {
         if (killrewardsmodule$rewardtier.key.equals(key)) {
            return killrewardsmodule$rewardtier;
         }
      }

      return null;
   }

   private List<KillRewardsModule.RewardTier> loadTiers() {
      ConfigurationSection configurationsection = this.config.getConfigurationSection("rewards");
      if (configurationsection == null) {
         return List.of();
      } else {
         List<Integer> list = this.parseSlots(this.config.getString("menu.reward-slots", "10-16,19-25,28-34,37-43"));
         List<KillRewardsModule.RewardTier> list1 = new ArrayList<>();
         int i = 0;

         for (String s : configurationsection.getKeys(false)) {
            ConfigurationSection configurationsection1 = configurationsection.getConfigurationSection(s);
            if (configurationsection1 != null) {
               int j = configurationsection1.getInt("kills", 0);
               if (j <= 0) {
                  String s1 = s.replaceAll("\\D", "");
                  if (!s1.isEmpty()) {
                     j = Integer.parseInt(s1);
                  }
               }

               int k = configurationsection1.contains("slot") ? configurationsection1.getInt("slot") : (i < list.size() ? list.get(i) : -1);
               List<String> list2 = new ArrayList<>();

               for (String s2 : configurationsection1.getStringList("commands")) {
                  if (!this.isMoneyCommand(s2)) {
                     list2.add(this.normalizeTokenCommand(s2));
                  }
               }

               List<String> list3 = configurationsection1.getStringList("display");
               if (list3.isEmpty()) {
                  list3 = configurationsection1.getStringList("lore");
               }

               list3 = this.stripMoneyDisplay(list3);
               String s3 = configurationsection1.getString("name", "&#FFEE00&l" + j + " Kills");
               list1.add(new KillRewardsModule.RewardTier(s, j, k, s3, list3, list2));
               i++;
            }
         }

         list1.sort(Comparator.comparingInt(t -> t.required));
         return list1;
      }
   }

   private List<Integer> parseSlots(String raw) {
      List<Integer> list = new ArrayList<>();
      if (raw != null && !raw.isBlank()) {
         for (String s : raw.split(",")) {
            String s1 = s.trim();
            if (s1.contains("-")) {
               String[] astring = s1.split("-", 2);

               try {
                  int i = Integer.parseInt(astring[0].trim());
                  int j = Integer.parseInt(astring[1].trim());

                  for (int k = i; k <= j; k++) {
                     list.add(k);
                  }
               } catch (NumberFormatException numberformatexception1) {
               }
            } else {
               try {
                  list.add(Integer.parseInt(s1));
               } catch (NumberFormatException numberformatexception) {
               }
            }
         }

         return list;
      } else {
         return list;
      }
   }

   private boolean isMoneyCommand(String cmd) {
      if (cmd == null) {
         return false;
      } else {
         String s = cmd.toLowerCase(Locale.ROOT);
         return s.contains("ecogive") || s.matches(".*\\b(eco|money|balance|bal)\\b.*give.*") || s.contains("deposit");
      }
   }

   private String normalizeTokenCommand(String cmd) {
      return cmd.replace("crystal give", "tokens give").replace("crystals give", "tokens give");
   }

   private List<String> stripMoneyDisplay(List<String> lines) {
      List<String> list = new ArrayList<>();

      for (String s : lines) {
         String s1 = s.replaceAll("§.", "").replaceAll("&[0-9a-fk-or]", "");
         if (!s1.contains("$") || s1.toUpperCase(Locale.ROOT).contains("TOKEN")) {
            list.add(s.replace("&lCRYSTALS", "&lTOKENS").replace("CRYSTALS", "TOKENS").replace("&#FC258D", "&#5C94FC").replace("&x&F&C&2&5&8&D", "&#5C94FC"));
         }
      }

      return list;
   }

   private void play(Player player, String key) {
      String s = "sounds." + key;
      if (this.config.getBoolean(s + ".enabled", true)) {
         String s1 = this.config.getString(s + ".sound", "ui.button.click");
         float f = (float)this.config.getDouble(s + ".volume", 1.0);
         float f1 = (float)this.config.getDouble(s + ".pitch", 1.0);

         try {
            player.playSound(player.getLocation(), Sound.valueOf(s1.replace('.', '_').replace('-', '_').toUpperCase(Locale.ROOT)), f, f1);
         } catch (IllegalArgumentException illegalargumentexception) {
            player.playSound(player.getLocation(), s1, f, f1);
         }
      }
   }

   private Material material(String raw, Material fallback) {
      if (raw != null && !raw.isBlank()) {
         Material material = Material.matchMaterial(raw.toUpperCase(Locale.ROOT));
         return material == null ? fallback : material;
      } else {
         return fallback;
      }
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      return List.of();
   }

   private static final class Holder implements InventoryHolder {
      private final UUID owner;
      private final Map<Integer, String> slots = new HashMap<>();
      private Inventory inventory;

      private Holder(UUID owner) {
         this.owner = owner;
      }

      public Inventory getInventory() {
         return this.inventory;
      }
   }

   private static record RewardTier(String key, int required, int slot, String name, List<String> display, List<String> commands) {
   }
}
