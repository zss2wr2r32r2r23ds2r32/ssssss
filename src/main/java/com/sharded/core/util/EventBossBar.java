package com.sharded.core.util;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Single global boss bar for KOTH / Outpost events. */
public final class EventBossBar {

    private BossBar bar;
    private String owner = "";

    public void show(String id, String title, BarColor color, double progress) {
        String parsed = Text.legacySection(title);
        if (bar == null || !id.equals(owner)) {
            hide();
            bar = Bukkit.createBossBar(parsed, color, BarStyle.SOLID);
            owner = id;
        } else {
            bar.setTitle(parsed);
            bar.setColor(color);
        }
        bar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!bar.getPlayers().contains(player)) bar.addPlayer(player);
        }
    }

    public void hide(String id) {
        if (!id.equals(owner)) return;
        hide();
    }

    public void hide() {
        if (bar == null) return;
        bar.removeAll();
        bar = null;
        owner = "";
    }

    public void syncPlayers() {
        if (bar == null) return;
        Set<UUID> online = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) online.add(player.getUniqueId());
        for (Player p : bar.getPlayers()) {
            if (!online.contains(p.getUniqueId())) bar.removePlayer(p);
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!bar.getPlayers().contains(player)) bar.addPlayer(player);
        }
    }

    public void shutdown() {
        hide();
    }
}
