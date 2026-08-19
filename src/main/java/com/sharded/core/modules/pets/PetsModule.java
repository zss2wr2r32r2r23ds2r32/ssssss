package com.sharded.core.modules.pets;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.ColorUtil;
import com.sharded.core.util.TabCompleteHelper;
import com.sharded.core.util.Text;
import com.sharded.core.util.WordBlacklist;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Parrot;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Cosmetic tiny pets — no damage, no death, follow the owner. */
public final class PetsModule extends Module implements CommandExecutor, TabCompleter {

    private static final class ActivePet {
        final PetType type;
        UUID entityId;
        String displayName;

        ActivePet(PetType type, UUID entityId, String displayName) {
            this.type = type;
            this.entityId = entityId;
            this.displayName = displayName;
        }
    }

    private final Map<UUID, ActivePet> active = new ConcurrentHashMap<>();
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
        if (!guiFile.exists()) plugin.saveResource("modules/pets/gui.yml", false);
        plugin.gui().loadMenu(guiFile, "pets");

        plugin.gui().registerAction("pet_equip_parrot", p -> equip(p, PetType.PARROT));
        plugin.gui().registerAction("pet_equip_warden", p -> equip(p, PetType.WARDEN));
        plugin.gui().registerAction("pet_equip_enderdragon", p -> equip(p, PetType.ENDER_DRAGON));

        registerCommand("pets", this);
        registerCommand("pet", this);

        followTask = plugin.getServer().getScheduler().runTaskTimer(plugin, (Runnable) this::tickFollow, 2L, 2L);
    }

    @Override
    protected void onDisable() {
        if (followTask != null) followTask.cancel();
        for (UUID uuid : new ArrayList<>(active.keySet())) {
            removePet(uuid, false);
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
                spawnPet(event.getPlayer(), record.type(), record.name());
            }
        }, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        removePet(event.getPlayer().getUniqueId(), false);
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
            plugin.getServer().getScheduler().runTask(plugin, () -> tickFollow(ownerId));
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
                equip(player, type);
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
                if (database != null) database.save(player.getUniqueId(), pet.type, name);
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
        return List.of();
    }

    private void equip(Player player, PetType type) {
        if (!player.hasPermission(type.permission())) {
            send(player, "no-pet-permission", "%pet%", type.id());
            return;
        }
        removePet(player.getUniqueId(), false);
        String defaultName = raw("default-name-" + type.id());
        spawnPet(player, type, defaultName);
        if (database != null) database.save(player.getUniqueId(), type, defaultName);
        send(player, "equipped", "%pet%", type.id());
    }

    private void unequip(Player player) {
        if (!active.containsKey(player.getUniqueId())) {
            send(player, "no-pet");
            return;
        }
        removePet(player.getUniqueId(), true);
        if (database != null) database.clear(player.getUniqueId());
        send(player, "removed");
    }

    private void spawnPet(Player owner, PetType type, String displayName) {
        Location spawn = owner.getLocation();
        Entity entity = owner.getWorld().spawn(spawn, type.entityType().getEntityClass(),
                e -> configurePet(e, owner.getUniqueId(), type));
        if (!(entity instanceof LivingEntity living)) {
            entity.remove();
            return;
        }
        active.put(owner.getUniqueId(), new ActivePet(type, entity.getUniqueId(), displayName));
        applyName(owner.getUniqueId(), active.get(owner.getUniqueId()));
        tickFollow(owner.getUniqueId());
    }

    private void configurePet(Entity entity, UUID ownerId, PetType type) {
        entity.setInvulnerable(true);
        entity.setSilent(true);
        entity.setPersistent(false);
        entity.setGravity(false);
        entity.getPersistentDataContainer().set(petOwnerKey, PersistentDataType.STRING, ownerId.toString());

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
        if (entity instanceof EnderDragon dragon) {
            dragon.setPhase(EnderDragon.Phase.HOVER);
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

    private void removePet(UUID ownerId, boolean notify) {
        ActivePet pet = active.remove(ownerId);
        if (pet == null) return;
        Entity entity = Bukkit.getEntity(pet.entityId);
        if (entity != null) entity.remove();
    }

    private void tickFollow() {
        if (active.isEmpty()) return;
        for (UUID ownerId : active.keySet()) {
            tickFollow(ownerId);
        }
    }

    private void tickFollow(UUID ownerId) {
        Player owner = Bukkit.getPlayer(ownerId);
        ActivePet pet = active.get(ownerId);
        if (owner == null || !owner.isOnline() || pet == null) return;

        Entity entity = Bukkit.getEntity(pet.entityId);
        if (entity == null || entity.isDead()) {
            spawnPet(owner, pet.type, pet.displayName);
            return;
        }
        if (!entity.getWorld().equals(owner.getWorld())) {
            entity.teleport(owner.getLocation());
            return;
        }

        Location target = pet.type.shoulder()
                ? shoulderLocation(owner)
                : followLocation(owner);
        entity.teleport(target);
    }

    private Location shoulderLocation(Player player) {
        Location loc = player.getLocation().clone();
        double yaw = Math.toRadians(loc.getYaw());
        return loc.add(-Math.sin(yaw) * 0.35, 1.45, Math.cos(yaw) * 0.35);
    }

    private Location followLocation(Player player) {
        Location loc = player.getLocation().clone();
        double yaw = Math.toRadians(loc.getYaw() + 180);
        return loc.add(-Math.sin(yaw) * 1.2, 0.5, Math.cos(yaw) * 1.2);
    }
}
