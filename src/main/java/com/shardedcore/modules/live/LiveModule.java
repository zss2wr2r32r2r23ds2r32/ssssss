package com.shardedcore.modules.live;

import com.shardedcore.ShardedCore;
import com.shardedcore.module.Module;
import com.shardedcore.modules.settings.SettingsModule;
import com.shardedcore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LiveModule extends Module implements CommandExecutor {

    private final Map<UUID, Long> cooldown = new ConcurrentHashMap<>();

    public LiveModule(ShardedCore plugin) {
        super(plugin, "live");
    }

    @Override
    public void enable() {
        registerCommand("live", this);
        registerCommand("livetoggle", this);
    }

    @Override
    public void disable() {
        cleanup();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sendRaw(sender, "&#FF0000&lERROR &8▷ &fOnly a player can use that.");
            return true;
        }
        if (command.getName().equalsIgnoreCase("livetoggle")
                || (args.length > 0 && args[0].equalsIgnoreCase("toggle"))) {
            SettingsModule settings = plugin.modules().get(SettingsModule.class);
            if (settings == null) return true;
            boolean next = settings.flipLive(player);
            send(player, next ? "messages.toggle-on" : "messages.toggle-off");
            return true;
        }
        if (!player.hasPermission("shardedcore.live")) {
            sendRaw(player, "&#FF0000&lERROR &8▷ &fYou do not have permission.");
            return true;
        }
        if (args.length == 0) {
            send(player, "messages.usage");
            return true;
        }
        String link = args[0];
        if (!allowed(link)) {
            send(player, "messages.invalid-platform");
            return true;
        }
        long wait = config.getLong("cooldown-seconds", 600) * 1000L;
        Long last = cooldown.get(player.getUniqueId());
        if (last != null && System.currentTimeMillis() - last < wait && !player.hasPermission("shardedcore.live.bypass")) {
            send(player, "messages.cooldown", "seconds",
                    String.valueOf((wait - (System.currentTimeMillis() - last) + 999) / 1000));
            return true;
        }
        cooldown.put(player.getUniqueId(), System.currentTimeMillis());
        List<String> lines = Text.applyList(config.getStringList("message"), "player", player.getName(), "link", link);
        SettingsModule settings = plugin.modules().get(SettingsModule.class);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (settings != null && !settings.live(viewer)) continue;
            sendLines(viewer, lines, link.startsWith("http") ? link : "https://" + link);
        }
        return true;
    }

    private boolean allowed(String link) {
        String lower = link.toLowerCase(Locale.ROOT);
        try {
            String host = URI.create(lower.startsWith("http") ? lower : "https://" + lower).getHost();
            if (host == null) return false;
            for (String platform : config.getStringList("platforms")) {
                if (host.equals(platform) || host.endsWith("." + platform)) return true;
            }
        } catch (Exception ignored) {
            return false;
        }
        return false;
    }
}
