package com.sharded.core.modules.pickupspawners;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.modules.tokens.TokenService;
import com.sharded.core.util.MessageUtil;
import com.sharded.core.util.TabCompleteHelper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.persistence.PersistentDataType;

public final class PickupSpawnersModule extends Module implements CommandExecutor, TabCompleter {
   private NamespacedKey mobKey;
   private final Map<UUID, Integer> paidPickups = new HashMap<>();

   public PickupSpawnersModule(ShardedCore plugin) {
      super(plugin, "pickupspawners");
   }

   @Override
   protected void onEnable() {
      this.mobKey = new NamespacedKey(this.plugin, "spawner_mob");
      this.registerCommand("spawners", this);
   }

   @Override
   protected void onDisable() {
      this.paidPickups.clear();
   }

   private long price() {
      return this.config.getLong("price", 1500L);
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (sender instanceof Player player) {
         if (!player.hasPermission("sharded.spawners.use")) {
            this.send(player, "no-permission", new String[0]);
            return true;
         } else if (args.length != 0 && args[0].equalsIgnoreCase("pay")) {
            long i = this.price();
            if (args.length >= 2) {
               long j;
               try {
                  j = Long.parseLong(args[1]);
               } catch (NumberFormatException numberformatexception) {
                  this.send(player, "usage", new String[0]);
                  return true;
               }

               if (j != i) {
                  this.send(player, "wrong-amount", new String[]{"%price%", String.valueOf(i)});
                  return true;
               }
            }

            TokenService tokenservice = this.plugin.modules().tokens();
            if (!this.config.getBoolean("use-tokens", true) || tokenservice != null && tokenservice.take(player.getUniqueId(), i)) {
               this.paidPickups.merge(player.getUniqueId(), 1, Integer::sum);
               this.send(player, "paid", new String[]{"%price%", String.valueOf(i)});
               return true;
            } else {
               this.send(player, "not-enough", new String[]{"%price%", String.valueOf(i)});
               return true;
            }
         } else {
            this.send(player, "usage", new String[0]);
            return true;
         }
      } else {
         this.send(sender, "players-only", new String[0]);
         return true;
      }
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      return args.length == 1 ? TabCompleteHelper.filter(args[0], "pay") : List.of();
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onBreak(BlockBreakEvent event) {
      if (event.getBlock().getType() != Material.SPAWNER) {
         return;
      }
      Player player = event.getPlayer();
      ItemStack tool = player.getInventory().getItemInMainHand();
      boolean silk = !this.config.getBoolean("require-silk-touch", true) || this.hasSilkTouch(tool);
      if (!silk) {
         return;
      }
      boolean ability = player.hasPermission("sharded.spawners.pickup");
      int paid = this.paidPickups.getOrDefault(player.getUniqueId(), 0);
      if (!ability && paid <= 0) {
         event.setCancelled(true);
         MessageUtil.deliver(
            player, this.raw("actionbar-hint", new String[]{"%price%", String.valueOf(this.price())}), this.resolveDelivery("actionbar-hint")
         );
         return;
      }
      if (!ability) {
         this.paidPickups.put(player.getUniqueId(), paid - 1);
      }
      event.setDropItems(false);
      event.setExpToDrop(0);
      EntityType entitytype = EntityType.PIG;
      if (event.getBlock().getState() instanceof CreatureSpawner creaturespawner && creaturespawner.getSpawnedType() != null) {
         entitytype = creaturespawner.getSpawnedType();
      }
      ItemStack itemstack1 = new ItemStack(Material.SPAWNER);
      BlockStateMeta blockstatemeta = (BlockStateMeta)itemstack1.getItemMeta();
      CreatureSpawner creaturespawner1 = (CreatureSpawner)blockstatemeta.getBlockState();
      creaturespawner1.setSpawnedType(entitytype);
      blockstatemeta.setBlockState(creaturespawner1);
      blockstatemeta.getPersistentDataContainer().set(this.mobKey, PersistentDataType.STRING, entitytype.name());
      itemstack1.setItemMeta(blockstatemeta);
      event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), itemstack1);
      this.send(player, "picked-up", new String[]{"%mob%", entitytype.name().replace('_', ' ')});
   }

   private boolean hasSilkTouch(ItemStack item) {
      if (item == null || item.getType().isAir()) {
         return false;
      }
      if (item.getEnchantmentLevel(Enchantment.SILK_TOUCH) > 0 || item.containsEnchantment(Enchantment.SILK_TOUCH)) {
         return true;
      }
      for (Enchantment enchantment : item.getEnchantments().keySet()) {
         if (enchantment.getKey().getKey().equalsIgnoreCase("silk_touch")) {
            return true;
         }
      }
      return false;
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onPlace(BlockPlaceEvent event) {
      if (event.getBlock().getType() == Material.SPAWNER) {
         ItemStack itemstack = event.getItemInHand();
         if (itemstack.getItemMeta() instanceof BlockStateMeta blockstatemeta) {
            if (blockstatemeta.getPersistentDataContainer().has(this.mobKey, PersistentDataType.STRING)) {
               String s = (String)blockstatemeta.getPersistentDataContainer().get(this.mobKey, PersistentDataType.STRING);

               try {
                  EntityType entitytype = EntityType.valueOf(s);
                  Block block = event.getBlockPlaced();
                  if (block.getState() instanceof CreatureSpawner creaturespawner) {
                     creaturespawner.setSpawnedType(entitytype);
                     creaturespawner.update(true, false);
                  }
               } catch (IllegalArgumentException illegalargumentexception) {
               }
            } else if (blockstatemeta.getBlockState() instanceof CreatureSpawner creaturespawner1) {
               Block block1 = event.getBlockPlaced();
               if (block1.getState() instanceof CreatureSpawner creaturespawner2) {
                  creaturespawner2.setSpawnedType(creaturespawner1.getSpawnedType());
                  creaturespawner2.update(true, false);
               }
            }
         }
      }
   }
}
