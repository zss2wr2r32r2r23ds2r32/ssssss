package com.shardedcore.hook;

import com.shardedcore.ShardedCore;
import com.shardedcore.modules.economy.EconomyModule;
import com.shardedcore.modules.joincounter.JoinCounterModule;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ShardedCoreExpansion extends PlaceholderExpansion {

    private final ShardedCore plugin;

    public ShardedCoreExpansion(ShardedCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "shardedcore";
    }

    @Override
    public @NotNull String getAuthor() {
        return "ShardedCore";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (params.equalsIgnoreCase("prefix")) {
            return plugin.prefix();
        }

        if (params.equalsIgnoreCase("balance") || params.equalsIgnoreCase("balance_raw")) {
            EconomyModule economy = plugin.modules().get(EconomyModule.class);
            if (economy == null || player == null) {
                return "0";
            }
            return String.valueOf(economy.service().getBalance(player.getUniqueId()));
        }

        if (params.equalsIgnoreCase("balance_formatted")) {
            EconomyModule economy = plugin.modules().get(EconomyModule.class);
            if (economy == null || player == null) {
                return "0";
            }
            return economy.formatBalance(economy.service().getBalance(player.getUniqueId()));
        }

        if (params.equalsIgnoreCase("ping")) {
            if (player == null) {
                return "0";
            }
            return String.valueOf(player.getPing());
        }

        if (params.equalsIgnoreCase("join_counter") || params.equalsIgnoreCase("join_number")) {
            JoinCounterModule joinCounter = plugin.modules().get(JoinCounterModule.class);
            return String.valueOf(joinCounter == null ? 0L : joinCounter.counter());
        }

        if (params.startsWith("module_")) {
            String moduleId = params.substring("module_".length());
            return plugin.modules().isEnabled(moduleId) ? "true" : "false";
        }

        return null;
    }
}
