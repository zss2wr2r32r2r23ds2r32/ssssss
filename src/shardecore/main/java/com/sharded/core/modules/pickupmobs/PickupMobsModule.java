package com.sharded.core.modules.pickupmobs;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.ItemBuilder;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class PickupMobsModule extends Module {
   private NamespacedKey mobKey;
   private Set<EntityType> allowed = new HashSet<>();

   public PickupMobsModule(ShardedCore plugin) {
      super(plugin, "pickupmobs");
   }

   @Override
   protected void onEnable() {
      this.mobKey = new NamespacedKey(this.plugin, "picked_mob");
      this.reloadAllowed();
   }

   private void reloadAllowed() {
      this.allowed.clear();

      for (String s : this.config.getStringList("allowed-mobs")) {
         try {
            this.allowed.add(EntityType.valueOf(s.toUpperCase(Locale.ROOT)));
         } catch (IllegalArgumentException illegalargumentexception) {
            this.plugin.getLogger().warning("[pickupmobs] Unknown mob: " + s);
         }
      }
   }

   @EventHandler
   public void onInteract(PlayerInteractAtEntityEvent event) {
      Player player = event.getPlayer();
      if (player.isSneaking()) {
         if (player.hasPermission("sharded.pickupmobs.use")) {
            Entity entity = event.getRightClicked();
            if (!(entity instanceof LivingEntity livingentity) || entity instanceof Player) {
               return;
            }

            if (this.allowed.contains(entity.getType())) {
               Material material;
               try {
                  material = Material.valueOf(entity.getType().name() + "_SPAWN_EGG");
               } catch (IllegalArgumentException illegalargumentexception) {
                  return;
               }

               ItemStack itemstack = new ItemBuilder(material).name("&f" + entity.getType().name().replace('_', ' ') + " &7(Mob)").build();
               ItemMeta itemmeta = itemstack.getItemMeta();
               itemmeta.getPersistentDataContainer().set(this.mobKey, PersistentDataType.STRING, entity.getType().name());
               itemstack.setItemMeta(itemmeta);
               entity.remove();
               player.getInventory()
                  .addItem(new ItemStack[]{itemstack})
                  .values()
                  .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
               this.send(player, "picked-up", new String[]{"%mob%", entity.getType().name().replace('_', ' ')});
            }
         }
      }
   }
}
