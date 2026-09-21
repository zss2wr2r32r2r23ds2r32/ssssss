package dev.sharded.core.mobs;

import dev.sharded.core.ShardedCore;
import com.destroystokyo.paper.event.entity.PhantomPreSpawnEvent;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class MobToggleService implements Listener, CommandExecutor {
    public static final int RADIUS = 50;

    private final ShardedCore plugin;
    private final Set<UUID> enabled = new HashSet<>();
    private File file;

    public MobToggleService(ShardedCore plugin) {
        this.plugin = plugin;
    }

    public void load() {
        enabled.clear();
        file = new File(plugin.getDataFolder(), "mob-toggles.yml");
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String raw : yaml.getStringList("enabled")) {
            try {
                enabled.add(UUID.fromString(raw));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("enabled", enabled.stream().map(UUID::toString).toList());
        try {
            yaml.save(file);
        } catch (IOException ignored) {
        }
    }

    public int radius() {
        return Math.max(1, plugin.getConfig().getInt("mob-toggle.radius", RADIUS));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.messages().get("core.players-only"));
            return true;
        }
        if (!player.hasPermission("shardedcore.mobtoggle")) {
            sender.sendMessage(plugin.messages().get("core.no-permission"));
            return true;
        }
        if (enabled.remove(player.getUniqueId())) {
            player.sendMessage(plugin.messages().get("mob-toggle.off"));
        } else {
            enabled.add(player.getUniqueId());
            player.sendMessage(plugin.messages().get("mob-toggle.on"));
        }
        save();
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreature(CreatureSpawnEvent event) {
        if (shouldBlock(event.getEntityType(), event.getLocation(), event.getSpawnReason())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPhantom(PhantomPreSpawnEvent event) {
        if (shouldBlock(EntityType.PHANTOM, event.getSpawnLocation(), CreatureSpawnEvent.SpawnReason.NATURAL)) {
            event.setCancelled(true);
        }
    }

    private boolean shouldBlock(EntityType type, Location location, CreatureSpawnEvent.SpawnReason reason) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        if (reason == CreatureSpawnEvent.SpawnReason.CUSTOM
                || reason == CreatureSpawnEvent.SpawnReason.COMMAND
                || reason == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG) {
            return false;
        }
        boolean hostile = isHostile(type);
        if (!hostile) {
            return false;
        }
        if (plugin.getConfig().getBoolean("mob-toggle.protect-spawn-region", true)
                && plugin.spawns().isInSpawnRegion(location)) {
            return true;
        }
        return nearToggledPlayer(location);
    }

    private boolean nearToggledPlayer(Location location) {
        if (enabled.isEmpty()) {
            return false;
        }
        int radius = radius();
        double rangeSq = (double) radius * radius;
        for (UUID uuid : enabled) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player == null || !player.getWorld().equals(location.getWorld())) {
                continue;
            }
            if (player.getLocation().distanceSquared(location) <= rangeSq) {
                return true;
            }
        }
        return false;
    }

    private static boolean isHostile(EntityType type) {
        if (type == EntityType.PHANTOM) {
            return true;
        }
        Class<?> clazz = type.getEntityClass();
        return clazz != null && (Monster.class.isAssignableFrom(clazz) || Phantom.class.isAssignableFrom(clazz));
    }
}
