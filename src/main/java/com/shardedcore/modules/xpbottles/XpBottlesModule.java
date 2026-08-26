package com.shardedcore.modules.xpbottles;

import com.shardedcore.ShardedCore;
import com.shardedcore.module.Module;
import com.destroystokyo.paper.event.entity.ExperienceOrbMergeEvent;
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
        if (!config.getBoolean("convert-merges", true)) return;
        ExperienceOrb target = event.getMergeTarget();
        ExperienceOrb source = event.getMergeSource();
        int total = target.getExperience() + source.getExperience();
        int per = Math.max(1, config.getInt("experience-per-bottle", 7));
        int min = config.getInt("min-experience", per * config.getInt("min-bottles", 8));
        if (total < min) return;
        event.setCancelled(true);
        convert(target, total, per);
        source.remove();
    }

    @EventHandler
    public void onSpawn(EntitySpawnEvent event) {
        if (!config.getBoolean("convert-spawns", true)) return;
        if (!(event.getEntity() instanceof ExperienceOrb orb)) return;
        int per = Math.max(1, config.getInt("experience-per-bottle", 7));
        int min = config.getInt("min-experience", per * config.getInt("min-bottles", 8));
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            if (!orb.isValid()) return;
            if (orb.getExperience() < min) return;
            convert(orb, orb.getExperience(), per);
        });
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
        } else {
            orb.remove();
        }
    }
}
