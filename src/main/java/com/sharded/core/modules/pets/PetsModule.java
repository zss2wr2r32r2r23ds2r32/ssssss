package com.sharded.core.modules.pets;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.ColorUtil;
import com.sharded.core.util.ItemBuilder;
import com.sharded.core.util.TabCompleteHelper;
import com.sharded.core.util.Text;
import com.sharded.core.util.WordBlacklist;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Axolotl;
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
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.scheduler.BukkitTask;

import com.sharded.core.util.ConfigSync;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Cosmetic tiny pets — no damage, no death, follow the owner. */
public final class PetsModule extends Module implements CommandExecutor, TabCompleter {

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

    private final java.util.Map<UUID, ActivePet> active = new ConcurrentHashMap<>();
    private PetDatabase database;
    private NamespacedKey petOwnerKey;
    private BukkitTask followTask;

    public PetsModule(ShardedCore plugin) {
        super(plugin, "pets");
    }

    @Override
    protected void onEnable() {
        try {
            database = new PetDatabase(plugin, moduleFolder());
        } catch (Exception e) {
            throw new IllegalStateException("Could not open pets database", e);
        }
        petOwnerKey = new NamespacedKey(plugin, "pet_owner");

        File guiFile = new File(moduleFolder(), "gui.yml");
        ConfigSync.sync(plugin, guiFile, "modules/pets/gui.yml");
        plugin.gui().loadMenu(guiFile, "pets");

        for (PetType type : PetType.values()) {
            plugin.gui().registerAction("pet_equip_" + type.id(), p -> equip(p, type, null));
        }

        registerCommand("pets", this);
        registerCommand("pet", this);

        followTask = plugin.getServer().getScheduler().runTaskTimer(plugin, (Runnable) this::tickFollow, 1L, 1L);
    }

    @Override
    protected void onDisable() {
        if (followTask != null) followTask.cancel();
        for (UUID uuid : new ArrayList<>(active.keySet())) {
            removePet(uuid);
        }
        active.clear();
        if (database != null) database.close();
        database = null;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!event.getPlayer().isOnline() || database == null) return;
            PetDatabase.PetRecord record = database.get(event.getPlayer().getUniqueId());
            if (record != null && record.type() != null) {
                spawnPet(event.getPlayer(), record.type(), record.name(), record.variant());
            }
        }, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        removePet(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMount(EntityMountEvent event) {
        if (isPet(event.getEntity()) && event.getMount() instanceof Player owner) {
            UUID ownerId = petOwner(event.getEntity());
            if (ownerId != null && ownerId.equals(owner.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPetDamage(EntityDamageEvent event) {
        if (isPet(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPetAttack(EntityDamageByEntityEvent event) {
        if (isPet(event.getDamager())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPetTarget(EntityTargetEvent event) {
        if (isPet(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPetDeath(EntityDeathEvent event) {
        if (!isPet(event.getEntity())) return;
        event.setCancelled(true);
        event.getDrops().clear();
        event.setDroppedExp(0);
        UUID ownerId = petOwner(event.getEntity());
        if (ownerId != null) {
            plugin.getServer().getScheduler().runTask(plugin, () -> respawnIfNeeded(ownerId));
        }
    }

    private boolean isPet(Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(petOwnerKey, PersistentDataType.STRING);
    }

    private UUID petOwner(Entity entity) {
        String raw = entity.getPersistentDataContainer().get(petOwnerKey, PersistentDataType.STRING);
        if (raw == null) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("sharded.pets.use")) {
            send(player, "no-permission");
            return true;
        }

        if (command.getName().equalsIgnoreCase("pets")) {
            plugin.gui().open(player, "pets");
            return true;
        }

        if (args.length == 0) {
            send(player, "usage");
            return true;
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "equip" -> {
                if (args.length < 2) {
                    send(player, "equip-usage");
                    yield true;
                }
                PetType type = PetType.fromId(args[1]);
                if (type == null) {
                    send(player, "unknown-pet");
                    yield true;
                }
                String variant = null;
                if (type.supportsVariant() && args.length >= 3) {
                    variant = args[2];
                    if (!PetType.isValidAxolotlColor(variant)) {
                        send(player, "unknown-color");
                        yield true;
                    }
                }
                equip(player, type, variant);
                yield true;
            }
            case "remove" -> {
                unequip(player);
                yield true;
            }
            case "rename" -> {
                if (args.length < 2) {
                    send(player, "rename-usage");
                    yield true;
                }
                String name = ColorUtil.normalize(String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)));
                if (WordBlacklist.contains(config, "blacklist", name)) {
                    send(player, "blacklisted");
                    yield true;
                }
                ActivePet pet = active.get(player.getUniqueId());
                if (pet == null) {
                    send(player, "no-pet");
                    yield true;
                }
                pet.displayName = name;
                applyName(player.getUniqueId(), pet);
                if (database != null) database.save(player.getUniqueId(), pet.type, name, pet.variant);
                send(player, "renamed", "%name%", name);
                yield true;
            }
            default -> {
                send(player, "usage");
                yield true;
            }
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("pet")) return List.of();
        if (args.length == 1) {
            return TabCompleteHelper.filter(args[0], "equip", "remove", "rename");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("equip")) {
            List<String> types = new ArrayList<>();
            for (PetType type : PetType.values()) {
                if (sender.hasPermission(type.permission())) types.add(type.id());
            }
            return TabCompleteHelper.filter(args[1], types);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("equip") && args[1].equalsIgnoreCase("axolotl")) {
            return TabCompleteHelper.filter(args[2], PetType.axolotlColorNames());
        }
        return List.of();
    }

    private void equip(Player player, PetType type, String variant) {
        if (!player.hasPermission(type.permission())) {
            send(player, "no-pet-permission", "%pet%", type.id());
            return;
        }
        removePet(player.getUniqueId());
        String defaultName = raw("default-name-" + type.id());
        String savedVariant = type.supportsVariant()
                ? PetType.parseAxolotlVariant(variant).name().toLowerCase(Locale.ROOT)
                : null;
        spawnPet(player, type, defaultName, savedVariant);
        if (database != null) database.save(player.getUniqueId(), type, defaultName, savedVariant);
        send(player, "equipped", "%pet%", type.id());
    }

    private void unequip(Player player) {
        if (!active.containsKey(player.getUniqueId())) {
            send(player, "no-pet");
            return;
        }
        removePet(player.getUniqueId());
        if (database != null) database.clear(player.getUniqueId());
        send(player, "removed");
    }

    private void spawnPet(Player owner, PetType type, String displayName, String variant) {
        removePet(owner.getUniqueId());
        Location spawn = owner.getLocation();
        Entity entity;
        String axolotlVariant = variant;

        if (type.armorStand()) {
            entity = owner.getWorld().spawn(spawn, ArmorStand.class, stand -> {
                stand.setInvisible(true);
                stand.setMarker(true);
                stand.setSmall(true);
                stand.setBasePlate(false);
                stand.setArms(false);
                stand.getEquipment().setHelmet(new ItemBuilder(type.helmet()).build());
                configurePet(stand, owner.getUniqueId(), type, null);
            });
        } else {
            try {
                entity = owner.getWorld().spawn(
                        spawn,
                        type.entityType().getEntityClass(),
                        CreatureSpawnEvent.SpawnReason.CUSTOM,
                        e -> configurePet(e, owner.getUniqueId(), type, axolotlVariant));
            } catch (Exception ex) {
                plugin.getLogger().warning("Could not spawn pet " + type.id() + " for " + owner.getName()
                        + " in world " + owner.getWorld().getName() + ": " + ex.getMessage());
                send(owner, "spawn-failed", "%pet%", type.id());
                return;
            }
        }

        if (!(entity instanceof LivingEntity)) {
            entity.remove();
            plugin.getLogger().warning("Failed to spawn pet " + type.id() + " for " + owner.getName());
            return;
        }

        active.put(owner.getUniqueId(), new ActivePet(type, entity.getUniqueId(), displayName, axolotlVariant));
        applyName(owner.getUniqueId(), active.get(owner.getUniqueId()));
        tickFollow(owner.getUniqueId());
    }

    private void configurePet(Entity entity, UUID ownerId, PetType type, String axolotlVariant) {
        entity.setInvulnerable(true);
        entity.setSilent(true);
        entity.setPersistent(false);
        entity.getPersistentDataContainer().set(petOwnerKey, PersistentDataType.STRING, ownerId.toString());

        // Pets are cosmetic — never use gravity (warden was sinking underground).
        entity.setGravity(false);

        if (entity instanceof LivingEntity living) {
            living.setCollidable(false);
            if (living.getAttribute(Attribute.SCALE) != null) {
                living.getAttribute(Attribute.SCALE).setBaseValue(type.scale());
            }
            living.setCustomNameVisible(false);
        }
        if (entity instanceof Mob mob) {
            mob.setAI(false);
            mob.setAware(false);
            mob.setRemoveWhenFarAway(false);
        }
        if (entity instanceof Parrot parrot) {
            parrot.setTamed(true);
            parrot.setAdult();
        }
        if (entity instanceof Bee bee) {
            bee.setAnger(0);
            bee.setHasStung(false);
            bee.setHasNectar(false);
        }
        if (entity instanceof Axolotl axolotl) {
            axolotl.setPlayingDead(false);
            axolotl.setVariant(PetType.parseAxolotlVariant(axolotlVariant));
        }
    }

    private void applyName(UUID ownerId, ActivePet pet) {
        Entity entity = Bukkit.getEntity(pet.entityId);
        if (!(entity instanceof LivingEntity living)) return;
        if (pet.displayName == null || pet.displayName.isBlank()) {
            living.customName(null);
            living.setCustomNameVisible(false);
            return;
        }
        living.customName(Text.c(pet.displayName));
        living.setCustomNameVisible(true);
    }

    private void removePet(UUID ownerId) {
        ActivePet pet = active.remove(ownerId);
        if (pet == null) return;
        Entity entity = Bukkit.getEntity(pet.entityId);
        if (entity != null) entity.remove();
    }

    private void respawnIfNeeded(UUID ownerId) {
        Player owner = Bukkit.getPlayer(ownerId);
        ActivePet pet = active.get(ownerId);
        if (owner == null || !owner.isOnline() || pet == null) return;
        Entity entity = Bukkit.getEntity(pet.entityId);
        if (entity == null || entity.isDead()) {
            spawnPet(owner, pet.type, pet.displayName, pet.variant);
        }
    }

    private void tickFollow() {
        if (active.isEmpty()) return;
        for (UUID ownerId : new ArrayList<>(active.keySet())) {
            tickFollow(ownerId);
        }
    }

    private void tickFollow(UUID ownerId) {
        Player owner = Bukkit.getPlayer(ownerId);
        ActivePet pet = active.get(ownerId);
        if (owner == null || !owner.isOnline() || pet == null) return;

        Entity entity = Bukkit.getEntity(pet.entityId);
        if (entity == null || entity.isDead()) {
            spawnPet(owner, pet.type, pet.displayName, pet.variant);
            return;
        }
        if (!entity.getWorld().equals(owner.getWorld())) {
            entity.teleport(owner.getLocation());
            return;
        }

        Location target;
        if (pet.type.flyOrbit()) {
            clearParrotFromShoulder(owner, entity);
            target = flyOrbitLocation(owner, ownerId);
        } else if (pet.type.groundSnap()) {
            target = groundFollowLocation(owner);
        } else {
            target = followLocation(owner);
        }
        entity.teleport(target);
    }

    private void clearParrotFromShoulder(Player owner, Entity petEntity) {
        Entity left = owner.getShoulderEntityLeft();
        Entity right = owner.getShoulderEntityRight();
        if (left != null && left.getUniqueId().equals(petEntity.getUniqueId())) {
            owner.releaseLeftShoulderEntity();
        }
        if (right != null && right.getUniqueId().equals(petEntity.getUniqueId())) {
            owner.releaseRightShoulderEntity();
        }
    }

    private Location flyOrbitLocation(Player player, UUID ownerId) {
        long tick = System.currentTimeMillis() / 80L + ownerId.hashCode();
        double angle = (tick % 360) * Math.PI / 180.0;
        Location center = player.getLocation().clone().add(0, 1.9, 0);
        return center.add(Math.cos(angle) * 1.4, Math.sin(angle * 2) * 0.25, Math.sin(angle) * 1.4);
    }

    private Location followLocation(Player player) {
        Location loc = player.getLocation().clone();
        double yaw = Math.toRadians(loc.getYaw() + 180);
        return loc.add(-Math.sin(yaw) * 1.0, 0.55, Math.cos(yaw) * 1.0);
    }

    private Location groundFollowLocation(Player player) {
        Location loc = followLocation(player);
        loc.setY(player.getLocation().getY());
        return loc;
    }
}
