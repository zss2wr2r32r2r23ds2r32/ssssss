package com.shardedcore.modules.wipe;

import com.shardedcore.ShardedCore;
import com.shardedcore.gui.Menus;
import com.shardedcore.module.Module;
import com.shardedcore.modules.chatcolor.ChatColorModule;
import com.shardedcore.modules.crates.CratesModule;
import com.shardedcore.modules.crystals.CrystalsModule;
import com.shardedcore.modules.economy.EconomyModule;
import com.shardedcore.modules.enderchest.EnderchestModule;
import com.shardedcore.modules.glows.GlowsModule;
import com.shardedcore.modules.orders.OrdersModule;
import com.shardedcore.modules.spawn.SpawnModule;
import com.shardedcore.modules.tags.TagsModule;
import com.shardedcore.modules.teams.TeamsModule;
import com.shardedcore.modules.vaults.VaultsModule;
import com.shardedcore.util.Items;
import com.shardedcore.util.Players;
import com.shardedcore.util.Tabs;
import com.shardedcore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

public final class WipeModule extends Module implements CommandExecutor, TabCompleter {

    public WipeModule(ShardedCore plugin) {
        super(plugin, "wipe");
    }

    @Override
    public void enable() {
        registerCommand("wipe", this);
    }

    @Override
    public void disable() {
        cleanup();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("shardedcore.wipe")) {
            send(sender, "no-permission");
            return true;
        }
        if (args.length < 1) {
            send(sender, "usage");
            return true;
        }
        OfflinePlayer target = Players.offline(args[0]);
        if (target == null || target.getUniqueId() == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            send(sender, "unknown-player", "player", args[0]);
            return true;
        }
        if (!(sender instanceof Player player)) {
            wipe(target);
            send(sender, "wiped", "player", Players.name(target));
            return true;
        }
        openConfirm(player, target);
        return true;
    }

    private void openConfirm(Player viewer, OfflinePlayer target) {
        String name = Players.name(target);
        Menus.Menu menu = plugin.menus().create(viewer,
                Text.apply(cfg("gui.title", "&8Wipe %player%?"), "player", name),
                config.getInt("gui.rows", 3));
        List<String> headLore = Items.lore(config, "gui.head.lore", List.of("&7Wipe %player%"), "player", name);
        ItemStack head = target.isOnline()
                ? Items.head(target.getPlayer(), Text.apply(cfg("gui.head.name", "&c%player%"), "player", name), headLore)
                : Items.named(Material.PLAYER_HEAD, Text.apply(cfg("gui.head.name", "&c%player%"), "player", name), headLore);
        menu.set(config.getInt("gui.head.slot", 13), head);
        ConfigurationSection confirm = config.getConfigurationSection("gui.confirm");
        ConfigurationSection cancel = config.getConfigurationSection("gui.cancel");
        menu.set(confirm == null ? 11 : confirm.getInt("slot", 11),
                confirm == null ? Items.named(Material.LIME_DYE, "&#9FFF00&lConfirm", List.of())
                        : Items.fromSection(confirm, viewer, "player", name),
                event -> {
                    event.setCancelled(true);
                    viewer.closeInventory();
                    wipe(target);
                    send(viewer, "wiped", "player", name);
                });
        menu.set(cancel == null ? 15 : cancel.getInt("slot", 15),
                cancel == null ? Items.named(Material.RED_DYE, "&#FF2727&lCancel", List.of())
                        : Items.fromSection(cancel, viewer, "player", name),
                event -> {
                    event.setCancelled(true);
                    viewer.closeInventory();
                });
        if (config.getBoolean("gui.filler.enabled", true)) {
            menu.fill(Items.fromSection(config.getConfigurationSection("gui.filler"), viewer));
        }
        plugin.menus().open(viewer, menu);
    }

    public void wipe(OfflinePlayer target) {
        UUID uuid = target.getUniqueId();
        Player online = target.getPlayer();
        if (online != null) {
            online.setStatistic(Statistic.PLAYER_KILLS, 0);
            online.setStatistic(Statistic.DEATHS, 0);
            online.setStatistic(Statistic.PLAY_ONE_MINUTE, 0);
            online.getEnderChest().clear();
            SpawnModule spawn = plugin.modules().get(SpawnModule.class);
            if (spawn != null && spawn.location() != null) {
                online.teleport(spawn.location());
            }
        }
        EconomyModule economy = plugin.modules().get(EconomyModule.class);
        if (economy != null) economy.service().set(uuid, 0);
        CrystalsModule crystals = plugin.modules().get(CrystalsModule.class);
        if (crystals != null) crystals.service().set(uuid, 0);
        TeamsModule teams = plugin.modules().get(TeamsModule.class);
        if (teams != null) teams.wipe(uuid);
        VaultsModule vaults = plugin.modules().get(VaultsModule.class);
        if (vaults != null) vaults.wipe(uuid);
        EnderchestModule enderchest = plugin.modules().get(EnderchestModule.class);
        if (enderchest != null) enderchest.wipe(uuid);
        CratesModule crates = plugin.modules().get(CratesModule.class);
        if (crates != null) crates.wipe(uuid);
        OrdersModule orders = plugin.modules().get(OrdersModule.class);
        if (orders != null) orders.wipe(uuid);
        TagsModule tags = plugin.modules().get(TagsModule.class);
        if (tags != null) tags.wipe(uuid);
        ChatColorModule colors = plugin.modules().get(ChatColorModule.class);
        if (colors != null) colors.wipe(uuid);
        GlowsModule glows = plugin.modules().get(GlowsModule.class);
        if (glows != null) glows.wipe(uuid);
        if (online != null && online.isOnline()) {
            online.kick(com.shardedcore.util.ColorUtil.parse(cfg("kick", "&cYou was kicked due to being Wiped.")));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("shardedcore.wipe")) return Tabs.players(args[0]);
        return List.of();
    }
}
