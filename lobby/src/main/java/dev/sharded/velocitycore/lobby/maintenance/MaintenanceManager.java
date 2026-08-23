package dev.sharded.velocitycore.lobby.maintenance;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class MaintenanceManager {

    public static final String KICK_MESSAGE = String.join("\n",
            "&#FF0000&lMAINTENANCE",
            "&fThis server is currently in downtime",
            "",
            "&fIf you believe the is an error contact staff via",
            "&#FFD900▷ &n&ldiscord.gg/shardedmc&f &#FFD900◁"
    );

    private final JavaPlugin plugin;
    private final File file;
    private final Set<UUID> bypassPlayers = new LinkedHashSet<>();
    private boolean enabled;

    public MaintenanceManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "maintenance.yml");
        load();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Component kickComponent() {
        return dev.sharded.velocitycore.lobby.util.LegacyText.parse(KICK_MESSAGE);
    }

    public boolean canJoin(Player player) {
        return !enabled || bypassPlayers.contains(player.getUniqueId());
    }

    public boolean canJoin(UUID playerId) {
        return !enabled || bypassPlayers.contains(playerId);
    }

    public Set<UUID> bypassPlayers() {
        return Collections.unmodifiableSet(bypassPlayers);
    }

    public boolean enableAndKick() {
        enabled = true;
        save();

        Component kickMessage = kickComponent();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!canJoin(player)) {
                player.kick(kickMessage);
            }
        }
        return true;
    }

    public boolean disable() {
        enabled = false;
        save();
        return true;
    }

    public boolean toggle() {
        if (enabled) {
            disable();
            return false;
        }
        enableAndKick();
        return true;
    }

    public boolean addBypass(String playerName) {
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
        UUID uuid = offlinePlayer.getUniqueId();
        if (bypassPlayers.contains(uuid)) {
            return false;
        }
        bypassPlayers.add(uuid);
        save();
        return true;
    }

    public boolean removeBypass(String playerName) {
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
        UUID uuid = offlinePlayer.getUniqueId();
        if (!bypassPlayers.contains(uuid)) {
            return false;
        }
        bypassPlayers.remove(uuid);
        save();
        if (enabled) {
            Player online = Bukkit.getPlayer(uuid);
            if (online != null && online.isOnline()) {
                online.kick(kickComponent());
            }
        }
        return true;
    }

    public void wipeBypass() {
        if (bypassPlayers.isEmpty()) {
            return;
        }

        bypassPlayers.clear();
        save();

        if (enabled) {
            Component kickMessage = kickComponent();
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.kick(kickMessage);
            }
        }
    }

    public List<String> bypassNames() {
        List<String> names = new ArrayList<>();
        for (UUID uuid : bypassPlayers) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
            String name = offlinePlayer.getName();
            names.add(name != null ? name : uuid.toString());
        }
        return names;
    }

    public List<String> onlinePlayerSuggestions() {
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .collect(Collectors.toList());
    }

    private void load() {
        if (!file.exists()) {
            enabled = false;
            return;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        enabled = config.getBoolean("enabled", false);
        bypassPlayers.clear();
        for (String uuidString : config.getStringList("bypass")) {
            try {
                bypassPlayers.add(UUID.fromString(uuidString));
            } catch (IllegalArgumentException ignored) {
                // Skip invalid UUID entries.
            }
        }
    }

    private void save() {
        FileConfiguration config = new YamlConfiguration();
        config.set("enabled", enabled);
        config.set("bypass", bypassPlayers.stream().map(UUID::toString).toList());
        try {
            if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
                plugin.getLogger().warning("Unable to create plugin data folder for maintenance.yml");
            }
            config.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to save maintenance.yml: " + exception.getMessage());
        }
    }
}
