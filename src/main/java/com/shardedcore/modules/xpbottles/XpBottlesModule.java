package com.shardedcore.modules.xpbottles;

import com.shardedcore.ShardedCore;
import com.shardedcore.module.Module;
import com.destroystokyo.paper.event.entity.ExperienceOrbMergeEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.inventory.ItemStack;

public final class XpBottlesModule extends Module implements Listener {

    public XpBottlesModule(ShardedCore plugin) {
        super(plugin, "xpbottles");
    }

    @Override
    public void enable() {
        registerListener(this);
    }

    @Override
    public void disable() {
        cleanup();
    }

    @EventHandler
    public void onMerge(ExperienceOrbMergeEvent event) {
        if (config.getBoolean("combine-orbs", true)) return;
        if (!config.getBoolean("convert-merges", false)) return;
        ExperienceOrb target = event.getMergeTarget();
        ExperienceOrb source = event.getMergeSource();
        int total = xp(target) + xp(source);
        int per = Math.max(1, config.getInt("experience-per-bottle", 7));
        int min = config.getInt("min-experience", per * config.getInt("min-bottles", 2));
        if (total < min) return;
        event.setCancelled(true);
        convert(target, total, per);
        source.remove();
    }

    @EventHandler
    public void onSpawn(EntitySpawnEvent event) {
        if (!(event.getEntity() instanceof ExperienceOrb orb)) return;
        if (config.getBoolean("combine-orbs", true)) {
            Bukkit.getScheduler().runTask(plugin, () -> mergeNearby(orb));
            return;
        }
        if (!config.getBoolean("convert-spawns", false)) return;
        int per = Math.max(1, config.getInt("experience-per-bottle", 7));
        int min = config.getInt("min-experience", per * config.getInt("min-bottles", 2));
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!orb.isValid()) return;
            int total = xp(orb);
            if (total < min) return;
            convert(orb, total, per);
        });
    }

    private void mergeNearby(ExperienceOrb orb) {
        if (!orb.isValid() || orb.getWorld() == null) return;
        double radius = Math.max(1D, config.getDouble("combine-radius", 4));
        ExperienceOrb target = null;
        for (org.bukkit.entity.Entity entity : orb.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof ExperienceOrb other && other != orb && other.isValid()) {
                target = other;
                break;
            }
        }
        if (target == null) return;
        target.setExperience(xp(target) + xp(orb));
        try {
            target.setCount(1);
        } catch (Throwable ignored) {
        }
        orb.remove();
    }

    private int xp(ExperienceOrb orb) {
        int experience = Math.max(0, orb.getExperience());
        int count = 1;
        try {
            count = Math.max(1, orb.getCount());
        } catch (Throwable ignored) {
        }
        return Math.max(1, experience) * count;
    }

    private void convert(ExperienceOrb orb, int total, int per) {
        if (orb.getWorld() == null) return;
        int bottles = Math.min(config.getInt("max-bottles", 64), total / per);
        int leftover = total - bottles * per;
        if (bottles <= 0) return;
        while (bottles > 0) {
            int stack = Math.min(64, bottles);
            orb.getWorld().dropItemNaturally(orb.getLocation(), new ItemStack(Material.EXPERIENCE_BOTTLE, stack));
            bottles -= stack;
        }
        if (leftover > 0 && config.getBoolean("keep-leftover-orb", true)) {
            orb.setExperience(leftover);
            try {
                orb.setCount(1);
            } catch (Throwable ignored) {
            }
        } else {
            orb.remove();
        }
    }
}
