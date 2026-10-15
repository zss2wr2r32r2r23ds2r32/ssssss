package dev.sharded.core.rollback;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class JoinedPlayerCache implements Listener {
    private final JavaPlugin plugin;
    private final Map<UUID, String> tabNames = new LinkedHashMap<>();
    private final Set<UUID> knownPlayers = new HashSet<>();
    private File file;

    public JoinedPlayerCache(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        tabNames.clear();
        knownPlayers.clear();
        file = new File(plugin.getDataFolder(), "joined-players.yml");
        if (file.exists()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            var section = yaml.getConfigurationSection("players");
            if (section != null) {
                for (String uuid : section.getKeys(false)) {
                    try {
                        UUID id = UUID.fromString(uuid);
                        tabNames.put(id, section.getString(uuid));
                        knownPlayers.add(id);
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        }
        plugin.getServer().getOnlinePlayers().forEach(this::indexInternal);
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        tabNames.forEach((uuid, name) -> yaml.set("players." + uuid, name));
        try {
            yaml.save(file);
        } catch (IOException ignored) {
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLogin(PlayerLoginEvent event) {
        indexInternal(event.getPlayer());
    }

    public void remember(Player player) {
        indexInternal(player);
        knownPlayers.add(player.getUniqueId());
        save();
    }

    public void indexForTab(Player player) {
        tabNames.put(player.getUniqueId(), player.getName());
    }

    private void indexInternal(Player player) {
        tabNames.put(player.getUniqueId(), player.getName());
    }

    public boolean isFirstJoin(UUID uuid) {
        return !knownPlayers.contains(uuid);
    }

    public List<String> tabNames(String prefix) {
        String needle = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String name : tabNames.values()) {
            if (name != null && name.toLowerCase(Locale.ROOT).startsWith(needle)) {
                out.add(name);
            }
        }
        plugin.getServer().getOnlinePlayers().forEach(player -> {
            if (player.getName().toLowerCase(Locale.ROOT).startsWith(needle) && !out.contains(player.getName())) {
                out.add(player.getName());
            }
        });
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    public UUID lookup(String name) {
        for (var e : tabNames.entrySet()) {
            if (e.getValue().equalsIgnoreCase(name)) {
                return e.getKey();
            }
        }
        Player online = plugin.getServer().getPlayerExact(name);
        return online == null ? null : online.getUniqueId();
    }

    public String name(UUID uuid) {
        return tabNames.get(uuid);
    }
}
