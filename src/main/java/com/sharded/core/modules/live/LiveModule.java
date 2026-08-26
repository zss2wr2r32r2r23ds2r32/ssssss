package com.sharded.core.modules.live;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.TabCompleteHelper;
import com.sharded.core.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** /live — announce a stream link or toggle seeing live announcements. */
public final class LiveModule extends Module implements CommandExecutor, TabCompleter {

    public static final String STATE_KEY = "live";

    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public LiveModule(ShardedCore plugin) {
        super(plugin, "core", "live");
    }

    @Override
    protected void onEnable() {
        registerCommand("live", this);
    }

    @Override
    protected void onDisable() {
        cooldowns.clear();
    }

    /** UNSET defaults to shown (true). */
    public boolean isLiveShown(Player player) {
        return plugin.stateStore().getBool(player.getUniqueId(), STATE_KEY, true);
    }

    public void setLiveShown(Player player, boolean shown) {
        plugin.stateStore().setBool(player.getUniqueId(), STATE_KEY, shown);
        send(player, shown ? "toggle-on" : "toggle-off");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("sharded.live.use")) {
            send(player, "no-permission");
            return true;
        }
        if (args.length == 0) {
            send(player, "usage");
            return true;
        }
        if (args[0].equalsIgnoreCase("toggle")) {
            setLiveShown(player, !isLiveShown(player));
            return true;
        }

        String url = String.join(" ", args).trim();
        if (!isAllowedPlatform(url)) {
            send(player, "invalid-platform");
            return true;
        }

        long cooldownMs = config.getLong("cooldown-seconds", 600L) * 1000L;
        long now = System.currentTimeMillis();
        Long last = cooldowns.get(player.getUniqueId());
        if (last != null && now - last < cooldownMs) {
            long remaining = (cooldownMs - (now - last)) / 1000L + 1;
            send(player, "cooldown", "%time%", Text.time(remaining));
            return true;
        }
        cooldowns.put(player.getUniqueId(), now);

        String normalized = normalizeUrl(url);
        broadcastLive(player.getName(), normalized);
        return true;
    }

    private void broadcastLive(String playerName, String url) {
        List<String> lines = config.getStringList("broadcast");
        if (lines.isEmpty()) {
            lines = List.of("&#FF0000&lLIVE &8▷ &f%player% &7is streaming! &a&l%link%");
        }
        Component message = buildBroadcast(playerName, url, lines);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!isLiveShown(viewer)) continue;
            viewer.sendMessage(message);
        }
    }

    private Component buildBroadcast(String playerName, String url, List<String> lines) {
        List<Component> parts = new ArrayList<>(lines.size());
        for (String line : lines) {
            parts.add(formatLine(line, playerName, url));
        }
        return Component.join(net.kyori.adventure.text.JoinConfiguration.newlines(), parts);
    }

    private Component formatLine(String template, String playerName, String url) {
        if (!template.contains("%link%")) {
            return Text.c(Text.apply(template, "%player%", playerName, "%link%", url));
        }
        String[] split = template.split("%link%", -1);
        Component result = Component.empty();
        for (int i = 0; i < split.length; i++) {
            result = result.append(Text.c(Text.apply(split[i], "%player%", playerName)));
            if (i < split.length - 1) {
                result = result.append(
                        Text.c(url)
                                .clickEvent(ClickEvent.openUrl(url))
                                .hoverEvent(HoverEvent.showText(Text.c(raw("click-hover", "%link%", url)))));
            }
        }
        return result;
    }

    private boolean isAllowedPlatform(String url) {
        List<String> platforms = config.getStringList("platforms");
        if (platforms.isEmpty()) return false;
        try {
            URI uri = URI.create(normalizeUrl(url));
            String host = uri.getHost();
            if (host == null || host.isBlank()) return false;
            host = host.toLowerCase(Locale.ROOT);
            if (host.startsWith("www.")) host = host.substring(4);
            for (String platform : platforms) {
                if (platform == null || platform.isBlank()) continue;
                String allowed = platform.toLowerCase(Locale.ROOT).trim();
                if (host.equals(allowed) || host.endsWith("." + allowed)) return true;
            }
        } catch (IllegalArgumentException ignored) {
        }
        return false;
    }

    private String normalizeUrl(String url) {
        String trimmed = url.trim();
        if (trimmed.isEmpty()) return trimmed;
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed;
        return "https://" + trimmed;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return TabCompleteHelper.filter(args[0], "toggle");
        }
        return List.of();
    }
}
