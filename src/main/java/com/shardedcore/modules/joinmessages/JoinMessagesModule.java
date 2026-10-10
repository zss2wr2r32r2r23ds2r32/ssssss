package com.shardedcore.modules.joinmessages;

import com.shardedcore.ShardedCore;
import com.shardedcore.module.Module;
import com.shardedcore.modules.settings.SettingsModule;
import com.shardedcore.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.io.IOException;
import org.bukkit.configuration.file.YamlConfiguration;

public final class JoinMessagesModule extends Module implements Listener {

    private int counter;
    private File counterFile;

    public JoinMessagesModule(ShardedCore plugin) {
        super(plugin, "joinmessages");
    }

    @Override
    public void enable() {
        counterFile = new File(folder, "counter.yml");
        YamlConfiguration yaml = counterFile.exists() ? YamlConfiguration.loadConfiguration(counterFile) : new YamlConfiguration();
        counter = yaml.getInt("counter", 0);
        registerListener(this);
    }

    @Override
    public void disable() {
        saveCounter();
        cleanup();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        event.joinMessage(null);
        Player player = event.getPlayer();
        SettingsModule settings = plugin.modules().get(SettingsModule.class);
        if (!player.hasPlayedBefore()) {
            counter++;
            saveCounter();
            String first = cfg("message", "")
                    .replace("%player%", player.getName())
                    .replace("%counter%", String.valueOf(counter));
            Bukkit.getOnlinePlayers().forEach(viewer -> {
                if (settings == null || settings.joinLeave(viewer)) viewer.sendMessage(ColorUtil.parse(first));
            });
            return;
        }
        if (!config.getBoolean("join.enabled", true)) return;
        String line = cfg("join.message", "").replace("%player%", player.getName());
        Bukkit.getOnlinePlayers().forEach(viewer -> {
            if (settings == null || settings.joinLeave(viewer)) viewer.sendMessage(ColorUtil.parse(line));
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        event.quitMessage(null);
        if (!config.getBoolean("leave.enabled", true)) return;
        SettingsModule settings = plugin.modules().get(SettingsModule.class);
        String line = cfg("leave.message", "").replace("%player%", event.getPlayer().getName());
        Bukkit.getOnlinePlayers().forEach(viewer -> {
            if (settings == null || settings.joinLeave(viewer)) viewer.sendMessage(ColorUtil.parse(line));
        });
    }

    public int counter() {
        return counter;
    }

    private void saveCounter() {
        if (counterFile == null) return;
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("counter", counter);
        try {
            yaml.save(counterFile);
        } catch (IOException ignored) {
        }
    }
}
