package com.sharded.core.modules.pets;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.modules.collisions.CollisionsModule;
import com.sharded.core.util.ColorUtil;
import com.sharded.core.util.ConfigSync;
import com.sharded.core.util.HeadUtil;
import com.sharded.core.util.ItemBuilder;
import com.sharded.core.util.TabCompleteHelper;
import com.sharded.core.util.Text;
import com.sharded.core.util.WordBlacklist;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Axolotl;
import org.bukkit.entity.Bat;
import org.bukkit.entity.Bee;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Parrot;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

public final class PetsModule extends Module implements CommandExecutor, TabCompleter {
   private final Map<UUID, PetsModule.ActivePet> active = new ConcurrentHashMap<>();
   private final Map<UUID, PetsModule.ActivePet> pendingRestore = new ConcurrentHashMap<>();
   private PetDatabase database;
   private NamespacedKey petOwnerKey;
   private BukkitTask followTask;

   public PetsModule(ShardedCore plugin) {
      super(plugin, "pets");
   }

   @Override
   protected void onEnable() {
      try {
         this.database = new PetDatabase(this.plugin, this.moduleFolder());
      } catch (Exception exception) {
         throw new IllegalStateException("Could not open pets database", exception);
      }

      this.petOwnerKey = new NamespacedKey(this.plugin, "pet_owner");
      File file1 = new File(this.moduleFolder(), "gui.yml");
      ConfigSync.sync(this.plugin, file1, "modules/pets/gui.yml");
      this.plugin.gui().loadMenu(file1, "pets");

      for (PetType pettype : PetType.values()) {
         this.plugin.gui().registerAction("pet_equip_" + pettype.id(), p -> this.equip(p, pettype, null));
      }

      this.plugin.gui().registerAction("pet_equip_happy_ghast", p -> this.equip(p, PetType.BAT, null));
      this.plugin.gui().registerAction("pet_equip_axolotle", p -> this.equip(p, PetType.AXOLOTL, null));
      this.plugin.gui().registerAction("pet_equip_axolotls", p -> this.equip(p, PetType.AXOLOTL, null));
      this.registerCommand("pets", this);
      this.registerCommand("pet", this);
      this.followTask = this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, (Runnable) this::tickFollow, 2L, 2L);
      this.respawnOnlinePlayers();
   }

   private void respawnOnlinePlayers() {
      this.plugin
         .getServer()
         .getScheduler()
         .runTaskLater(
            this.plugin,
            () -> {
               if (this.database != null) {
                  for (Player player : Bukkit.getOnlinePlayers()) {
                     PetDatabase.PetRecord petdatabase$petrecord;
                     if (!this.active.containsKey(player.getUniqueId())
                        && (petdatabase$petrecord = this.database.get(player.getUniqueId())) != null
                        && petdatabase$petrecord.type() != null) {
                        this.spawnPet(player, petdatabase$petrecord.type(), petdatabase$petrecord.name(), petdatabase$petrecord.variant());
                     }
                  }
               }
            },
            1L
         );
   }

   @Override
   protected void onDisable() {
      if (this.followTask != null) {
         this.followTask.cancel();
      }

      this.followTask = null;
      this.despawnAllEntities();
      this.active.clear();
      if (this.database != null) {
         this.database.close();
      }

      this.database = null;
   }

   private Entity spawnLivingPet(Player owner, PetType type, Location spawn, String axolotlVariant) {
      Class<?> oclass = type.entityType().getEntityClass();
      if (oclass != null && Entity.class.isAssignableFrom(oclass)) {
         Class<? extends Entity> oclass1 = (Class<? extends Entity>)oclass;
         Exception exception = null;

         for (SpawnReason spawnreason : new SpawnReason[]{SpawnReason.CUSTOM, SpawnReason.COMMAND}) {
            try {
               return owner.getWorld().spawn(spawn, oclass1, spawnreason, e -> this.configurePet(e, owner.getUniqueId(), type, axolotlVariant));
            } catch (Exception exception2) {
               exception = exception2;

               try {
                  Entity entity = owner.getWorld().spawnEntity(spawn, type.entityType(), spawnreason);
                  if (entity != null) {
                     this.configurePet(entity, owner.getUniqueId(), type, axolotlVariant);
                     return entity;
                  }
               } catch (Exception exception1) {
                  exception = exception1;
               }
            }
         }

         if (exception != null) {
            this.plugin
               .getLogger()
               .warning(
                  "Could not spawn pet " + type.id() + " for " + owner.getName() + " in world " + owner.getWorld().getName() + ": " + exception.getMessage()
               );
         }

         return null;
      } else {
         return null;
      }
   }

   private void despawnAllEntities() {
      for (UUID uuid : new ArrayList<>(this.active.keySet())) {
         PetsModule.ActivePet petsmodule$activepet = this.active.get(uuid);
         Entity entity;
         if (petsmodule$activepet != null && (entity = Bukkit.getEntity(petsmodule$activepet.entityId)) != null) {
            entity.remove();
         }
      }
   }

   @EventHandler
   public void onJoin(PlayerJoinEvent event) {
      this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
         if (event.getPlayer().isOnline() && this.database != null) {
            if (!this.active.containsKey(event.getPlayer().getUniqueId())) {
               PetDatabase.PetRecord petdatabase$petrecord = this.database.get(event.getPlayer().getUniqueId());
               if (petdatabase$petrecord != null && petdatabase$petrecord.type() != null) {
                  this.spawnPet(event.getPlayer(), petdatabase$petrecord.type(), petdatabase$petrecord.name(), petdatabase$petrecord.variant());
               }
            }
         }
      }, 20L);
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      this.despawnEntity(event.getPlayer().getUniqueId());
   }

   private void despawnEntity(UUID ownerId) {
      PetsModule.ActivePet petsmodule$activepet = this.active.remove(ownerId);
      if (petsmodule$activepet != null) {
         Entity entity = Bukkit.getEntity(petsmodule$activepet.entityId);
         if (entity != null) {
            entity.remove();
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onPetInteract(PlayerInteractEntityEvent event) {
      if (this.isPet(event.getRightClicked())) {
         event.setCancelled(true);
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onPetInteractAt(PlayerInteractAtEntityEvent event) {
      if (this.isPet(event.getRightClicked())) {
         event.setCancelled(true);
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onMount(EntityMountEvent event) {
      if (this.isPet(event.getEntity())) {
         event.setCancelled(true);
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onPetSpawn(CreatureSpawnEvent event) {
      if (this.isPet(event.getEntity())) {
         this.zeroVelocity(event.getEntity());
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onPetDamage(EntityDamageEvent event) {
      if (this.isPet(event.getEntity())) {
         event.setCancelled(true);
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onPetAttack(EntityDamageByEntityEvent event) {
      if (this.isPet(event.getDamager())) {
         event.setCancelled(true);
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onPetTarget(EntityTargetEvent event) {
      if (this.isPet(event.getEntity())) {
         event.setCancelled(true);
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onPetDeath(EntityDeathEvent event) {
      if (this.isPet(event.getEntity())) {
         event.setCancelled(true);
         event.getDrops().clear();
         event.setDroppedExp(0);
         UUID uuid = this.petOwner(event.getEntity());
         if (uuid != null) {
            this.plugin.getServer().getScheduler().runTask(this.plugin, () -> this.respawnIfNeeded(uuid));
         }
      }
   }

   private boolean isPet(Entity entity) {
      return entity != null && entity.getPersistentDataContainer().has(this.petOwnerKey, PersistentDataType.STRING);
   }

   private UUID petOwner(Entity entity) {
      String s = (String)entity.getPersistentDataContainer().get(this.petOwnerKey, PersistentDataType.STRING);
      if (s == null) {
         return null;
      } else {
         try {
            return UUID.fromString(s);
         } catch (IllegalArgumentException illegalargumentexception) {
            return null;
         }
      }
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (!(sender instanceof Player player)) {
         this.send(sender, "players-only", new String[0]);
         return true;
      } else if (command.getName().equalsIgnoreCase("pets")) {
         this.plugin.gui().open(player, "pets");
         return true;
      } else if (!player.hasPermission("sharded.pets.use")) {
         this.send(player, "no-permission", new String[0]);
         return true;
      } else if (args.length == 0) {
         this.send(player, "usage", new String[0]);
         return true;
      } else {
         String s = args[0].toLowerCase(Locale.ROOT);

         return switch (s) {
            case "equip" -> {
               if (args.length < 2) {
                  this.send(player, "equip-usage", new String[0]);
                  yield true;
               } else {
                  PetType pettype = PetType.fromId(args[1]);
                  if (pettype == null) {
                     this.send(player, "unknown-pet", new String[0]);
                     yield true;
                  } else {
                     String s2 = null;
                     if (pettype.supportsVariant() && args.length >= 3 && !PetType.isValidAxolotlColor(s2 = args[2])) {
                        this.send(player, "unknown-color", new String[0]);
                        yield true;
                     } else {
                        this.equip(player, pettype, s2);
                        yield true;
                     }
                  }
               }
            }
            case "remove" -> {
               this.unequip(player);
               yield true;
            }
            case "rename" -> {
               if (args.length < 2) {
                  this.send(player, "rename-usage", new String[0]);
                  yield true;
               } else {
                  String s1 = ColorUtil.normalize(String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
                  if (WordBlacklist.contains(this.config, "blacklist", s1)) {
                     this.send(player, "blacklisted", new String[0]);
                     yield true;
                  } else {
                     PetsModule.ActivePet petsmodule$activepet = this.active.get(player.getUniqueId());
                     if (petsmodule$activepet == null) {
                        this.send(player, "no-pet", new String[0]);
                        yield true;
                     } else {
                        petsmodule$activepet.displayName = s1;
                        this.applyName(player.getUniqueId(), petsmodule$activepet);
                        if (this.database != null) {
                           this.database.save(player.getUniqueId(), petsmodule$activepet.type, s1, petsmodule$activepet.variant);
                        }

                        this.send(player, "renamed", new String[]{"%name%", s1});
                        yield true;
                     }
                  }
               }
            }
            default -> {
               this.send(player, "usage", new String[0]);
               yield true;
            }
         };
      }
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      if (!command.getName().equalsIgnoreCase("pet")) {
         return List.of();
      } else if (args.length == 1) {
         return TabCompleteHelper.filter(args[0], "equip", "remove", "rename");
      } else if (args.length == 2 && args[0].equalsIgnoreCase("equip")) {
         ArrayList<String> arraylist = new ArrayList<>();

         for (PetType pettype : PetType.values()) {
            if (sender.hasPermission(pettype.permission())) {
               arraylist.add(pettype.id());
            }
         }

         return TabCompleteHelper.filter(args[1], arraylist);
      } else {
         return args.length == 3 && args[0].equalsIgnoreCase("equip") && args[1].equalsIgnoreCase("axolotl")
            ? TabCompleteHelper.filter(args[2], PetType.axolotlColorNames())
            : List.of();
      }
   }

   private void equip(Player player, PetType type, String variant) {
      if (player.hasPermission(type.permission()) || type == PetType.BAT && player.hasPermission("sharded.pets.happy_ghast")) {
         this.despawnEntity(player.getUniqueId());
         String s = this.raw("default-name-" + type.id(), new String[0]);
         String s1 = type.supportsVariant() ? PetType.parseAxolotlVariant(variant).name().toLowerCase(Locale.ROOT) : null;
         this.spawnPet(player, type, s, s1);
         if (this.database != null) {
            this.database.save(player.getUniqueId(), type, s, s1);
         }

         this.send(player, "equipped", new String[]{"%pet%", type.id()});
      } else {
         this.send(player, "no-pet-permission", new String[]{"%pet%", type.id()});
      }
   }

   public void hideEquippedPet(Player player) {
      if (this.active.containsKey(player.getUniqueId())) {
         PetsModule.ActivePet petsmodule$activepet = this.active.remove(player.getUniqueId());
         if (petsmodule$activepet != null) {
            Entity entity = Bukkit.getEntity(petsmodule$activepet.entityId);
            if (entity != null) {
               entity.remove();
            }

            this.pendingRestore.put(player.getUniqueId(), petsmodule$activepet);
         }
      }
   }

   public void restoreEquippedPet(Player player) {
      PetsModule.ActivePet petsmodule$activepet = this.pendingRestore.remove(player.getUniqueId());
      if (petsmodule$activepet != null) {
         this.spawnPet(player, petsmodule$activepet.type, petsmodule$activepet.displayName, petsmodule$activepet.variant);
      } else if (!this.active.containsKey(player.getUniqueId()) && this.database != null) {
         PetDatabase.PetRecord petdatabase$petrecord = this.database.get(player.getUniqueId());
         if (petdatabase$petrecord != null && petdatabase$petrecord.type() != null) {
            this.spawnPet(player, petdatabase$petrecord.type(), petdatabase$petrecord.name(), petdatabase$petrecord.variant());
         }
      }
   }

   public boolean hasEquippedPet(UUID uuid) {
      return this.active.containsKey(uuid) || this.pendingRestore.containsKey(uuid);
   }

   private void unequip(Player player) {
      if (!this.active.containsKey(player.getUniqueId())) {
         this.send(player, "no-pet", new String[0]);
      } else {
         this.removePet(player.getUniqueId());
         if (this.database != null) {
            this.database.clear(player.getUniqueId());
         }

         this.send(player, "removed", new String[0]);
      }
   }

   private void removePet(UUID ownerId) {
      this.despawnEntity(ownerId);
   }

   private void spawnPet(Player owner, PetType type, String displayName, String variant) {
      this.despawnEntity(owner.getUniqueId());
      Location location = owner.getLocation().clone().add(0.0, 1.15, 0.0);
      Entity entity;
      if (type.armorStand()) {
         entity = owner.getWorld()
            .spawn(
               location,
               ArmorStand.class,
               SpawnReason.CUSTOM,
               stand -> {
                  stand.setInvisible(true);
                  stand.setMarker(true);
                  stand.setSmall(true);
                  stand.setBasePlate(false);
                  stand.setArms(false);
                  ItemStack itemstack = type.headTexture() != null
                     ? HeadUtil.textureHead(type.headTexture())
                     : (type.helmet() != null ? new ItemBuilder(type.helmet()).build() : new ItemStack(Material.PLAYER_HEAD));
                  stand.getEquipment().setHelmet(itemstack);
                  this.configurePet(stand, owner.getUniqueId(), type, null);
               }
            );
      } else {
         entity = this.spawnLivingPet(owner, type, location, variant);
         if (entity == null) {
            this.plugin.getLogger().warning("Could not spawn pet " + type.id() + " for " + owner.getName() + " in world " + owner.getWorld().getName());
            this.send(owner, "spawn-failed", new String[]{"%pet%", type.id()});
            return;
         }
      }

      if (!(entity instanceof LivingEntity)) {
         entity.remove();
         this.plugin.getLogger().warning("Failed to spawn pet " + type.id() + " for " + owner.getName());
      } else {
         this.active.put(owner.getUniqueId(), new PetsModule.ActivePet(type, entity.getUniqueId(), displayName, variant));
         this.applyName(owner.getUniqueId(), this.active.get(owner.getUniqueId()));
         this.zeroVelocity(entity);
         UUID uuid = entity.getUniqueId();
         this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
            Entity entity1 = Bukkit.getEntity(uuid);
            if (entity1 != null) {
               this.zeroVelocity(entity1);
            }
         }, 1L);
         this.tickFollow(owner.getUniqueId());
      }
   }

   private void zeroVelocity(Entity entity) {
      if (entity != null) {
         entity.setVelocity(new Vector(0, 0, 0));
      }
   }

   private void configurePet(Entity entity, UUID ownerId, PetType type, String axolotlVariant) {
      entity.setInvulnerable(true);
      entity.setSilent(true);
      entity.setPersistent(true);
      entity.getPersistentDataContainer().set(this.petOwnerKey, PersistentDataType.STRING, ownerId.toString());
      this.zeroVelocity(entity);
      entity.setGravity(false);
      if (entity instanceof LivingEntity livingentity) {
         livingentity.setCollidable(false);
         livingentity.setInvisible(false);
         livingentity.setRemoveWhenFarAway(false);
         livingentity.setPersistent(true);
         if (livingentity.getAttribute(Attribute.SCALE) != null) {
            livingentity.getAttribute(Attribute.SCALE).setBaseValue(type.scale());
         }

         livingentity.setCustomNameVisible(false);
         livingentity.setRemainingAir(livingentity.getMaximumAir());
      }

      CollisionsModule collisionsmodule;
      if ((collisionsmodule = this.plugin.modules().get(CollisionsModule.class)) != null) {
         collisionsmodule.applyEntityCollision(entity);
      }

      if (entity instanceof Mob mob) {
         mob.setAI(false);
         mob.setAware(false);
         mob.setRemoveWhenFarAway(false);
      }

      if (entity instanceof Parrot parrot) {
         parrot.setTamed(true);
         parrot.setAdult();
         parrot.setSitting(false);
         parrot.setAI(false);
      }

      if (entity instanceof Bee bee) {
         bee.setAnger(0);
         bee.setHasStung(false);
         bee.setHasNectar(false);
         bee.setCannotEnterHiveTicks(Integer.MAX_VALUE);
         bee.setAI(false);
      }

      if (entity instanceof Axolotl axolotl) {
         axolotl.setPlayingDead(false);
         axolotl.setVariant(PetType.parseAxolotlVariant(axolotlVariant));
         axolotl.setFromBucket(true);
         axolotl.setRemainingAir(axolotl.getMaximumAir());
         axolotl.setAI(false);
      }

      if (entity instanceof Bat bat) {
         bat.setAwake(true);
      }
   }

   private void applyName(UUID ownerId, PetsModule.ActivePet pet) {
      if (Bukkit.getEntity(pet.entityId) instanceof LivingEntity livingentity) {
         if (pet.displayName != null && !pet.displayName.isBlank()) {
            livingentity.customName(Text.c(pet.displayName));
            livingentity.setCustomNameVisible(true);
         } else {
            livingentity.customName(null);
            livingentity.setCustomNameVisible(false);
         }
      }
   }

   private void respawnIfNeeded(UUID ownerId) {
      Player player = Bukkit.getPlayer(ownerId);
      PetsModule.ActivePet petsmodule$activepet = this.active.get(ownerId);
      if (player != null && player.isOnline() && petsmodule$activepet != null) {
         Entity entity = Bukkit.getEntity(petsmodule$activepet.entityId);
         if (entity == null || entity.isDead()) {
            this.spawnPet(player, petsmodule$activepet.type, petsmodule$activepet.displayName, petsmodule$activepet.variant);
         }
      }
   }

   private void tickFollow() {
      if (!this.active.isEmpty()) {
         for (UUID uuid : new ArrayList<>(this.active.keySet())) {
            this.tickFollow(uuid);
         }
      }
   }

   private void tickFollow(UUID ownerId) {
      Player player = Bukkit.getPlayer(ownerId);
      PetsModule.ActivePet petsmodule$activepet = this.active.get(ownerId);
      if (player != null && player.isOnline() && petsmodule$activepet != null) {
         Entity entity = Bukkit.getEntity(petsmodule$activepet.entityId);
         if (entity != null && !entity.isDead()) {
            if (!entity.getWorld().equals(player.getWorld())) {
               entity.teleport(player.getLocation());
               this.zeroVelocity(entity);
            } else {
               Location location;
               if (!petsmodule$activepet.type.flyOrbit() && petsmodule$activepet.type != PetType.BEE && petsmodule$activepet.type != PetType.PARROT) {
                  location = petsmodule$activepet.type.groundSnap() ? this.groundFollowLocation(player) : this.followLocation(player);
               } else {
                  this.clearParrotFromShoulder(player, entity);
                  location = this.flyOrbitLocation(player, ownerId);
               }

               if (entity.getLocation().distanceSquared(location) > 0.04) {
                  entity.teleport(location);
                  this.zeroVelocity(entity);
               }
            }
         } else {
            this.spawnPet(player, petsmodule$activepet.type, petsmodule$activepet.displayName, petsmodule$activepet.variant);
         }
      }
   }

   private void clearParrotFromShoulder(Player owner, Entity petEntity) {
      Entity entity = owner.getShoulderEntityLeft();
      Entity entity1 = owner.getShoulderEntityRight();
      if (entity != null && entity.getUniqueId().equals(petEntity.getUniqueId())) {
         owner.releaseLeftShoulderEntity();
      }

      if (entity1 != null && entity1.getUniqueId().equals(petEntity.getUniqueId())) {
         owner.releaseRightShoulderEntity();
      }
   }

   private Location flyOrbitLocation(Player player, UUID ownerId) {
      long i = System.currentTimeMillis() / 80L + (long)ownerId.hashCode();
      double d0 = (double)(i % 360L) * Math.PI / 180.0;
      Location location = player.getLocation().clone().add(0.0, 1.9, 0.0);
      return location.add(Math.cos(d0) * 1.4, Math.sin(d0 * 2.0) * 0.25, Math.sin(d0) * 1.4);
   }

   private Location followLocation(Player player) {
      Location location = player.getLocation().clone();
      double d0 = Math.toRadians((double)(location.getYaw() + 180.0F));
      return location.add(-Math.sin(d0) * 1.6, 0.85, Math.cos(d0) * 1.6);
   }

   private Location groundFollowLocation(Player player) {
      Location location = this.followLocation(player);
      location.setY(player.getLocation().getY());
      return location;
   }

   private static final class ActivePet {
      final PetType type;
      UUID entityId;
      String displayName;
      String variant;

      ActivePet(PetType type, UUID entityId, String displayName, String variant) {
         this.type = type;
         this.entityId = entityId;
         this.displayName = displayName;
         this.variant = variant;
      }
   }
}
