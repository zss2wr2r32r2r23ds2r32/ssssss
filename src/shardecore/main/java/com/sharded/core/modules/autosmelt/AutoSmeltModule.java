package com.sharded.core.modules.autosmelt;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.Text;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class AutoSmeltModule extends Module implements CommandExecutor {
   private NamespacedKey enchantKey;
   private final Map<Material, Material> smeltMap = new HashMap<>();

   public AutoSmeltModule(ShardedCore plugin) {
      super(plugin, "autosmelt");
   }

   @Override
   protected void onEnable() {
      this.enchantKey = new NamespacedKey(this.plugin, "autosmelt");
      this.registerCommand("autosmelt", this);
      this.smeltMap.clear();
      ConfigurationSection configurationsection = this.config.getConfigurationSection("smelt-map");
      if (configurationsection != null) {
         for (String s : configurationsection.getKeys(false)) {
            Material material = Material.getMaterial(s.toUpperCase(Locale.ROOT));
            Material material1 = Material.getMaterial(configurationsection.getString(s, "").toUpperCase(Locale.ROOT));
            if (material != null && material1 != null) {
               this.smeltMap.put(material, material1);
            } else {
               this.plugin.getLogger().warning("[autosmelt] Invalid smelt-map entry: " + s);
            }
         }
      }
   }

   public boolean hasAutoSmelt(ItemStack item) {
      if (item != null && !item.getType().isAir()) {
         ItemMeta itemmeta = item.getItemMeta();
         return itemmeta != null && itemmeta.getPersistentDataContainer().has(this.enchantKey, PersistentDataType.BYTE);
      } else {
         return false;
      }
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (sender instanceof Player player) {
         if (!player.hasPermission("sharded.autosmelt.use")) {
            this.send(player, "no-permission", new String[0]);
            return true;
         } else {
            ItemStack itemstack = player.getInventory().getItemInMainHand();
            if (itemstack.getType().isAir() || !Tag.ITEMS_PICKAXES.isTagged(itemstack.getType())) {
               this.send(player, "not-a-pickaxe", new String[0]);
               return true;
            } else if (this.hasAutoSmelt(itemstack)) {
               this.send(player, "already-enchanted", new String[0]);
               return true;
            } else {
               ItemMeta itemmeta = itemstack.getItemMeta();
               itemmeta.getPersistentDataContainer().set(this.enchantKey, PersistentDataType.BYTE, (byte)1);
               List<Component> list = itemmeta.hasLore() ? new ArrayList<>(itemmeta.lore()) : new ArrayList<>();
               list.add(0, Text.c(this.config.getString("lore-line", "&7Auto Smelt I")));
               itemmeta.lore(list);
               itemstack.setItemMeta(itemmeta);
               player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8F, 1.2F);
               this.send(player, "applied", new String[0]);
               return true;
            }
         }
      } else {
         this.send(sender, "players-only", new String[0]);
         return true;
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onBreak(BlockBreakEvent event) {
      Player player = event.getPlayer();
      ItemStack itemstack = player.getInventory().getItemInMainHand();
      if (this.hasAutoSmelt(itemstack)) {
         if (Tag.ITEMS_PICKAXES.isTagged(itemstack.getType())) {
            Block block = event.getBlock();
            Collection<ItemStack> collection = block.getDrops(itemstack, player);
            if (!collection.isEmpty()) {
               boolean flag = false;
               List<ItemStack> list = new ArrayList<>();

               for (ItemStack itemstack1 : collection) {
                  Material material = this.smeltMap.get(itemstack1.getType());
                  if (material != null) {
                     list.add(new ItemStack(material, itemstack1.getAmount()));
                     flag = true;
                  } else {
                     list.add(itemstack1);
                  }
               }

               if (flag) {
                  event.setDropItems(false);

                  for (ItemStack itemstack2 : list) {
                     block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), itemstack2);
                  }

                  if (this.config.getBoolean("play-sound", false)) {
                     player.playSound(block.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.2F, 1.8F);
                  }
               }
            }
         }
      }
   }
}
