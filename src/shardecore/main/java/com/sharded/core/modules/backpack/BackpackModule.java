package com.sharded.core.modules.backpack;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.modules.tokens.TokenService;
import com.sharded.core.util.Numbers;
import com.sharded.core.util.OfflinePlayers;
import com.sharded.core.util.TabCompleteHelper;
import com.sharded.core.util.Text;
import com.sharded.core.util.TrackedInventories;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class BackpackModule extends Module implements CommandExecutor, TabCompleter {
   private BackpackDatabase database;

   public BackpackModule(ShardedCore plugin) {
      super(plugin, "backpack");
   }

   @Override
   protected void onEnable() {
      try {
         this.database = new BackpackDatabase(this.plugin, this.moduleFolder());
      } catch (Exception exception) {
         throw new IllegalStateException("Could not open backpack database", exception);
      }

      this.registerCommand("backpack", this);
   }

   @Override
   protected void onDisable() {
      for (Player player : this.plugin.getServer().getOnlinePlayers()) {
         if (TrackedInventories.lookup(player.getOpenInventory().getTopInventory(), BackpackModule.BackpackHolder.class) != null) {
            player.closeInventory();
         }
      }

      if (this.database != null) {
         this.database.close();
      }

      this.database = null;
   }

   private int maxSlots() {
      return Math.min(9, Math.max(1, this.config.getInt("max-slots", 9)));
   }

   private int allowedSlots(Player player) {
      if (player == null) {
         return Math.max(1, this.config.getInt("default-slots", 1));
      } else {
         int i = this.maxSlots();
         int j = Math.min(i, Math.max(1, this.config.getInt("default-slots", 1)));

         for (int k = i; k > j; k--) {
            if (player.hasPermission("sharded.backpack.slots." + k)) {
               return k;
            }
         }

         return j;
      }
   }

   public boolean tryPurchaseSlot(Player player, int slotCount, long cost) {
      if (!player.hasPermission("sharded.tokenshop.use")) {
         this.send(player, "no-permission", new String[0]);
         return false;
      } else if (slotCount >= 2 && slotCount <= this.maxSlots()) {
         String s = "sharded.backpack.slots." + slotCount;
         if (player.hasPermission(s)) {
            this.send(player, "slot-owned", new String[]{"%slots%", String.valueOf(slotCount)});
            return false;
         } else if (slotCount > 2 && !player.hasPermission("sharded.backpack.slots." + (slotCount - 1))) {
            this.send(player, "slot-needs-previous", new String[]{"%slots%", String.valueOf(slotCount - 1)});
            return false;
         } else {
            TokenService tokenservice = this.plugin.modules().tokens();
            if (tokenservice == null) {
               return false;
            } else {
               long i = tokenservice.getBalance(player.getUniqueId());
               if (i < cost) {
                  this.send(player, "not-enough-tokens", new String[]{"%missing%", Numbers.format(cost - i)});
                  return false;
               } else if (!tokenservice.take(player.getUniqueId(), cost)) {
                  this.send(player, "not-enough-tokens", new String[]{"%missing%", Numbers.format(cost - i)});
                  return false;
               } else if (!this.plugin.luckPerms().isAvailable()) {
                  tokenservice.give(player.getUniqueId(), cost);
                  this.send(player, "lp-missing", new String[0]);
                  return false;
               } else {
                  this.plugin.luckPerms().runConsole("lp user " + player.getName() + " permission set " + s + " true");
                  this.send(player, "slot-purchased", new String[]{"%slots%", String.valueOf(slotCount)});
                  player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.0F);
                  return true;
               }
            }
         }
      } else {
         return false;
      }
   }

   private int[] storageSlots(int count) {
      int i = (9 - count) / 2;
      int[] aint = new int[count];

      for (int j = 0; j < count; j++) {
         aint[j] = i + j;
      }

      return aint;
   }

   private int displaySlotCount(Player owner, ItemStack[] stored) {
      int i = this.allowedSlots(owner);
      if (stored != null && stored.length != 0) {
         int j = 0;

         for (ItemStack itemstack : stored) {
            if (itemstack != null && !itemstack.getType().isAir()) {
               j++;
            }
         }

         return Math.max(i, Math.min(this.maxSlots(), Math.max(stored.length, j)));
      } else {
         return i;
      }
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (sender instanceof Player player) {
         if (!player.hasPermission("sharded.backpack.use")) {
            this.send(player, "no-permission", new String[0]);
            return true;
         } else {
            UUID uuid = player.getUniqueId();
            String s = player.getName();
            boolean flag = false;
            if (args.length >= 1) {
               boolean flag1 = player.hasPermission("sharded.backpack.view.others") || player.hasPermission("sharded.backpack.admin");
               if (!flag1) {
                  this.send(player, "no-permission-others", new String[0]);
                  return true;
               }

               OfflinePlayer offlineplayer = OfflinePlayers.resolve(args[0]);
               uuid = offlineplayer.getUniqueId();
               s = offlineplayer.getName() == null ? args[0] : offlineplayer.getName();
               flag = !player.getUniqueId().equals(uuid);
            }

            Player player1 = Bukkit.getPlayer(uuid);
            ItemStack[] aitemstack = this.database.load(uuid);
            int i = this.displaySlotCount(player1, aitemstack);
            boolean flag2 = flag && player.hasPermission("sharded.backpack.admin");
            boolean flag3 = flag && !flag2;
            this.open(player, uuid, s, flag3, i, aitemstack, flag2);
            return true;
         }
      } else {
         this.send(sender, "players-only", new String[0]);
         return true;
      }
   }

   private void open(Player viewer, UUID ownerId, String ownerName, boolean readOnly, int slotCount, ItemStack[] stored, boolean adminView) {
      int[] aint = this.storageSlots(slotCount);
      BackpackModule.BackpackHolder backpackmodule$backpackholder = new BackpackModule.BackpackHolder(ownerId, readOnly, aint);
      String s = adminView ? "title-admin" : "title";
      String s1 = Text.apply(this.config.getString(s, this.config.getString("title", "&5Backpack &8- &7%player%")), "%player%", ownerName);
      Inventory inventory = Bukkit.createInventory(backpackmodule$backpackholder, 9, Text.c(s1));
      backpackmodule$backpackholder.inventory = inventory;
      TrackedInventories.track(inventory, backpackmodule$backpackholder);
      Material material = Material.BLACK_STAINED_GLASS_PANE;
      String s2 = " ";
      if (this.config.isConfigurationSection("filler")) {
         material = Material.matchMaterial(this.config.getString("filler.material", "BLACK_STAINED_GLASS_PANE").toUpperCase(Locale.ROOT));
         if (material == null) {
            material = Material.BLACK_STAINED_GLASS_PANE;
         }

         s2 = this.config.getString("filler.name", " ");
      }

      ItemStack itemstack = new ItemStack(material);
      ItemMeta itemmeta = itemstack.getItemMeta();
      if (itemmeta != null) {
         itemmeta.displayName(Text.c(s2));
         itemstack.setItemMeta(itemmeta);
      }

      for (int i = 0; i < 9; i++) {
         inventory.setItem(i, itemstack);
      }

      for (int j = 0; j < aint.length; j++) {
         if (j < stored.length && stored[j] != null && !stored[j].getType().isAir()) {
            inventory.setItem(aint[j], stored[j]);
         } else {
            inventory.setItem(aint[j], null);
         }
      }

      viewer.openInventory(inventory);
      if (this.config.getBoolean("play-sound", true)) {
         viewer.playSound(viewer.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.6F, 1.3F);
      }

      if (adminView) {
         this.send(viewer, "admin-view", new String[]{"%player%", ownerName});
      } else if (readOnly) {
         this.send(viewer, "viewing-other", new String[]{"%player%", ownerName});
      }
   }

   @EventHandler
   public void onClick(InventoryClickEvent event) {
      BackpackModule.BackpackHolder backpackmodule$backpackholder = TrackedInventories.lookup(
         event.getView().getTopInventory(), BackpackModule.BackpackHolder.class
      );
      if (backpackmodule$backpackholder != null) {
         if (backpackmodule$backpackholder.readOnly) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
               this.send(player, "read-only", new String[0]);
            }
         } else {
            if (event.getClickedInventory() == event.getView().getTopInventory() && !backpackmodule$backpackholder.isStorageSlot(event.getSlot())) {
               event.setCancelled(true);
            }
         }
      }
   }

   @EventHandler
   public void onClose(InventoryCloseEvent event) {
      BackpackModule.BackpackHolder backpackmodule$backpackholder = TrackedInventories.untrack(event.getInventory(), BackpackModule.BackpackHolder.class);
      if (backpackmodule$backpackholder != null) {
         if (!backpackmodule$backpackholder.readOnly) {
            ItemStack[] aitemstack = new ItemStack[backpackmodule$backpackholder.slots.length];

            for (int i = 0; i < backpackmodule$backpackholder.slots.length; i++) {
               ItemStack itemstack = event.getInventory().getItem(backpackmodule$backpackholder.slots[i]);
               aitemstack[i] = itemstack != null && !itemstack.getType().isAir() ? itemstack.clone() : null;
            }

            this.plugin.getServer().getScheduler().runTaskAsynchronously(this.plugin, () -> {
               if (this.database != null) {
                  this.database.save(backpackmodule$backpackholder.owner, aitemstack);
               }
            });
         }
      }
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      return args.length != 1 || !sender.hasPermission("sharded.backpack.view.others") && !sender.hasPermission("sharded.backpack.admin")
         ? List.of()
         : TabCompleteHelper.onlinePlayers(args[0]);
   }

   private static final class BackpackHolder implements InventoryHolder {
      private final UUID owner;
      private final boolean readOnly;
      private final int[] slots;
      private Inventory inventory;

      private BackpackHolder(UUID owner, boolean readOnly, int[] slots) {
         this.owner = owner;
         this.readOnly = readOnly;
         this.slots = slots;
      }

      public Inventory getInventory() {
         return this.inventory;
      }

      private boolean isStorageSlot(int slot) {
         for (int i : this.slots) {
            if (i == slot) {
               return true;
            }
         }

         return false;
      }
   }
}
