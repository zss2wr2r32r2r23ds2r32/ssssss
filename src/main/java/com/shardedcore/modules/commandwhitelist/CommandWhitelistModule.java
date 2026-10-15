package com.shardedcore.modules.commandwhitelist;

import com.shardedcore.ShardedCore;
import com.shardedcore.module.Module;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.TabCompleteEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class CommandWhitelistModule extends Module implements Listener {

    private static final String BYPASS_PERMISSION = "shardedcore.commandwhitelist.bypass";

    private volatile boolean filteringActive;
    private List<WhitelistGroup> groups = List.of();
    private Set<String> alwaysAllowed = Set.of();

    public CommandWhitelistModule(ShardedCore plugin) {
        super(plugin, "commandwhitelist");
    }

    @Override
    public void enable() {
        registerListener(this);
        reloadWhitelist();
    }

    @Override
    public void disable() {
        cleanup();
    }

    @Override
    public void reload() {
        super.reload();
        reloadWhitelist();
    }

    private void reloadWhitelist() {
        alwaysAllowed = normalizeCommands(config.getStringList("always-allowed"));
        groups = loadGroups();
        Set<String> allConfigured = new LinkedHashSet<>(alwaysAllowed);
        for (WhitelistGroup group : groups) {
            allConfigured.addAll(group.commands());
        }
        filteringActive = config.getBoolean("enabled", true) && !allConfigured.isEmpty();
        if (config.getBoolean("enabled", true) && allConfigured.isEmpty()) {
            plugin.getLogger().warning("[commandwhitelist] No commands configured — filtering stays OFF.");
        }
    }

    private List<WhitelistGroup> loadGroups() {
        ConfigurationSection section = config.getConfigurationSection("groups");
        if (section == null) {
            List<String> legacy = config.getStringList("commands");
            if (legacy.isEmpty()) return List.of();
            return List.of(new WhitelistGroup("default", null, normalizeCommands(legacy)));
        }
        List<WhitelistGroup> loaded = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection groupSection = section.getConfigurationSection(key);
            if (groupSection == null) continue;
            String permission = groupSection.getString("permission");
            if (permission != null && permission.isBlank()) permission = null;
            loaded.add(new WhitelistGroup(
                    key,
                    permission,
                    normalizeCommands(groupSection.getStringList("commands"))));
        }
        return List.copyOf(loaded);
    }

    private Set<String> allowedCommands(Player player) {
        if (canBypass(player)) return null;

        Set<String> allowed = new LinkedHashSet<>(alwaysAllowed);
        for (WhitelistGroup group : groups) {
            if (group.permission() == null || player.hasPermission(group.permission())) {
                allowed.addAll(group.commands());
            }
        }
        if (allowed.isEmpty()) return null;
        return allowed;
    }

    private boolean canBypass(Player player) {
        if (player.hasPermission(BYPASS_PERMISSION)) return true;
        return config.getBoolean("ops-bypass", true) && player.isOp();
    }

    private boolean isAllowed(Player player, String commandLabel) {
        if (!filteringActive) return true;
        Set<String> allowed = allowedCommands(player);
        if (allowed == null) return true;
        return allowed.contains(normalizeCommandLabel(commandLabel));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!filteringActive) return;
        Player player = event.getPlayer();
        String label = extractCommandLabel(event.getMessage());
        if (label.isEmpty()) return;
        if (isAllowed(player, label)) return;
        event.setCancelled(true);
        send(player, "blocked");
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTabComplete(TabCompleteEvent event) {
        if (!filteringActive) return;
        if (!(event.getSender() instanceof Player player)) return;
        String buffer = event.getBuffer();
        if (buffer == null || !buffer.startsWith("/")) return;

        Set<String> allowed = allowedCommands(player);
        if (allowed == null) return;

        String withoutSlash = buffer.substring(1);
        int space = withoutSlash.indexOf(' ');
        if (space >= 0) {
            String label = normalizeCommandLabel(withoutSlash.substring(0, space));
            if (!allowed.contains(label)) {
                event.getCompletions().clear();
            }
            return;
        }

        String partial = withoutSlash.toLowerCase(Locale.ROOT);
        List<String> filtered = new ArrayList<>();
        for (String command : allowed) {
            if (command.startsWith(partial)) filtered.add(command);
        }
        Collections.sort(filtered);
        event.getCompletions().clear();
        event.getCompletions().addAll(filtered);
    }

    private static String extractCommandLabel(String message) {
        if (message == null || message.isBlank()) return "";
        String trimmed = message.trim();
        if (!trimmed.startsWith("/")) return "";
        String withoutSlash = trimmed.substring(1);
        int space = withoutSlash.indexOf(' ');
        String root = space >= 0 ? withoutSlash.substring(0, space) : withoutSlash;
        return normalizeCommandLabel(root);
    }

    private static String normalizeCommandLabel(String label) {
        if (label == null || label.isBlank()) return "";
        String normalized = label.toLowerCase(Locale.ROOT);
        int colon = normalized.indexOf(':');
        if (colon >= 0 && colon + 1 < normalized.length()) {
            normalized = normalized.substring(colon + 1);
        }
        return normalized;
    }

    private static Set<String> normalizeCommands(List<String> commands) {
        Set<String> out = new LinkedHashSet<>();
        if (commands == null) return out;
        for (String command : commands) {
            if (command == null || command.isBlank()) continue;
            out.add(normalizeCommandLabel(command));
        }
        return out;
    }

    private record WhitelistGroup(String id, String permission, Set<String> commands) {
    }
}
