package com.sharded.core.modules.trash;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.Text;
import com.sharded.core.util.TrackedInventories;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class TrashModule extends Module implements CommandExecutor {
   public TrashModule(ShardedCore plugin) {
      super(plugin, "trash");
   }

   @Override
   protected void onEnable() {
      this.registerCommand("trash", this);
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (sender instanceof Player player) {
         if (!player.hasPermission("sharded.trash.use")) {
            this.send(player, "no-permission", new String[0]);
            return true;
         } else {
            int i = Math.max(1, Math.min(6, this.config.getInt("rows", 4)));
            TrashModule.TrashHolder trashmodule$trashholder = new TrashModule.TrashHolder();
            Inventory inventory = Bukkit.createInventory(trashmodule$trashholder, i * 9, Text.c(this.config.getString("title", "&8Trash &7(closes = deletes)")));
            trashmodule$trashholder.inventory = inventory;
            TrackedInventories.track(inventory, trashmodule$trashholder);
            player.openInventory(inventory);
            return true;
         }
      } else {
         this.send(sender, "players-only", new String[0]);
         return true;
      }
   }

   @EventHandler
   public void onClose(InventoryCloseEvent event) {
      if (TrackedInventories.untrack(event.getInventory(), TrashModule.TrashHolder.class) != null) {
         boolean flag = !event.getInventory().isEmpty();
         event.getInventory().clear();
         if (flag && event.getPlayer() instanceof Player player && this.config.getBoolean("play-sound", true)) {
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.8F, 0.8F);
         }
      }
   }

   private static final class TrashHolder implements InventoryHolder {
      private Inventory inventory;

      public Inventory getInventory() {
         return this.inventory;
      }
   }
}
