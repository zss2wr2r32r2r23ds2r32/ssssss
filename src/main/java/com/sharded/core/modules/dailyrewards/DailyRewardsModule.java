package com.sharded.core.modules.dailyrewards;

import com.sharded.core.ShardedCore;
import com.sharded.core.gui.RewardPickerGui;
import com.sharded.core.module.Module;
import com.sharded.core.util.Text;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.Locale;

/** Daily reward picker GUI. */
public final class DailyRewardsModule extends Module implements CommandExecutor {

    private static final String LAST_CLAIM = "daily-reward-last";
    private RewardPickerGui picker;

    public DailyRewardsModule(ShardedCore plugin) {
        super(plugin, "dailyrewards");
    }

    @Override
    protected void onEnable() {
        picker = new RewardPickerGui("daily", config);
        registerCommand("dailyrewards", this);
        registerCommand("daily", this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("sharded.dailyrewards.use")) {
            send(player, "no-permission");
            return true;
        }
        long cooldownMs = config.getLong("cooldown-hours", 24L) * 3_600_000L;
        long now = System.currentTimeMillis();
        long last = plugin.stateStore().getLong(player.getUniqueId(), LAST_CLAIM, 0L);
        if (last > 0 && now - last < cooldownMs) {
            long left = (cooldownMs - (now - last)) / 1000L;
            send(player, "cooldown", "%time%", Text.time(left));
            return true;
        }
        picker.open(player);
        return true;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof RewardPickerGui.Holder holder)) return;
        if (!holder.pickerId.equals("daily")) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        RewardPickerGui.RewardOption option = picker.optionAt(event.getSlot());
        if (option == null) return;

        long cooldownMs = config.getLong("cooldown-hours", 24L) * 3_600_000L;
        long now = System.currentTimeMillis();
        long last = plugin.stateStore().getLong(player.getUniqueId(), LAST_CLAIM, 0L);
        if (last > 0 && now - last < cooldownMs) {
            send(player, "cooldown", "%time%", Text.time((cooldownMs - (now - last)) / 1000L));
            player.closeInventory();
            return;
        }

        RewardPickerGui.grant(player, option, (p, reward) -> {
            plugin.stateStore().setLong(p.getUniqueId(), LAST_CLAIM, System.currentTimeMillis());
            send(p, "claimed", "%reward%", reward.display(), "%rarity%", reward.rarityLabel());
            playSound(p, config.getString("sounds.win", "ENTITY_PLAYER_LEVELUP"));
            p.closeInventory();
        });
    }

    private void playSound(Player player, String name) {
        try {
            player.playSound(player.getLocation(), Sound.valueOf(name.toUpperCase(Locale.ROOT)), 1f, 1f);
        } catch (IllegalArgumentException ignored) {
        }
    }
}
