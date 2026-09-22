package com.sharded.core.modules.rewards;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.modules.dailyrewards.DailyRewardsModule;
import com.sharded.core.modules.weeklyrewards.WeeklyRewardsModule;
import com.sharded.core.util.ItemBuilder;
import com.sharded.core.util.Text;
import com.sharded.core.util.TrackedInventories;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class RewardsModule extends Module implements CommandExecutor {
   public RewardsModule(ShardedCore plugin) {
      super(plugin, "rewards");
   }

   @Override
   protected void onEnable() {
      this.migrateGui();
      this.registerCommand("rewards", this);
   }

   private void migrateGui() {
      if (this.config.getInt("config-version", 0) >= 4) {
         return;
      }
      this.config.set("gui.size", 27);
      this.config.set("gui.ready", "&#8aff00&lREADY");
      this.config.set("gui.items.daily.slot", 11);
      this.config.set("gui.items.daily.name", "&#9FFF00&lDAILY REWARDS");
      this.config.set("gui.items.daily.lore", List.of(
         "&8Rewards",
         "",
         "&#9FFF00Information:",
         "&#9FFF00| &fClaim your daily reward.",
         "",
         "&#9FFF00☀ &fTime: &#9FFF00%time%",
         "",
         "&x&F&F&B&A&0&0▷ &x&F&F&B&A&0&0&l&nCLICK&r &x&F&F&B&A&0&0to claim"
      ));
      this.config.set("gui.items.daily.lore-claimed", List.of(
         "&8Rewards",
         "",
         "&#9FFF00Information:",
         "&#9FFF00| &fYou have already claimed this.",
         "",
         "&#9FFF00☀ &fTime: &#9FFF00%time%",
         "",
         "&x&F&F&B&A&0&0▷ &x&F&F&B&A&0&0%time%"
      ));
      this.config.set("gui.items.weekly.slot", 15);
      this.config.set("gui.items.weekly.name", "&#FFD700&lWEEKLY REWARDS");
      this.config.set("gui.items.weekly.lore", List.of(
         "&8Rewards",
         "",
         "&#FFD700Information:",
         "&#FFD700| &fClaim your weekly reward.",
         "",
         "&#FFD700☀ &fTime: &#FFD700%time%",
         "",
         "&x&F&F&B&A&0&0▷ &x&F&F&B&A&0&0&l&nCLICK&r &x&F&F&B&A&0&0to claim"
      ));
      this.config.set("gui.items.weekly.lore-claimed", List.of(
         "&8Rewards",
         "",
         "&#FFD700Information:",
         "&#FFD700| &fYou have already claimed this.",
         "",
         "&#FFD700☀ &fTime: &#FFD700%time%",
         "",
         "&x&F&F&B&A&0&0▷ &x&F&F&B&A&0&0%time%"
      ));
      this.config.set("config-version", 4);
      try {
         this.config.save(new java.io.File(this.moduleFolder(), "config.yml"));
      } catch (Exception ignored) {
      }
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (sender instanceof Player player) {
         this.openHub(player);
         return true;
      } else {
         this.send(sender, "players-only", new String[0]);
         return true;
      }
   }

   void openHub(Player player) {
      String s = this.config.getString("gui.title", "&8Rewards");
      RewardsModule.Holder holder = new RewardsModule.Holder();
      Inventory inventory = Bukkit.createInventory(holder, this.config.getInt("gui.size", 27), Text.c(s));
      holder.inventory = inventory;
      TrackedInventories.track(inventory, holder);
      ItemStack filler = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
      for (int i = 0; i < inventory.getSize(); i++) {
         inventory.setItem(i, filler);
      }
      inventory.setItem(this.config.getInt("gui.items.daily.slot", 11), this.hubItem(player, "daily"));
      inventory.setItem(this.config.getInt("gui.items.weekly.slot", 15), this.hubItem(player, "weekly"));
      player.openInventory(inventory);
   }

   private ItemStack hubItem(Player player, String key) {
      long remainingSeconds = this.remainingSeconds(player, key);
      boolean claimed = remainingSeconds > 0L;
      Material material = claimed ? Material.MINECART : Material.CHEST_MINECART;
      String color = this.config.getString("gui.colors." + key, "&e");
      String time = claimed
         ? Text.timeWeeksDaysMinutes(remainingSeconds)
         : this.config.getString("gui.ready", "&#8aff00&lREADY");
      String loreKey = claimed ? "gui.items." + key + ".lore-claimed" : "gui.items." + key + ".lore";
      List<String> lore = new ArrayList<>(this.config.getStringList(loreKey));
      if (lore.isEmpty()) {
         lore.addAll(this.config.getStringList("gui.items." + key + ".lore"));
      }
      if (lore.isEmpty()) {
         lore.add("&8Rewards");
         lore.add("");
         lore.add(color + "Information:");
         lore.add(color + "| &f" + (claimed ? "You have already claimed this." : "Claim your " + key + " reward."));
         lore.add("");
         lore.add(color + "☀ &fTime: " + color + "%time%");
         lore.add("");
         lore.add(claimed
            ? "&x&F&F&B&A&0&0▷ &x&F&F&B&A&0&0%time%"
            : "&x&F&F&B&A&0&0▷ &x&F&F&B&A&0&0&l&nCLICK&r &x&F&F&B&A&0&0to claim");
      }
      lore.replaceAll(line -> line.replace("%time%", time));
      return new ItemBuilder(material)
         .name(this.config.getString("gui.items." + key + ".name", color + "&l" + key.toUpperCase() + " REWARDS"))
         .lore(lore)
         .build();
   }

   private long cooldownHours(String key) {
      if ("weekly".equals(key)) {
         WeeklyRewardsModule weekly = this.plugin.modules().get(WeeklyRewardsModule.class);
         return weekly == null ? 168L : weekly.cooldownMillis() / 3600000L;
      }
      DailyRewardsModule daily = this.plugin.modules().get(DailyRewardsModule.class);
      return daily == null ? 24L : daily.cooldownMillis() / 3600000L;
   }

   private long remainingSeconds(Player player, String key) {
      long hours = this.cooldownHours(key);
      String storeKey = "weekly".equals(key) ? "weekly-reward-last" : "daily-reward-last";
      long last = this.plugin.stateStore().getLong(player.getUniqueId(), storeKey, 0L);
      if (last <= 0L) {
         return 0L;
      }
      long remainingMs = last + hours * 3600000L - System.currentTimeMillis();
      return remainingMs > 0L ? remainingMs / 1000L : 0L;
   }

   @EventHandler
   public void onClick(InventoryClickEvent event) {
      if (event.getWhoClicked() instanceof Player player) {
         if (TrackedInventories.lookup(event.getView().getTopInventory(), RewardsModule.Holder.class) != null) {
            event.setCancelled(true);
            if (event.getClickedInventory() == event.getView().getTopInventory()) {
               if (event.getSlot() == this.config.getInt("gui.items.daily.slot", 11)) {
                  player.closeInventory();
                  player.performCommand("dailyrewards");
               } else if (event.getSlot() == this.config.getInt("gui.items.weekly.slot", 15)) {
                  player.closeInventory();
                  player.performCommand("weeklyrewards");
               }
            }
         }
      }
   }

   static final class Holder implements InventoryHolder {
      Inventory inventory;

      public Inventory getInventory() {
         return this.inventory;
      }
   }
}
