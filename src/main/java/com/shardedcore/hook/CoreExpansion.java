package com.shardedcore.hook;

import com.shardedcore.ShardedCore;
import com.shardedcore.modules.economy.EconomyModule;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class CoreExpansion extends PlaceholderExpansion {

    private final ShardedCore plugin;

    public CoreExpansion(ShardedCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "shardedcore";
    }

    @Override
    public @NotNull String getAuthor() {
        return "ShardedMC";
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
        if (player == null) return "";
        EconomyModule economy = plugin.modules().get(EconomyModule.class);
        return switch (params.toLowerCase()) {
            case "prefix" -> plugin.prefix();
            case "ping" -> String.valueOf(player.getPing());
            case "balance", "lifestealcore_balance" ->
                    economy == null ? "0" : String.valueOf((long) economy.service().get(player.getUniqueId()));
            case "balance_formatted", "lifestealcore_balance_formatted" ->
                    economy == null ? "0" : economy.service().format(economy.service().get(player.getUniqueId()));
            case "module_announce" -> plugin.modules().isEnabled("announce") ? "true" : "false";
            default -> {
                if (params.startsWith("module_")) {
                    yield plugin.modules().isEnabled(params.substring(7)) ? "enabled" : "disabled";
                }
                yield null;
            }
        };
    }
}
