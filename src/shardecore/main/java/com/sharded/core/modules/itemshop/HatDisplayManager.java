package com.sharded.core.modules.itemshop;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.sharded.core.ShardedCore;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.ItemDisplay.ItemDisplayTransform;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class HatDisplayManager {
   private final ShardedCore plugin;
   private final NamespacedKey key;
   private final Map<UUID, UUID> displays = new ConcurrentHashMap<>();

   public HatDisplayManager(ShardedCore plugin) {
      this.plugin = plugin;
      this.key = new NamespacedKey(plugin, "itemshop-hat");
   }

   public void apply(Player player, ItemShopTypes.Cosmetic hat, java.util.List<String> hiddenWorlds, boolean hideSpectator) {
      this.remove(player);
      if (hat == null || player.isDead()) {
         return;
      }
      if (hideSpectator && player.getGameMode() == GameMode.SPECTATOR) {
         return;
      }
      if (hiddenWorlds != null && hiddenWorlds.stream().anyMatch(w -> w.equalsIgnoreCase(player.getWorld().getName()))) {
         return;
      }
      ItemStack stack = this.stack(hat);
      ItemDisplay display = player.getWorld().spawn(player.getLocation().add(0, 1.8, 0), ItemDisplay.class, entity -> {
         entity.getPersistentDataContainer().set(this.key, PersistentDataType.BYTE, (byte) 1);
         entity.setPersistent(false);
         entity.setInvulnerable(true);
         entity.setGravity(false);
         entity.setItemDisplayTransform(ItemDisplayTransform.HEAD);
         entity.setItemStack(stack);
         Quaternionf rotation = new Quaternionf().rotateXYZ(
            (float) Math.toRadians(hat.rotX()),
            (float) Math.toRadians(hat.rotY()),
            (float) Math.toRadians(hat.rotZ())
         );
         entity.setTransformation(new Transformation(
            new Vector3f((float) hat.offsetX(), (float) hat.offsetY(), (float) hat.offsetZ()),
            rotation,
            new Vector3f(hat.scale(), hat.scale(), hat.scale()),
            new Quaternionf()
         ));
      });
      player.addPassenger(display);
      this.displays.put(player.getUniqueId(), display.getUniqueId());
   }

   public void remove(Player player) {
      UUID id = this.displays.remove(player.getUniqueId());
      if (id != null) {
         var entity = Bukkit.getEntity(id);
         if (entity != null) {
            entity.remove();
         }
      }
      for (var passenger : List.copyOf(player.getPassengers())) {
         if (passenger.getPersistentDataContainer().has(this.key, PersistentDataType.BYTE)) {
            passenger.remove();
         }
      }
   }

   public void removeAll() {
      for (UUID entityId : this.displays.values()) {
         var entity = Bukkit.getEntity(entityId);
         if (entity != null) {
            entity.remove();
         }
      }
      this.displays.clear();
   }

   ItemStack stack(ItemShopTypes.Cosmetic hat) {
      ItemStack fromPack = com.sharded.core.util.ItemsAdderHook.resolve(hat.material());
      if (fromPack == null && hat.itemModel() != null && !hat.itemModel().isBlank()) {
         fromPack = com.sharded.core.util.ItemsAdderHook.resolve(hat.itemModel());
      }
      if (fromPack != null && !fromPack.getType().isAir()) {
         return fromPack.clone();
      }
      Material material = Material.matchMaterial(hat.material() == null ? "" : hat.material());
      if (material == null || material.isAir()) {
         material = "tag".equals(hat.type()) ? Material.NAME_TAG : Material.PAPER;
      }
      ItemStack item = new ItemStack(material);
      ItemMeta meta = item.getItemMeta();
      if (meta != null) {
         if (hat.itemModel() != null && !hat.itemModel().isBlank()) {
            NamespacedKey model = NamespacedKey.fromString(hat.itemModel().toLowerCase());
            if (model != null) {
               meta.setItemModel(model);
            }
         }
         item.setItemMeta(meta);
      }
      if (hat.headTexture() != null && !hat.headTexture().isBlank() && item.getItemMeta() instanceof SkullMeta skull) {
         PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID(), "hat");
         profile.setProperty(new ProfileProperty("textures", hat.headTexture()));
         skull.setPlayerProfile(profile);
         item.setItemMeta(skull);
      }
      return item;
   }
}
