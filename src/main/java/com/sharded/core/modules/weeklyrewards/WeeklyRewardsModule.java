package com.sharded.core.modules.weeklyrewards;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/** Weekly reward wheel with action-bar spin animation. */
public final class WeeklyRewardsModule extends Module implements CommandExecutor {

    private static final String LAST_CLAIM = "weekly-reward-last";

    public WeeklyRewardsModule(ShardedCore plugin) {
        super(plugin, "weeklyrewards");
    }

    @Override
    protected void onEnable() {
        registerCommand("weeklyrewards", this);
        registerCommand("weekly", this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("sharded.weeklyrewards.use")) {
            send(player, "no-permission");
            return true;
        }
        long cooldownMs = config.getLong("cooldown-hours", 168L) * 3_600_000L;
        long now = System.currentTimeMillis();
        long last = plugin.stateStore().getLong(player.getUniqueId(), LAST_CLAIM, 0L);
        if (last > 0 && now - last < cooldownMs) {
            long left = (cooldownMs - (now - last)) / 1000L;
            send(player, "cooldown", "%time%", Text.timeDaysHours(left));
            return true;
        }
        List<RewardOption> options = loadOptions();
        if (options.isEmpty()) {
            send(player, "no-rewards");
            return true;
        }
        spinAndReward(player, options);
        return true;
    }

    private List<RewardOption> loadOptions() {
        List<RewardOption> list = new ArrayList<>();
        ConfigurationSection section = config.getConfigurationSection("rewards");
        if (section == null) return list;
        for (String key : section.getKeys(false)) {
            ConfigurationSection r = section.getConfigurationSection(key);
            if (r == null) continue;
            list.add(new RewardOption(
                    r.getString("display", key),
                    r.getString("rarity-label", "Common"),
                    r.getDouble("weight", 1.0),
                    r.getStringList("commands")));
        }
        return list;
    }

    private void spinAndReward(Player player, List<RewardOption> options) {
        RewardOption winner = weightedPick(options);
        int spins = config.getInt("spin-ticks", 40);
        BukkitTask[] holder = new BukkitTask[1];
        holder[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            int tick = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    holder[0].cancel();
                    return;
                }
                RewardOption shown = tick >= spins - 1 ? winner : options.get(ThreadLocalRandom.current().nextInt(options.size()));
                String bar = config.getString("spin-format", "&6Weekly spin... &f%reward% &8[%rarity%&8]")
                        .replace("%reward%", shown.display())
                        .replace("%rarity%", shown.rarity());
                player.sendActionBar(Text.c(bar));
                tick++;
                if (tick >= spins) {
                    holder[0].cancel();
                    grant(player, winner);
                    plugin.stateStore().setLong(player.getUniqueId(), LAST_CLAIM, System.currentTimeMillis());
                    send(player, "won", "%reward%", winner.display(), "%rarity%", winner.rarity());
                    playSound(player, config.getString("sounds.win", "ENTITY_PLAYER_LEVELUP"));
                }
            }
        }, 0L, 2L);
        playSound(player, config.getString("sounds.spin", "UI_BUTTON_CLICK"));
    }

    private void grant(Player player, RewardOption reward) {
        for (String cmd : reward.commands()) {
            cmd = cmd.replace("%player%", player.getName())
                    .replace("%uuid%", player.getUniqueId().toString())
                    .replace("%player_name%", player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }
    }

    private RewardOption weightedPick(List<RewardOption> options) {
        double total = options.stream().mapToDouble(RewardOption::weight).sum();
        double roll = ThreadLocalRandom.current().nextDouble(total);
        double acc = 0;
        for (RewardOption option : options) {
            acc += option.weight();
            if (roll <= acc) return option;
        }
        return options.get(options.size() - 1);
    }

    private void playSound(Player player, String name) {
        try {
            player.playSound(player.getLocation(), Sound.valueOf(name.toUpperCase(Locale.ROOT)), 1f, 1f);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private record RewardOption(String display, String rarity, double weight, List<String> commands) {
    }
}
